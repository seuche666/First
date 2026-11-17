package de.fernfahrer.fahrtenbuch.domain.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Ein aufgezeichneter GPS-Punkt (Grad × 100 000, Zeit in Sekunden seit 1970, UTC). */
data class TrackPoint(val latE5: Int, val lonE5: Int, val timeSec: Long) {
    val lat: Double get() = latE5 / E5
    val lon: Double get() = lonE5 / E5

    companion object {
        const val E5 = 100_000.0
        fun of(lat: Double, lon: Double, timeSec: Long) =
            TrackPoint(Math.round(lat * E5).toInt(), Math.round(lon * E5).toInt(), timeSec)
    }
}

/** Kleine Geo-Hilfen ohne Android: Entfernungen und Web-Mercator (wie die Kartenkacheln). */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Erdumfang am Äquator in Metern (Web-Mercator). */
    const val EARTH_CIRCUMFERENCE_M = 40_075_016.686
    private const val MAX_LAT = 85.05112878

    /** Entfernung in Metern (Haversine). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceM(a: TrackPoint, b: TrackPoint) = distanceM(a.lat, a.lon, b.lat, b.lon)

    /** Länge einer Punktfolge in Metern. */
    fun lengthM(points: List<TrackPoint>): Double {
        var sum = 0.0
        for (i in 1 until points.size) sum += distanceM(points[i - 1], points[i])
        return sum
    }

    /** Web-Mercator, normiert auf 0..1 (x: West → Ost, y: Nord → Süd). */
    fun mercatorX(lon: Double): Double = (lon + 180.0) / 360.0

    fun mercatorY(lat: Double): Double {
        val phi = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0
    }

    fun latFromMercatorY(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))

    fun lonFromMercatorX(x: Double): Double = x * 360.0 - 180.0

    /** Meter pro normierter Mercator-Einheit bei dieser Breite. */
    fun metersPerUnit(lat: Double): Double = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(lat))

    private fun sinh(x: Double) = (exp(x) - exp(-x)) / 2.0
}
