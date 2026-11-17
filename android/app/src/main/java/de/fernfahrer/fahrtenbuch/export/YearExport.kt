package de.fernfahrer.fahrtenbuch.export

import android.content.Context
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

enum class ExportFormat { PDF, CSV }

/** Erstellt die Jahresübersicht als PDF oder CSV im Export-Ordner der App (alte Exporte werden gelöscht). */
object YearExport {
    suspend fun createFile(
        context: Context,
        year: Int,
        format: ExportFormat,
        allDays: List<ClassifiedDay>,
        shifts: List<WorkShiftEntity>,
        driver: String,
        rules: CountingRules,
    ): File {
        val period = Period(PeriodType.YEAR, LocalDate.of(year, 1, 1))
        val inYear = allDays.filter { period.contains(it.day.date) }
        val workMap = WorkSplitter.byDate(shifts, LocalDateTime.now())
        val summary = Stats.summarize(inYear, period, workMap)
        return withContext(Dispatchers.IO) {
            val dir = Share.exportDir(context)
            dir.listFiles()?.forEach(File::delete)
            when (format) {
                ExportFormat.PDF -> File(dir, "Fahrtenbuch_$year.pdf").also {
                    PdfExporter.write(it, year, inYear, summary, driver, rules, workMap)
                }
                ExportFormat.CSV -> File(dir, "Fahrtenbuch_$year.csv").also {
                    it.writeText(CsvExporter.build(year, inYear, summary, driver, rules, workMap), Charsets.UTF_8)
                }
            }
        }
    }

    fun mimeType(format: ExportFormat) = if (format == ExportFormat.PDF) "application/pdf" else "text/csv"
}
