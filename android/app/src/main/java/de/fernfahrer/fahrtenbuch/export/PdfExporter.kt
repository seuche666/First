package de.fernfahrer.fahrtenbuch.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Summary
import java.io.File
import java.time.LocalDate

/** Erstellt die Jahresübersicht als PDF (A4). */
object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private val accent = Color.rgb(30, 79, 216)
    private val muted = Color.rgb(100, 105, 120)
    private val zebra = Color.rgb(243, 245, 250)

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
            textAlign = align
        }

    fun write(file: File, year: Int, days: List<ClassifiedDay>, summary: Summary, driverName: String) {
        val doc = PdfDocument()
        val w = Writer(doc)

        // Kopf
        w.text("Fahrtenbuch – Jahresübersicht $year", paint(20f, bold = true, color = accent), 26f)
        val sub = buildString {
            if (driverName.isNotBlank()) append("Fahrer: $driverName  ·  ")
            append("Erstellt am ${Format.date(LocalDate.now())}")
        }
        w.text(sub, paint(10f, color = muted), 22f)

        // Zusammenfassung
        w.text("Zusammenfassung", paint(13f, bold = true), 20f)
        val cols = floatArrayOf(235f, 90f, 90f, 100f)
        val headPaint = paint(10f, bold = true, color = muted)
        w.row(listOf("Land", "Volle Tage", "Halbe Tage", "Tage gesamt"), cols, headPaint, rightFrom = 1)
        w.line()
        val body = paint(10.5f)
        (listOf(summary.germany) + summary.abroad).forEachIndexed { i, c ->
            w.row(
                listOf(Countries.name(c.countryCode), "${c.full}", "${c.half}", "${c.total}"),
                cols, body, rightFrom = 1, background = if (i % 2 == 1) zebra else null,
            )
        }
        w.line()
        w.row(
            listOf("Gesamt", "${summary.full}", "${summary.half}", "${summary.daysAway}"),
            cols, paint(10.5f, bold = true), rightFrom = 1,
        )
        w.space(10f)
        w.text("Tage unterwegs gesamt: ${summary.daysAway}  (davon ${summary.full} volle und ${summary.half} halbe Tage)", body, 16f)
        w.text("Davon im Ausland: ${summary.abroadDays} Tage in ${summary.abroad.size} Ländern", body, 16f)
        w.text("Gefahrene Kilometer: ${Format.km(summary.km)}", body, 16f)
        if (summary.notCounted > 0) {
            w.text("Nicht gezählt (eintägig bis 8 Std. oder manuell): ${summary.notCounted} Tage", body, 16f)
        }
        w.space(4f)
        w.text("Hinweis: Die App zählt nur Tage. Steuerbeträge werden nicht berechnet.", paint(9f, color = muted), 14f)
        w.space(14f)

        // Einzelne Tage
        w.text("Einzelne Tage", paint(13f, bold = true), 20f)
        val dayCols = floatArrayOf(72f, 92f, 36f, 36f, 52f, 62f, 45f, CONTENT_W - 395f)
        val dayHead = listOf("Datum", "Land", "Beginn", "Ende", "Dauer", "Einordnung", "km", "Notiz")
        val small = paint(8.5f)
        val smallHead = paint(8.5f, bold = true, color = muted)
        w.onNewPage = {
            w.row(dayHead, dayCols, smallHead, rightAt = setOf(6))
            w.line()
        }
        w.onNewPage!!.invoke()
        var anyManual = false
        days.sortedBy { it.day.date }.forEachIndexed { i, c ->
            val d = c.day
            if (c.isManual) anyManual = true
            w.row(
                listOf(
                    Format.weekdayShort(d.date) + ", " + Format.date(d.date),
                    Countries.name(d.countryCode),
                    Format.time(d.startMinute),
                    Format.time(d.endMinute),
                    "%d:%02d h".format(d.absenceMinutes / 60, d.absenceMinutes % 60),
                    c.type.label + if (c.isManual) " *" else "",
                    d.distanceKm?.let { Format.number(it) } ?: "",
                    d.note.replace('\n', ' '),
                ),
                dayCols, small, rightAt = setOf(6), background = if (i % 2 == 1) zebra else null, rowHeight = 13f,
            )
        }
        if (days.isEmpty()) w.text("Keine Einträge in diesem Jahr.", small, 14f)
        if (anyManual) {
            w.space(6f)
            w.text("* Einordnung manuell geändert", paint(8.5f, color = muted), 12f)
        }

        w.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    /** Schreibt Zeilen von oben nach unten und beginnt bei Bedarf eine neue Seite. */
    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page = newPage()
        private var y = MARGIN
        var onNewPage: (() -> Unit)? = null
        private val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = muted
            textAlign = Paint.Align.RIGHT
        }

        private fun newPage(): PdfDocument.Page {
            pageNo++
            return doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        }

        private fun ensure(height: Float) {
            if (y + height <= PAGE_H - MARGIN - 14f) return
            finishPage()
            page = newPage()
            y = MARGIN
            onNewPage?.invoke()
        }

        private fun finishPage() {
            page.canvas.drawText("Seite $pageNo", PAGE_W - MARGIN, PAGE_H - MARGIN / 2, footer)
            doc.finishPage(page)
        }

        fun finish() = finishPage()

        fun space(h: Float) {
            y += h
        }

        fun text(s: String, p: Paint, lineHeight: Float) {
            ensure(lineHeight)
            y += lineHeight
            page.canvas.drawText(fit(s, p, CONTENT_W), MARGIN, y - lineHeight * 0.28f, p)
        }

        fun line() {
            ensure(4f)
            y += 2f
            val lp = Paint().apply { color = Color.rgb(200, 204, 214); strokeWidth = 0.7f }
            page.canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, lp)
            y += 2f
        }

        fun row(
            cells: List<String>,
            widths: FloatArray,
            p: Paint,
            rightFrom: Int = Int.MAX_VALUE,
            rightAt: Set<Int> = emptySet(),
            background: Int? = null,
            rowHeight: Float = 16f,
        ) {
            ensure(rowHeight)
            if (background != null) {
                val bg = Paint().apply { color = background }
                page.canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowHeight, bg)
            }
            val baseline = y + rowHeight * 0.72f
            var x = MARGIN
            cells.forEachIndexed { i, cell ->
                val w = widths[i]
                val right = i >= rightFrom || i in rightAt
                val text = fit(cell, p, w - 6f)
                if (right) {
                    val rp = Paint(p).apply { textAlign = Paint.Align.RIGHT }
                    page.canvas.drawText(text, x + w - 6f, baseline, rp)
                } else {
                    page.canvas.drawText(text, x + 2f, baseline, p)
                }
                x += w
            }
            y += rowHeight
        }

        private fun fit(s: String, p: Paint, maxWidth: Float): String {
            if (p.measureText(s) <= maxWidth) return s
            val n = p.breakText(s, true, maxWidth - p.measureText("…"), null)
            return s.take(n).trimEnd() + "…"
        }
    }
}
