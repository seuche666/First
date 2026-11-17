package de.fernfahrer.fahrtenbuch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Stellt nach einem Neustart oder App-Update den Erinnerungs-Wecker und die Streckenaufzeichnung
 * einer laufenden Arbeitszeit wieder her.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FahrtenbuchApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shift = app.repository.getRunningShift()
                shift?.let { app.reminders.ensureScheduled(it) }
                // Aufzeichnung fortsetzen (Android erlaubt das nur mit „Standort immer erlauben“)
                if (shift != null) app.tracking.sync(workRunning = true)
            } finally {
                pending.finish()
            }
        }
    }
}
