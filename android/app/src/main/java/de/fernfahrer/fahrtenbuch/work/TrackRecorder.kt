package de.fernfahrer.fahrtenbuch.work

import de.fernfahrer.fahrtenbuch.data.TrackChunkEntity
import de.fernfahrer.fahrtenbuch.data.TrackChunks
import de.fernfahrer.fahrtenbuch.domain.geo.TrackFilter
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nimmt GPS-Messungen entgegen, filtert sie ([TrackFilter]) und speichert die Punkte in Stücken
 * von höchstens [MAX_POINTS] Punkten. Gespeichert wird regelmäßig (alle [SAVE_EVERY_POINTS] Punkte
 * bzw. [SAVE_EVERY_MS]), damit bei einem Absturz höchstens wenige Punkte fehlen – ohne Internet.
 */
class TrackRecorder(
    /** Speichert ein Stück und gibt seine id zurück. */
    private val save: suspend (TrackChunkEntity) -> Long,
    /** Land an einer Position (offline) oder null. */
    private val countryAt: suspend (lat: Double, lon: Double) -> String? = { _, _ -> null },
    private val filter: TrackFilter = TrackFilter(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var trackId = 0L
    private var seq = 0
    private var chunkId = 0L
    private val points = ArrayList<TrackPoint>()
    private val countries = LinkedHashSet<String>()
    private var unsaved = 0
    private var lastSaveMs = 0L
    private var accepted = 0L

    /** Anzahl gespeicherter Punkte seit Beginn (für die Benachrichtigung). */
    val acceptedPoints: Long get() = accepted

    suspend fun onFix(fix: TrackFilter.Fix): TrackFilter.Decision = mutex.withLock {
        val decision = filter.check(fix)
        if (!decision.keep) return@withLock decision
        if (trackId == 0L) trackId = fix.timeMs
        if (decision == TrackFilter.Decision.BREAK && points.isNotEmpty()) {
            // Neues Teilstück ohne Verbindung zum vorigen
            saveLocked()
            startNextChunk(carryLast = false)
        }
        filter.inserted?.let { addPoint(it) }
        addPoint(fix)
        if (decision != TrackFilter.Decision.ACCEPT || accepted % COUNTRY_EVERY == 0L) {
            countryAt(fix.lat, fix.lon)?.let { countries += it }
        }
        if (unsaved >= SAVE_EVERY_POINTS || nowMs() - lastSaveMs >= SAVE_EVERY_MS) saveLocked()
        decision
    }

    private suspend fun addPoint(fix: TrackFilter.Fix) {
        points += TrackPoint.of(fix.lat, fix.lon, Math.floorDiv(fix.timeMs, 1000L))
        accepted++
        unsaved++
        if (points.size >= MAX_POINTS) {
            countryAt(fix.lat, fix.lon)?.let { countries += it }
            saveLocked()
            startNextChunk(carryLast = true)
        }
    }

    /** Alles Ungespeicherte sofort speichern (z. B. bei „Arbeit beenden“). */
    suspend fun flush() = mutex.withLock { saveLocked() }

    /** Speichert, falls seit dem letzten Speichern genug Zeit vergangen ist. */
    suspend fun flushIfDue() = mutex.withLock {
        if (unsaved > 0 && nowMs() - lastSaveMs >= SAVE_EVERY_MS) saveLocked()
    }

    private suspend fun saveLocked() {
        lastSaveMs = nowMs()
        if (unsaved == 0 || points.isEmpty()) return
        chunkId = save(TrackChunks.build(chunkId, trackId, seq, points, countries))
        unsaved = 0
    }

    private fun startNextChunk(carryLast: Boolean) {
        val last = points.lastOrNull()
        points.clear()
        countries.clear()
        chunkId = 0L
        seq++
        // Das Folgestück beginnt mit dem letzten Punkt, damit die Linie durchgehend bleibt.
        if (carryLast && last != null) points += last
    }

    companion object {
        const val MAX_POINTS = 500
        const val SAVE_EVERY_POINTS = 10
        const val SAVE_EVERY_MS = 60_000L
        const val COUNTRY_EVERY = 10L
    }
}
