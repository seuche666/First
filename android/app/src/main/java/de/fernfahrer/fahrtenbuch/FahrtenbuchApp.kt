package de.fernfahrer.fahrtenbuch

import android.app.Application
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.PreMigrationBackup
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
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

        repository = Repository(AppDatabase.create(this), settings.rules)
        reminders = ReminderScheduler(this, settings)
        work = WorkController(repository, settings, reminders, LocationProvider(this))
        ReminderScheduler.createChannel(this)
    }
}
