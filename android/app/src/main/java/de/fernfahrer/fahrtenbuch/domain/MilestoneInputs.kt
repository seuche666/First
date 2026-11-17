package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.TrackChunkMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stellt die Eingabe für [Milestones] aus erfassten Tagen und GPS-Strecken zusammen. */
object MilestoneInputs {
    fun from(days: List<ClassifiedDay>, meta: List<TrackChunkMeta>, zone: ZoneId = ZoneId.systemDefault()): Milestones.Input {
        fun dateOf(sec: Long) = Instant.ofEpochSecond(sec).atZone(zone).toLocalDate()
        val countries = HashMap<String, LocalDate>()
        fun seen(code: String, d: LocalDate) {
            if (code.isBlank()) return
            val prev = countries[code]
            if (prev == null || d < prev) countries[code] = d
        }
        meta.forEach { m -> m.countries.split(',').forEach { seen(it, dateOf(m.startTime)) } }
        days.forEach { seen(it.day.countryCode, it.day.date) }
        return Milestones.Input(
            gpsDistances = meta.map { dateOf(it.startTime) to it.lengthMeters },
            countriesFirstSeen = countries,
            overnightDates = days.filter { it.overnight }.map { it.day.date },
            tours = days.filter { it.day.tourId != null }.groupBy { it.day.tourId }.values
                .map { list -> list.minOf { it.day.date } to list.maxOf { it.day.date } },
        )
    }
}
