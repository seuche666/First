package de.fernfahrer.fahrtenbuch.domain

import java.time.LocalDate

/**
 * Meilensteine: welche erreicht sind (mit dem Datum, an dem das laut den Daten passiert ist) und
 * wie weit die offenen sind. Reine Logik ohne Android.
 */
object Milestones {

    enum class Group(val title: String) {
        KM("Kilometer laut GPS"),
        COUNTRIES("Länder"),
        NIGHTS("Übernachtungen im LKW"),
        TOURS("Touren"),
    }

    /** Ein Meilenstein mit Stand. [achievedOn] = null: noch offen. */
    data class Status(
        val id: String,
        val group: Group,
        val title: String,
        /** Text für die Meldung beim Erreichen. */
        val message: String,
        val current: Long,
        val target: Long,
        val achievedOn: LocalDate?,
    ) {
        val achieved: Boolean get() = achievedOn != null
        val progress: Float get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
    }

    /** Daten, aus denen die Meilensteine berechnet werden. */
    data class Input(
        /** GPS-Strecke: (Datum, Meter) je Streckenstück. */
        val gpsDistances: List<Pair<LocalDate, Double>>,
        /** Land → erstes Datum, an dem es befahren wurde (GPS-Strecke oder erfasster Tag). */
        val countriesFirstSeen: Map<String, LocalDate>,
        /** Daten aller Übernachtungen im LKW. */
        val overnightDates: List<LocalDate>,
        /** Touren: (erster Tag, letzter Tag). */
        val tours: List<Pair<LocalDate, LocalDate>>,
    )

    val KM_STEPS = listOf(1_000L, 5_000L, 10_000L, 25_000L, 50_000L, 100_000L)
    val NIGHT_STEPS = listOf(10L, 50L, 100L)
    val COUNTRY_STEPS = listOf(5L, 10L, 20L)
    const val LONG_TOUR_DAYS = 5L

    fun evaluate(input: Input, countryLabel: (String) -> String = { it }): List<Status> = buildList {
        // Kilometer laut GPS: Datum, an dem die Summe die Schwelle überschreitet
        val dist = input.gpsDistances.sortedBy { it.first }
        val totalKm = dist.sumOf { it.second } / 1000.0
        for (step in KM_STEPS) {
            var sum = 0.0
            var date: LocalDate? = null
            for ((d, m) in dist) {
                sum += m
                if (sum / 1000.0 >= step) {
                    date = d
                    break
                }
            }
            add(
                Status(
                    "km_$step", Group.KM, "${Format.number(step.toInt())} km",
                    "${Format.number(step.toInt())} km laut GPS gefahren!", totalKm.toLong(), step, date,
                ),
            )
        }

        // Länder: jedes neue Land einzeln, dazu Stufen
        val countries = input.countriesFirstSeen.entries.sortedWith(compareBy({ it.value }, { it.key }))
        for ((code, date) in countries) {
            val label = countryLabel(code)
            add(Status("land_$code", Group.COUNTRIES, label, "Neues Land befahren: $label", 1, 1, date))
        }
        for (step in COUNTRY_STEPS) {
            val date = countries.getOrNull(step.toInt() - 1)?.value
            add(
                Status(
                    "laender_$step", Group.COUNTRIES, "$step Länder", "$step Länder befahren!",
                    countries.size.toLong(), step, date,
                ),
            )
        }

        // Übernachtungen
        val nights = input.overnightDates.distinct().sorted()
        for (step in NIGHT_STEPS) {
            add(
                Status(
                    "naechte_$step", Group.NIGHTS, "$step Übernachtungen", "$step Übernachtungen im LKW!",
                    nights.size.toLong(), step, nights.getOrNull(step.toInt() - 1),
                ),
            )
        }

        // Erste Tour mit mindestens 5 Tagen
        val longest = input.tours.maxOfOrNull { (s, e) -> java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1 } ?: 0
        val firstLong = input.tours
            .filter { (s, e) -> java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1 >= LONG_TOUR_DAYS }
            .minOfOrNull { (s, _) -> s.plusDays(LONG_TOUR_DAYS - 1) }
        add(
            Status(
                "tour_5", Group.TOURS, "Erste Tour mit 5 Tagen", "Erste Tour mit 5 Tagen oder mehr!",
                longest, LONG_TOUR_DAYS, firstLong,
            ),
        )
    }
}
