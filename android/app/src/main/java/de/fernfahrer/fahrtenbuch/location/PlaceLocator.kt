package de.fernfahrer.fahrtenbuch.location

import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Offline: nächster Ort zu einer Position (z. B. Übernachtungsplatz). Daten aus
 * assets/places.bin (GeoNames, Orte ab 1000 Einwohnern in Europa; siehe tools/build_places.py).
 */
class PlaceLocator private constructor(
    private val lat: IntArray,
    private val lon: IntArray,
    private val names: Array<String>,
) {
    /** Ein gefundener Ort mit Entfernung. */
    data class Place(val name: String, val distanceKm: Double) {
        /** Für die Anzeige: „Würzburg“ oder „12 km von Würzburg“. */
        val label: String
            get() = if (distanceKm < 2.0) name else "${distanceKm.roundToInt()} km von $name"
    }

    val size: Int get() = lat.size

    /** Nächster Ort im Umkreis von [maxKm] oder null. */
    fun nearest(latitude: Double, longitude: Double, maxKm: Double = 40.0): Place? {
        if (lat.isEmpty()) return null
        val y = Math.round(latitude * E5).toInt()
        val dLat = (maxKm / 111.2 * E5).toInt()
        val kx = cos(Math.toRadians(latitude))
        var i = lowerBound(y - dLat)
        var best = -1
        var bestD2 = Double.MAX_VALUE
        while (i < lat.size && lat[i] <= y + dLat) {
            val dy = (lat[i] - y).toDouble()
            val dx = (lon[i] - longitude * E5) * kx
            if (abs(dx) <= dLat) {
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) {
                    bestD2 = d2
                    best = i
                }
            }
            i++
        }
        if (best < 0) return null
        val km = Geo.distanceM(latitude, longitude, lat[best] / E5, lon[best] / E5) / 1000.0
        return if (km <= maxKm) Place(names[best], km) else null
    }

    private fun lowerBound(v: Int): Int {
        var lo = 0
        var hi = lat.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lat[mid] < v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        const val ASSET_NAME = "places.bin"
        private const val E5 = 100_000.0

        fun load(input: InputStream): PlaceLocator {
            val d = DataInputStream(BufferedInputStream(input, 64 * 1024))
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "FBPL") { "Keine Ortsdatei" }
            require(d.readUnsignedByte() == 1) { "Unbekannte Version der Ortsdatei" }
            val n = Integer.reverseBytes(d.readInt())
            val lat = IntArray(n)
            val lon = IntArray(n)
            val names = arrayOfNulls<String>(n)
            val cc = ByteArray(2)
            val buf = ByteArray(255)
            for (i in 0 until n) {
                lat[i] = Integer.reverseBytes(d.readInt())
                lon[i] = Integer.reverseBytes(d.readInt())
                d.readFully(cc)
                val len = d.readUnsignedByte()
                d.readFully(buf, 0, len)
                names[i] = String(buf, 0, len, Charsets.UTF_8)
            }
            @Suppress("UNCHECKED_CAST")
            return PlaceLocator(lat, lon, names as Array<String>)
        }
    }
}
