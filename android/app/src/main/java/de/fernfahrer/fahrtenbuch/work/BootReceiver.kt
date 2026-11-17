package de.fernfahrer.fahrtenbuch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Stellt nach einem Neustart oder App-Update den Erinnerungs-Wecker einer laufenden Arbeitszeit wieder her. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FahrtenbuchApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.getRunningShift()?.let { app.reminders.ensureScheduled(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
