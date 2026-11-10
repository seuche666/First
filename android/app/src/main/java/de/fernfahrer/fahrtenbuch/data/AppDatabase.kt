package de.fernfahrer.fahrtenbuch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DayEntity::class, TourEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FahrtenbuchDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        const val DB_NAME = "fahrtenbuch.db"

        /** Version 1 → 2: Arbeitsbeginn und Arbeitsende pro Tag (bestehende Tage: leer). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN workStartMinute INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN workEndMinute INTEGER")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
