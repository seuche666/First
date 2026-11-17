package de.fernfahrer.fahrtenbuch.domain.geo

import java.io.ByteArrayOutputStream

/**
 * Kompakte Speicherung einer Punktfolge: erster Punkt absolut, danach nur die Differenzen
 * (ZigZag-Varint). Bei einem Punkt alle 30–50 m braucht ein Punkt meist nur 3–4 Byte.
 *
 * Format (Version 1): Version (1 Byte), Anzahl (Varint), dann je Punkt
 * Δ Breite, Δ Länge (Grad × 100 000, ZigZag-Varint), Δ Zeit in Sekunden (ZigZag-Varint).
 * Der erste Punkt hat als „Vorgänger“ (0, 0, 0).
 */
object TrackCodec {
    private const val VERSION = 1

    fun encode(points: List<TrackPoint>): ByteArray {
        val out = ByteArrayOutputStream(points.size * 4 + 8)
        out.write(VERSION)
        writeVarint(out, points.size.toLong())
        var lat = 0
        var lon = 0
        var time = 0L
        for (p in points) {
            writeVarint(out, zigZag((p.latE5 - lat).toLong()))
            writeVarint(out, zigZag((p.lonE5 - lon).toLong()))
            writeVarint(out, zigZag(p.timeSec - time))
            lat = p.latE5
            lon = p.lonE5
            time = p.timeSec
        }
        return out.toByteArray()
    }

    /** Gelesene Punkte als Felder (spart bei vielen Punkten Speicher). */
    class Decoded(val latE5: IntArray, val lonE5: IntArray, val timeSec: LongArray) {
        val size: Int get() = latE5.size
        fun point(i: Int) = TrackPoint(latE5[i], lonE5[i], timeSec[i])
        fun toList(): List<TrackPoint> = List(size, ::point)
    }

    fun decode(bytes: ByteArray): Decoded {
        val r = Reader(bytes)
        val version = r.byte()
        require(version == VERSION) { "Unbekanntes Streckenformat $version" }
        val n = r.varint()
        require(n in 0..10_000_000) { "Ungültige Punktanzahl" }
        val count = n.toInt()
        val lats = IntArray(count)
        val lons = IntArray(count)
        val times = LongArray(count)
        var lat = 0L
        var lon = 0L
        var time = 0L
        for (i in 0 until count) {
            lat += unZigZag(r.varint())
            lon += unZigZag(r.varint())
            time += unZigZag(r.varint())
            require(lat in -9_000_000..9_000_000 && lon in -18_000_000..18_000_000) { "Ungültige Koordinate" }
            lats[i] = lat.toInt()
            lons[i] = lon.toInt()
            times[i] = time
        }
        return Decoded(lats, lons, times)
    }

    private fun zigZag(v: Long): Long = (v shl 1) xor (v shr 63)
    private fun unZigZag(v: Long): Long = (v ushr 1) xor -(v and 1)

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private class Reader(private val b: ByteArray) {
        private var pos = 0
        fun byte(): Int {
            require(pos < b.size) { "Streckendaten zu kurz" }
            return b[pos++].toInt() and 0xFF
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val v = byte()
                result = result or ((v and 0x7F).toLong() shl shift)
                if (v and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "Ungültige Zahl in Streckendaten" }
            }
        }
    }
}
