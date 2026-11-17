package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Eine Arbeitsschicht für die Firma. Sie kann über Mitternacht und mehrere Tage laufen; die
 * Aufteilung auf die Kalendertage berechnet [de.fernfahrer.fahrtenbuch.domain.WorkSplitter].
 * Hat keinen Einfluss auf die Steuer-Einordnung.
 */
@Entity(tableName = "work_shifts", indices = [Index("startDate")])
data class WorkShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: LocalDate,
    /** Beginn in Minuten ab Mitternacht; null nur bei unvollständigen alten Einträgen. */
    val startMinute: Int?,
    /** Ende; beide null, solange die Arbeit läuft oder bei unvollständigen alten Einträgen. */
    val endDate: LocalDate?,
    /** Ende in Minuten ab Mitternacht (1440 = 24:00). */
    val endMinute: Int?,
    /** true = Arbeit läuft gerade (höchstens eine Schicht). */
    val running: Boolean = false,
    /** km-Stand bei Arbeitsbeginn und -ende. */
    val kmStart: Int? = null,
    val kmEnd: Int? = null,
) {
    val start: LocalDateTime? get() = startMinute?.let { startDate.atStartOfDay().plusMinutes(it.toLong()) }
    val end: LocalDateTime?
        get() = if (endDate != null && endMinute != null) endDate.atStartOfDay().plusMinutes(endMinute.toLong()) else null
}
