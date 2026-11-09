package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Summary

/** CSV für Excel (deutsch): Semikolon als Trenner, UTF-8 mit BOM. */
object CsvExporter {

    fun build(year: Int, days: List<ClassifiedDay>, summary: Summary, driverName: String): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Fahrtenbuch – Jahresübersicht $year")
        if (driverName.isNotBlank()) rows += listOf("Fahrer", driverName)
        rows += listOf("Erstellt am", Format.date(java.time.LocalDate.now()))
        rows += emptyList<String>()

        rows += listOf(
            "Datum", "Wochentag", "Land", "Ländercode", "Beginn", "Ende", "Abwesenheit (Std.)",
            "Einordnung", "Manuell geändert", "Tour", "km morgens", "km abends", "km gefahren", "Notiz",
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
                "%d:%02d".format(d.absenceMinutes / 60, d.absenceMinutes % 60),
                c.type.label,
                if (c.isManual) "ja" else "",
                if (d.tourId != null) "ja" else "",
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
        rows += listOf("Gefahrene Kilometer", "${summary.km}")
        rows += listOf("Nicht gezählte Tage", "${summary.notCounted}")

        return "﻿" + rows.joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
    }

    private fun cell(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
