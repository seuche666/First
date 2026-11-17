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
    entities = [DayEntity::class, TourEntity::class, WorkShiftEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FahrtenbuchDao

    companion object {
        const val VERSION = 3
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

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
