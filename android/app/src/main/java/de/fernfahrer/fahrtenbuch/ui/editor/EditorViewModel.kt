package de.fernfahrer.fahrtenbuch.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Wie der Bildschirm geöffnet wurde. */
enum class EditorMode { NORMAL, TOUR_START, TOUR_DAY, TOUR_END }

const val MIDNIGHT = 24 * 60

data class EditorState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.NORMAL,
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val startMinute: Int = 6 * 60,
    val endMinute: Int = 18 * 60,
    val countryCode: String = Stats.HOME_COUNTRY,
    val kmStart: String = "",
    val kmEnd: String = "",
    val note: String = "",
    val tourId: Long? = null,
    val tourStart: LocalDate? = null,
    val override: DayType? = null,
    val autoType: DayType = DayType.NONE,
    val autoReason: String = "",
    val missingDates: List<LocalDate> = emptyList(),
    val fillGaps: Boolean = true,
    val error: String? = null,
    /** Nach Speichern/Löschen: Meldung für den Startbildschirm; Bildschirm schließt sich. */
    val finishedMessage: String? = null,
    val finished: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
    val distanceKm: Int?
        get() {
            val s = kmStart.toIntOrNull() ?: return null
            val e = kmEnd.toIntOrNull() ?: return null
            return if (e >= s) e - s else null
        }
    val effectiveType: DayType get() = override ?: autoType
    val isTourDay: Boolean get() = tourId != null || mode == EditorMode.TOUR_START

    val title: String
        get() = when (mode) {
            EditorMode.TOUR_START -> "Tour starten"
            EditorMode.TOUR_END -> "Tour beenden"
            EditorMode.TOUR_DAY -> if (isNew) "Tour-Tag erfassen" else "Tour-Tag bearbeiten"
            EditorMode.NORMAL -> if (isNew) "Tag erfassen" else "Tag bearbeiten"
        }
}

class EditorViewModel(
    private val repo: Repository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val requestedId: Long = savedState["id"] ?: 0L
    private val requestedMode: EditorMode =
        savedState.get<String>("mode")?.let { m -> EditorMode.entries.firstOrNull { it.name == m } } ?: EditorMode.NORMAL

    /** true = immer einen neuen Eintrag anlegen (aus dem Verlauf). */
    private val fresh: Boolean = savedState["fresh"] ?: false

    private val _state = MutableStateFlow(EditorState(mode = requestedMode))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Andere Tage derselben Tour (für die Vorschau der Einordnung). */
    private var otherTourDays: List<DayEntity> = emptyList()
    private var tourEnded = false
    private var original: DayEntity? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val today = LocalDate.now()
        val mode = requestedMode
        val active = if (mode == EditorMode.TOUR_DAY || mode == EditorMode.TOUR_END) repo.getActiveTour() else null

        val existing = when {
            requestedId > 0 -> repo.getDay(requestedId)
            fresh -> null
            else -> repo.getDayByDate(today)
        }
        var s: EditorState

        if (existing != null) {
            original = existing
            s = EditorState(
                loading = false,
                mode = mode,
                id = existing.id,
                date = existing.date,
                startMinute = existing.startMinute,
                endMinute = existing.endMinute,
                countryCode = existing.countryCode,
                kmStart = existing.kmStart?.toString().orEmpty(),
                kmEnd = existing.kmEnd?.toString().orEmpty(),
                note = existing.note,
                tourId = existing.tourId ?: active?.id,
                override = existing.dayTypeOverride,
            )
            when (mode) {
                EditorMode.TOUR_START -> s = s.copy(endMinute = MIDNIGHT)
                EditorMode.TOUR_END -> if (s.endMinute == MIDNIGHT) s = s.copy(endMinute = Format.nowMinutes())
                else -> Unit
            }
        } else {
            var date = today
            if (fresh) {
                // Neuer Eintrag aus dem Verlauf: letzten freien Tag vorschlagen.
                while (repo.getDayByDate(date) != null && date.isAfter(today.minusDays(366))) date = date.minusDays(1)
            }
            val previous = repo.getDayBefore(date)
            val country = previous?.countryCode ?: Stats.HOME_COUNTRY
            val km = previous?.kmEnd?.toString().orEmpty()
            s = when (mode) {
                EditorMode.NORMAL -> {
                    val usual = repo.getLatestSingleDay()
                    EditorState(
                        loading = false, mode = mode, date = date,
                        startMinute = usual?.startMinute ?: (6 * 60),
                        endMinute = usual?.endMinute?.takeIf { it < MIDNIGHT } ?: (18 * 60),
                        countryCode = country, kmStart = km,
                    )
                }
                EditorMode.TOUR_START -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = Format.nowMinutes(), endMinute = MIDNIGHT,
                    countryCode = country, kmStart = km,
                )
                EditorMode.TOUR_DAY -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = 0, endMinute = MIDNIGHT,
                    countryCode = country, kmStart = km, tourId = active?.id,
                )
                EditorMode.TOUR_END -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = 0, endMinute = Format.nowMinutes().coerceAtLeast(5),
                    countryCode = country, kmStart = km, tourId = active?.id,
                )
            }
        }
        _state.value = s
        loadTourContext()
    }

    /** Lädt die anderen Tage der Tour und fehlende Tage, dann Vorschau neu berechnen. */
    private suspend fun loadTourContext() {
        val s = _state.value
        val tourId = s.tourId
        if (tourId != null) {
            val tour = repo.getTour(tourId)
            tourEnded = tour?.ended ?: false
            otherTourDays = repo.getDaysOfTour(tourId).filter { it.id != s.id }
            val missing = if (s.mode == EditorMode.TOUR_DAY || s.mode == EditorMode.TOUR_END) {
                repo.missingTourDates(tourId, s.date)
            } else {
                emptyList()
            }
            _state.update {
                it.copy(
                    tourStart = (otherTourDays.map { d -> d.date } + it.date).minOrNull(),
                    missingDates = missing,
                )
            }
        } else {
            otherTourDays = emptyList()
            tourEnded = false
            _state.update { it.copy(tourStart = if (it.mode == EditorMode.TOUR_START) it.date else null, missingDates = emptyList()) }
        }
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        val day = s.toEntity()
        val (type, reason) = when {
            s.mode == EditorMode.TOUR_START && s.tourId == null -> Classifier.autoType(day, listOf(day), false)
            s.tourId != null -> Classifier.autoType(
                day,
                otherTourDays + day,
                tourEnded || s.mode == EditorMode.TOUR_END,
            )
            else -> Classifier.autoType(day, null, false)
        }
        _state.update { it.copy(autoType = type, autoReason = reason) }
    }

    private fun EditorState.toEntity() = DayEntity(
        id = id,
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        countryCode = countryCode,
        kmStart = kmStart.toIntOrNull(),
        kmEnd = kmEnd.toIntOrNull(),
        note = note.trim(),
        tourId = tourId,
        dayTypeOverride = override,
    )

    private fun edit(block: (EditorState) -> EditorState) {
        _state.update { block(it).copy(error = null) }
        recompute()
    }

    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date, error = null) }
        viewModelScope.launch { loadTourContext() }
    }

    fun setStart(minutes: Int) = edit { it.copy(startMinute = minutes.coerceIn(0, MIDNIGHT - 1)) }

    /** 00:00 als Ende bedeutet Mitternacht (24:00). */
    fun setEnd(minutes: Int) = edit { it.copy(endMinute = if (minutes == 0) MIDNIGHT else minutes) }

    fun setAwaySincePreviousDay(on: Boolean) = edit {
        it.copy(startMinute = if (on) 0 else if (it.startMinute == 0) 6 * 60 else it.startMinute)
    }

    fun setOvernight(on: Boolean) = edit {
        it.copy(endMinute = if (on) MIDNIGHT else if (it.endMinute == MIDNIGHT) 18 * 60 else it.endMinute)
    }

    fun setCountry(code: String) = edit { it.copy(countryCode = code) }
    fun setKmStart(text: String) = edit { it.copy(kmStart = text.filter(Char::isDigit).take(7)) }
    fun setKmEnd(text: String) = edit { it.copy(kmEnd = text.filter(Char::isDigit).take(7)) }
    fun setNote(text: String) = edit { it.copy(note = text.take(500)) }
    fun setOverride(type: DayType?) = edit { it.copy(override = type) }
    fun setFillGaps(on: Boolean) = edit { it.copy(fillGaps = on) }

    fun save() {
        val s = _state.value
        val error = validate(s)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            val clash = repo.getDayByDate(s.date)
            if (clash != null && clash.id != s.id) {
                _state.update {
                    it.copy(error = "Für ${Format.weekdayDate(s.date)} gibt es schon einen Eintrag. Bitte diesen im Verlauf bearbeiten.")
                }
                return@launch
            }
            val tourFirst = otherTourDays.minOfOrNull { it.date }
            if (s.mode == EditorMode.TOUR_END && tourFirst != null && s.date.isBefore(tourFirst)) {
                _state.update { it.copy(error = "Das Tourende kann nicht vor dem Tourbeginn (${Format.date(tourFirst)}) liegen.") }
                return@launch
            }
            try {
                repo.saveDay(
                    day = s.toEntity(),
                    startTour = s.mode == EditorMode.TOUR_START && s.tourId == null,
                    endTour = s.mode == EditorMode.TOUR_END && s.tourId != null,
                    fillGaps = s.fillGaps && s.missingDates.isNotEmpty(),
                )
                val message = when (s.mode) {
                    EditorMode.TOUR_START -> "Tour gestartet – gute Fahrt!"
                    EditorMode.TOUR_END -> "Tour beendet – willkommen zu Hause!"
                    else -> "Gespeichert: ${s.effectiveType.label}"
                }
                _state.update { it.copy(finished = true, finishedMessage = message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Speichern fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun delete() {
        val day = original ?: return
        viewModelScope.launch {
            repo.deleteDay(day)
            _state.update { it.copy(finished = true, finishedMessage = "Eintrag vom ${Format.date(day.date)} gelöscht") }
        }
    }

    companion object {
        fun validate(s: EditorState): String? {
            if (s.endMinute <= s.startMinute) return "Das Ende muss nach dem Beginn liegen."
            val start = s.kmStart.toIntOrNull()
            val end = s.kmEnd.toIntOrNull()
            if (start != null && end != null && end < start) {
                return "Der Kilometerstand abends ist kleiner als morgens."
            }
            if (s.countryCode.isBlank()) return "Bitte ein Land wählen."
            return null
        }

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                EditorViewModel(app.repository, createSavedStateHandle())
            }
        }
    }
}
