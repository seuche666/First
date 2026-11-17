package de.fernfahrer.fahrtenbuch.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Legt vor jeder Datenbank-Migration automatisch Sicherungskopien an:
 * 1. die Datenbankdateien unverändert im App-Speicher (files/db-sicherungen/…),
 * 2. eine wiederherstellbare Datensicherung (JSON) im App-Speicher und unter Download/Fahrtenbuch
 *    (ab Android 10; diese Datei bleibt auch nach einer Deinstallation erhalten).
 * Muss aufgerufen werden, bevor Room die Datenbank öffnet.
 */
object PreMigrationBackup {
    private const val TAG = "PreMigrationBackup"
    private const val KEEP = 5
    const val DIR_NAME = "db-sicherungen"

    data class Result(
        val fromVersion: Int,
        val internalDir: File,
        /** Anzeigename der Datei unter Download/Fahrtenbuch, falls dort gespeichert. */
        val downloadFileName: String?,
    )

    /** Schreibt eine Datei nach Download/Fahrtenbuch; gibt den Dateinamen zurück oder null. */
    fun interface PublicWriter {
        fun write(context: Context, fileName: String, bytes: ByteArray): String?
    }

    fun runIfNeeded(
        context: Context,
        dbName: String,
        targetVersion: Int,
        driverName: String,
        rules: CountingRules?,
        publicWriter: PublicWriter = DownloadsWriter,
    ): Result? {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return null
        return try {
            val version = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            if (version == 0 || version >= targetVersion) return null

            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val root = File(context.filesDir, DIR_NAME)
            val dir = File(root, "vor-update-v$version-$stamp").apply { mkdirs() }

            // 1. Datenbankdateien unverändert kopieren (inkl. Write-Ahead-Log, sonst fehlen evtl. Daten)
            for (suffix in listOf("", "-wal", "-shm")) {
                val f = File(dbFile.path + suffix)
                if (f.exists()) f.copyTo(File(dir, dbName + suffix), overwrite = true)
            }

            // 2. Wiederherstellbare Datensicherung aus der alten Datenbank
            val data = readOldDatabase(dbFile, driverName, rules)
            val json = Backup.toJson(data).toByteArray()
            val fileName = "Fahrtenbuch_Sicherung_vor_Update_$stamp.json"
            File(dir, fileName).writeBytes(json)
            val publicName = try {
                publicWriter.write(context, fileName, json)
            } catch (e: Exception) {
                Log.w(TAG, "Sicherung in Download nicht möglich", e)
                null
            }

            // Nur die letzten Sicherungen behalten
            root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.drop(KEEP)
                ?.forEach { it.deleteRecursively() }

            Result(version, dir, publicName)
        } catch (e: Exception) {
            // Die Migration selbst löscht nie Daten; eine fehlgeschlagene Kopie verhindert sie nicht.
            Log.e(TAG, "Sicherung vor dem Update fehlgeschlagen", e)
            null
        }
    }

    /** Liest Tage und Touren aus einer Datenbank der Version 1 oder 2 (nur lesend). */
    fun readOldDatabase(dbFile: File, driverName: String, rules: CountingRules?): BackupData =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tours = db.rawQuery("SELECT id, ended FROM tours", null).use { c ->
                buildList { while (c.moveToNext()) add(TourEntity(c.getLong(0), c.getInt(1) != 0)) }
            }
            val days = db.rawQuery("SELECT * FROM days ORDER BY date", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            DayEntity(
                                id = c.long("id")!!,
                                date = LocalDate.parse(c.string("date")),
                                startMinute = c.int("startMinute")!!,
                                endMinute = c.int("endMinute")!!,
                                countryCode = c.string("countryCode")!!,
                                kmStart = c.int("kmStart"),
                                kmEnd = c.int("kmEnd"),
                                note = c.string("note").orEmpty(),
                                tourId = c.long("tourId"),
                                dayTypeOverride = c.string("dayTypeOverride")
                                    ?.let { v -> DayType.entries.firstOrNull { it.name == v } },
                                legacyWorkStartMinute = c.int("workStartMinute"),
                                legacyWorkEndMinute = c.int("workEndMinute"),
                                countryManual = true,
                            ),
                        )
                    }
                }
            }
            BackupData(tours, days, driverName, rules, LegacyWork.toShifts(days))
        }

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    private fun Cursor.int(name: String): Int? = index(name)?.let(::getInt)
    private fun Cursor.long(name: String): Long? = index(name)?.let(::getLong)
    private fun Cursor.string(name: String): String? = index(name)?.let(::getString)

    /** Speichert unter Download/Fahrtenbuch (Android 10+, ohne zusätzliche Berechtigung). */
    object DownloadsWriter : PublicWriter {
        override fun write(context: Context, fileName: String, bytes: ByteArray): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fahrtenbuch")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            return "Download/Fahrtenbuch/$fileName"
        }
    }
}
