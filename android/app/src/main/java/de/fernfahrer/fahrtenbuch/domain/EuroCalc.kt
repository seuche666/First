package de.fernfahrer.fahrtenbuch.domain

import java.time.LocalDate

/** Spesentabelle eines Jahres in der Rechenlogik (Beträge in Cent, null = nicht eingetragen). */
data class ExpenseTable(
    val year: Int,
    /** Übernachtungspauschale LKW pro Nacht. */
    val overnightCents: Long?,
    val rates: Map<String, ExpenseRate>,
)

/**
 * Alle Spesentabellen. Jeder Tag wird mit der Tabelle **seines** Jahres berechnet, das Land nach der
 * zentralen Länderregel ([ClassifiedDay.country], Ausland hat Vorrang).
 */
class ExpenseBook(tables: Collection<ExpenseTable>) {
    private val byYear = tables.associateBy { it.year }

    /** Verpflegungssatz für [type] in [country] am [date]; null = Betrag fehlt (oder Tag zählt nicht). */
    fun mealCents(date: LocalDate, country: String, type: DayType): Long? {
        val rate = byYear[date.year]?.rates?.get(country) ?: return null
        return when (type) {
            DayType.FULL -> rate.fullDayCents
            DayType.HALF -> rate.partialDayCents
            DayType.NONE -> null
        }
    }

    /** Übernachtungspauschale für eine Nacht, die [date] zugeordnet ist; null = fehlt. */
    fun overnightCents(date: LocalDate): Long? = byYear[date.year]?.overnightCents
}

/**
 * Übernachtungen im LKW für die Euro-Berechnung. „Übernachtung im LKW“ ist am Tag vor der Nacht
 * gesetzt; die Pauschale gehört aber zum **Folgetag** (Nacht vom 31.10. auf den 1.11. → November).
 * Eine Nacht zählt nur, wenn der Folgetag erfasst ist – so zählt bei laufender Tour die kommende
 * Nacht erst, wenn sie vorbei ist.
 */
object Nights {
    /** Tage, denen eine Übernachtungspauschale zugeordnet ist (jeweils der Folgetag einer Nacht im LKW). */
    fun dates(days: List<ClassifiedDay>): Set<LocalDate> {
        val recorded = days.map { it.day.date }.toHashSet()
        return days.asSequence()
            .filter { it.overnight }
            .map { it.day.date.plusDays(1) }
            .filter { it in recorded }
            .toHashSet()
    }
}

/** Euro-Beträge eines Landes in einem Zeitraum (Summen in Cent; null = mindestens ein Betrag fehlt). */
data class CountryEuro(
    val countryCode: String,
    val fullDays: Int,
    val halfDays: Int,
    val fullCents: Long?,
    val halfCents: Long?,
) {
    val totalCents: Long? get() = if (fullCents != null && halfCents != null) fullCents + halfCents else null
    val missing: Boolean get() = totalCents == null
}

/** Euro-Auswertung eines Zeitraums (Verpflegung je Land, Übernachtung, Gesamt). */
data class EuroSummary(
    val countries: List<CountryEuro>,
    val nights: Int,
    /** Summe Übernachtungspauschale; null = Pauschale fehlt. */
    val overnightCents: Long?,
) {
    val mealCents: Long?
        get() = if (countries.any { it.missing }) null else countries.sumOf { it.totalCents ?: 0 }

    /** Gesamtsumme; null = unvollständig, weil Beträge fehlen. */
    val totalCents: Long?
        get() {
            val meal = mealCents ?: return null
            val night = overnightCents ?: return null
            return meal + night
        }

    val complete: Boolean get() = totalCents != null
    val missingCountries: List<String> get() = countries.filter { it.missing }.map { it.countryCode }
    val overnightMissing: Boolean get() = overnightCents == null
    val isEmpty: Boolean get() = countries.isEmpty() && nights == 0
}

/** Euro-Beträge eines einzelnen Tages (für Exporte). */
data class DayEuro(
    /** Verpflegung; null = kein Anspruch oder fehlt ([mealMissing]). */
    val mealCents: Long?,
    val mealMissing: Boolean,
    /** Übernachtungspauschale; null = keine Nacht oder fehlt ([nightMissing]). */
    val nightCents: Long?,
    val hasNight: Boolean,
    val nightMissing: Boolean,
) {
    val missing: Boolean get() = mealMissing || nightMissing

    /** Summe des Tages; null = ein Betrag fehlt. */
    val totalCents: Long? get() = if (missing) null else (mealCents ?: 0) + (nightCents ?: 0)
}

object EuroCalc {
    /** Beträge je Tag (Land nach Länderregel, Nacht beim Folgetag, Tabelle des jeweiligen Jahres). */
    fun perDay(days: List<ClassifiedDay>, book: ExpenseBook): Map<LocalDate, DayEuro> {
        val nights = Nights.dates(days)
        return days.associate { c ->
            val date = c.day.date
            val meal = book.mealCents(date, c.country, c.type)
            val hasNight = date in nights
            val night = if (hasNight) book.overnightCents(date) else null
            date to DayEuro(meal, c.type != DayType.NONE && meal == null, night, hasNight, hasNight && night == null)
        }
    }

    /**
     * Berechnet Verpflegung je Land und Übernachtungspauschale für [period] (null = gesamt).
     * Fehlende Beträge werden nie als 0 € gerechnet, sondern als fehlend gemeldet.
     *
     * @param days alle Tage (für die Zuordnung der Nächte auch außerhalb des Zeitraums)
     */
    fun summarize(days: List<ClassifiedDay>, book: ExpenseBook, period: Period? = null): EuroSummary {
        val selected = days.filter { period == null || period.contains(it.day.date) }
        val countries = selected.filter { it.type != DayType.NONE }
            .groupBy { it.country }
            .map { (code, list) ->
                val full = list.filter { it.type == DayType.FULL }
                val half = list.filter { it.type == DayType.HALF }
                CountryEuro(code, full.size, half.size, sumOrNull(full, book), sumOrNull(half, book))
            }
            .sortedWith(
                compareBy<CountryEuro> { it.countryCode != Stats.HOME_COUNTRY }
                    .thenByDescending { it.fullDays + it.halfDays }
                    .thenBy { Countries.name(it.countryCode) },
            )
        val nightDates = Nights.dates(days).filter { period == null || period.contains(it) }
        val overnight = nightDates.fold(0L as Long?) { acc, d ->
            val c = book.overnightCents(d)
            if (acc == null || c == null) null else acc + c
        }
        return EuroSummary(countries, nightDates.size, overnight)
    }

    private fun sumOrNull(list: List<ClassifiedDay>, book: ExpenseBook): Long? =
        list.fold(0L as Long?) { acc, c ->
            val cents = book.mealCents(c.day.date, c.country, c.type)
            if (acc == null || cents == null) null else acc + cents
        }

    /** Jahre, in denen im Zeitraum ein benötigter Betrag fehlt (aufsteigend). */
    fun missingYears(days: List<ClassifiedDay>, book: ExpenseBook, period: Period?): List<Int> {
        val years = sortedSetOf<Int>()
        for (c in days) {
            if (period != null && !period.contains(c.day.date)) continue
            if (c.type != DayType.NONE && book.mealCents(c.day.date, c.country, c.type) == null) years += c.day.date.year
        }
        for (d in Nights.dates(days)) {
            if (period != null && !period.contains(d)) continue
            if (book.overnightCents(d) == null) years += d.year
        }
        return years.toList()
    }
}
