package de.fernfahrer.fahrtenbuch.work

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.fernfahrer.fahrtenbuch.MainActivity
import de.fernfahrer.fahrtenbuch.R
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Erinnerung „Arbeit läuft seit … Std. – vergessen zu beenden?“, wenn eine Arbeitszeit länger als
 * die eingestellte Stundenzahl läuft. Nutzt einen einzelnen Wecker (AlarmManager), keinen Dauerdienst.
 */
class ReminderScheduler(private val context: Context, private val settings: Settings) {

    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Plant die Erinnerung für eine gerade begonnene oder laufende Schicht. */
    fun scheduleFor(shift: WorkShiftEntity, now: LocalDateTime = LocalDateTime.now()) {
        val work = settings.work.value
        val start = shift.start
        if (!work.reminderEnabled || !shift.running || start == null) {
            cancel()
            return
        }
        val due = firstReminder(start, work.reminderHours, now)
        settings.reminderShiftId = shift.id
        settings.reminderDueAt = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        arm(settings.reminderDueAt)
    }

    /** Nach Neustart oder Update: Wecker für die laufende Schicht wiederherstellen. */
    fun ensureScheduled(shift: WorkShiftEntity) {
        val work = settings.work.value
        if (!work.reminderEnabled) return cancel()
        if (settings.reminderShiftId == shift.id && settings.reminderDueAt > 0) {
            arm(maxOf(settings.reminderDueAt, System.currentTimeMillis() + 5_000))
        } else {
            scheduleFor(shift)
        }
    }

    /** „Läuft noch“: in 3 Stunden erneut erinnern. */
    fun snooze(now: LocalDateTime = LocalDateTime.now()) {
        val due = now.plus(SNOOZE)
        settings.reminderDueAt = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        arm(settings.reminderDueAt)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun cancel() {
        alarms.cancel(alarmIntent())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        settings.reminderDueAt = 0
        settings.reminderShiftId = 0
    }

    private fun arm(atMillis: Long) {
        // Ungefährer Wecker (auch im Energiesparmodus), braucht keine Sonderberechtigung.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Zeigt die Erinnerung mit den Buttons „Arbeit beenden“ und „Läuft noch“. */
    fun showNotification(hoursRunning: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_END_WORK)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context, 4,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Arbeit läuft seit $hoursRunning Std. – vergessen zu beenden?")
            .setContentText("Tippe auf „Arbeit beenden“ oder „Läuft noch“.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Arbeit beenden", end)
            .addAction(0, "Läuft noch", snooze)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Berechtigung wurde entzogen – nichts zu tun
        }
    }

    companion object {
        const val CHANNEL_ID = "erinnerung_arbeitszeit"
        const val NOTIFICATION_ID = 15
        val SNOOZE: Duration = Duration.ofHours(3)

        /** Zeitpunkt der ersten Erinnerung: Arbeitsbeginn + Stunden, frühestens in einer Minute. */
        fun firstReminder(start: LocalDateTime, hours: Int, now: LocalDateTime): LocalDateTime {
            val due = start.plusHours(hours.toLong())
            return if (due.isAfter(now)) due else now.plusMinutes(1)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(CHANNEL_ID, "Erinnerung Arbeitszeit", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Erinnert, wenn eine Arbeitszeit sehr lange läuft"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
