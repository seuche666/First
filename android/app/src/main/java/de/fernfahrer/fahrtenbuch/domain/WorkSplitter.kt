package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToLong

/** Der Teil einer Arbeitsschicht, der auf einen Kalendertag fällt. */
data class WorkPortion(
    val shiftId: Long,
    val date: LocalDate,
    /** Beginn an diesem Tag (0 = Schicht lief schon seit dem Vortag); null = unbekannt (alter Eintrag). */
    val startMinute: Int?,
    /** Ende an diesem Tag (1440 = läuft über Mitternacht weiter); null = unbekannt (alter Eintrag). */
    val endMinute: Int?,
    /** Dieser Teil läuft gerade (Ende = jetzt). */
    val running: Boolean = false,
    val fromPreviousDay: Boolean = false,
    val toNextDay: Boolean = false,
) {
    val minutes: Int?
        get() = if (startMinute != null && endMinute != null) (endMinute - startMinute).coerceAtLeast(0) else null

    /** Kurzer Text, z. B. "23:30–24:00", "ab 06:10 (läuft)". */
    val label: String
        get() = when {
            running && startMinute != null -> "ab ${Format.time(startMinute)} (läuft)"
            startMinute != null && endMinute != null -> "${Format.time(startMinute)}–${Format.time(endMinute)}"
            startMinute != null -> "ab ${Format.time(startMinute)}"
            endMinute != null -> "bis ${Format.time(endMinute)}"
            else -> "?"
        }
}

/** Km-Anteil einer Schicht an einem Kalendertag (km-Stand zu Beginn und Ende des Tagesanteils). */
data class KmShare(
    val date: LocalDate,
    val kmAtStart: Int,
    val kmAtEnd: Int,
    /** km-Stand am Tagesbeginn wurde anteilig geschätzt (Schicht lief über Mitternacht). */
    val startEstimated: Boolean,
    /** km-Stand am Tagesende wurde anteilig geschätzt. */
    val endEstimated: Boolean,
)

/**
 * Teilt Arbeitsschichten an den Tagesgrenzen auf, z. B. Sonntag 23:30 bis Montag 10:00 in
 * Sonntag 23:30–24:00 und Montag 00:00–10:00.
 */
object WorkSplitter {
    private const val DAY = 24 * 60

    fun split(shift: WorkShiftEntity, now: LocalDateTime): List<WorkPortion> {
        val start = shift.start
        // Unvollständige alte Einträge: nur ein Tag mit bekanntem Beginn oder Ende
        if (start == null) {
            return listOf(WorkPortion(shift.id, shift.endDate ?: shift.startDate, null, shift.endMinute))
        }
        val end: LocalDateTime = when {
            shift.running -> if (now.isAfter(start)) now else start
            shift.end != null -> shift.end!!.let { if (it.isBefore(start)) start else it }
            else -> return listOf(WorkPortion(shift.id, shift.startDate, shift.startMinute, null))
        }
        if (!end.isAfter(start)) {
            return listOf(WorkPortion(shift.id, start.toLocalDate(), shift.startMinute, shift.startMinute, running = shift.running))
        }
        val portions = mutableListOf<WorkPortion>()
        var date = start.toLocalDate()
        val lastDate = end.toLocalDate()
        while (!date.isAfter(lastDate)) {
            val from = if (date == start.toLocalDate()) shift.startMinute!! else 0
            val to = if (date == lastDate) end.hour * 60 + end.minute else DAY
            // Endet die Schicht genau um 00:00, entsteht am Folgetag kein leerer Teil.
            if (to > from || date == start.toLocalDate()) {
                portions += WorkPortion(
                    shiftId = shift.id,
                    date = date,
                    startMinute = from,
                    endMinute = to,
                    running = shift.running && date == lastDate,
                )
            }
            date = date.plusDays(1)
        }
        // Übergänge markieren (endet die Schicht genau um 24:00, gibt es keinen Folgetag-Teil)
        return portions.mapIndexed { i, p ->
            p.copy(fromPreviousDay = i > 0, toNextDay = i < portions.lastIndex)
        }
    }

    /** Alle Tagesanteile aller Schichten, nach Datum gruppiert. */
    fun byDate(shifts: List<WorkShiftEntity>, now: LocalDateTime): Map<LocalDate, List<WorkPortion>> =
        shifts.flatMap { split(it, now) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sortedBy { it.startMinute ?: it.endMinute ?: 0 } }

    /** Summe der bekannten Arbeitsminuten. */
    fun minutes(portions: List<WorkPortion>?): Int = portions.orEmpty().sumOf { it.minutes ?: 0 }

    /** Text für einen Tag, z. B. "00:00–10:00, 22:00–24:00". */
    fun label(portions: List<WorkPortion>?): String = portions.orEmpty().joinToString(", ") { it.label }

    /**
     * Verteilt die km einer beendeten Schicht anteilig nach Arbeitszeit auf die Tage.
     * Die Summe entspricht immer genau km-Ende minus km-Beginn. Null, wenn km fehlen oder unplausibel sind.
     */
    fun distributeKm(shift: WorkShiftEntity): List<KmShare>? {
        val kmStart = shift.kmStart ?: return null
        val kmEnd = shift.kmEnd ?: return null
        if (kmEnd < kmStart || shift.running || shift.end == null || shift.start == null) return null
        val portions = split(shift, shift.end!!)
        val total = portions.sumOf { it.minutes ?: 0 }
        val distance = (kmEnd - kmStart).toLong()
        if (portions.size <= 1 || total == 0) {
            val date = portions.lastOrNull()?.date ?: shift.startDate
            return listOf(KmShare(date, kmStart, kmEnd, startEstimated = false, endEstimated = false))
        }
        val shares = mutableListOf<KmShare>()
        var cumulative = 0L
        var previous = kmStart
        portions.forEachIndexed { i, p ->
            cumulative += p.minutes ?: 0
            val last = i == portions.lastIndex
            val atEnd = if (last) kmEnd else kmStart + (distance * cumulative.toDouble() / total).roundToLong().toInt()
            shares += KmShare(p.date, previous, atEnd, startEstimated = i > 0, endEstimated = !last)
            previous = atEnd
        }
        return shares
    }
}
