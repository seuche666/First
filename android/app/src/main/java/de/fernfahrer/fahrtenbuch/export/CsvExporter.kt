package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Summary
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import java.time.LocalDate

/** CSV für Excel (deutsch): Semikolon als Trenner, UTF-8 mit BOM. */
object CsvExporter {

    fun build(
        year: Int,
        days: List<ClassifiedDay>,
        summary: Summary,
        driverName: String,
        rules: CountingRules = CountingRules(),
        work: Map<LocalDate, List<WorkPortion>> = emptyMap(),
    ): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Fahrtenbuch – Jahresübersicht $year")
        if (driverName.isNotBlank()) rows += listOf("Fahrer", driverName)
        rows += listOf("Erstellt am", Format.date(java.time.LocalDate.now()))
        rows += listOf("Zählregel eintägige Fahrten", rules.singleDayRuleText)
        rows += emptyList<String>()

        rows += listOf(
            "Datum", "Wochentag", "Land", "Ländercode", "Beginn", "Ende", "Abwesenheit (Std.)",
            "Einordnung", "Begründung", "Manuell geändert", "Firmenregel", "Tour", "Übernachtung im LKW",
            "Arbeitszeit", "Arbeitszeit (Std.)",
            "km morgens", "km abends", "km gefahren", "km anteilig geschätzt", "Notiz",
        )
        days.sortedBy { it.day.date }.forEach { c ->
            val d = c.day
            val portions = work[d.date]
            rows += listOf(
                Format.date(d.date),
                Format.weekdayShort(d.date),
                Countries.name(c.country),
                c.country,
                Format.time(d.startMinute),
                Format.time(d.endMinute),
                hours(d.absenceMinutes),
                c.type.label,
                c.fullReason,
                if (c.isManual) "ja" else "",
                if (c.byCompanyRule) "ja" else "",
                if (d.tourId != null) "ja" else "",
                if (c.overnight) "ja" else "",
                WorkSplitter.label(portions),
                if (portions.isNullOrEmpty()) "" else hours(WorkSplitter.minutes(portions)),
                d.kmStart?.toString().orEmpty(),
                d.kmEnd?.toString().orEmpty(),
                d.distanceKm?.toString().orEmpty(),
                if (d.kmInterpolated) "ja" else "",
                d.note,
            )
        }

        rows += emptyList<String>()
        rows += listOf("Zusammenfassung")
        rows += listOf("Land", "Volle Tage", "Halbe Tage", "Summe")
        (listOf(summary.germany) + summary.abroad).forEach {
            rows += listOf(Countries.name(it.countryCode), "${it.full}", "${it.half}", "${it.total}")
        }
        rows += listOf("Gesamt", "${summary.full}", "${summary.half}", "${summary.daysAway}")
        rows += listOf("Davon halbe Tage nur nach Firmenregel (eintägig, 8 Std. oder weniger)", "${summary.companyRuleDays}")
        rows += listOf("Übernachtungen im LKW", "${summary.overnights}")
        rows += listOf("Gefahrene Kilometer", "${summary.km}")
        rows += listOf("Nicht gezählte Tage", "${summary.notCounted}")
        rows += listOf("Arbeitszeit gesamt (Std.)", hours(summary.workMinutes))

        return "﻿" + rows.joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
    }

    private fun hours(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)

    private fun cell(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
