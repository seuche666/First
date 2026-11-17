package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.TrackChunkEntity
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import de.fernfahrer.fahrtenbuch.domain.geo.TrackFilter
import de.fernfahrer.fahrtenbuch.domain.geo.TrackFilter.Decision
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint
import de.fernfahrer.fahrtenbuch.work.TrackRecorder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Aufzeichnung: Speicherformat, Filter (Abstand, Genauigkeit, Ausreißer, Funkloch), Stücke, Vereinfachung. */
class TrackRecordingTest {

    // ---------- Hilfen: simulierte Fahrt ----------

    private val metersPerDegLat = 111_195.0
    private fun metersPerDegLon(lat: Double) = metersPerDegLat * cos(Math.toRadians(lat))

    /** Punkt [eastM]/[northM] Meter vom Ursprung (München). */
    private fun at(eastM: Double, northM: Double, lat0: Double = 48.137, lon0: Double = 11.575) =
        (lat0 + northM / metersPerDegLat) to (lon0 + eastM / metersPerDegLon(lat0))

    private class Route(val fixes: List<TrackFilter.Fix>, val truePath: List<Pair<Double, Double>>)

    /**
     * Fahrt mit Messung jede Sekunde: 1 km geradeaus (90 km/h), Kreisverkehr (Radius 20 m, 25 km/h,
     * 3/4 Umrundung), dann eine enge Kurve (Radius 60 m, 40 km/h) und wieder geradeaus (90 km/h).
     */
    private fun drive(startMs: Long = 1_700_000_000_000L, accuracy: Float = 5f): Route {
        val fixes = ArrayList<TrackFilter.Fix>()
        val path = ArrayList<Pair<Double, Double>>()
        var t = startMs
        fun add(e: Double, n: Double) {
            val (lat, lon) = at(e, n)
            fixes += TrackFilter.Fix(lat, lon, accuracy, t)
            path += lat to lon
            t += 1000
        }
        // geradeaus nach Osten, 25 m/s
        var e = 0.0
        while (e < 1000) { add(e, 0.0); e += 25.0 }
        // Kreisverkehr: Mittelpunkt (1000, 20), Einfahrt von Süden, 7 m/s
        val r = 20.0
        var a = -PI / 2
        while (a < PI) { add(1000 + r * cos(a), 20 + r * sin(a)); a += 7.0 / r }
        // Ausfahrt im Westen (980, 20), dann Kurve Radius 60 m (11 m/s) Richtung Westen
        a = 0.0
        while (a > -PI / 2) { add(920 + 60 * cos(a), 20 + 60 * sin(a)); a -= 11.0 / 60 }
        // geradeaus nach Westen
        var x = 920.0
        while (x > -200) { add(x, -40.0); x -= 25.0 }
        return Route(fixes, path)
    }

    // ---------- Speicherformat ----------

    @Test
    fun speicherformatHinUndZurueckUndKompakt() {
        val points = drive().fixes.mapIndexed { i, f -> TrackPoint.of(f.lat, f.lon, f.timeMs / 1000) }
        val extreme = listOf(TrackPoint.of(-33.9, 151.2, 0), TrackPoint.of(71.17, -25.0, 4_000_000_000L), TrackPoint.of(0.0, 0.0, 5))
        for (list in listOf(points, extreme, emptyList())) {
            assertEquals(list, TrackCodec.decode(TrackCodec.encode(list)).toList())
        }
        // Punkte im Abstand von 25 m: deutlich weniger als 6 Byte pro Punkt
        assertTrue(TrackCodec.encode(points).size < points.size * 6)
    }

    // ---------- Filter ----------

    @Test
    fun punkteAlle30Bis50MeterAuchInKurvenUndKreisverkehr() {
        val route = drive()
        val filter = TrackFilter()
        val kept = ArrayList<TrackFilter.Fix>()
        for (f in route.fixes) {
            if (filter.check(f).keep) {
                filter.inserted?.let { kept += it }
                kept += f
            }
        }
        assertTrue(kept.size > 40)
        val gaps = (1 until kept.size).map { i -> Geo.distanceM(kept[i - 1].lat, kept[i - 1].lon, kept[i].lat, kept[i].lon) }
        assertTrue("Abstände $gaps", gaps.all { it in 20.0..56.0 })
        assertTrue("Abstände $gaps", gaps.count { it >= 30.0 } >= gaps.size * 0.9)
        // Kreisverkehr (Umfang 125 m, 3/4 befahren): mindestens 2 Punkte im Kreis
        val inRoundabout = kept.count { f ->
            val (lat, lon) = at(1000.0, 20.0)
            Geo.distanceM(lat, lon, f.lat, f.lon) in 15.0..25.0
        }
        assertTrue("Punkte im Kreisverkehr: $inRoundabout", inRoundabout >= 2)
    }

    @Test
    fun ungenauePunkteWerdenVerworfen() {
        val filter = TrackFilter()
        val (lat, lon) = at(0.0, 0.0)
        assertEquals(Decision.INACCURATE, filter.check(TrackFilter.Fix(lat, lon, 80f, 0)))
        assertEquals(Decision.INACCURATE, filter.check(TrackFilter.Fix(lat, lon, null, 0)))
        assertEquals(Decision.FIRST, filter.check(TrackFilter.Fix(lat, lon, 8f, 1000)))
        val (lat2, lon2) = at(50.0, 0.0)
        assertEquals(Decision.INACCURATE, filter.check(TrackFilter.Fix(lat2, lon2, 26f, 3000)))
        assertEquals(Decision.ACCEPT, filter.check(TrackFilter.Fix(lat2, lon2, 12f, 4000)))
    }

    @Test
    fun ausreisserWirdVerworfen() {
        val filter = TrackFilter()
        val p0 = at(0.0, 0.0)
        val jump = at(0.0, 2000.0)
        val p1 = at(50.0, 0.0)
        assertEquals(Decision.FIRST, filter.check(TrackFilter.Fix(p0.first, p0.second, 5f, 0)))
        assertEquals(Decision.JUMP, filter.check(TrackFilter.Fix(jump.first, jump.second, 5f, 1000)))
        assertEquals(Decision.ACCEPT, filter.check(TrackFilter.Fix(p1.first, p1.second, 5f, 2000)))
    }

    @Test
    fun funklochPlausibelVerbindenSonstNeuesTeilstueck() {
        val filter = TrackFilter()
        val p0 = at(0.0, 0.0)
        filter.check(TrackFilter.Fix(p0.first, p0.second, 5f, 0))
        // Tunnel: 3 km in 3 Minuten (60 km/h) → verbinden
        val p1 = at(3000.0, 0.0)
        assertEquals(Decision.ACCEPT, filter.check(TrackFilter.Fix(p1.first, p1.second, 5f, 180_000)))
        // Aufzeichnung war unterbrochen: 80 km weiter → nicht quer übers Land verbinden
        val p2 = at(83_000.0, 0.0)
        assertEquals(Decision.BREAK, filter.check(TrackFilter.Fix(p2.first, p2.second, 5f, 180_000 + 1_800_000)))
    }

    // ---------- Speichern in Stücken ----------

    private class FakeStore {
        val chunks = LinkedHashMap<Long, TrackChunkEntity>()
        var saves = 0
        private var nextId = 1L
        suspend fun save(c: TrackChunkEntity): Long {
            saves++
            val id = if (c.id == 0L) nextId++ else c.id
            chunks[id] = c.copy(id = id)
            return id
        }
    }

    /** Lange gerade Fahrt mit Messung jede Sekunde, alle 40 m. */
    private fun straightFixes(count: Int, startMs: Long = 1_700_000_000_000L, northOffset: Double = 0.0) = (0 until count).map { i ->
        val (lat, lon) = at(i * 40.0, northOffset)
        TrackFilter.Fix(lat, lon, 4f, startMs + i * 1000L)
    }

    @Test
    fun aufzeichnungInStueckenDurchgehendUndRegelmaessigGespeichert() = runBlocking {
        val store = FakeStore()
        val recorder = TrackRecorder(save = store::save, countryAt = { _, _ -> "DE" }, nowMs = { 0L })
        val fixes = straightFixes(1203)
        for ((i, f) in fixes.withIndex()) {
            recorder.onFix(f)
            // Spätestens alle 10 Punkte gespeichert: nie mehr als 10 Punkte gehen bei einem Absturz verloren
            val saved = store.chunks.values.sumOf { it.pointCount } - (store.chunks.size - 1)
            assertTrue("nach Punkt $i nur $saved gespeichert", i + 1 - saved < TrackRecorder.SAVE_EVERY_POINTS)
        }
        recorder.flush()
        val chunks = store.chunks.values.sortedBy { it.seq }
        assertEquals(3, chunks.size)
        assertEquals(listOf(500, 500, 205), chunks.map { it.pointCount })
        // Folgestück beginnt mit dem letzten Punkt des vorigen → durchgehende Linie
        for (i in 1 until chunks.size) {
            val prev = TrackCodec.decode(chunks[i - 1].data)
            val next = TrackCodec.decode(chunks[i].data)
            assertEquals(prev.point(prev.size - 1), next.point(0))
        }
        assertEquals(1203, chunks.sumOf { it.pointCount } - 2)
        assertTrue(chunks.all { it.countries == "DE" && it.trackId == fixes.first().timeMs })
        assertEquals(1202 * 40.0, chunks.sumOf { it.lengthMeters }, 5.0)
    }

    @Test
    fun nachUnterbrechungNeuesStueckOhneVerbindung() = runBlocking {
        val store = FakeStore()
        val recorder = TrackRecorder(save = store::save)
        straightFixes(20).forEach { recorder.onFix(it) }
        // 2 Stunden später, 50 km weiter nördlich
        straightFixes(20, startMs = 1_700_000_000_000L + 7_200_000L, northOffset = 50_000.0).forEach { recorder.onFix(it) }
        recorder.flush()
        val chunks = store.chunks.values.sortedBy { it.seq }
        assertEquals(listOf(20, 20), chunks.map { it.pointCount })
        val first = TrackCodec.decode(chunks[0].data)
        val second = TrackCodec.decode(chunks[1].data)
        assertTrue(Geo.distanceM(first.point(first.size - 1), second.point(0)) > 40_000)
    }

    // ---------- Vereinfachung ----------

    @Test
    fun vereinfachungBeimRauszoomen() {
        val route = drive()
        val lat = IntArray(route.truePath.size) { Math.round(route.truePath[it].first * TrackPoint.E5).toInt() }
        val lon = IntArray(route.truePath.size) { Math.round(route.truePath[it].second * TrackPoint.E5).toInt() }
        val c = FogGeometry.chunk(1, 1, 0, lat, lon)
        val sizes = (0 until FogGeometry.LEVELS).map { c.size(it) }
        // Europa-Ansicht: nur noch 2–3 Punkte; feinste Stufe behält die Kurven
        assertTrue(sizes[0] <= 3)
        assertTrue(sizes.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(sizes[3] > 20)
        // Feinste Stufe weicht höchstens ≈ 1 m vom Original ab
        val last = FogGeometry.LEVELS - 1
        for (i in 0 until c.size(last)) {
            val la = Geo.latFromMercatorY(c.y(last, i))
            val lo = Geo.lonFromMercatorX(c.x(last, i))
            val nearest = route.truePath.minOf { Geo.distanceM(la, lo, it.first, it.second) }
            assertTrue(nearest < 1.5)
        }
        assertEquals(0, FogGeometry.levelFor(10_000.0))
        assertEquals(3, FogGeometry.levelFor(100_000_000.0))
    }
}
