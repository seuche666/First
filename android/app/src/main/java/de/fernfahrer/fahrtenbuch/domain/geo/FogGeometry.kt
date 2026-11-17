package de.fernfahrer.fahrtenbuch.domain.geo

import kotlin.math.floor

/**
 * Für die Karte vorbereitete Strecken: jede Strecke in mehreren Detailstufen (beim Rauszoomen
 * stark vereinfacht), und je Detailstufe räumlich in Gruppen zusammengefasst. So zeichnet die
 * Karte immer nur wenige, passend vereinfachte Linien – auch bei vielen Monaten Daten.
 *
 * Koordinaten: normiertes Web-Mercator (0..1); Linien sind relativ zu einem Ursprung in
 * Einheiten von 1/[WORLD_UNITS] gespeichert (genau genug für Float).
 */
class FogGeometry(val chunks: List<ChunkGeom>) {

    /** Ein Streckenstück in allen Detailstufen. */
    class ChunkGeom(
        val id: Long,
        val trackId: Long,
        val seq: Int,
        val pointCount: Int,
        val originX: Double,
        val originY: Double,
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
        /** Je Detailstufe: x, y abwechselnd, relativ zu (originX, originY) in 1/[WORLD_UNITS]. */
        val levels: Array<FloatArray>,
    ) {
        fun x(level: Int, i: Int): Double = originX + levels[level][2 * i] / WORLD_UNITS
        fun y(level: Int, i: Int): Double = originY + levels[level][2 * i + 1] / WORLD_UNITS
        fun size(level: Int): Int = levels[level].size / 2
    }

    /** Streckenstücke einer Detailstufe in einem Rasterfeld. */
    class Group(
        val level: Int,
        val key: Long,
        val originX: Double,
        val originY: Double,
        val chunks: List<ChunkGeom>,
    ) {
        val minX = chunks.minOf { it.minX }
        val minY = chunks.minOf { it.minY }
        val maxX = chunks.maxOf { it.maxX }
        val maxY = chunks.maxOf { it.maxY }

        /** Ändert sich, sobald ein Stück dazukommt oder wächst (für Zwischenspeicher beim Zeichnen). */
        val signature: Long = chunks.fold(17L) { acc, c -> acc * 31 + c.id * 1_000_003 + c.pointCount }
    }

    private val groupsByLevel: Array<List<Group>> = Array(LEVELS) { level ->
        val cells = 1 shl GRID_BITS[level]
        chunks.groupBy { c ->
            val gx = floor(c.originX * cells).toLong()
            val gy = floor(c.originY * cells).toLong()
            (gx shl 32) or (gy and 0xFFFFFFFFL)
        }.map { (key, list) ->
            val gx = (key shr 32).toDouble()
            val gy = (key and 0xFFFFFFFFL).toDouble()
            Group(level, key, gx / cells, gy / cells, list.sortedWith(compareBy({ it.trackId }, { it.seq })))
        }
    }

    fun groups(level: Int): List<Group> = groupsByLevel[level]

    val isEmpty: Boolean get() = chunks.isEmpty()

    /** Gesamtbegrenzung (normiert) oder null ohne Strecken. */
    val bounds: DoubleArray? = if (chunks.isEmpty()) null else doubleArrayOf(
        chunks.minOf { it.minX }, chunks.minOf { it.minY }, chunks.maxOf { it.maxX }, chunks.maxOf { it.maxY },
    )

    companion object {
        const val WORLD_UNITS = 1_048_576.0 // 2^20
        const val LEVELS = 4

        /**
         * Erlaubte Abweichung je Detailstufe (normiert). Stufe 0 für Europa-Ansicht … Stufe 3 (≈ 1 m)
         * für die Straßenansicht.
         */
        val TOLERANCES = doubleArrayOf(0.5 / (1 shl 14), 0.5 / (1 shl 17), 0.5 / (1 shl 20), 1.0 / Geo.EARTH_CIRCUMFERENCE_M)

        /** Größe der Gruppen je Stufe: Welt in 2^n × 2^n Felder. */
        private val GRID_BITS = intArrayOf(2, 6, 9, 11)

        /** Feinste Stufe, die bei dieser Weltgröße in Pixeln noch nötig ist (Abweichung ≤ ½ Pixel). */
        fun levelFor(worldPx: Double): Int = when {
            worldPx <= (1 shl 14) -> 0
            worldPx <= (1 shl 17) -> 1
            worldPx <= (1 shl 20) -> 2
            else -> 3
        }

        /** Bereitet ein Streckenstück vor (Punkte in Grad × 100 000). */
        fun chunk(id: Long, trackId: Long, seq: Int, latE5: IntArray, lonE5: IntArray): ChunkGeom {
            val n = latE5.size
            require(n > 0 && lonE5.size == n)
            val xs = DoubleArray(n) { Geo.mercatorX(lonE5[it] / TrackPoint.E5) }
            val ys = DoubleArray(n) { Geo.mercatorY(latE5[it] / TrackPoint.E5) }
            val ox = xs[0]
            val oy = ys[0]
            val levels = Array(LEVELS) { level ->
                val keep = Simplify.douglasPeucker(xs, ys, TOLERANCES[level])
                FloatArray(keep.size * 2).also { out ->
                    keep.forEachIndexed { j, i ->
                        out[2 * j] = ((xs[i] - ox) * WORLD_UNITS).toFloat()
                        out[2 * j + 1] = ((ys[i] - oy) * WORLD_UNITS).toFloat()
                    }
                }
            }
            return ChunkGeom(
                id, trackId, seq, n, ox, oy,
                xs.min(), ys.min(), xs.max(), ys.max(), levels,
            )
        }
    }
}
