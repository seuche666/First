package de.fernfahrer.fahrtenbuch.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/** Empfängt den Erinnerungs-Wecker und „Läuft noch“ aus der Benachrichtigung. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FahrtenbuchApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shift = app.repository.getRunningShift()
                when (intent.action) {
                    ACTION_REMIND -> {
                        val start = shift?.start
                        if (shift != null && start != null && app.settings.work.value.reminderEnabled) {
                            app.reminders.showNotification(Duration.between(start, LocalDateTime.now()).toHours())
                        }
                    }
                    ACTION_SNOOZE -> if (shift != null) app.reminders.snooze() else app.reminders.cancel()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "de.fernfahrer.fahrtenbuch.ERINNERUNG"
        const val ACTION_SNOOZE = "de.fernfahrer.fahrtenbuch.LAEUFT_NOCH"
    }
}
