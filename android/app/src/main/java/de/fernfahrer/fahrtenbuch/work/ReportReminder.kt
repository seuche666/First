package de.fernfahrer.fahrtenbuch.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.MainActivity
import de.fernfahrer.fahrtenbuch.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Erinnerung am 1. jedes Monats um 18 Uhr: „Reisekostenabrechnung für Oktober erstellen?“. Tippen
 * öffnet die Vorschau des Vormonats. Nur wenn im Vormonat Tage erfasst sind; in den Einstellungen
 * abschaltbar. War das Handy am 1. aus, kommt die Erinnerung bis zum 7. nach.
 */
object ReportReminder {
    private const val TAG = "ReportReminder"
    private const val WORK_NAME = "abrechnung_erinnerung"
    const val CHANNEL_ID = "abrechnung"
    private const val NOTIFICATION_ID = 4713
    val TIME: LocalTime = LocalTime.of(18, 0)

    /** Letzter Tag im Monat, an dem eine verpasste Erinnerung noch nachgeholt wird. */
    private const val CATCH_UP_DAYS = 7

    /** Nächster Zeitpunkt der Erinnerung (1. des Monats, 18 Uhr) nach [now]. */
    fun nextFire(now: LocalDateTime): LocalDateTime {
        val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(TIME)
        return if (now.isBefore(thisMonth)) thisMonth else thisMonth.plusMonths(1)
    }

    /**
     * Für welchen Monat jetzt erinnert werden soll: der Vormonat, sobald der 1. um 18 Uhr erreicht ist
     * (bis zum 7.), und nur, wenn für ihn noch nicht erinnert wurde. Sonst null.
     */
    fun dueMonth(now: LocalDateTime, lastReminded: String?): YearMonth? {
        val firstOfMonth = now.toLocalDate().withDayOfMonth(1).atTime(TIME)
        if (now.isBefore(firstOfMonth) || now.dayOfMonth > CATCH_UP_DAYS) return null
        val month = YearMonth.from(now).minusMonths(1)
        return if (lastReminded == month.toString()) null else month
    }

    fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, Locale.GERMANY)

    /** Plant die nächste Prüfung (oder hebt sie auf, wenn ausgeschaltet). */
    fun schedule(context: Context, enabled: Boolean, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        try {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = LocalDateTime.now()
            // Steht eine Erinnerung noch aus (z. B. Handy war am 1. aus), bald prüfen
            val at = if (dueMonth(now, (context.applicationContext as? FahrtenbuchApp)?.settings?.lastReportReminder) != null) {
                now.plusMinutes(1)
            } else {
                nextFire(now)
            }
            val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
            wm.enqueueUniqueWork(
                WORK_NAME, policy,
                OneTimeWorkRequestBuilder<ReportReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erinnerung an die Abrechnung konnte nicht geplant werden", e)
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Reisekostenabrechnung", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Erinnert am 1. des Monats an die Reisekostenabrechnung des Vormonats."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun show(context: Context, month: YearMonth) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_REPORT)
                .putExtra(MainActivity.EXTRA_MONTH, month.toString())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reisekostenabrechnung für ${monthName(month)} erstellen?")
            .setContentText("Tippen, um die Vorschau für ${monthName(month)} ${month.year} zu öffnen.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
        }
    }
}

/** Prüft, ob die Erinnerung fällig ist, zeigt sie und plant die nächste. */
class ReportReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FahrtenbuchApp ?: return Result.success()
        val settings = app.settings
        if (!settings.reportReminder.value) return Result.success()
        val now = LocalDateTime.now()
        val month = ReportReminder.dueMonth(now, settings.lastReportReminder)
        if (month != null) {
            val hasDays = app.repository.getAllDays().any { YearMonth.from(it.date) == month }
            if (hasDays) ReportReminder.show(app, month)
            settings.lastReportReminder = month.toString()
        }
        // Nächsten Monat planen (wird nach diesem Auftrag ausgeführt)
        ReportReminder.schedule(app, enabled = true, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
