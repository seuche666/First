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
    /** Position beim Arbeitsende für den Übernachtungsplatz auf der Karte. */
    private val stopLocator: StopLocator = StopLocator.NONE,
) {
    /** Zuletzt gestartete Speicherung eines Übernachtungsplatzes (für Tests). */
    @Volatile var pendingPlace: kotlinx.coroutines.Job? = null
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
            // Das vom Vortag übernommene Land durch das tatsächliche ersetzen
            detectCountryLater(at.toLocalDate(), replaceSingle = true)
        }
        return result
    }

    /**
     * Arbeit beenden. Nur wenn [savePlace] gesetzt ist (Haken im km-Fenster), wird an der aktuellen
     * Position ein Übernachtungsplatz auf der Karte gespeichert. Die Zählung „Übernachtung im LKW“
     * für die Steuer hängt davon nicht ab.
     */
    suspend fun endWork(at: LocalDateTime, km: Int?, savePlace: Boolean = false): WorkShiftEntity? {
        val ended = repo.endWork(at, km) ?: return null
        reminders.cancel()
        tracking.sync(workRunning = false)
        if (savePlace) savePlaceLater(ended)
        detectCountryLater(ended.endDate ?: at.toLocalDate())
        return ended
    }

    suspend fun endTour(at: LocalDateTime, km: Int?): EndTourResult {
        val result = repo.endTour(at, km)
        if (result is EndTourResult.Ended) {
            if (result.endedShift != null) {
                reminders.cancel()
                tracking.sync(workRunning = false)
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

    /**
     * Grenzübertritt während der Arbeitszeit (von der Streckenaufzeichnung erkannt): Land für den
     * aktuellen Tag ergänzen, sofern es dort nicht von Hand gewählt wurde.
     */
    suspend fun onBorderCrossed(at: LocalDateTime, code: String): Boolean {
        if (!settings.work.value.gpsEnabled) return false
        if (repo.getRunningShift() == null) return false
        val changed = repo.recordBorderCrossing(at, code)
        if (changed) _messages.tryEmit("Grenze überquert: ${Countries.label(code)}")
        return changed
    }

    /** Speichert im Hintergrund die aktuelle Position als Übernachtungsplatz auf der Karte. */
    private fun savePlaceLater(shift: WorkShiftEntity) {
        val date = shift.endDate ?: return
        val minute = (shift.endMinute ?: 0).coerceIn(0, 24 * 60)
        pendingPlace = scope.launch {
            val fix = try {
                stopLocator.locate()
            } catch (e: Exception) {
                null
            }
            if (fix == null) {
                _messages.tryEmit("Übernachtungsplatz nicht gespeichert – kein GPS-Standort.")
                return@launch
            }
            try {
                val place = repo.addOvernightStay(
                    date = date,
                    minute = minute,
                    latE5 = Math.round(fix.lat * 100_000).toInt(),
                    lonE5 = Math.round(fix.lon * 100_000).toInt(),
                    countryCode = fix.countryCode,
                )
                _messages.tryEmit(
                    if (place.name.isNotBlank()) "Übernachtung bei „${place.name}“ gespeichert"
                    else "Übernachtungsplatz auf der Karte gespeichert",
                )
            } catch (e: Exception) {
                _messages.tryEmit("Übernachtungsplatz konnte nicht gespeichert werden.")
            }
        }
    }

    /** Ermittelt das Land per GPS im Hintergrund und übernimmt es, falls nicht von Hand gewählt. */
    private fun detectCountryLater(date: LocalDate, replaceSingle: Boolean = false) {
        if (!settings.work.value.gpsEnabled || !location.hasPermission()) return
        scope.launch {
            val code = location.currentCountry() ?: return@launch
            if (repo.applyDetectedCountry(date, code, replaceSingle)) {
                _messages.tryEmit("Land per GPS: ${Countries.label(code)}")
            }
        }
    }
}
