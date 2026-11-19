package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.DayEntity

/**
 * Mehrere Länder an einem Tag.
 *
 * Gespeichert wird die Reihenfolge der Länder eines Tages (z. B. „DE,NL,DE“ bei Hin- und Rückfahrt),
 * aufeinanderfolgende gleiche Länder zusammengefasst. Gezählt wird der Tag für ein einziges Land:
 * **Ausland hat Vorrang** – war man an dem Tag im Ausland, zählt das zuletzt besuchte ausländische
 * Land, auch wenn man abends wieder in Deutschland ist. So zählt kein Tag doppelt.
 *
 * **Zentrale Regel:** Verlauf, Auswertung, Export (und später Euro-Berechnung und Spesenbericht) nutzen
 * ausschließlich [countingCountry] bzw. [ClassifiedDay.country]. Die Rohdaten (alle Länder) bleiben
 * gespeichert und sind nur im Eintrag-Bildschirm sichtbar. Von Hand gewählte Länder (`countryManual`)
 * werden von GPS nicht mehr verändert und sind damit maßgeblich.
 */
object DayCountries {
    /**
     * Das eine Land, für das [day] zählt: nur Deutschland → Deutschland; auch (kurz) im Ausland →
     * das Ausland; mehrere ausländische Staaten → der zuletzt besuchte. Gilt für alle Einträge,
     * auch ältere, weil es immer aus den gespeicherten Ländern neu berechnet wird.
     */
    fun countingCountry(day: DayEntity): String = counting(day.countryRoute)

    /** Gespeicherte Reihenfolge lesen; leer → nur [fallback]. */
    fun parse(route: String, fallback: String): List<String> {
        val list = route.split(',').map { it.trim().uppercase() }.filter { it.length == 2 }
        return list.ifEmpty { listOf(fallback) }
    }

    fun join(route: List<String>): String = collapse(route).joinToString(",")

    /** Aufeinanderfolgende gleiche Länder zusammenfassen. */
    fun collapse(route: List<String>): List<String> =
        route.fold(ArrayList()) { acc, c -> if (acc.lastOrNull() != c) acc += c; acc }

    /** Land, für das der Tag zählt: letztes ausländisches Land, sonst das Heimatland. */
    fun counting(route: List<String>): String =
        route.lastOrNull { it != Stats.HOME_COUNTRY } ?: route.lastOrNull() ?: Stats.HOME_COUNTRY

    /** Jedes Land nur einmal, in der Reihenfolge des ersten Besuchs (für die Anzeige). */
    fun distinct(route: List<String>): List<String> = route.distinct()

    /** Ein neues Land erreicht: am Ende anhängen (sofern es nicht schon das aktuelle ist). */
    fun withVisit(route: List<String>, code: String): List<String> =
        if (route.lastOrNull() == code) route else route + code
}
