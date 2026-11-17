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
import de.fernfahrer.fahrtenbuch.data.StartResult
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.work.WorkController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Wie der Bildschirm geöffnet wurde. (Tour beenden geht über den km-Dialog auf dem Startbildschirm.) */
enum class EditorMode { NORMAL, TOUR_START, TOUR_DAY }

const val MIDNIGHT = 24 * 60

data class EditorState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.NORMAL,
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val startMinute: Int = 6 * 60,
    val endMinute: Int = 18 * 60,
    val countryCode: String = Stats.HOME_COUNTRY,
    /** Land von Hand gewählt (GPS ändert es dann nicht mehr). */
    val countryManual: Boolean = false,
    val kmStart: String = "",
    val kmEnd: String = "",
    /** km-Stände anteilig geschätzt (Schicht über Mitternacht), solange nicht geändert. */
    val kmEstimated: Boolean = false,
    val note: String = "",
    val tourId: Long? = null,
    val tourStart: LocalDate? = null,
    val override: DayType? = null,
    /** Übernachtung im LKW von Hand gesetzt; null = automatisch. */
    val overnightOverride: Boolean? = null,
    val overnightAuto: Boolean = false,
    val autoType: DayType = DayType.NONE,
    val autoReason: String = "",
    /** Automatische Einordnung beruht auf der Firmenregel. */
    val autoCompanyRule: Boolean = false,
    val missingDates: List<LocalDate> = emptyList(),
    val fillGaps: Boolean = true,
    /** Arbeitszeit an diesem Tag (Anteile der Schichten). */
    val work: List<WorkPortion> = emptyList(),
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
    val overnight: Boolean get() = overnightOverride ?: overnightAuto
    val workMinutes: Int get() = WorkSplitter.minutes(work)
    val isTourDay: Boolean get() = tourId != null || mode == EditorMode.TOUR_START

    val title: String
        get() = when (mode) {
            EditorMode.TOUR_START -> "Tour starten"
            EditorMode.TOUR_DAY -> if (isNew) "Tour-Tag erfassen" else "Tour-Tag bearbeiten"
            EditorMode.NORMAL -> if (isNew) "Tag erfassen" else "Tag bearbeiten"
        }
}

/** Bearbeitung einer Arbeitsschicht im Dialog. */
data class ShiftDraft(
    val id: Long = 0,
    val startDate: LocalDate,
    val startMinute: Int,
    val endDate: LocalDate,
    val endMinute: Int,
    val running: Boolean = false,
    val kmStart: String = "",
    val kmEnd: String = "",
    val error: String? = null,
) {
    val isNew: Boolean get() = id == 0L
    val start: LocalDateTime get() = startDate.atStartOfDay().plusMinutes(startMinute.toLong())
    val end: LocalDateTime get() = endDate.atStartOfDay().plusMinutes(endMinute.toLong())
}

class EditorViewModel(
    private val repo: Repository,
    private val work: WorkController,
    private val rules: StateFlow<CountingRules>,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val requestedId: Long = savedState["id"] ?: 0L
    private val requestedMode: EditorMode =
        savedState.get<String>("mode")?.let { m -> EditorMode.entries.firstOrNull { it.name == m } } ?: EditorMode.NORMAL

    /** true = immer einen neuen Eintrag anlegen (aus dem Verlauf). */
    private val fresh: Boolean = savedState["fresh"] ?: false

    private val _state = MutableStateFlow(EditorState(mode = requestedMode))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _shift = MutableStateFlow<ShiftDraft?>(null)
    /** Gerade im Dialog bearbeitete Schicht. */
    val shift: StateFlow<ShiftDraft?> = _shift.asStateFlow()

    /** Andere Tage derselben Tour (für die Vorschau der Einordnung). */
    private var otherTourDays: List<DayEntity> = emptyList()
    private var tourEnded = false
    private var original: DayEntity? = null
    private var allShifts: List<WorkShiftEntity> = emptyList()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            repo.shifts.collect {
                allShifts = it
                refreshWork()
            }
        }
    }

    private fun refreshWork() {
        val date = _state.value.date
        val portions = WorkSplitter.byDate(allShifts, LocalDateTime.now())[date].orEmpty()
        _state.update { it.copy(work = portions) }
    }

    private suspend fun load() {
        val today = LocalDate.now()
        val mode = requestedMode
        val active = if (mode == EditorMode.TOUR_DAY) repo.getActiveTour() else null

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
                countryManual = existing.countryManual,
                kmStart = existing.kmStart?.toString().orEmpty(),
                kmEnd = existing.kmEnd?.toString().orEmpty(),
                kmEstimated = existing.kmInterpolated,
                note = existing.note,
                tourId = existing.tourId ?: active?.id,
                override = existing.dayTypeOverride,
                overnightOverride = existing.overnightOverride,
            )
            if (mode == EditorMode.TOUR_START) s = s.copy(endMinute = MIDNIGHT)
        } else {
            var date = today
            if (fresh) {
                // Neuer Eintrag aus dem Verlauf: letzten freien Tag vorschlagen.
                while (repo.getDayByDate(date) != null && date.isAfter(today.minusDays(366))) date = date.minusDays(1)
            }
            val previous = repo.getDayBefore(date)
            val country = previous?.countryCode ?: Stats.HOME_COUNTRY
            // Start-km automatisch vom letzten bekannten km-Stand
            val km = repo.lastKnownKm()?.toString().orEmpty()
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
                    startMinute = Format.nowMinutes(roundTo = 1), endMinute = MIDNIGHT,
                    countryCode = country, kmStart = km,
                )
                EditorMode.TOUR_DAY -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = 0, endMinute = MIDNIGHT,
                    countryCode = country, kmStart = km, tourId = active?.id,
                )
            }
        }
        _state.value = s
        refreshWork()
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
            val missing = if (s.mode == EditorMode.TOUR_DAY) repo.missingTourDates(tourId, s.date) else emptyList()
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
        val r = rules.value
        val inNewTour = s.mode == EditorMode.TOUR_START && s.tourId == null
        val tourDays = when {
            inNewTour -> listOf(day.copy(tourId = -1))
            s.tourId != null -> otherTourDays + day
            else -> null
        }
        val auto = Classifier.autoType(day, tourDays, if (inNewTour) false else tourEnded, r)
        val overnight = Classifier.overnightAuto(
            if (inNewTour) day.copy(tourId = -1) else day,
            tourDays,
            if (inNewTour) false else tourEnded,
        )
        _state.update {
            it.copy(
                autoType = auto.type,
                autoReason = auto.reason,
                autoCompanyRule = auto.companyRule,
                overnightAuto = overnight,
            )
        }
    }

    private fun EditorState.toEntity(): DayEntity {
        val orig = original
        val kmS = kmStart.toIntOrNull()
        val kmE = kmEnd.toIntOrNull()
        return DayEntity(
            id = id,
            date = date,
            startMinute = startMinute,
            endMinute = endMinute,
            countryCode = countryCode,
            kmStart = kmS,
            kmEnd = kmE,
            note = note.trim(),
            tourId = tourId,
            dayTypeOverride = override,
            legacyWorkStartMinute = orig?.legacyWorkStartMinute,
            legacyWorkEndMinute = orig?.legacyWorkEndMinute,
            countryManual = countryManual,
            overnightOverride = overnightOverride,
            // Geschätzte km bleiben nur markiert, solange sie nicht von Hand geändert wurden
            kmInterpolated = orig != null && orig.kmInterpolated && orig.kmStart == kmS && orig.kmEnd == kmE,
            // Gespeichert über die Eingabe = von Hand bestätigt
            timesAuto = false,
        )
    }

    private fun edit(block: (EditorState) -> EditorState) {
        _state.update { block(it).copy(error = null) }
        recompute()
    }

    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date, error = null) }
        refreshWork()
        viewModelScope.launch { loadTourContext() }
    }

    fun setStart(minutes: Int) = edit { it.copy(startMinute = minutes.coerceIn(0, MIDNIGHT - 1)) }

    /** Ende der Abwesenheit; 00:00 bedeutet Mitternacht (24:00). */
    fun setEnd(minutes: Int) = edit { it.copy(endMinute = if (minutes == 0) MIDNIGHT else minutes) }

    fun setAwaySincePreviousDay(on: Boolean) = edit {
        it.copy(startMinute = if (on) 0 else if (it.startMinute == 0) 6 * 60 else it.startMinute)
    }

    fun setOvernight(on: Boolean) = edit {
        it.copy(endMinute = if (on) MIDNIGHT else if (it.endMinute == MIDNIGHT) 18 * 60 else it.endMinute)
    }

    /** Land von Hand gewählt – hat Vorrang vor der GPS-Erkennung. */
    fun setCountry(code: String) = edit { it.copy(countryCode = code, countryManual = true) }
    fun setKmStart(text: String) = edit { it.copy(kmStart = text.filter(Char::isDigit).take(7)) }
    fun setKmEnd(text: String) = edit { it.copy(kmEnd = text.filter(Char::isDigit).take(7)) }
    fun setNote(text: String) = edit { it.copy(note = text.take(500)) }
    fun setOverride(type: DayType?) = edit { it.copy(override = type) }
    fun setFillGaps(on: Boolean) = edit { it.copy(fillGaps = on) }

    /** Schalter „Übernachtung im LKW“; entspricht er dem Vorschlag, gilt wieder „automatisch“. */
    fun setOvernightInTruck(on: Boolean) = edit {
        it.copy(overnightOverride = if (on == it.overnightAuto) null else on)
    }

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
            try {
                repo.saveDay(
                    day = s.toEntity(),
                    startTour = s.mode == EditorMode.TOUR_START && s.tourId == null,
                    endTour = false,
                    fillGaps = s.fillGaps && s.missingDates.isNotEmpty(),
                )
                var message = when (s.mode) {
                    EditorMode.TOUR_START -> "Tour gestartet – gute Fahrt!"
                    else -> "Gespeichert: ${s.effectiveType.label}"
                }
                // Beim Tourstart beginnt automatisch auch die Arbeitszeit (falls nicht schon eine läuft).
                if (s.mode == EditorMode.TOUR_START && s.date == LocalDate.now()) {
                    val at = s.date.atStartOfDay().plusMinutes(s.startMinute.toLong())
                    if (work.startWork(at) is StartResult.Started) {
                        message += " Arbeit läuft seit ${Format.time(s.startMinute)}."
                    }
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

    // ---------- Arbeitsschichten ----------

    /** Schicht im Dialog öffnen. */
    fun editShift(id: Long) {
        val s = allShifts.firstOrNull { it.id == id } ?: return
        val now = LocalDateTime.now()
        val end = s.end ?: if (s.running) now else s.start ?: now
        _shift.value = ShiftDraft(
            id = s.id,
            startDate = s.startDate,
            startMinute = s.startMinute ?: 0,
            endDate = end.toLocalDate(),
            endMinute = end.hour * 60 + end.minute,
            running = s.running,
            kmStart = s.kmStart?.toString().orEmpty(),
            kmEnd = s.kmEnd?.toString().orEmpty(),
        )
    }

    /** Neue Schicht für den angezeigten Tag anlegen. */
    fun newShift() {
        val d = _state.value.date
        viewModelScope.launch {
            _shift.value = ShiftDraft(
                startDate = d, startMinute = 6 * 60, endDate = d, endMinute = 16 * 60,
                kmStart = repo.lastKnownKm()?.toString().orEmpty(),
            )
        }
    }

    fun updateShift(block: (ShiftDraft) -> ShiftDraft) {
        _shift.update { it?.let(block)?.copy(error = null) }
    }

    fun closeShift() {
        _shift.value = null
    }

    fun saveShift() {
        val d = _shift.value ?: return
        val error = validateShift(d)
        if (error != null) {
            _shift.update { it?.copy(error = error) }
            return
        }
        viewModelScope.launch {
            try {
                val (endDate, endMinute) = normalizeEnd(d)
                repo.saveShift(
                    WorkShiftEntity(
                        id = d.id,
                        startDate = d.startDate,
                        startMinute = d.startMinute,
                        endDate = if (d.running) null else endDate,
                        endMinute = if (d.running) null else endMinute,
                        running = d.running,
                        kmStart = d.kmStart.toIntOrNull(),
                        kmEnd = if (d.running) null else d.kmEnd.toIntOrNull(),
                    ),
                )
                work.onReminderSettingsChanged()
                _shift.value = null
            } catch (e: Exception) {
                _shift.update { it?.copy(error = e.localizedMessage ?: "Speichern fehlgeschlagen") }
            }
        }
    }

    fun deleteShift() {
        val d = _shift.value ?: return
        viewModelScope.launch {
            repo.deleteShift(d.id)
            work.onReminderSettingsChanged()
            _shift.value = null
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

        fun validateShift(d: ShiftDraft): String? {
            if (!d.running && !d.end.isAfter(d.start)) return "Das Arbeitsende muss nach dem Arbeitsbeginn liegen."
            if (d.running && d.start.isAfter(LocalDateTime.now())) return "Der Arbeitsbeginn liegt in der Zukunft."
            val ks = d.kmStart.toIntOrNull()
            val ke = d.kmEnd.toIntOrNull()
            if (!d.running && ks != null && ke != null && ke < ks) return "Der km-Stand am Ende ist kleiner als zu Beginn."
            return null
        }

        /** Ende um 00:00 eines Folgetags wird als 24:00 des Vortags gespeichert. */
        fun normalizeEnd(d: ShiftDraft): Pair<LocalDate, Int> =
            if (d.endMinute == 0 && d.endDate.isAfter(d.startDate)) d.endDate.minusDays(1) to MIDNIGHT else d.endDate to d.endMinute

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                EditorViewModel(app.repository, app.work, app.settings.rules, createSavedStateHandle())
            }
        }
    }
}
