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

    /**
     * Liest alle Daten aus einer alten Datenbank (nur lesend). Funktioniert für jede bisherige Version:
     * Spalten und Tabellen, die es in der alten Version noch nicht gab, erhalten sinnvolle Werte.
     */
    fun readOldDatabase(dbFile: File, driverName: String, rules: CountingRules?): BackupData =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun hasTable(name: String) = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name),
            ).use { it.moveToFirst() }

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
                                // Ab Version 3; ältere Einträge gelten als von Hand erfasst.
                                countryManual = c.int("countryManual")?.let { it != 0 } ?: true,
                                overnightOverride = c.int("overnightOverride")?.let { it != 0 },
                                kmInterpolated = c.int("kmInterpolated")?.let { it != 0 } ?: false,
                                timesAuto = c.int("timesAuto")?.let { it != 0 } ?: false,
                            ),
                        )
                    }
                }
            }
            val shifts = if (hasTable("work_shifts")) {
                db.rawQuery("SELECT * FROM work_shifts ORDER BY startDate, startMinute", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                WorkShiftEntity(
                                    id = c.long("id")!!,
                                    startDate = LocalDate.parse(c.string("startDate")),
                                    startMinute = c.int("startMinute"),
                                    endDate = c.string("endDate")?.let(LocalDate::parse),
                                    endMinute = c.int("endMinute"),
                                    running = (c.int("running") ?: 0) != 0,
                                    kmStart = c.int("kmStart"),
                                    kmEnd = c.int("kmEnd"),
                                ),
                            )
                        }
                    }
                }
            } else {
                LegacyWork.toShifts(days)
            }
            val tracks: List<TrackChunkEntity>? = if (hasTable("track_chunks")) {
                db.rawQuery("SELECT * FROM track_chunks ORDER BY trackId, seq", null).use { c ->
                    buildList<TrackChunkEntity> {
                        while (c.moveToNext()) {
                            add(
                                TrackChunkEntity(
                                    id = c.long("id")!!,
                                    trackId = c.long("trackId")!!,
                                    seq = c.int("seq")!!,
                                    startTime = c.long("startTime")!!,
                                    endTime = c.long("endTime")!!,
                                    pointCount = c.int("pointCount")!!,
                                    minLatE5 = c.int("minLatE5")!!,
                                    minLonE5 = c.int("minLonE5")!!,
                                    maxLatE5 = c.int("maxLatE5")!!,
                                    maxLonE5 = c.int("maxLonE5")!!,
                                    lengthMeters = c.double("lengthMeters") ?: 0.0,
                                    countries = c.string("countries").orEmpty(),
                                    data = c.blob("data") ?: ByteArray(0),
                                ),
                            )
                        }
                    }
                }
            } else {
                null
            }
            val stops: List<StopLocationEntity>? = if (hasTable("stop_locations")) {
                db.rawQuery("SELECT * FROM stop_locations ORDER BY date, minute", null).use { c ->
                    buildList<StopLocationEntity> {
                        while (c.moveToNext()) {
                            add(
                                StopLocationEntity(
                                    id = c.long("id")!!,
                                    date = LocalDate.parse(c.string("date")),
                                    minute = c.int("minute") ?: 0,
                                    latE5 = c.int("latE5")!!,
                                    lonE5 = c.int("lonE5")!!,
                                    countryCode = c.string("countryCode"),
                                ),
                            )
                        }
                    }
                }
            } else {
                null
            }
            val milestones: List<MilestoneEntity>? = if (hasTable("milestones")) {
                db.rawQuery("SELECT * FROM milestones", null).use { c ->
                    buildList<MilestoneEntity> {
                        while (c.moveToNext()) add(MilestoneEntity(c.string("id")!!, LocalDate.parse(c.string("achievedOn"))))
                    }
                }
            } else {
                null
            }
            BackupData(tours, days, driverName, rules, shifts, tracks = tracks, stops = stops, milestones = milestones)
        }

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    private fun Cursor.int(name: String): Int? = index(name)?.let(::getInt)
    private fun Cursor.long(name: String): Long? = index(name)?.let(::getLong)
    private fun Cursor.string(name: String): String? = index(name)?.let(::getString)
    private fun Cursor.double(name: String): Double? = index(name)?.let(::getDouble)
    private fun Cursor.blob(name: String): ByteArray? = index(name)?.let(::getBlob)

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
