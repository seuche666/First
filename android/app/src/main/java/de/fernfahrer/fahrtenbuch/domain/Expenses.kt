package de.fernfahrer.fahrtenbuch.domain

import java.time.LocalDate

/** Euro-Beträge: gespeichert in Cent, angezeigt und eingegeben mit Komma (z. B. „14,00“). */
object Euro {
    private val pattern = Regex("""^(\d{1,6})(?:[.,](\d{0,2}))?$""")

    /** Eingabe in Cent umwandeln; leer → null. Ungültige Eingabe → [IllegalArgumentException]. */
    fun parse(text: String): Long? {
        val t = text.trim().removeSuffix("€").trim()
        if (t.isEmpty()) return null
        val m = pattern.matchEntire(t) ?: throw IllegalArgumentException("Ungültiger Betrag: $text")
        val euros = m.groupValues[1].toLong()
        val centsText = m.groupValues[2].padEnd(2, '0')
        return euros * 100 + centsText.toLong()
    }

    /** Ist die Eingabe leer oder ein gültiger Betrag? */
    fun isValid(text: String): Boolean = runCatching { parse(text) }.isSuccess

    /** Cent → „14,00“ (ohne Tausenderpunkt, ohne €-Zeichen). */
    fun format(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)

    /** Cent → „14,00 €“, null → „–“. */
    fun label(cents: Long?): String = cents?.let { format(it) + " €" } ?: "–"
}

/** Beträge eines Landes in der Spesentabelle eines Jahres (in Cent; null = noch nicht eingetragen). */
data class ExpenseRate(
    val countryCode: String,
    /** Betrag für einen vollen Tag (24 Std.). */
    val fullDayCents: Long?,
    /** Betrag für An-/Abreisetag bzw. halben Tag. */
    val partialDayCents: Long?,
)

/** Ein Land mit erfassten Tagen im Jahr, für das noch ein benötigter Betrag fehlt. */
data class MissingRate(val countryCode: String, val fullDays: Int, val halfDays: Int) {
    val days: Int get() = fullDays + halfDays
}

/**
 * Regeln der Spesentabelle (eine Tabelle pro Jahr). Berechnet keine Euro-Summen – das kommt später
 * und nutzt dann ebenfalls [ClassifiedDay.country] (Ausland hat Vorrang).
 */
object ExpenseTables {
    /** Aus welchem Jahr eine neue Tabelle für [year] kopiert wird: das jüngste frühere Jahr, sonst keins. */
    fun sourceYear(year: Int, existing: Collection<Int>): Int? = existing.filter { it < year }.maxOrNull()

    /**
     * Auswählbare Jahre: alle Jahre mit Tabelle oder erfassten Tagen, das aktuelle und das nächste Jahr
     * (damit man im Dezember schon die neuen Beträge eintragen kann).
     */
    fun selectableYears(existing: Collection<Int>, dayYears: Collection<Int>, today: LocalDate): List<Int> =
        (existing + dayYears + listOf(today.year, today.year + 1)).toSortedSet().toList()

    /**
     * Länder, für die im Jahr [year] schon Tage gezählt werden, aber in [rates] noch kein Betrag steht
     * (fehlt das Land ganz, oder fehlt der Betrag für volle bzw. halbe Tage, die es gibt).
     */
    fun missingRates(days: List<ClassifiedDay>, year: Int, rates: List<ExpenseRate>): List<MissingRate> {
        val byCode = rates.associateBy { it.countryCode }
        return days.asSequence()
            .filter { it.day.date.year == year && it.type != DayType.NONE }
            .groupBy { it.country }
            .map { (code, list) ->
                MissingRate(code, list.count { it.type == DayType.FULL }, list.count { it.type == DayType.HALF })
            }
            .filter { m ->
                val r = byCode[m.countryCode]
                r == null || (m.fullDays > 0 && r.fullDayCents == null) || (m.halfDays > 0 && r.partialDayCents == null)
            }
            .sortedWith(compareByDescending<MissingRate> { it.days }.thenBy { Countries.name(it.countryCode) })
    }

    /** Anzahl der Übernachtungen im LKW im Jahr (für den Hinweis auf eine fehlende Übernachtungspauschale). */
    fun overnights(days: List<ClassifiedDay>, year: Int): Int =
        days.count { it.day.date.year == year && it.overnight }
}
