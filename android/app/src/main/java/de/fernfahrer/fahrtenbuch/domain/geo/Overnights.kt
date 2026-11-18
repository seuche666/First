package de.fernfahrer.fahrtenbuch.domain.geo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Berechnung der Übernachtungsplätze bis Datenbank-Version 5 (Position je Übernachtungstag, zu
 * Stammplätzen zusammengefasst). Seit Version 6 werden Plätze nur noch beim „Arbeit beenden“ mit
 * Haken fest gespeichert; diese Berechnung dient nur noch der einmaligen Übernahme der alten Plätze.
 */
object Overnights {
    /** Eine Übernachtung mit Position (Grad × 100 000). */
    data class Spot(
        val date: LocalDate,
        val latE5: Int,
        val lonE5: Int,
        val countryCode: String?,
        /** Nur zum Vergleichen, welcher GPS-Punkt eines Tages der letzte ist. */
        val timeKey: Long = 0,
    ) {
        val lat: Double get() = latE5 / TrackPoint.E5
        val lon: Double get() = lonE5 / TrackPoint.E5
    }

    /** Mehrere Übernachtungen am selben Platz. */
    data class Cluster(val lat: Double, val lon: Double, val spots: List<Spot>) {
        val count: Int get() = spots.size
    }

    const val CLUSTER_RADIUS_M = 200.0

    /**
     * Ordnet jeder Übernachtung eine Position zu: zuerst der gespeicherte Standort beim Arbeitsende
     * an diesem Tag (der letzte), sonst – für Tage vor dieser Funktion – der letzte GPS-Punkt des Tages.
     */
    fun spots(
        overnightDates: Collection<LocalDate>,
        stopsByDate: Map<LocalDate, Spot>,
        lastTrackPointByDate: Map<LocalDate, Spot>,
    ): List<Spot> = overnightDates.distinct().sorted().mapNotNull { d -> stopsByDate[d] ?: lastTrackPointByDate[d] }

    /** Fasst Übernachtungen im Umkreis von [radiusM] zusammen (Mittelpunkt = Durchschnitt). */
    fun cluster(spots: List<Spot>, radiusM: Double = CLUSTER_RADIUS_M): List<Cluster> {
        val groups = ArrayList<MutableList<Spot>>()
        val centers = ArrayList<DoubleArray>()
        for (s in spots.sortedBy { it.date }) {
            var best = -1
            var bestD = Double.MAX_VALUE
            for (i in centers.indices) {
                val d = Geo.distanceM(centers[i][0], centers[i][1], s.lat, s.lon)
                if (d <= radiusM && d < bestD) {
                    best = i
                    bestD = d
                }
            }
            if (best < 0) {
                groups += mutableListOf(s)
                centers += doubleArrayOf(s.lat, s.lon)
            } else {
                val g = groups[best]
                g += s
                centers[best][0] = g.sumOf { it.lat } / g.size
                centers[best][1] = g.sumOf { it.lon } / g.size
            }
        }
        return groups.indices.map { Cluster(centers[it][0], centers[it][1], groups[it].toList()) }
    }

    /** Letzter Punkt je Kalendertag einer dekodierten Strecke (Zeitzone [zone]). */
    fun lastPointPerDay(d: TrackCodec.Decoded, zone: ZoneId): Map<LocalDate, Spot> {
        val out = HashMap<LocalDate, Spot>()
        for (i in 0 until d.size) {
            val date = Instant.ofEpochSecond(d.timeSec[i]).atZone(zone).toLocalDate()
            val prev = out[date]
            if (prev == null || prev.timeKey <= d.timeSec[i]) out[date] = Spot(date, d.latE5[i], d.lonE5[i], null, d.timeSec[i])
        }
        return out
    }

    /** Fasst die letzten Punkte je Tag mehrerer Strecken zusammen (jeweils der späteste). */
    fun mergeDayEnds(parts: Iterable<Map<LocalDate, Spot>>): Map<LocalDate, Spot> {
        val merged = HashMap<LocalDate, Spot>()
        for (m in parts) for ((date, spot) in m) {
            val prev = merged[date]
            if (prev == null || spot.timeKey > prev.timeKey) merged[date] = spot
        }
        return merged
    }
}
