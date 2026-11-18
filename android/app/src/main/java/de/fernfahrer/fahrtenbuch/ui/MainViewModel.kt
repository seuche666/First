package de.fernfahrer.fahrtenbuch.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import android.content.Intent
import de.fernfahrer.fahrtenbuch.data.AutoBackupSettings
import de.fernfahrer.fahrtenbuch.data.BackupFiles
import de.fernfahrer.fahrtenbuch.data.OvernightImport
import de.fernfahrer.fahrtenbuch.data.SafBackupFolder
import de.fernfahrer.fahrtenbuch.work.AutoBackup
import de.fernfahrer.fahrtenbuch.export.ExportFormat
import de.fernfahrer.fahrtenbuch.export.YearExport
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFormatException
import de.fernfahrer.fahrtenbuch.data.EndTourResult
import de.fernfahrer.fahrtenbuch.data.MapSettings
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.StartResult
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Milestones
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.export.Share
import de.fernfahrer.fahrtenbuch.work.WorkController
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MainViewModel(
    private val repo: Repository,
    private val settings: Settings,
    private val work: WorkController,
    private val app: FahrtenbuchApp?,
) : ViewModel() {

    /** Alle Tage, neueste zuerst; null solange noch geladen wird. */
    val days: StateFlow<List<ClassifiedDay>?> =
        repo.classifiedDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Minütlicher Takt, damit eine laufende Arbeitszeit aktuell angezeigt wird. */
    private val minuteTicker = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    /** Arbeitsschichten (roh). */
    val shifts: StateFlow<List<WorkShiftEntity>> =
        repo.shifts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Laufende Arbeitszeit oder null. */
    val runningShift: StateFlow<WorkShiftEntity?> = repo.shifts.map { list -> list.firstOrNull { it.running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Arbeitszeit je Kalendertag (Schichten an Mitternacht geteilt). */
    val workByDate: StateFlow<Map<LocalDate, List<WorkPortion>>> =
        combine(repo.shifts, minuteTicker) { list, now -> WorkSplitter.byDate(list, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val now: StateFlow<LocalDateTime> =
        minuteTicker.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    val activeTour: StateFlow<ActiveTour?> = combine(repo.classifiedDays, repo.tours) { days, tours ->
        val tour = tours.filter { !it.ended }.maxByOrNull { it.id } ?: return@combine null
        val tourDays = days.filter { it.day.tourId == tour.id }.map { it.day }
        val start = tourDays.minOfOrNull { it.date } ?: return@combine null
        val today = LocalDate.now()
        ActiveTour(
            tourId = tour.id,
            startDate = start,
            dayNumber = ChronoUnit.DAYS.between(start, today).toInt() + 1,
            lastCountry = tourDays.maxBy { it.date }.lastCountry,
            todayEntered = tourDays.any { it.date == today },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentCountries: StateFlow<List<String>> =
        repo.recentCountries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val driverName: StateFlow<String> = settings.driverName
    fun setDriverName(name: String) = settings.setDriverName(name)

    val rules: StateFlow<CountingRules> = settings.rules
    fun setShortSingleDayAsHalf(enabled: Boolean) = settings.setShortSingleDayAsHalf(enabled)

    val workSettings: StateFlow<WorkSettings> = settings.work
    fun setWorkSettings(value: WorkSettings) {
        settings.setWorkSettings(value)
        viewModelScope.launch { work.onReminderSettingsChanged() }
    }

    val mapSettings: StateFlow<MapSettings> = settings.map
    fun setMapSettings(value: MapSettings) {
        settings.setMapSettings(value)
        syncTracking()
    }

    /** Aufzeichnung an Arbeitszeit, Einstellungen und Berechtigungen anpassen. */
    fun syncTracking() {
        viewModelScope.launch { work.syncTracking() }
    }

    /** Alle GPS-Strecken löschen (nach Rückfrage). Tage, Touren und Arbeitszeiten bleiben erhalten. */
    fun deleteAllTracks() {
        val tracks = app?.tracks ?: return
        viewModelScope.launch {
            try {
                tracks.deleteAll()
                showMessage("Alle Strecken wurden gelöscht.")
            } catch (e: Exception) {
                showMessage("Löschen fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Zuletzt geöffnete Ansicht (Karte oder Fahrtenbuch), bleibt nach dem Schließen der App erhalten. */
    var lastViewMap: Boolean
        get() = settings.lastViewMap
        set(value) {
            settings.lastViewMap = value
        }

    /** Wurde „Standort immer erlauben“ schon erklärt? */
    val backgroundLocationExplained: Boolean get() = settings.backgroundLocationExplained
    fun markBackgroundLocationExplained() {
        settings.backgroundLocationExplained = true
    }

    /** Muss vor dem ersten „Arbeit beginnen“ die Erklärung zu den Berechtigungen gezeigt werden? */
    val permissionsExplained: Boolean get() = settings.permissionsExplained
    fun markPermissionsExplained() {
        settings.permissionsExplained = true
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    // ---------- Meilensteine ----------

    private val milestoneState = MilestoneState(viewModelScope, repo, settings, app?.tracks?.meta ?: flowOf(emptyList()))

    /** Für die Übersicht: alle Meilensteine mit Stand. */
    val milestones: StateFlow<List<Milestones.Status>> get() = milestoneState.milestones

    /** Gerade anzuzeigende Meldung „Meilenstein erreicht“ (eine nach der anderen). */
    val celebration: StateFlow<Milestones.Status?> get() = milestoneState.celebration

    fun dismissCelebration() = milestoneState.dismissCelebration()

    init {
        // Meldungen des WorkControllers (GPS) weiterreichen
        viewModelScope.launch { work.messages.collect { showMessage(it) } }
        // „Arbeit beenden“ aus der Benachrichtigung
        app?.let { a ->
            viewModelScope.launch {
                a.endWorkRequests.filterNotNull().collect { at ->
                    a.endWorkRequests.value = null
                    requestEndWork(at)
                }
            }
        }
    }

    /** Beim Öffnen der App: neue Tage einer laufenden Schicht anlegen, Hinweise anzeigen. */
    fun onForeground() {
        app?.importOldOvernightPlacesIfNeeded()
        viewModelScope.launch {
            try {
                work.onForeground()
            } catch (e: Exception) {
                showMessage("Fehler beim Aktualisieren: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
            settings.pendingBackupNotice?.let {
                settings.pendingBackupNotice = null
                showMessage(it)
            }
        }
    }

    // ---------- Arbeitszeit ----------

    fun startWork(at: LocalDateTime = work.now()) {
        viewModelScope.launch {
            try {
                when (val r = work.startWork(at)) {
                    is StartResult.Started -> showMessage(
                        "Arbeit begonnen um ${Format.time(r.shift.startMinute ?: 0)}" +
                            (r.shift.kmStart?.let { " · Start-km ${Format.number(it)}" } ?: ""),
                    )
                    is StartResult.AlreadyRunning -> showMessage("Die Arbeit läuft bereits.")
                }
            } catch (e: Exception) {
                showMessage("Speichern fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    private val _kmPrompt = MutableStateFlow<KmPrompt?>(null)
    val kmPrompt: StateFlow<KmPrompt?> = _kmPrompt.asStateFlow()

    /** „Arbeit beenden“ gedrückt: sofort nach dem km-Stand fragen. */
    fun requestEndWork(at: LocalDateTime = work.now()) {
        viewModelScope.launch {
            val shift = repo.getRunningShift()
            if (shift == null) {
                showMessage("Es läuft keine Arbeitszeit.")
                return@launch
            }
            _kmPrompt.value = KmPrompt(
                kind = KmPrompt.Kind.END_WORK,
                at = at,
                suggestion = repo.lastKnownKm(),
                minimum = shift.kmStart ?: repo.getDayByDate(shift.startDate)?.kmStart,
            )
        }
    }

    /** „Tour beenden“ gedrückt: sofort nach dem km-Stand fragen (nur einmal, auch bei laufender Arbeit). */
    fun requestEndTour(at: LocalDateTime = work.now()) {
        viewModelScope.launch {
            val shift = repo.getRunningShift()
            val today = repo.getDayByDate(at.toLocalDate())
            _kmPrompt.value = KmPrompt(
                kind = KmPrompt.Kind.END_TOUR,
                at = at,
                suggestion = repo.lastKnownKm(),
                minimum = shift?.kmStart ?: today?.kmStart ?: repo.getDayBefore(at.toLocalDate())?.kmEnd,
                endsRunningWork = shift != null,
            )
        }
    }

    /**
     * km-Abfrage beantwortet: [km] = null bei „Später“. Beendet in beiden Fällen.
     * [savePlace]: Haken „Übernachtungsplatz auf der Karte speichern“ (nur bei „Arbeit beenden“).
     */
    fun confirmKm(km: Int?, savePlace: Boolean = false) {
        val prompt = _kmPrompt.value ?: return
        _kmPrompt.value = null
        viewModelScope.launch {
            try {
                when (prompt.kind) {
                    KmPrompt.Kind.END_WORK -> {
                        val ended = work.endWork(prompt.at, km, savePlace)
                        if (ended == null) {
                            showMessage("Es läuft keine Arbeitszeit.")
                        } else {
                            val minutes = WorkSplitter.minutes(WorkSplitter.split(ended, prompt.at))
                            showMessage(
                                "Arbeit beendet um ${Format.time(prompt.at.hour * 60 + prompt.at.minute)} · " +
                                    Format.duration(minutes) +
                                    (km?.let { " · km ${Format.number(it)}" } ?: " · km später nachtragen"),
                            )
                        }
                    }
                    KmPrompt.Kind.END_TOUR -> when (val r = work.endTour(prompt.at, km)) {
                        EndTourResult.NoActiveTour -> showMessage("Es läuft keine Tour.")
                        is EndTourResult.Ended -> showMessage(
                            "Tour beendet – willkommen zu Hause!" +
                                (if (r.endedShift != null) " Arbeitszeit ebenfalls beendet." else ""),
                        )
                    }
                }
            } catch (e: Exception) {
                showMessage("Speichern fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Dialog ohne Auswahl geschlossen (z. B. Zurück-Taste): nichts beenden. */
    fun dismissKmPrompt() {
        _kmPrompt.value = null
    }

    /** Zeitraum der Auswertung (bleibt beim Wechsel der Tabs erhalten). */
    private val _period = MutableStateFlow(Period(PeriodType.YEAR, LocalDate.now()))
    val period: StateFlow<Period> = _period.asStateFlow()
    fun setPeriod(p: Period) {
        _period.value = p
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // ---------- Export ----------

    fun exportYear(context: Context, year: Int, format: ExportFormat) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _busy.value = true
            try {
                val file = YearExport.createFile(
                    appContext, year, format, days.value ?: emptyList(), repo.getAllShifts(),
                    settings.driverName.value, settings.rules.value,
                )
                Share.shareFile(appContext, file, YearExport.mimeType(format), "Jahresübersicht $year teilen")
            } catch (e: Exception) {
                showMessage("Export fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------- Datensicherung ----------

    fun writeBackup(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        val a = app ?: return
        viewModelScope.launch {
            try {
                val data = a.fullBackupData()
                BackupFiles.write(resolver, uri, data)
                showMessage("Datensicherung gespeichert (${data.days.size} Tage)")
            } catch (e: Exception) {
                showMessage("Sicherung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    // ---------- Automatisches Backup ----------

    val autoBackup: StateFlow<AutoBackupSettings> = settings.autoBackup
    val backupFolder: StateFlow<String?> = settings.backupFolder
    val lastAutoBackup: StateFlow<Long> = settings.lastAutoBackup

    fun setAutoBackup(value: AutoBackupSettings) {
        settings.setAutoBackup(value)
        app?.let { AutoBackup.schedule(it, value, settings.backupFolder.value != null) }
    }

    /** Ordner aus der Android-Ordnerauswahl übernehmen (Zugriff dauerhaft behalten). */
    fun setBackupFolder(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            showMessage("Auf diesen Ordner ist kein dauerhafter Zugriff möglich. Bitte einen anderen wählen.")
            return
        }
        // Früheren Ordner freigeben
        settings.backupFolder.value?.takeIf { it != uri.toString() }?.let { old ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.setBackupFolder(uri.toString())
        app?.let { AutoBackup.schedule(it, settings.autoBackup.value, true) }
        showMessage("Speicherort gewählt: ${SafBackupFolder.displayName(resolver, uri) ?: "Ordner"}")
    }

    /** „Jetzt sichern“: sofort in den gewählten Ordner sichern. */
    fun backupNow() {
        val a = app ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val name = AutoBackup.runNow(a)
                showMessage("Gesichert: $name")
            } catch (e: Exception) {
                showMessage("Sicherung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Eingelesene Sicherung, die noch bestätigt werden muss. */
    private val _pendingRestore = MutableStateFlow<BackupData?>(null)
    val pendingRestore: StateFlow<BackupData?> = _pendingRestore.asStateFlow()

    fun readBackup(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            try {
                _pendingRestore.value = BackupFiles.read(resolver, uri)
            } catch (e: BackupFormatException) {
                showMessage(e.message ?: "Ungültige Datei")
            } catch (e: Exception) {
                showMessage("Datei konnte nicht gelesen werden.")
            }
        }
    }

    fun confirmRestore() {
        val data = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            try {
                milestoneState.silenceNext()
                repo.replaceAll(data)
                if (data.driverName.isNotBlank()) settings.setDriverName(data.driverName)
                data.rules?.let { settings.setShortSingleDayAsHalf(it.shortSingleDayAsHalf) }
                data.workSettings?.let { settings.setWorkSettings(it) }
                data.mapSettings?.let { settings.setMapSettings(it) }
                data.autoBackup?.let { ab ->
                    // Der Speicherort gehört zum Gerät: ohne gewählten Ordner bleibt das Backup aus.
                    val v = ab.copy(enabled = ab.enabled && settings.backupFolder.value != null)
                    setAutoBackup(v)
                }
                // Ältere Sicherung ohne Übernachtungsplätze: frühere Plätze wie beim Update übernehmen
                if (data.places == null) app?.let { OvernightImport.importNow(repo, it.tracks) }
                work.onReminderSettingsChanged()
                work.syncTracking()
                showMessage("Datensicherung geladen: ${data.days.size} Tage")
            } catch (e: Exception) {
                showMessage("Wiederherstellen fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                MainViewModel(app.repository, app.settings, app.work, app)
            }
        }
    }
}
