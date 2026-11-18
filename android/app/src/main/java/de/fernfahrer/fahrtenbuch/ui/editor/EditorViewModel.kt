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
import de.fernfahrer.fahrtenbuch.domain.DayCountries
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.work.WorkController
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                countries = existing.countryRoute,
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
            val country = listOf(previous?.lastCountry ?: Stats.HOME_COUNTRY)
            // Start-km automatisch vom letzten bekannten km-Stand
            val km = repo.lastKnownKm()?.toString().orEmpty()
            s = when (mode) {
                EditorMode.NORMAL -> {
                    val usual = repo.getLatestSingleDay()
                    EditorState(
                        loading = false, mode = mode, date = date,
                        startMinute = usual?.startMinute ?: (6 * 60),
                        endMinute = usual?.endMinute?.takeIf { it < MIDNIGHT } ?: (18 * 60),
                        countries = country, kmStart = km,
                    )
                }
                EditorMode.TOUR_START -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = Format.nowMinutes(roundTo = 1), endMinute = MIDNIGHT,
                    countries = country, kmStart = km,
                )
                EditorMode.TOUR_DAY -> EditorState(
                    loading = false, mode = mode, date = today,
                    startMinute = 0, endMinute = MIDNIGHT,
                    countries = country, kmStart = km, tourId = active?.id,
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
            countries = DayCountries.join(countries),
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

    /**
     * Land von Hand gewählt – hat Vorrang vor der GPS-Erkennung. [index] = welches der angezeigten
     * Länder ersetzt wird; null = weiteres Land hinzufügen.
     */
    fun setCountry(index: Int?, code: String) = edit {
        val shown = it.shownCountries
        val list = when {
            index == null -> if (code in shown) shown else shown + code
            index in shown.indices -> shown.toMutableList().also { l -> l[index] = code }.distinct()
            else -> shown
        }
        it.copy(countries = list, countryManual = true)
    }

    /** Ein Land des Tages entfernen (mindestens eins bleibt). */
    fun removeCountry(code: String) = edit {
        val list = it.shownCountries - code
        if (list.isEmpty()) it else it.copy(countries = list, countryManual = true)
    }
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
        val error = EditorRules.validate(s)
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
        val error = EditorRules.validateShift(d)
        if (error != null) {
            _shift.update { it?.copy(error = error) }
            return
        }
        viewModelScope.launch {
            try {
                val (endDate, endMinute) = EditorRules.normalizeEnd(d)
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
                work.syncTracking()
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
            work.syncTracking()
            _shift.value = null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                EditorViewModel(app.repository, app.work, app.settings.rules, createSavedStateHandle())
            }
        }
    }
}
