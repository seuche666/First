package de.fernfahrer.fahrtenbuch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate

/** Ein erfasster Tag. Pro Kalendertag gibt es höchstens einen Eintrag. */
@Entity(
    tableName = "days",
    indices = [Index(value = ["date"], unique = true), Index("tourId")],
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tourId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class DayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Beginn der Abwesenheit in Minuten ab Mitternacht (0 = 00:00). */
    val startMinute: Int,
    /** Ende der Abwesenheit in Minuten ab Mitternacht (1440 = 24:00, über Nacht weg). */
    val endMinute: Int,
    /** ISO-Ländercode, z. B. "DE", "AT". */
    val countryCode: String,
    val kmStart: Int? = null,
    val kmEnd: Int? = null,
    val note: String = "",
    val tourId: Long? = null,
    /** Manuell gesetzte Einordnung; null = automatisch. */
    val dayTypeOverride: DayType? = null,
    /**
     * Arbeitszeit aus App-Version 1.0.6 (ein Zeitraum pro Tag). Seit Datenbank-Version 3 steht die
     * Arbeitszeit in [WorkShiftEntity]; diese Spalten bleiben unverändert als Sicherung erhalten.
     */
    @ColumnInfo(name = "workStartMinute") val legacyWorkStartMinute: Int? = null,
    @ColumnInfo(name = "workEndMinute") val legacyWorkEndMinute: Int? = null,
    /** Land wurde von Hand gewählt – die GPS-Erkennung ändert es dann nie. Alte Einträge: ja. */
    @ColumnInfo(defaultValue = "1") val countryManual: Boolean = false,
    /** Übernachtung im LKW von Hand gesetzt; null = automatisch (bei mehrtägiger Tour). */
    val overnightOverride: Boolean? = null,
    /** km-Stände wurden bei einer Schicht über Mitternacht anteilig geschätzt. */
    @ColumnInfo(defaultValue = "0") val kmInterpolated: Boolean = false,
    /**
     * Abwesenheitszeiten wurden automatisch aus der Arbeitszeit vorgeschlagen (nicht von Hand
     * bestätigt). Nur dann passt die App sie beim Arbeitsende noch an.
     */
    @ColumnInfo(defaultValue = "0") val timesAuto: Boolean = false,
) {
    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** Gefahrene km, falls beide Kilometerstände vorhanden und plausibel sind. */
    val distanceKm: Int?
        get() = if (kmStart != null && kmEnd != null && kmEnd >= kmStart) kmEnd - kmStart else null
}
