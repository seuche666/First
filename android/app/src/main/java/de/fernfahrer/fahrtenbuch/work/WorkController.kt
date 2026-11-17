package de.fernfahrer.fahrtenbuch.work

import de.fernfahrer.fahrtenbuch.data.EndTourResult
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.StartResult
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Steuert Arbeitsbeginn/-ende, Tourende, Erinnerung und GPS-Ländererkennung an einer Stelle,
 * damit Startbildschirm, Eingabe und Benachrichtigung sich gleich verhalten.
 */
class WorkController(
    private val repo: Repository,
    private val settings: Settings,
    private val reminders: ReminderScheduler,
    private val location: LocationProvider,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
    /** Streckenaufzeichnung für die Karte (nur während der Arbeitszeit). */
    private val tracking: TrackingControl = TrackingControl.NONE,
    /** Position beim Arbeitsende für die Übernachtungsorte auf der Karte. */
    private val stopLocator: StopLocator = StopLocator.NONE,
) {
    /** Zuletzt gestartete Standort-Speicherung (für Tests). */
    @Volatile var pendingStop: kotlinx.coroutines.Job? = null
        private set

    /** Hintergrundaufgaben (GPS), die weiterlaufen, auch wenn ein Bildschirm geschlossen wird. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Meldungen für die Anzeige (z. B. „Land per GPS: Österreich“). */
    val messages: SharedFlow<String> = _messages

    fun now(): LocalDateTime = clock()

    suspend fun startWork(at: LocalDateTime = now()): StartResult {
        val result = repo.startWork(at)
        if (result is StartResult.Started) {
            reminders.scheduleFor(result.shift, now())
            tracking.sync(workRunning = true)
            detectCountryLater(at.toLocalDate())
        }
        return result
    }

    suspend fun endWork(at: LocalDateTime, km: Int?): WorkShiftEntity? {
        val ended = repo.endWork(at, km) ?: return null
        reminders.cancel()
        tracking.sync(workRunning = false)
        recordStopLater(ended)
        detectCountryLater(ended.endDate ?: at.toLocalDate())
        return ended
    }

    suspend fun endTour(at: LocalDateTime, km: Int?): EndTourResult {
        val result = repo.endTour(at, km)
        if (result is EndTourResult.Ended) {
            if (result.endedShift != null) {
                reminders.cancel()
                tracking.sync(workRunning = false)
                recordStopLater(result.endedShift)
            }
            detectCountryLater(at.toLocalDate())
        }
        return result
    }

    /** Beim Öffnen der App: neue Tage einer laufenden Arbeitszeit anlegen, Erinnerung sicherstellen. */
    suspend fun onForeground() {
        val shift = repo.getRunningShift()
        // Aufzeichnung passend zur Arbeitszeit (z. B. nach Neustart des Handys oder Update wieder aufnehmen)
        tracking.sync(workRunning = shift != null)
        if (shift == null) return
        val created = repo.syncRunningWork(now())
        reminders.ensureScheduled(shift)
        if (now().toLocalDate() in created) detectCountryLater(now().toLocalDate())
    }

    /** Einstellungen der Erinnerung geändert: Wecker neu planen oder entfernen. */
    suspend fun onReminderSettingsChanged() {
        val shift = repo.getRunningShift()
        if (shift != null) reminders.scheduleFor(shift, now()) else reminders.cancel()
    }

    /** Einstellungen der Karte oder Berechtigungen geändert, Arbeitszeit von Hand bearbeitet. */
    suspend fun syncTracking() {
        tracking.sync(workRunning = repo.getRunningShift() != null)
    }

    /** Speichert im Hintergrund die Position beim Arbeitsende (Übernachtungsort, falls der Tag eine Übernachtung ist). */
    private fun recordStopLater(shift: WorkShiftEntity) {
        val date = shift.endDate ?: return
        val minute = (shift.endMinute ?: 0).coerceIn(0, 24 * 60)
        pendingStop = scope.launch {
            val fix = try {
                stopLocator.locate()
            } catch (e: Exception) {
                null
            } ?: return@launch
            repo.addStop(
                de.fernfahrer.fahrtenbuch.data.StopLocationEntity(
                    date = date,
                    minute = minute,
                    latE5 = Math.round(fix.lat * 100_000).toInt(),
                    lonE5 = Math.round(fix.lon * 100_000).toInt(),
                    countryCode = fix.countryCode,
                ),
            )
        }
    }

    /** Ermittelt das Land per GPS im Hintergrund und übernimmt es, falls nicht von Hand gewählt. */
    private fun detectCountryLater(date: LocalDate) {
        if (!settings.work.value.gpsEnabled || !location.hasPermission()) return
        scope.launch {
            val code = location.currentCountry() ?: return@launch
            if (repo.applyDetectedCountry(date, code)) {
                _messages.tryEmit("Land per GPS: ${Countries.label(code)}")
            }
        }
    }
}
