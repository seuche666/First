package de.fernfahrer.fahrtenbuch.location

import java.io.DataInputStream
import java.io.InputStream
import java.io.BufferedInputStream

/**
 * Offline-Ländererkennung: Prüft anhand der mitgelieferten Ländergrenzen
 * (Natural Earth 1:10m, Datei assets/country_borders.bin), in welchem Land ein Punkt liegt.
 * Es wird kein Internet benötigt. Format siehe tools/build_country_borders.py.
 */
class CountryLocator private constructor(private val countries: List<CountryShape>) {

    private class Ring(
        val minLon: Int, val minLat: Int, val maxLon: Int, val maxLat: Int,
        /** Abwechselnd Länge, Breite in Grad x 100000. */
        val coords: IntArray,
    )

    private class CountryShape(val code: String, val rings: List<Ring>)

    /** ISO-Ländercode für die Position oder null (z. B. auf dem Meer). */
    fun countryAt(latitude: Double, longitude: Double): String? {
        val x = Math.round(longitude * SCALE).toInt()
        val y = Math.round(latitude * SCALE).toInt()
        var best: String? = null
        var bestArea = Double.MAX_VALUE
        for (country in countries) {
            var inside = false
            var area = 0.0
            for (ring in country.rings) {
                if (x < ring.minLon || x > ring.maxLon || y < ring.minLat || y > ring.maxLat) continue
                if (contains(ring.coords, x, y)) {
                    inside = !inside
                    area = (ring.maxLon - ring.minLon).toDouble() * (ring.maxLat - ring.minLat)
                }
            }
            // Überlappen sich Länder (Enklaven wie Vatikan in Italien), gewinnt die kleinere Fläche.
            if (inside && area < bestArea) {
                best = country.code
                bestArea = area
            }
        }
        return best
    }

    /** Strahl-Methode (Gerade-Ungerade-Regel) für einen Ring. */
    private fun contains(c: IntArray, x: Int, y: Int): Boolean {
        var inside = false
        val n = c.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = c[2 * i].toDouble()
            val yi = c[2 * i + 1].toDouble()
            val xj = c[2 * j].toDouble()
            val yj = c[2 * j + 1].toDouble()
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    val countryCount: Int get() = countries.size

    companion object {
        private const val SCALE = 100_000.0
        const val ASSET_NAME = "country_borders.bin"

        fun load(input: InputStream): CountryLocator {
            val data = DataInputStream(BufferedInputStream(input, 64 * 1024))
            val magic = ByteArray(4).also { data.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "FBGB") { "Keine Ländergrenzen-Datei" }
            val version = data.readUnsignedByte()
            require(version == 2) { "Unbekannte Version $version" }
            val count = data.readU16()
            val list = ArrayList<CountryShape>(count)
            repeat(count) {
                val code = String(ByteArray(2).also { data.readFully(it) }, Charsets.US_ASCII)
                val ringCount = data.readU16()
                val rings = ArrayList<Ring>(ringCount)
                repeat(ringCount) {
                    val points = data.readU32()
                    val minLon = data.readI32()
                    val minLat = data.readI32()
                    val maxLon = data.readI32()
                    val maxLat = data.readI32()
                    val coords = IntArray(points * 2)
                    var px = 0L
                    var py = 0L
                    for (i in 0 until points) {
                        px += data.readZigZagVarint()
                        py += data.readZigZagVarint()
                        coords[2 * i] = px.toInt()
                        coords[2 * i + 1] = py.toInt()
                    }
                    rings += Ring(minLon, minLat, maxLon, maxLat, coords)
                }
                list += CountryShape(code, rings)
            }
            return CountryLocator(list)
        }

        // Little-Endian-Hilfen
        private fun DataInputStream.readU16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)
        private fun DataInputStream.readI32(): Int =
            readUnsignedByte() or (readUnsignedByte() shl 8) or (readUnsignedByte() shl 16) or (readUnsignedByte() shl 24)
        private fun DataInputStream.readU32(): Int = readI32()

        private fun DataInputStream.readZigZagVarint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = readUnsignedByte()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return (result ushr 1) xor -(result and 1)
        }
    }
}
