package de.fernfahrer.fahrtenbuch.work

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.content.ContextCompat
import de.fernfahrer.fahrtenbuch.data.Settings

/** Startet und stoppt die Streckenaufzeichnung passend zur laufenden Arbeitszeit. */
fun interface TrackingControl {
    /** [workRunning]: läuft gerade eine Arbeitszeit? */
    fun sync(workRunning: Boolean)

    companion object {
        val NONE = TrackingControl { }
    }
}

/**
 * Aufzeichnung nur während der Arbeitszeit, nur wenn in den Einstellungen eingeschaltet und der
 * Standort erlaubt ist. Ohne Berechtigung passiert nichts – der Rest der App funktioniert normal.
 */
class Tracking(private val context: Context, private val settings: Settings) : TrackingControl {

    override fun sync(workRunning: Boolean) {
        if (workRunning && settings.map.value.trackingEnabled && hasLocationPermission(context)) {
            TrackingService.start(context)
        } else {
            TrackingService.stop(context)
        }
    }

    companion object {
        private const val TAG = "Tracking"

        fun hasLocationPermission(context: Context): Boolean =
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

        /** „Immer erlauben“ (ab Android 10). Vorher gilt die normale Standort-Berechtigung auch im Hintergrund. */
        fun hasBackgroundPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        /** Öffnet die Android-Abfrage „Akku-Optimierung ignorieren“ (oder die Liste, falls nicht möglich). */
        fun openBatterySettings(context: Context) {
            val direct = Intent(
                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val list = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            for (intent in listOf(direct, list, appDetails(context))) {
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Einstellung nicht verfügbar: ${intent.action}", e)
                }
            }
        }

        fun appDetails(context: Context): Intent =
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
