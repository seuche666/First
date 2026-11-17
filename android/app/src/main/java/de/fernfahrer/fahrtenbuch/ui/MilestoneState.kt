package de.fernfahrer.fahrtenbuch.ui

import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.TrackChunkMeta
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.MilestoneInputs
import de.fernfahrer.fahrtenbuch.domain.MilestoneTracker
import de.fernfahrer.fahrtenbuch.domain.Milestones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Meilensteine für die Oberfläche: berechnet sie laufend aus Tagen und GPS-Strecken, speichert neu
 * erreichte (über [MilestoneTracker]) und hält die Warteschlange der Meldungen „Meilenstein erreicht“.
 * Gehört zum [MainViewModel] und läuft in dessen [scope].
 */
@OptIn(FlowPreview::class)
class MilestoneState(
    private val scope: CoroutineScope,
    repo: Repository,
    settings: Settings,
    trackMeta: Flow<List<TrackChunkMeta>>,
) {
    /** Aus den Daten berechneter Stand aller Meilensteine. */
    private val evaluated: StateFlow<List<Milestones.Status>?> =
        combine(repo.classifiedDays, trackMeta) { days, meta -> Milestones.evaluate(MilestoneInputs.from(days, meta), Countries::label) }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Für die Übersicht: gespeicherte Meilensteine bleiben erreicht, auch wenn Daten gelöscht wurden. */
    val milestones: StateFlow<List<Milestones.Status>> =
        combine(evaluated.filterNotNull(), repo.milestones) { list, stored ->
            val dates = stored.associate { it.id to it.achievedOn }
            list.map { st -> dates[st.id]?.let { st.copy(achievedOn = it) } ?: st }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _celebrations = MutableStateFlow<List<Milestones.Status>>(emptyList())

    /** Gerade anzuzeigende Meldung „Meilenstein erreicht“ (eine nach der anderen). */
    val celebration: StateFlow<Milestones.Status?> = _celebrations.map { it.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Das nächste Prüfen still durchführen (nach dem Wiederherstellen einer Sicherung). */
    fun silenceNext() = milestoneTracker.silenceNext()

    fun dismissCelebration() {
        _celebrations.value = _celebrations.value.drop(1)
    }

    private val milestoneTracker = MilestoneTracker(
        stored = repo::getMilestones,
        store = repo::addMilestones,
        isInitialized = { settings.milestonesInitialized },
        setInitialized = { settings.milestonesInitialized = true },
    )

    init {
        scope.launch {
            evaluated.filterNotNull().debounce(800).collect { list ->
                try {
                    val show = milestoneTracker.check(list)
                    if (show.isNotEmpty()) _celebrations.value = _celebrations.value + show
                } catch (e: Exception) {
                    android.util.Log.w("MilestoneState", "Meilensteine nicht geprüft", e)
                }
            }
        }
    }
}
