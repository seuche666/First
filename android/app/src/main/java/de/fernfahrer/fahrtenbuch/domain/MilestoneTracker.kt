package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.MilestoneEntity

/**
 * Merkt sich erreichte Meilensteine und entscheidet, welche als Meldung erscheinen.
 * Beim allerersten Prüfen (vorhandene Daten nach dem Update) und nach dem Wiederherstellen einer
 * Sicherung werden erreichte Meilensteine still freigeschaltet – ohne Flut von Meldungen.
 */
class MilestoneTracker(
    private val stored: suspend () -> List<MilestoneEntity>,
    private val store: suspend (List<MilestoneEntity>) -> Unit,
    private val isInitialized: () -> Boolean,
    private val setInitialized: () -> Unit,
) {
    @Volatile private var silentNext = false

    /** Das nächste Prüfen still durchführen (z. B. nach dem Wiederherstellen einer Sicherung). */
    fun silenceNext() {
        silentNext = true
    }

    /** Speichert neu erreichte Meilensteine und gibt die zurück, die gemeldet werden sollen. */
    suspend fun check(list: List<Milestones.Status>): List<Milestones.Status> {
        val known = stored().map { it.id }.toSet()
        val fresh = list.filter { it.achieved && it.id !in known }
        if (fresh.isNotEmpty()) store(fresh.map { MilestoneEntity(it.id, it.achievedOn!!) })
        val silent = !isInitialized() || silentNext
        setInitialized()
        silentNext = false
        // Mehrere gleichzeitig: höchstens die drei neuesten zeigen
        return if (silent) emptyList() else fresh.takeLast(MAX_AT_ONCE)
    }

    companion object {
        const val MAX_AT_ONCE = 3
    }
}
