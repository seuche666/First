package de.fernfahrer.fahrtenbuch.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

enum class PeriodType(val label: String) {
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr"),
    ALL("Gesamt"),
}

/** Ein Auswertungszeitraum, festgelegt durch Art und einen Tag darin. */
data class Period(val type: PeriodType, val anchor: LocalDate) {

    /** Erster Tag (inklusive); null bei "Gesamt". */
    val start: LocalDate?
        get() = when (type) {
            PeriodType.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            PeriodType.MONTH -> anchor.withDayOfMonth(1)
            PeriodType.YEAR -> anchor.withDayOfYear(1)
            PeriodType.ALL -> null
        }

    /** Letzter Tag (inklusive); null bei "Gesamt". */
    val end: LocalDate?
        get() = when (type) {
            PeriodType.WEEK -> start!!.plusDays(6)
            PeriodType.MONTH -> anchor.with(TemporalAdjusters.lastDayOfMonth())
            PeriodType.YEAR -> anchor.with(TemporalAdjusters.lastDayOfYear())
            PeriodType.ALL -> null
        }

    fun contains(date: LocalDate): Boolean {
        val s = start ?: return true
        return !date.isBefore(s) && !date.isAfter(end!!)
    }

    fun previous(): Period = shift(-1)
    fun next(): Period = shift(1)

    private fun shift(n: Long): Period = when (type) {
        PeriodType.WEEK -> copy(anchor = anchor.plusWeeks(n))
        PeriodType.MONTH -> copy(anchor = anchor.plusMonths(n))
        PeriodType.YEAR -> copy(anchor = anchor.plusYears(n))
        PeriodType.ALL -> this
    }

    val label: String
        get() = when (type) {
            PeriodType.WEEK -> {
                val s = start!!
                val e = end!!
                "KW ${s.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${Format.shortDate(s)} – ${Format.date(e)}"
            }
            PeriodType.MONTH -> Format.monthYear(anchor)
            PeriodType.YEAR -> anchor.year.toString()
            PeriodType.ALL -> "Alle Einträge"
        }
}
