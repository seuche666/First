package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.TourEntity
import java.time.LocalDate
import java.time.YearMonth

/** Orte eines Tages aus GPS und Übernachtungsplätzen (für den automatischen Reiseanlass). */
data class DayPlaces(
    /** Nächster Ort zum ersten GPS-Punkt des Tages. */
    val first: String? = null,
    /** Nächster Ort zum letzten GPS-Punkt des Tages. */
    val last: String? = null,
    /** Übernachtungsplatz des Tages: eigener Name, sonst nächster Ort. */
    val overnight: String? = null,
)

/** Eine Zeile der Reisekostenabrechnung (ein Tag). */
data class ReportRow(
    val date: LocalDate,
    /** Arbeitszeiten (Beginn, Ende in Minuten ab Mitternacht); ersatzweise die Abwesenheit des Tages. */
    val times: List<Pair<Int, Int>>,
    /** Automatischer Text „Reiseanlass und Reiseweg“ (Zeilen durch „\n“ getrennt). */
    val autoText: String,
    /** Von Hand geänderter Text; null = automatisch. */
    val manualText: String?,
    val countryCode: String,
    val type: DayType,
    /** Verpflegung; null = kein Anspruch oder Betrag fehlt (siehe [mealMissing]). */
    val mealCents: Long?,
    val mealMissing: Boolean,
    /** Übernachtungspauschale; null = keine Nacht im LKW oder Betrag fehlt (siehe [overnightMissing]). */
    val overnightCents: Long?,
    val hasNight: Boolean,
    val overnightMissing: Boolean,
) {
    val text: String get() = manualText ?: autoText
    val isManual: Boolean get() = manualText != null
    val sumCents: Long get() = (mealCents ?: 0) + (overnightCents ?: 0)
}

/** Zusammenfassung je Land in der Abrechnung. */
data class ReportCountry(
    val countryCode: String,
    val fullDays: Int,
    val halfDays: Int,
    val fullCents: Long,
    val halfCents: Long,
) {
    val totalCents: Long get() = fullCents + halfCents
}

/** Ein fehlender Betrag in der Spesentabelle. */
data class MissingAmount(val year: Int, val countryCode: String?, val type: DayType?) {
    /** z. B. „Niederlande: voller Tag (2026)“ oder „Übernachtungspauschale (2026)“. */
    val label: String
        get() = if (countryCode == null) {
            "Übernachtungspauschale ($year)"
        } else {
            val kind = if (type == DayType.FULL) "voller Tag" else "halber Tag / An- und Abreise"
            "${Countries.name(countryCode)}: $kind ($year)"
        }
}

/** Monatliche Reisekostenabrechnung. */
data class TravelReport(
    val month: YearMonth,
    val name: String,
    val rows: List<ReportRow>,
    /** Tag, der weggelassen wurde, weil die Arbeitszeit dort gerade läuft. */
    val skippedRunning: LocalDate?,
    val countries: List<ReportCountry>,
    val missing: List<MissingAmount>,
) {
    val from: LocalDate? get() = rows.firstOrNull()?.date
    val to: LocalDate? get() = rows.lastOrNull()?.date
    val nights: Int get() = rows.count { it.hasNight }
    val mealCents: Long get() = rows.sumOf { it.mealCents ?: 0 }
    val overnightCents: Long get() = rows.sumOf { it.overnightCents ?: 0 }
    val totalCents: Long get() = mealCents + overnightCents
    val complete: Boolean get() = missing.isEmpty()

    /** Übernachtungspauschale pro Nacht, falls in allen Zeilen gleich (für „3 Nächte × 8,00 €“). */
    val overnightRate: Long?
        get() = rows.mapNotNull { if (it.hasNight) it.overnightCents else null }.distinct().singleOrNull()

    /** Dateiname, z. B. „Reisekosten_2026-11_Max_Muster.pdf“. */
    val fileName: String get() = TravelReports.fileName(month, name)
}

/**
 * Baut die monatliche Reisekostenabrechnung für die Firma.
 *
 * - Zählregel: **Firmenregel** – die Tage müssen mit `CountingRules(shortSingleDayAsHalf = true)`
 *   eingeordnet sein (eintägige Fahrten bis 8 Std. = halber Tag). Für die Steuer gilt das nicht.
 * - Jeder Tag gehört in den Monat, in dem er liegt. Der Tag mit laufender Arbeitszeit fehlt.
 * - Land nach der zentralen Länderregel ([ClassifiedDay.country]).
 * - Übernachtungspauschale am Folgetag der Nacht im LKW ([Nights]).
 */
object TravelReports {
    fun build(
        month: YearMonth,
        name: String,
        /** Alle Tage, eingeordnet nach Firmenregel. */
        days: List<ClassifiedDay>,
        tours: List<TourEntity>,
        work: Map<LocalDate, List<WorkPortion>>,
        book: ExpenseBook,
        places: Map<LocalDate, DayPlaces> = emptyMap(),
        /** Datum mit gerade laufender Arbeitszeit (wird weggelassen). */
        runningDate: LocalDate? = null,
        today: LocalDate = LocalDate.now(),
    ): TravelReport {
        val nights = Nights.dates(days)
        val tourEnded = tours.associate { it.id to it.ended }
        val tourDates = days.filter { it.day.tourId != null }.groupBy({ it.day.tourId!! }, { it.day.date })
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        var skipped: LocalDate? = null

        val rows = days.asSequence()
            .filter { !it.day.date.isBefore(first) && !it.day.date.isAfter(last) && !it.day.date.isAfter(today) }
            .filter { it.type != DayType.NONE || it.day.date in nights }
            .filter { c ->
                (c.day.date != runningDate).also { if (!it) skipped = c.day.date }
            }
            .sortedBy { it.day.date }
            .map { c ->
                val date = c.day.date
                val type = c.type
                val meal = book.mealCents(date, c.country, type)
                val hasNight = date in nights
                val night = if (hasNight) book.overnightCents(date) else null
                val tourId = c.day.tourId
                val dates = tourId?.let { tourDates[it] }
                ReportRow(
                    date = date,
                    times = times(c, work[date]),
                    autoText = autoText(
                        c, month,
                        tourFirst = dates?.min(),
                        tourLast = if (tourId != null && tourEnded[tourId] != false) dates?.max() else null,
                        places = places[date] ?: DayPlaces(),
                    ),
                    manualText = c.day.travelText,
                    countryCode = c.country,
                    type = type,
                    mealCents = meal,
                    mealMissing = type != DayType.NONE && meal == null,
                    overnightCents = night,
                    hasNight = hasNight,
                    overnightMissing = hasNight && night == null,
                )
            }
            .toList()

        val countries = rows.filter { it.type != DayType.NONE }
            .groupBy { it.countryCode }
            .map { (code, list) ->
                val full = list.filter { it.type == DayType.FULL }
                val half = list.filter { it.type == DayType.HALF }
                ReportCountry(code, full.size, half.size, full.sumOf { it.mealCents ?: 0 }, half.sumOf { it.mealCents ?: 0 })
            }
            .sortedWith(
                compareBy<ReportCountry> { it.countryCode != Stats.HOME_COUNTRY }.thenBy { Countries.name(it.countryCode) },
            )

        val missing = buildList {
            rows.filter { it.mealMissing }.forEach { add(MissingAmount(it.date.year, it.countryCode, it.type)) }
            rows.filter { it.overnightMissing }.forEach { add(MissingAmount(it.date.year, null, null)) }
        }.distinct()

        return TravelReport(month, name.trim(), rows, skipped, countries, missing)
    }

    /** Arbeitszeiten des Tages; ohne Arbeitszeit ersatzweise die Abwesenheit. */
    internal fun times(c: ClassifiedDay, portions: List<WorkPortion>?): List<Pair<Int, Int>> {
        val work = portions.orEmpty()
            .filter { it.startMinute != null && it.endMinute != null }
            .map { it.startMinute!! to it.endMinute!! }
            .sortedBy { it.first }
        return work.ifEmpty { listOf(c.day.startMinute to c.day.endMinute) }
    }

    /**
     * Automatischer Text „Reiseanlass und Reiseweg“, z. B. „Abfahrt Fulda\nÜbernachtung bei Seßlach“.
     *
     * @param tourLast letzter Tag der Tour, nur wenn die Tour beendet ist.
     */
    fun autoText(
        c: ClassifiedDay,
        month: YearMonth,
        tourFirst: LocalDate?,
        tourLast: LocalDate?,
        places: DayPlaces,
    ): String {
        val date = c.day.date
        val lines = mutableListOf<String>()
        fun at(prefix: String, place: String?) = if (place.isNullOrBlank()) prefix else "$prefix $place"
        fun overnightLine() =
            if (places.overnight != null || places.last != null) "Übernachtung bei ${places.overnight ?: places.last}"
            else "Übernachtung im LKW"

        if (c.day.tourId == null || tourFirst == null) {
            // Eintägige Fahrt
            lines += at("Abfahrt", places.first)
            lines += if (c.overnight) overnightLine() else at("Ankunft", places.last)
            return lines.joinToString("\n")
        }

        if (date == tourFirst) {
            lines += at("Abfahrt", places.first)
        } else if (date == month.atDay(1)) {
            lines += "Fortsetzung der Tour vom ${Format.date(tourFirst)}"
        }
        when {
            date == tourLast -> lines += at("Ankunft", places.last)
            c.overnight -> lines += overnightLine()
            places.last != null -> lines += "Tour, unterwegs bei ${places.last}"
            else -> lines += "Tour, unterwegs"
        }
        if (date == month.atEndOfMonth() && date != tourLast) lines += "Tour läuft weiter"
        return lines.joinToString("\n")
    }

    /** Dateiname „Reisekosten_2026-11_Name.pdf“ (Leerzeichen → „_“, ohne Namen ohne Zusatz). */
    fun fileName(month: YearMonth, name: String): String {
        val clean = name.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace(Regex("""\s+"""), "_")
        return if (clean.isEmpty()) "Reisekosten_$month.pdf" else "Reisekosten_${month}_$clean.pdf"
    }
}
