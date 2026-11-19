package de.fernfahrer.fahrtenbuch.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.COLS
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.CONTENT_W
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.LINE
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.MARGIN
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.PAGE_H
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.PAGE_W
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout.TABLE_HEAD
import java.io.File
import java.io.OutputStream

/**
 * Monatliche Reisekostenabrechnung als PDF (A4 Hochformat), eigenes schlichtes Design.
 * Seitenaufteilung aus [TravelReportLayout]. Nur aufrufen, wenn [TravelReport.complete].
 */
object TravelReportPdf {
    private val accent = Color.rgb(30, 79, 216)
    private val ink = Color.rgb(25, 28, 36)
    private val muted = Color.rgb(100, 105, 120)
    private val grid = Color.rgb(200, 204, 214)
    private val zebra = Color.rgb(243, 245, 250)
    private val headBg = Color.rgb(228, 233, 246)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
            textAlign = align
        }

    fun write(file: File, report: TravelReport) = file.outputStream().use { write(it, report) }

    private val cellPaint get() = paint(8.5f)

    /** Seitenaufteilung der Abrechnung (gleich für PDF und Vorschaubild). */
    fun pages(report: TravelReport): List<TravelReportLayout.Page> {
        val measure: (String) -> Float = cellPaint::measureText
        val heights = report.rows.map { TravelReportLayout.rowHeight(TravelReportLayout.cells(it, measure)) }
        return TravelReportLayout.paginate(heights, TravelReportLayout.summaryHeight(report))
    }

    fun write(out: OutputStream, report: TravelReport) {
        val pages = pages(report)

        val doc = PdfDocument()
        for (pageIndex in pages.indices) {
            val pdfPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
            drawPage(pdfPage.canvas, report, pageIndex)
            doc.finishPage(pdfPage)
        }
        doc.writeTo(out)
        doc.close()
    }

    /** Zeichnet Seite [pageIndex] (A4 in Punkt) auf [c] – für die PDF und für Vorschaubilder. */
    fun drawPage(c: Canvas, report: TravelReport, pageIndex: Int) {
        val cp = cellPaint
        val measure: (String) -> Float = cp::measureText
        val cells = report.rows.map { TravelReportLayout.cells(it, measure) }
        val pages = pages(report)
        val page = pages[pageIndex]
        c.drawColor(Color.WHITE)
        var y = MARGIN
        y = if (pageIndex == 0) firstHeader(c, report, y) else nextHeader(c, report, y)
        if (!page.rows.isEmpty()) {
            y = tableHead(c, y)
            for (i in page.rows) y = tableRow(c, y, cells[i], zebraRow = i % 2 == 1, paint = cp)
        }
        if (page.summary) summary(c, report, y)
        c.drawText(
            "Seite ${pageIndex + 1} von ${pages.size}",
            PAGE_W - MARGIN, PAGE_H - MARGIN / 2, paint(8f, color = muted, align = Paint.Align.RIGHT),
        )
    }

    private fun zeitraum(report: TravelReport): String {
        val from = report.from
        val to = report.to
        return if (from != null && to != null) "vom ${Format.date(from)} bis ${Format.date(to)}" else ""
    }

    private fun firstHeader(c: Canvas, r: TravelReport, top: Float): Float {
        var y = top
        c.drawRect(MARGIN, y, MARGIN + 6f, y + 26f, Paint().apply { color = accent })
        c.drawText("Reisekostenabrechnung", MARGIN + 14f, y + 21f, paint(20f, bold = true))
        c.drawText(
            Format.monthYear(r.month.atDay(1)), PAGE_W - MARGIN, y + 21f,
            paint(11f, color = muted, align = Paint.Align.RIGHT),
        )
        y += 40f
        val label = paint(9f, color = muted)
        val value = paint(11f, bold = true)
        c.drawText("Name", MARGIN, y, label)
        c.drawText(r.name.ifBlank { "" }, MARGIN + 70f, y, value)
        y += 16f
        c.drawText("Zeitraum", MARGIN, y, label)
        c.drawText(zeitraum(r), MARGIN + 70f, y, value)
        return top + TravelReportLayout.FIRST_HEADER
    }

    private fun nextHeader(c: Canvas, r: TravelReport, top: Float): Float {
        val text = buildString {
            append("Reisekostenabrechnung")
            if (r.name.isNotBlank()) append(" · ${r.name}")
            append(" · ${zeitraum(r)}")
        }
        c.drawText(text, MARGIN, top + 12f, paint(9f, color = muted))
        return top + TravelReportLayout.NEXT_HEADER
    }

    private fun tableHead(c: Canvas, top: Float): Float {
        c.drawRect(MARGIN, top, MARGIN + CONTENT_W, top + TABLE_HEAD, Paint().apply { color = headBg })
        val p = paint(8f, bold = true)
        var x = MARGIN
        TravelReportLayout.HEADERS.forEachIndexed { i, h ->
            val right = i in TravelReportLayout.MONEY_COLS
            val lp = if (right) Paint(p).apply { textAlign = Paint.Align.RIGHT } else p
            h.split('\n').forEachIndexed { line, text ->
                val tx = if (right) x + COLS[i] - 4f else x + 3f
                c.drawText(text, tx, top + 10.5f + line * 10f, lp)
            }
            x += COLS[i]
        }
        return top + TABLE_HEAD
    }

    private fun tableRow(c: Canvas, top: Float, cells: List<List<String>>, zebraRow: Boolean, paint: Paint): Float {
        val h = TravelReportLayout.rowHeight(cells)
        if (zebraRow) c.drawRect(MARGIN, top, MARGIN + CONTENT_W, top + h, Paint().apply { color = zebra })
        val right = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        var x = MARGIN
        cells.forEachIndexed { i, lines ->
            val isMoney = i in TravelReportLayout.MONEY_COLS
            lines.forEachIndexed { n, text ->
                val baseline = top + 3f + LINE * (n + 1) - 2.5f
                if (isMoney) {
                    c.drawText(text, x + COLS[i] - 4f, baseline, if (i == 6) Paint(right).apply { typeface = Typeface.DEFAULT_BOLD } else right)
                } else {
                    c.drawText(text, x + 3f, baseline, if (i == 0 && n == 1) Paint(paint).apply { color = muted } else paint)
                }
            }
            x += COLS[i]
        }
        c.drawLine(MARGIN, top + h, MARGIN + CONTENT_W, top + h, Paint().apply { color = grid; strokeWidth = 0.5f })
        return top + h
    }

    private fun summary(c: Canvas, r: TravelReport, top: Float) {
        var y = top + 14f
        c.drawText("Zusammenfassung", MARGIN, y + 13f, paint(12f, bold = true))
        y += 20f
        // Land | Volle Tage | Betrag | Halbe Tage | Betrag | Summe
        val cols = floatArrayOf(150f, 75f, 70f, 85f, 70f, CONTENT_W - 450f)
        val head = paint(8f, bold = true, color = muted)
        val body = paint(9f)
        fun row(values: List<String>, p: Paint, yy: Float) {
            var x = MARGIN
            values.forEachIndexed { i, v ->
                if (i == 0) {
                    c.drawText(v, x + 3f, yy, p)
                } else {
                    c.drawText(v, x + cols[i] - 4f, yy, Paint(p).apply { textAlign = Paint.Align.RIGHT })
                }
                x += cols[i]
            }
        }
        row(listOf("Land", "Volle Tage", "Betrag €", "Halbe Tage", "Betrag €", "Summe €"), head, y + 10f)
        y += 14f
        fun times(n: Int, total: Long) = if (n == 0) "–" else "$n × ${Euro.grouped(total / n)}"
        for (country in r.countries) {
            row(
                listOf(
                    Countries.name(country.countryCode),
                    times(country.fullDays, country.fullCents), if (country.fullDays > 0) Euro.grouped(country.fullCents) else "",
                    times(country.halfDays, country.halfCents), if (country.halfDays > 0) Euro.grouped(country.halfCents) else "",
                    Euro.grouped(country.totalCents),
                ),
                body, y + 11f,
            )
            y += 15f
        }
        if (r.nights > 0) {
            val nightText = r.overnightRate?.let { "${r.nights} × ${Euro.grouped(it)}" } ?: "${r.nights}"
            row(
                listOf(if (r.nights == 1) "Übernachtung im LKW (1 Nacht)" else "Übernachtungen im LKW (${r.nights} Nächte)", nightText, Euro.grouped(r.overnightCents), "", "", Euro.grouped(r.overnightCents)),
                body, y + 11f,
            )
            y += 15f
        }
        y += 6f
        c.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, Paint().apply { color = grid; strokeWidth = 0.7f })
        y += 8f

        // Summen
        val labelX = MARGIN + CONTENT_W - 250f
        val sumLabel = paint(10f)
        val sumValue = paint(10f, align = Paint.Align.RIGHT)
        c.drawText("Summe Verpflegung", labelX, y + 11f, sumLabel)
        c.drawText(Euro.money(r.mealCents), MARGIN + CONTENT_W - 8f, y + 11f, sumValue)
        y += 15f
        c.drawText("Summe Übernachtung", labelX, y + 11f, sumLabel)
        c.drawText(Euro.money(r.overnightCents), MARGIN + CONTENT_W - 8f, y + 11f, sumValue)
        y += 19f
        val box = RectF(labelX - 10f, y, MARGIN + CONTENT_W, y + 26f)
        c.drawRoundRect(box, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        c.drawText("GESAMTSUMME", labelX, y + 17.5f, paint(12f, bold = true, color = Color.WHITE))
        c.drawText(
            Euro.money(r.totalCents), MARGIN + CONTENT_W - 8f, y + 17.5f,
            paint(13f, bold = true, color = Color.WHITE, align = Paint.Align.RIGHT),
        )
        y += 30f

        // Ort, Datum, Unterschrift
        y += 44f
        val line = Paint().apply { color = ink; strokeWidth = 0.7f }
        val half = (CONTENT_W - 30f) / 2
        c.drawLine(MARGIN, y, MARGIN + half, y, line)
        c.drawLine(MARGIN + half + 30f, y, MARGIN + CONTENT_W, y, line)
        val small = paint(8.5f, color = muted)
        c.drawText("Ort, Datum", MARGIN, y + 11f, small)
        c.drawText("Unterschrift", MARGIN + half + 30f, y + 11f, small)
    }
}
