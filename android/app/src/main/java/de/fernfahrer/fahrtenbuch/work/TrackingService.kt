package de.fernfahrer.fahrtenbuch.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.MainActivity
import de.fernfahrer.fahrtenbuch.R
import de.fernfahrer.fahrtenbuch.domain.geo.TrackFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Vordergrunddienst, der während der Arbeitszeit die Strecke per GPS aufzeichnet (nur das GPS des
 * Handys, kein Google-Dienst, kein Internet). Die vorgeschriebene Benachrichtigung zeigt an, dass
 * aufgezeichnet wird. Endet die Arbeitszeit, beendet sich der Dienst selbst.
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fixes = Channel<TrackFilter.Fix>(capacity = Channel.BUFFERED)
    private var thread: HandlerThread? = null
    private var recorder: TrackRecorder? = null
    private var borders: BorderWatcher? = null
    private var listening = false
    private var gpsOff = false
    private var watchdog: Job? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            fixes.trySend(
                TrackFilter.Fix(
                    lat = location.latitude,
                    lon = location.longitude,
                    accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                    timeMs = location.time,
                ),
            )
        }

        override fun onProviderDisabled(provider: String) = setGpsOff(true)
        override fun onProviderEnabled(provider: String) = setGpsOff(false)

        @Deprecated("Nur für alte Android-Versionen")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as FahrtenbuchApp
        recorder = TrackRecorder(
            save = { app.tracks.save(it) },
            countryAt = { lat, lon ->
                try {
                    app.location.countryLocator().countryAt(lat, lon)
                } catch (e: Exception) {
                    null
                }
            },
        )
        // Grenzübertritte erkennen und das Land des Tages anpassen (offline)
        borders = BorderWatcher(
            initialCountry = { app.repository.lastCountryUpTo(java.time.LocalDate.now()) },
            countryAt = { lat, lon ->
                try {
                    app.location.countryLocator().countryAt(lat, lon)
                } catch (e: Exception) {
                    null
                }
            },
            onCrossed = { at, code -> app.work.onBorderCrossed(at, code) },
        )
        scope.launch {
            for (fix in fixes) {
                val decision = try {
                    recorder?.onFix(fix)
                } catch (e: Exception) {
                    Log.e(TAG, "Punkt konnte nicht gespeichert werden", e)
                    null
                }
                if (decision?.keep == true) {
                    try {
                        borders?.onPoint(fix.lat, fix.lon, fix.timeMs)
                    } catch (e: Exception) {
                        Log.w(TAG, "Grenzerkennung fehlgeschlagen", e)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Tracking.hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel(this)
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // z. B. Start aus dem Hintergrund ohne „Immer erlauben“: dann beim nächsten Öffnen der App.
            Log.w(TAG, "Aufzeichnung konnte nicht gestartet werden", e)
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        if (watchdog == null) watchdog = scope.launch { watch() }
        _running.value = true
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (listening) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val t = HandlerThread("Streckenaufzeichnung").also { it.start() }
        thread = t
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, t.looper)
            listening = true
            setGpsOff(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
        } catch (e: Exception) {
            Log.w(TAG, "GPS nicht verfügbar", e)
        }
    }

    /** Speichert regelmäßig und beendet den Dienst, sobald keine Arbeitszeit mehr läuft. */
    private suspend fun watch() {
        val app = application as FahrtenbuchApp
        while (true) {
            delay(WATCH_MS)
            try {
                recorder?.flushIfDue()
                val stillWorking = app.repository.getRunningShift() != null
                if (!stillWorking || !app.settings.map.value.trackingEnabled || !Tracking.hasLocationPermission(this)) {
                    stopSelf()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Prüfung fehlgeschlagen", e)
            }
        }
    }

    private fun setGpsOff(off: Boolean) {
        if (off == gpsOff) return
        gpsOff = off
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Strecke wird aufgezeichnet")
            .setContentText(
                if (gpsOff) "GPS ist ausgeschaltet – bitte Standort einschalten."
                else "Während der Arbeitszeit für deine Karte. Daten bleiben auf dem Handy.",
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        _running.value = false
        if (listening) {
            try {
                (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "GPS-Abmeldung fehlgeschlagen", e)
            }
        }
        listening = false
        thread?.quitSafely()
        fixes.close()
        // Restliche Punkte noch speichern (kurz warten, aber nie hängen bleiben)
        runBlocking { withTimeoutOrNull(3_000) { recorder?.flush() } }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackingService"
        const val CHANNEL_ID = "streckenaufzeichnung"
        private const val NOTIFICATION_ID = 4711

        /** Messung jede Sekunde; gespeichert wird nur alle ≈ 35–50 m (siehe [TrackFilter]). */
        const val INTERVAL_MS = 1_000L
        private const val WATCH_MS = 30_000L

        private val _running = MutableStateFlow(false)

        /** Läuft die Aufzeichnung gerade? (für die Anzeige auf der Karte) */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            } catch (e: Exception) {
                // Android erlaubt den Start aus dem Hintergrund nicht immer; beim nächsten Öffnen der App erneut.
                Log.w(TAG, "Start nicht erlaubt", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Streckenaufzeichnung", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt an, dass während der Arbeitszeit die Strecke für die Karte aufgezeichnet wird."
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
