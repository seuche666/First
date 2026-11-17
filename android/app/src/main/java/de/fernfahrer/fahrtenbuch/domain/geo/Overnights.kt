package de.fernfahrer.fahrtenbuch.domain.geo

import java.time.LocalDate

/** Übernachtungsplätze für die Karte: Position je Übernachtung, zu Stammplätzen zusammengefasst. */
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
}
