package de.fernfahrer.fahrtenbuch

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayCountries
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.geo.BorderDetector
import de.fernfahrer.fahrtenbuch.location.CountryLocator
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.BorderWatcher
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.WorkController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Land automatisch umstellen: Grenzübertritte aus der GPS-Strecke (offline, mitgelieferte
 * Ländergrenzen), Schutz vor Fehlalarmen an der Grenze, Vorrang der Handauswahl.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BorderCrossingTest {
    private val app = ApplicationProvider.getApplicationContext<FahrtenbuchApp>()
    private val zone = ZoneId.systemDefault()
    private val locator = File("src/main/assets/${CountryLocator.ASSET_NAME}").inputStream().use(CountryLocator::load)
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var controller: WorkController
    private val day = LocalDate.parse("2026-11-17")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = Settings(app)
        settings.setWorkSettings(WorkSettings(reminderEnabled = false, gpsEnabled = true))
        repo = Repository(db, settings.rules)
        controller = WorkController(repo, settings, ReminderScheduler(app, settings), LocationProvider(app), clock = { day.atTime(20, 0) })
    }

    @After
    fun tearDown() = db.close()

    private fun watcher() = BorderWatcher(
        initialCountry = { repo.lastCountryUpTo(day) },
        countryAt = { lat, lon -> locator.countryAt(lat, lon) },
        onCrossed = { at, code -> controller.onBorderCrossed(at, code) },
        zone = zone,
    )

    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    /**
     * Fährt von ([lat], [fromLon]) nach ([lat], [toLon]) mit 72 km/h (alle 40 m ein Punkt alle 2 s),
     * gibt die Ankunftszeit zurück.
     */
    private suspend fun BorderWatcher.drive(lat: Double, fromLon: Double, toLon: Double, start: LocalDateTime): LocalDateTime {
        val stepDeg = 40.0 / (111_320.0 * Math.cos(Math.toRadians(lat)))
        val steps = (Math.abs(toLon - fromLon) / stepDeg).toInt()
        val dir = Math.signum(toLon - fromLon)
        var t = start
        for (i in 0..steps) {
            onPoint(lat, fromLon + dir * i * stepDeg, ms(t))
            t = t.plusSeconds(2)
        }
        return t
    }

    // A30/A1 bei Bad Bentheim (DE) – Oldenzaal/Hengelo (NL)
    private val lat = 52.30
    private val inGermany = 7.20
    private val inNetherlands = 6.88

    @Test
    fun strassenpunkteLiegenInDenRichtigenLaendern() {
        assertEquals("DE", locator.countryAt(lat, inGermany))
        assertEquals("NL", locator.countryAt(lat, inNetherlands))
    }

    @Test
    fun deutschlandNiederlandeUndZurueckAmSelbenTag() = runBlocking {
        controller.startWork(day.atTime(6, 0))
        assertEquals(listOf("DE"), repo.getDayByDate(day)!!.countryRoute)
        val w = watcher()

        // Hinfahrt in die Niederlande
        var t = w.drive(lat, inGermany, inNetherlands, day.atTime(8, 0))
        var d = repo.getDayByDate(day)!!
        assertEquals(listOf("DE", "NL"), d.countryRoute)
        assertEquals("NL", d.countryCode)
        assertFalse("automatisch, nicht von Hand", d.countryManual)

        // Abladen, später zurück nach Deutschland
        t = w.drive(lat, inNetherlands, inGermany, t.plusHours(2))
        d = repo.getDayByDate(day)!!
        assertEquals(listOf("DE", "NL", "DE"), d.countryRoute)
        assertEquals("beide Länder eingetragen", listOf("DE", "NL"), d.allCountries)
        assertEquals("Ausland hat Vorrang", "NL", d.countryCode)

        // Auswertung: der Tag zählt einmal, für die Niederlande
        assertTrue(t.isBefore(day.atTime(19, 0)))
        controller.endWork(day.atTime(19, 0), 500_000)
        val summary = Stats.summarize(Classifier.classifyAll(listOf(repo.getDayByDate(day)!!), emptyList(), CountingRules()))
        assertEquals(listOf("NL"), summary.abroad.map { it.countryCode })
        assertEquals(0, summary.germany.total)

        // Folgetag beginnt in Deutschland (dort war man zuletzt)
        controller.startWork(day.plusDays(1).atTime(6, 0))
        assertEquals(listOf("DE"), repo.getDayByDate(day.plusDays(1))!!.countryRoute)
    }

    @Test
    fun kurzerAbstecherUeberDieGrenzeAendertNichts() = runBlocking {
        controller.startWork(day.atTime(6, 0))
        val w = watcher()
        // Kurz über die Grenze (gut 1 km in die Niederlande) und gleich wieder zurück
        val border = findBorderLon()
        var t = w.drive(lat, inGermany, border - 0.016, day.atTime(8, 0))
        t = w.drive(lat, border - 0.016, inGermany, t)
        assertEquals(listOf("DE"), repo.getDayByDate(day)!!.countryRoute)

        // Langsam im Stau: lange im neuen Land, aber nur wenige hundert Meter → noch keine Umstellung
        val slow = watcher()
        var s = day.atTime(12, 0)
        var lon = border + 0.001
        repeat(30) {
            slow.onPoint(lat, lon, ms(s))
            lon -= 0.0002 // ca. 14 m
            s = s.plusSeconds(30)
        }
        assertTrue(locator.countryAt(lat, lon) == "NL")
        assertEquals(listOf("DE"), repo.getDayByDate(day)!!.countryRoute)
    }

    @Test
    fun vonHandGewaehltesLandHatVorrang() = runBlocking {
        controller.startWork(day.atTime(6, 0))
        repo.getDayByDate(day)!!.let { db.dao().updateDay(it.withCountries(listOf("DE")).copy(countryManual = true)) }
        val w = watcher()
        w.drive(lat, inGermany, inNetherlands, day.atTime(8, 0))
        val d = repo.getDayByDate(day)!!
        assertEquals(listOf("DE"), d.countryRoute)
        assertTrue(d.countryManual)
    }

    @Test
    fun ohneLaufendeArbeitszeitKeineAenderung() = runBlocking {
        controller.startWork(day.atTime(6, 0))
        controller.endWork(day.atTime(7, 0), null)
        assertFalse(controller.onBorderCrossed(day.atTime(8, 0), "NL"))
        assertEquals(listOf("DE"), repo.getDayByDate(day)!!.countryRoute)
    }

    @Test
    fun grenzuebertrittNachMitternachtGiltFuerDenNeuenTag() = runBlocking {
        controller.startWork(day.atTime(18, 0))
        val w = watcher()
        // Abends in die Niederlande, nach Mitternacht zurück nach Deutschland
        w.drive(lat, inGermany, inNetherlands, day.atTime(22, 0))
        w.drive(lat, inNetherlands, inGermany, day.plusDays(1).atTime(0, 30))
        assertEquals(listOf("DE", "NL"), repo.getDayByDate(day)!!.countryRoute)
        val next = repo.getDayByDate(day.plusDays(1))!!
        assertEquals("neuer Tag beginnt im Land vor Mitternacht", listOf("NL", "DE"), next.countryRoute)
        assertEquals("NL", next.countryCode)
    }

    /** Längengrad der Grenze auf der Teststrecke (erster Punkt in den Niederlanden von Osten her). */
    private fun findBorderLon(): Double {
        var lon = inGermany
        while (locator.countryAt(lat, lon) == "DE") lon -= 0.0005
        return lon
    }

    // ---------- reine Logik ----------

    @Test
    fun laenderDesTagesUndZaehlendesLand() {
        assertEquals("DE", DayCountries.counting(listOf("DE")))
        assertEquals("NL", DayCountries.counting(listOf("DE", "NL", "DE")))
        assertEquals("BE", DayCountries.counting(listOf("NL", "BE", "DE")))
        assertEquals("FR", DayCountries.counting(listOf("FR")))
        assertEquals(listOf("DE", "NL", "DE"), DayCountries.collapse(listOf("DE", "DE", "NL", "NL", "DE")))
        assertEquals(listOf("DE", "NL"), DayCountries.distinct(listOf("DE", "NL", "DE")))
        assertEquals(listOf("AT"), DayCountries.parse("", "AT"))
        assertEquals(listOf("DE", "AT"), DayCountries.parse("DE,AT", "AT"))
        assertEquals(listOf("DE"), DayCountries.withVisit(listOf("DE"), "DE"))
    }

    @Test
    fun grenzerkennungBrauchtZeitUndStrecke() {
        val d = BorderDetector("DE")
        // 10 Minuten, aber nur 1 km → nichts
        var t = 0L
        var lon = 7.0
        repeat(11) {
            assertNull(d.onPoint(52.3, lon, t, "NL"))
            t += 60_000
            lon -= 0.0015
        }
        assertEquals("DE", d.current)
        // Weiter bis über 3 km → jetzt NL
        var result: String? = null
        while (result == null && lon > 6.9) {
            result = d.onPoint(52.3, lon, t, "NL")
            t += 2_000
            lon -= 0.0006
        }
        assertEquals("NL", result)
        // Punkt auf dem Meer/ohne Land wird übergangen
        assertNull(d.onPoint(53.5, 4.0, t, null))
        assertEquals("NL", d.current)
    }
}
