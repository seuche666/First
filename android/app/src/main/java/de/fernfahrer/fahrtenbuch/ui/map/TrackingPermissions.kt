package de.fernfahrer.fahrtenbuch.ui.map

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.fernfahrer.fahrtenbuch.work.Tracking

/** Aktueller Stand der Berechtigungen für die Aufzeichnung, mit Aktionen zum Erlauben. */
class TrackingPermissionState(
    val location: Boolean,
    val background: Boolean,
    val batteryOk: Boolean,
    val requestLocation: () -> Unit,
    val requestBackground: () -> Unit,
    val openBattery: () -> Unit,
)

/**
 * Prüft Standort, „Immer erlauben“ und Akku-Optimierung (auch nach Rückkehr aus den
 * Android-Einstellungen) und ruft [onChanged] auf, wenn sich etwas geändert hat.
 */
@Composable
fun rememberTrackingPermissions(onChanged: () -> Unit = {}): TrackingPermissionState {
    val context = LocalContext.current
    var location by remember { mutableStateOf(Tracking.hasLocationPermission(context)) }
    var background by remember { mutableStateOf(Tracking.hasBackgroundPermission(context)) }
    var battery by remember { mutableStateOf(Tracking.isIgnoringBatteryOptimizations(context)) }

    fun refresh() {
        val l = Tracking.hasLocationPermission(context)
        val b = Tracking.hasBackgroundPermission(context)
        val a = Tracking.isIgnoringBatteryOptimizations(context)
        val changed = l != location || b != background || a != battery
        location = l
        background = b
        battery = a
        if (changed) onChanged()
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    var askedAt by remember { mutableStateOf(0L) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        // Android fragt nicht mehr (dauerhaft abgelehnt): direkt zu den App-Einstellungen
        if (!location && SystemClock.elapsedRealtime() - askedAt < 600) context.startActivity(Tracking.appDetails(context))
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        if (!background && SystemClock.elapsedRealtime() - askedAt < 600) context.startActivity(Tracking.appDetails(context))
    }

    return TrackingPermissionState(
        location = location,
        background = background,
        batteryOk = battery,
        requestLocation = {
            askedAt = SystemClock.elapsedRealtime()
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        requestBackground = {
            askedAt = SystemClock.elapsedRealtime()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        },
        openBattery = { Tracking.openBatterySettings(context) },
    )
}

/** Erklärungen in einfachen Worten (Karte, Einstellungen, Dialog vor der Abfrage). */
object TrackingTexts {
    const val LOCATION_MISSING = "Standort nicht erlaubt – es wird keine Strecke für die Karte aufgezeichnet. " +
        "Der Rest der App funktioniert normal."
    const val BACKGROUND_MISSING = "Standort auf „Immer erlauben“ stellen: Nur dann kann Android die Aufzeichnung " +
        "nach einem Neustart des Handys oder einem App-Update selbst fortsetzen. Aufgezeichnet wird trotzdem " +
        "nur während der Arbeitszeit."
    const val BATTERY = "Die Akku-Optimierung von Android kann die Aufzeichnung unterwegs stoppen. " +
        "Erlaube eine Ausnahme für das Fahrtenbuch."
    const val TRACKING_OFF = "Die Streckenaufzeichnung ist ausgeschaltet."
}
