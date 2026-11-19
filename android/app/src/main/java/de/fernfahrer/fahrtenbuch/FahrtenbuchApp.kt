package de.fernfahrer.fahrtenbuch

import android.app.Application
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.ExpenseRepository
import de.fernfahrer.fahrtenbuch.data.PreMigrationBackup
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.TrackRepository
import de.fernfahrer.fahrtenbuch.ui.map.MapConfig
import de.fernfahrer.fahrtenbuch.work.DeviceStopLocator
import de.fernfahrer.fahrtenbuch.work.Tracking
import de.fernfahrer.fahrtenbuch.work.TrackingService
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.WorkController
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.OvernightImport
import de.fernfahrer.fahrtenbuch.work.AutoBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class FahrtenbuchApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settings: Settings
        private set
    lateinit var reminders: ReminderScheduler
        private set
    lateinit var work: WorkController
        private set
    lateinit var tracks: TrackRepository
        private set
    lateinit var location: LocationProvider
        private set
    lateinit var tracking: Tracking
        private set
    lateinit var expenses: ExpenseRepository
        private set

    /** „Arbeit beenden“ aus der Benachrichtigung: Zeitpunkt des Tippens, bis die App ihn abholt. */
    val endWorkRequests = MutableStateFlow<LocalDateTime?>(null)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)

        // Vor einer Datenbank-Migration automatisch eine Sicherungskopie anlegen.
        PreMigrationBackup.runIfNeeded(
            context = this,
            dbName = AppDatabase.DB_NAME,
            targetVersion = AppDatabase.VERSION,
            driverName = settings.driverName.value,
            rules = settings.rules.value,
        )?.let { result ->
            settings.pendingBackupNotice = result.downloadFileName
                ?.let { "Vor dem Update wurde eine Sicherung angelegt: $it" }
                ?: "Vor dem Update wurde eine Sicherung im App-Speicher angelegt."
        }

        val db = AppDatabase.create(this)
        repository = Repository(db, settings.rules)
        tracks = TrackRepository(db)
        expenses = ExpenseRepository(db)
        location = LocationProvider(this)
        reminders = ReminderScheduler(this, settings)
        tracking = Tracking(this, settings)
        work = WorkController(
            repository, settings, reminders, location,
            tracking = tracking, stopLocator = DeviceStopLocator(location, tracks),
        )
        ReminderScheduler.createChannel(this)
        TrackingService.createChannel(this)
        AutoBackup.createChannel(this)
        MapConfig.init(this)

        AutoBackup.schedule(this, settings.autoBackup.value, settings.backupFolder.value != null)
    }

    /** Hintergrundaufgaben der App (laufen unabhängig von Bildschirmen). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Einmalig nach dem Update auf Datenbank-Version 6: bisher berechnete Übernachtungsplätze als
     * feste Plätze übernehmen. Wird beim Öffnen der App angestoßen.
     */
    fun importOldOvernightPlacesIfNeeded() {
        if (settings.overnightPlacesImported) return
        appScope.launch { OvernightImport.runIfNeeded(settings, repository, tracks) }
    }

    /**
     * Spesentabelle des aktuellen Jahres anlegen, falls es sie noch nicht gibt (zum Jahreswechsel als
     * Kopie des Vorjahres). Wird beim Öffnen der App angestoßen.
     */
    fun ensureExpenseTableForThisYear() {
        appScope.launch { runCatching { expenses.ensureYear(java.time.LocalDate.now().year) } }
    }

    /** Vollständige Datensicherung mit allen Einstellungen (manuell und automatisch gleich). */
    suspend fun fullBackupData(): BackupData = repository.exportData().copy(
        driverName = settings.driverName.value,
        rules = settings.rules.value,
        workSettings = settings.work.value,
        mapSettings = settings.map.value,
        autoBackup = settings.autoBackup.value,
    )
}
