package de.fernfahrer.fahrtenbuch.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

data class CountryCount(val countryCode: String, val full: Int, val half: Int) {
    val total: Int get() = full + half
}

data class Summary(
    val full: Int,
    val half: Int,
    /** Erfasste Tage, die nicht zählen (eintägig bis 8 Std. oder manuell "zählt nicht"). */
    val notCounted: Int,
    val km: Int,
    val germany: CountryCount,
    /** Auslandstage je Land, meiste Tage zuerst. Nur Länder mit mindestens einem Tag. */
    val abroad: List<CountryCount>,
) {
    /** Tage unterwegs = volle + halbe Tage. */
    val daysAway: Int get() = full + half
    val abroadDays: Int get() = abroad.sumOf { it.total }

    /** Alle Länder inklusive Deutschland, für das Diagramm. */
    val allCountries: List<CountryCount>
        get() = (if (germany.total > 0) listOf(germany) else emptyList()) + abroad
}

data class ChartBar(val label: String, val value: Int)

object Stats {
    const val HOME_COUNTRY = "DE"

    fun summarize(days: List<ClassifiedDay>, period: Period? = null): Summary {
        val selected = if (period == null) days else days.filter { period.contains(it.day.date) }
        val byCountry = selected.filter { it.type != DayType.NONE }
            .groupBy { it.day.countryCode }
            .map { (code, list) ->
                CountryCount(code, list.count { it.type == DayType.FULL }, list.count { it.type == DayType.HALF })
            }
        val germany = byCountry.firstOrNull { it.countryCode == HOME_COUNTRY } ?: CountryCount(HOME_COUNTRY, 0, 0)
        val abroad = byCountry.filter { it.countryCode != HOME_COUNTRY }
            .sortedWith(compareByDescending<CountryCount> { it.total }.thenBy { Countries.name(it.countryCode) })
        return Summary(
            full = selected.count { it.type == DayType.FULL },
            half = selected.count { it.type == DayType.HALF },
            notCounted = selected.count { it.type == DayType.NONE },
            km = selected.sumOf { it.day.distanceKm ?: 0 },
            germany = germany,
            abroad = abroad,
        )
    }

    /** Titel des km-Diagramms passend zum Zeitraum. */
    fun kmChartTitle(type: PeriodType): String = when (type) {
        PeriodType.WEEK -> "Kilometer pro Tag"
        PeriodType.MONTH -> "Kilometer pro Woche"
        PeriodType.YEAR -> "Kilometer pro Monat"
        PeriodType.ALL -> "Kilometer pro Jahr"
    }

    /** km-Balken: Woche → Tage, Monat → Kalenderwochen, Jahr → Monate, Gesamt → Jahre. */
    fun kmBars(days: List<ClassifiedDay>, period: Period): List<ChartBar> {
        val inPeriod = days.filter { period.contains(it.day.date) }
        fun kmOn(filter: (LocalDate) -> Boolean) = inPeriod.filter { filter(it.day.date) }.sumOf { it.day.distanceKm ?: 0 }

        return when (period.type) {
            PeriodType.WEEK -> {
                val start = period.start!!
                (0L until 7L).map { i ->
                    val d = start.plusDays(i)
                    ChartBar(Format.weekdayShort(d), kmOn { it == d })
                }
            }
            PeriodType.MONTH -> {
                val bars = mutableListOf<ChartBar>()
                var weekStart = period.start!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                while (!weekStart.isAfter(period.end!!)) {
                    val ws = weekStart
                    val we = ws.plusDays(6)
                    bars += ChartBar(
                        "KW ${ws.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                        kmOn { !it.isBefore(ws) && !it.isAfter(we) },
                    )
                    weekStart = weekStart.plusWeeks(1)
                }
                bars
            }
            PeriodType.YEAR -> (1..12).map { m ->
                ChartBar(Format.monthShort(m), kmOn { it.monthValue == m })
            }
            PeriodType.ALL -> {
                val years = inPeriod.map { it.day.date.year }
                if (years.isEmpty()) {
                    listOf(ChartBar(LocalDate.now().year.toString(), 0))
                } else {
                    (years.min()..years.max()).map { y -> ChartBar(y.toString(), kmOn { it.year == y }) }
                }
            }
        }
    }
}
