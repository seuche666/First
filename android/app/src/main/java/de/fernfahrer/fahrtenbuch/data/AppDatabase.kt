package de.fernfahrer.fahrtenbuch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Datenbank der App.
 *
 * WICHTIG: Jede Schemaänderung braucht eine Migration in [MIGRATIONS]. Es wird nie
 * `fallbackToDestructiveMigration` verwendet – Einträge dürfen bei Updates nicht verloren gehen.
 * Vor jeder Migration legt [PreMigrationBackup] eine Sicherungskopie an.
 */
@Database(
    entities = [
        DayEntity::class, TourEntity::class, WorkShiftEntity::class, TrackChunkEntity::class,
        StopLocationEntity::class, MilestoneEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FahrtenbuchDao
    abstract fun trackDao(): TrackDao
    abstract fun stopDao(): StopDao

    companion object {
        const val VERSION = 5
        const val DB_NAME = "fahrtenbuch.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /** Version 1 → 2: Arbeitsbeginn und Arbeitsende pro Tag (bestehende Tage: leer). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN workStartMinute INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN workEndMinute INTEGER")
            }
        }

        /**
         * Version 2 → 3:
         * - neue Tabelle `work_shifts` (Arbeitszeit über Mitternacht und mehrere Tage)
         * - bisherige Arbeitszeiten pro Tag werden 1:1 als Schichten übernommen; die alten Spalten
         *   bleiben unverändert erhalten. Eine Arbeitszeit mit Beginn (heute oder gestern) ohne Ende,
         *   nach der nichts mehr kommt, läuft als laufende Schicht weiter.
         * - neue Tagesfelder: Land von Hand gewählt (bestehende Tage: ja, damit GPS sie nie ändert),
         *   Übernachtung im LKW (automatisch), km geschätzt (nein), Zeiten automatisch (nein).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `work_shifts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startDate` TEXT NOT NULL, `startMinute` INTEGER, `endDate` TEXT, `endMinute` INTEGER, " +
                        "`running` INTEGER NOT NULL, `kmStart` INTEGER, `kmEnd` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_shifts_startDate` ON `work_shifts` (`startDate`)")

                db.execSQL("ALTER TABLE days ADD COLUMN countryManual INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE days ADD COLUMN overnightOverride INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN kmInterpolated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE days ADD COLUMN timesAuto INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "INSERT INTO work_shifts (startDate, startMinute, endDate, endMinute, running, kmStart, kmEnd) " +
                        "SELECT date, workStartMinute, CASE WHEN workEndMinute IS NULL THEN NULL ELSE date END, " +
                        "workEndMinute, 0, NULL, NULL FROM days " +
                        "WHERE workStartMinute IS NOT NULL OR workEndMinute IS NOT NULL ORDER BY date",
                )
                // Eine gerade laufende Arbeitszeit (Beginn heute/gestern, ohne Ende, danach nichts) läuft weiter.
                db.execSQL(
                    "UPDATE work_shifts SET running = 1 WHERE id = (" +
                        "SELECT w.id FROM work_shifts w WHERE w.startMinute IS NOT NULL AND w.endMinute IS NULL " +
                        "AND w.startDate >= date('now', 'localtime', '-1 day') " +
                        "AND NOT EXISTS (SELECT 1 FROM work_shifts l WHERE l.startDate > w.startDate) " +
                        "ORDER BY w.startDate DESC LIMIT 1)",
                )
            }
        }

        /**
         * Version 3 → 4: neue Tabelle `track_chunks` für die GPS-Strecken der Karte.
         * Bestehende Tabellen bleiben unverändert.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_chunks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trackId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER NOT NULL, `pointCount` INTEGER NOT NULL, `minLatE5` INTEGER NOT NULL, " +
                        "`minLonE5` INTEGER NOT NULL, `maxLatE5` INTEGER NOT NULL, `maxLonE5` INTEGER NOT NULL, " +
                        "`lengthMeters` REAL NOT NULL, `countries` TEXT NOT NULL, `data` BLOB NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_chunks_trackId` ON `track_chunks` (`trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_chunks_startTime` ON `track_chunks` (`startTime`)")
            }
        }

        /**
         * Version 4 → 5: neue Tabellen `stop_locations` (Standort beim Arbeitsende, für
         * Übernachtungsorte) und `milestones`. Bestehende Tabellen bleiben unverändert.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stop_locations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `minute` INTEGER NOT NULL, `latE5` INTEGER NOT NULL, " +
                        "`lonE5` INTEGER NOT NULL, `countryCode` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stop_locations_date` ON `stop_locations` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `milestones` (`id` TEXT NOT NULL, `achievedOn` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
