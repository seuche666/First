package de.fernfahrer.fahrtenbuch

import android.app.Application
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.PreMigrationBackup
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.TrackRepository
import de.fernfahrer.fahrtenbuch.ui.map.MapConfig
import de.fernfahrer.fahrtenbuch.work.Tracking
import de.fernfahrer.fahrtenbuch.work.TrackingService
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.WorkController
import kotlinx.coroutines.flow.MutableStateFlow
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
        location = LocationProvider(this)
        reminders = ReminderScheduler(this, settings)
        tracking = Tracking(this, settings)
        work = WorkController(repository, settings, reminders, location, tracking = tracking)
        ReminderScheduler.createChannel(this)
        TrackingService.createChannel(this)
        MapConfig.init(this)
    }
}
