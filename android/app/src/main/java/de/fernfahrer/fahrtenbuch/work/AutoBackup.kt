package de.fernfahrer.fahrtenbuch.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.MainActivity
import de.fernfahrer.fahrtenbuch.R
import de.fernfahrer.fahrtenbuch.data.AutoBackupSettings
import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFolder
import de.fernfahrer.fahrtenbuch.data.SafBackupFolder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatisches Backup in einen vom Nutzer gewählten Ordner: gleiches Format wie die manuelle
 * Datensicherung (inklusive GPS-Strecken, Übernachtungsplätzen und Notizen). Ein Hintergrund-Auftrag
 * ([AutoBackupWorker]) prüft alle paar Stunden, ob wieder eine Sicherung fällig ist – auch wenn die
 * App geschlossen ist. Ältere automatische Sicherungen über der gewählten Anzahl werden gelöscht.
 */
object AutoBackup {
    private const val TAG = "AutoBackup"
    const val PREFIX = "Fahrtenbuch-Auto-"
    private const val SUFFIX = ".json"
    private const val WORK_NAME = "automatisches_backup"
    const val CHANNEL_ID = "datensicherung"
    private const val NOTIFICATION_ID = 4712

    /** Prüfabstand des Hintergrund-Auftrags. */
    const val CHECK_HOURS = 6L

    private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    fun fileName(now: LocalDateTime): String = PREFIX + now.format(NAME_FORMAT) + SUFFIX

    /**
     * Ist wieder eine Sicherung fällig? Etwas Spielraum (2 Std.), damit eine tägliche Sicherung nicht
     * durch den Prüfabstand jeden Tag später wird.
     */
    fun isDue(lastMs: Long, interval: AutoBackupSettings.Interval, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (lastMs <= 0L) return true
        val last = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMs), zone)
        val next = when (interval) {
            AutoBackupSettings.Interval.DAILY -> last.plusDays(1)
            AutoBackupSettings.Interval.WEEKLY -> last.plusWeeks(1)
            AutoBackupSettings.Interval.MONTHLY -> last.plusMonths(1)
        }.minusHours(2)
        return !now.isBefore(next) || now.isBefore(last)
    }

    /** Schreibt die Sicherung als neue Datei und löscht ältere automatische Sicherungen über [keep]. */
    fun writeAndPrune(folder: BackupFolder, data: BackupData, now: LocalDateTime, keep: Int): String {
        val name = fileName(now)
        folder.write(name, Backup.toJson(data).toByteArray())
        prune(folder, keep)
        return name
    }

    /** Löscht die ältesten automatischen Sicherungen, sodass höchstens [keep] übrig bleiben. */
    fun prune(folder: BackupFolder, keep: Int) {
        val auto = folder.list().filter { it.name.startsWith(PREFIX) }.sortedByDescending { it.name }
        for (old in auto.drop(keep.coerceAtLeast(1))) {
            try {
                folder.delete(old)
            } catch (e: Exception) {
                Log.w(TAG, "Alte Sicherung ${old.name} konnte nicht gelöscht werden", e)
            }
        }
    }

    /**
     * Sichert jetzt in den gewählten Ordner. Wirft eine Ausnahme mit verständlicher Meldung, wenn kein
     * Ordner gewählt ist oder das Schreiben fehlschlägt.
     */
    suspend fun runNow(app: FahrtenbuchApp, now: LocalDateTime = LocalDateTime.now()): String = withContext(Dispatchers.IO) {
        val uri = app.settings.backupFolder.value ?: throw IllegalStateException("Kein Speicherort gewählt.")
        val folder = SafBackupFolder(app.contentResolver, Uri.parse(uri))
        val data = app.fullBackupData()
        val name = try {
            writeAndPrune(folder, data, now, app.settings.autoBackup.value.keep)
        } catch (e: SecurityException) {
            throw IllegalStateException("Kein Zugriff mehr auf den Speicherort. Bitte den Ordner neu wählen.")
        }
        app.settings.setLastAutoBackup(System.currentTimeMillis())
        cancelFailureNotice(app)
        name
    }

    /** Hintergrund-Auftrag passend zu den Einstellungen planen oder entfernen. */
    fun schedule(context: Context, settings: AutoBackupSettings, hasFolder: Boolean) {
        try {
            val wm = WorkManager.getInstance(context)
            if (settings.enabled && hasFolder) {
                wm.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<AutoBackupWorker>(CHECK_HOURS, TimeUnit.HOURS).build(),
                )
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        } catch (e: Exception) {
            // z. B. in Tests ohne WorkManager
            Log.w(TAG, "Automatisches Backup konnte nicht geplant werden", e)
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Datensicherung", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Meldet, wenn das automatische Backup fehlgeschlagen ist."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showFailureNotice(context: Context, reason: String) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Automatisches Backup fehlgeschlagen")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\nBitte in den Einstellungen unter „Datensicherung“ prüfen."))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
        }
    }

    private fun cancelFailureNotice(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}

/** Hintergrund-Auftrag: sichert, wenn fällig; bei Fehlschlag eine Benachrichtigung. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FahrtenbuchApp ?: return Result.success()
        val s = app.settings.autoBackup.value
        if (!s.enabled) return Result.success()
        if (!AutoBackup.isDue(app.settings.lastAutoBackup.value, s.interval, LocalDateTime.now())) return Result.success()
        try {
            AutoBackup.runNow(app)
        } catch (e: Exception) {
            Log.w("AutoBackupWorker", "Backup fehlgeschlagen", e)
            AutoBackup.showFailureNotice(app, e.message ?: "Unbekannter Fehler")
        }
        // Nächster Versuch beim nächsten regulären Prüfen (kein schnelles Wiederholen)
        return Result.success()
    }
}
