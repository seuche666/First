package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Summary

/** CSV für Excel (deutsch): Semikolon als Trenner, UTF-8 mit BOM. */
object CsvExporter {

    fun build(
        year: Int,
        days: List<ClassifiedDay>,
        summary: Summary,
        driverName: String,
        rules: CountingRules = CountingRules(),
    ): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Fahrtenbuch – Jahresübersicht $year")
        if (driverName.isNotBlank()) rows += listOf("Fahrer", driverName)
        rows += listOf("Erstellt am", Format.date(java.time.LocalDate.now()))
        rows += listOf("Zählregel eintägige Fahrten", rules.singleDayRuleText)
        rows += emptyList<String>()

        rows += listOf(
            "Datum", "Wochentag", "Land", "Ländercode", "Beginn", "Ende", "Abwesenheit (Std.)",
            "Einordnung", "Begründung", "Manuell geändert", "Firmenregel", "Tour",
            "Arbeitsbeginn", "Arbeitsende", "Arbeitszeit (Std.)",
            "km morgens", "km abends", "km gefahren", "Notiz",
        )
        days.sortedBy { it.day.date }.forEach { c ->
            val d = c.day
            rows += listOf(
                Format.date(d.date),
                Format.weekdayShort(d.date),
                Countries.name(d.countryCode),
                d.countryCode,
                Format.time(d.startMinute),
                Format.time(d.endMinute),
                hours(d.absenceMinutes),
                c.type.label,
                c.fullReason,
                if (c.isManual) "ja" else "",
                if (c.byCompanyRule) "ja" else "",
                if (d.tourId != null) "ja" else "",
                d.workStartMinute?.let(Format::time).orEmpty(),
                d.workEndMinute?.let(Format::time).orEmpty(),
                d.workMinutes?.let(::hours).orEmpty(),
                d.kmStart?.toString().orEmpty(),
                d.kmEnd?.toString().orEmpty(),
                d.distanceKm?.toString().orEmpty(),
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
