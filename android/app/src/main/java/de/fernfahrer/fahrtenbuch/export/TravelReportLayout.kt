package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.ReportRow
import de.fernfahrer.fahrtenbuch.domain.TravelReport

/**
 * Aufteilung der Reisekostenabrechnung auf A4-Seiten (ohne Android, daher testbar). Die PDF
 * ([TravelReportPdf]) zeichnet genau diese Seiten: Tabellenkopf auf jeder Seite, Zusammenfassung
 * und Summen nur am Ende.
 */
object TravelReportLayout {
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN = 36f
    const val CONTENT_W = PAGE_W - 2 * MARGIN

    /** Platz unten für die Seitenzahl. */
    const val FOOTER = 22f

    /** Kopf der ersten Seite (Titel, Name, Zeitraum) und kleiner Kopf der Folgeseiten. */
    const val FIRST_HEADER = 84f
    const val NEXT_HEADER = 26f

    /** Höhe der Tabellen-Kopfzeile (zweizeilig). */
    const val TABLE_HEAD = 26f

    const val LINE = 10.5f
    const val ROW_PAD = 6f

    val HEADERS = listOf(
        "Datum", "Beginn /\nEnde", "Reiseanlass und Reiseweg", "Land", "Verpflegung\n€", "Übernachtung\n€", "Summe\n€",
    )

    /** Spaltenbreiten (Summe = [CONTENT_W]). */
    val COLS = floatArrayOf(60f, 50f, 167f, 70f, 60f, 64f, 52f)

    /** Spalten mit Beträgen (rechtsbündig). */
    val MONEY_COLS = setOf(4, 5, 6)

    /** Zellen einer Zeile, jede als Liste von Textzeilen (lange Texte umgebrochen mit [measure]). */
    fun cells(row: ReportRow, measure: (String) -> Float): List<List<String>> {
        fun wrapCol(text: String, col: Int) = text.split('\n').flatMap { wrap(it, COLS[col] - 6f, measure) }
        return listOf(
            listOf(Format.date(row.date), Format.weekdayLong(row.date)),
            row.times.flatMap { (s, e) -> listOf("${Format.time(s)} –", Format.time(e)) },
            wrapCol(row.text, 2),
            wrapCol(Countries.name(row.countryCode), 3),
            listOf(row.mealCents?.let(Euro::grouped).orEmpty()),
            listOf(row.overnightCents?.let(Euro::grouped).orEmpty()),
            listOf(Euro.grouped(row.sumCents)),
        )
    }

    fun rowHeight(cells: List<List<String>>): Float = cells.maxOf { it.size.coerceAtLeast(1) } * LINE + ROW_PAD

    /** Höhe des Blocks unter der Tabelle (Zusammenfassung, Summen, Unterschrift). */
    fun summaryHeight(report: TravelReport): Float {
        val countryRows = report.countries.size + (if (report.nights > 0) 1 else 0)
        return 14f + 20f + 14f + countryRows * 15f + 14f + 15f + 15f + 30f + 50f + 26f
    }

    /** Eine Seite: welche Tabellenzeilen darauf stehen und ob der Abschluss darauf steht. */
    data class Page(val rows: IntRange, val summary: Boolean)

    /** Verteilt Zeilen mit den Höhen [heights] und den Abschluss ([summaryHeight]) auf Seiten. */
    fun paginate(heights: List<Float>, summaryHeight: Float): List<Page> {
        val bottom = PAGE_H - MARGIN - FOOTER
        val pages = mutableListOf<Page>()
        var start = 0
        var y = MARGIN + FIRST_HEADER + TABLE_HEAD
        heights.forEachIndexed { i, h ->
            if (y + h > bottom && i > start) {
                pages += Page(start until i, summary = false)
                start = i
                y = MARGIN + NEXT_HEADER + TABLE_HEAD
            }
            y += h
        }
        return if (y + summaryHeight <= bottom) {
            pages + Page(start until heights.size, summary = true)
        } else {
            pages + Page(start until heights.size, summary = false) + Page(IntRange.EMPTY, summary = true)
        }
    }

    /** Bricht [s] an Wortgrenzen um, sodass jede Zeile höchstens [maxWidth] breit ist. */
    fun wrap(s: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (measure(s) <= maxWidth) return listOf(s)
        val lines = mutableListOf<String>()
        var current = ""
        for (word in s.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
