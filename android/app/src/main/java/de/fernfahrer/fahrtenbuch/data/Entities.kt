package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate

/** Eine mehrtägige Tour. Beginn und Ende ergeben sich aus den zugehörigen Tagen. */
@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** false = Tour läuft noch. */
    val ended: Boolean = false,
)

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
    /** Arbeitsbeginn für die Firma (Minuten ab Mitternacht); hat keinen Einfluss auf die Steuer-Einordnung. */
    val workStartMinute: Int? = null,
    /** Arbeitsende für die Firma (Minuten ab Mitternacht, 1440 = 24:00). */
    val workEndMinute: Int? = null,
) {
    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** Arbeitszeit in Minuten, falls Beginn und Ende erfasst sind. */
    val workMinutes: Int?
        get() = if (workStartMinute != null && workEndMinute != null && workEndMinute > workStartMinute) {
            workEndMinute - workStartMinute
        } else {
            null
        }

    /** Gefahrene km, falls beide Kilometerstände vorhanden und plausibel sind. */
    val distanceKm: Int?
        get() = if (kmStart != null && kmEnd != null && kmEnd >= kmStart) kmEnd - kmStart else null
}
