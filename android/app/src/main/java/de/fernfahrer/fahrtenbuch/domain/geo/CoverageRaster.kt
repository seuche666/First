package de.fernfahrer.fahrtenbuch.domain.geo

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Berechnet die aufgedeckte Fläche: Alle Strecken werden mit der Korridorbreite auf ein feines
 * Raster (Zellgröße = ¼ der Korridorbreite) gezeichnet; mehrfach befahrene Straßen zählen nur einmal.
 * Rechnet in normierten Web-Mercator-Koordinaten (0..1), die Zellfläche wird je Breitengrad korrigiert.
 * Speichert nur Kacheln, die tatsächlich berührt werden (32 × 32 Zellen als Bitfeld).
 */
class CoverageRaster(val corridorM: Int) {
    private val cellN = (corridorM / 4.0) / Geo.metersPerUnit(REF_LAT)
    private val tiles = HashMap<Long, LongArray>()

    /** Aufgedeckte Fläche in m². */
    var areaM2: Double = 0.0
        private set

    val areaKm2: Double get() = areaM2 / 1_000_000.0

    /** Fügt eine Linie in normierten Mercator-Koordinaten hinzu (Punkte [from] bis [to] exklusiv). */
    fun addPolyline(xs: DoubleArray, ys: DoubleArray, from: Int = 0, to: Int = xs.size) {
        if (to - from <= 0) return
        if (to - from == 1) {
            addSegment(xs[from], ys[from], xs[from], ys[from])
            return
        }
        for (i in from + 1 until to) addSegment(xs[i - 1], ys[i - 1], xs[i], ys[i])
    }

    fun addSegment(x0: Double, y0: Double, x1: Double, y1: Double) {
        val lat = Geo.latFromMercatorY((y0 + y1) / 2)
        val unit = Geo.metersPerUnit(lat)
        val hw = (corridorM / 2.0) / unit
        val cellArea = (cellN * unit) * (cellN * unit)
        val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        // Lange Strecken (z. B. nach einem Funkloch) in Stücke teilen, damit nur Zellen nahe der Linie geprüft werden.
        val pieces = max(1, (len / (2 * hw)).toInt() + 1)
        var ax = x0
        var ay = y0
        for (p in 1..pieces) {
            val t = p.toDouble() / pieces
            val bx = x0 + (x1 - x0) * t
            val by = y0 + (y1 - y0) * t
            stamp(ax, ay, bx, by, hw, cellArea)
            ax = bx
            ay = by
        }
    }

    private fun stamp(ax: Double, ay: Double, bx: Double, by: Double, hw: Double, cellArea: Double) {
        val hw2 = hw * hw
        val cx0 = floor((min(ax, bx) - hw) / cellN).toInt()
        val cx1 = floor((max(ax, bx) + hw) / cellN).toInt()
        val cy0 = floor((min(ay, by) - hw) / cellN).toInt()
        val cy1 = floor((max(ay, by) + hw) / cellN).toInt()
        for (cy in cy0..cy1) {
            val py = (cy + 0.5) * cellN
            for (cx in cx0..cx1) {
                val px = (cx + 0.5) * cellN
                if (Simplify.segmentDistance2(px, py, ax, ay, bx, by) <= hw2 && set(cx, cy)) areaM2 += cellArea
            }
        }
    }

    /** Setzt eine Zelle; true, wenn sie vorher noch nicht gesetzt war. */
    private fun set(cx: Int, cy: Int): Boolean {
        val key = ((cx shr 5).toLong() shl 32) or ((cy shr 5).toLong() and 0xFFFFFFFFL)
        val tile = tiles.getOrPut(key) { LongArray(16) }
        val bit = ((cy and 31) shl 5) or (cx and 31)
        val word = bit ushr 6
        val mask = 1L shl (bit and 63)
        if (tile[word] and mask != 0L) return false
        tile[word] = tile[word] or mask
        return true
    }

    private companion object {
        const val REF_LAT = 50.0
    }
}
