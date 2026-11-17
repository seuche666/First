package de.fernfahrer.fahrtenbuch.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import kotlin.math.abs
import kotlin.math.max

/**
 * Zeichnet den Nebel: eine dunkelgraue Fläche über der Karte, die entlang der gefahrenen Strecken
 * mit weichem Rand freigelegt wird. Unabhängig von der Kartenbibliothek (testbar).
 */
class FogRenderer(private val density: Float) {

    /** Aktuelle Kartenansicht: Pixel = normierte Mercator-Koordinate × [worldPx] + Versatz. */
    data class Viewport(
        val widthPx: Int,
        val heightPx: Int,
        val worldPx: Double,
        val offsetX: Double,
        val offsetY: Double,
        /** Meter pro Pixel in der Bildmitte. */
        val metersPerPx: Double,
    )

    var fogColor: Int = Color.argb(222, 0x22, 0x26, 0x2B)

    private val fogPaint = Paint()
    private val maskLayerPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        // SRC: Überlappende Strecken addieren sich nicht – der Rand bleibt überall gleich weich.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    /** Zwischenspeicher der Linien (Path) je Gruppe; wird bei Änderungen automatisch neu aufgebaut. */
    private val cache = object : LinkedHashMap<Long, CachedPath>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedPath>?) = size > MAX_CACHED
    }

    private class CachedPath(val signature: Long, val path: Path)

    /** Breite des vollständig freigelegten Korridors in Pixeln (mit Mindestbreite beim Rauszoomen). */
    fun corridorPx(corridorM: Int, metersPerPx: Double): Float =
        max((corridorM / metersPerPx).toFloat(), MIN_CORRIDOR_DP * density)

    /** Breite des weichen Übergangs in Pixeln. */
    fun featherPx(corePx: Float): Float = (corePx * 0.35f).coerceIn(4f * density, 28f * density)

    fun draw(canvas: Canvas, geometry: FogGeometry?, view: Viewport, corridorM: Int) {
        val w = view.widthPx.toFloat()
        val h = view.heightPx.toFloat()
        if (geometry == null || geometry.isEmpty) {
            fogPaint.color = fogColor
            canvas.drawRect(0f, 0f, w, h, fogPaint)
            return
        }
        val core = corridorPx(corridorM, view.metersPerPx)
        val feather = featherPx(core)
        val margin = (core / 2 + feather) / view.worldPx
        val minX = (0 - view.offsetX) / view.worldPx - margin
        val maxX = (w - view.offsetX) / view.worldPx + margin
        val minY = (0 - view.offsetY) / view.worldPx - margin
        val maxY = (h - view.offsetY) / view.worldPx + margin
        val level = FogGeometry.levelFor(view.worldPx)
        val visible = geometry.groups(level).filter { g ->
            g.maxX >= minX && g.minX <= maxX && g.maxY >= minY && g.minY <= maxY
        }

        val fogLayer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawColor(fogColor)
        if (visible.isNotEmpty()) {
            val maskLayer = canvas.saveLayer(0f, 0f, w, h, maskLayerPaint)
            val scale = (view.worldPx / FogGeometry.WORLD_UNITS).toFloat()
            // Von außen (breit, durchscheinend) nach innen (Korridor, ganz frei)
            for (pass in 0 until PASSES) {
                val t = 1f - pass.toFloat() / (PASSES - 1) // 1 … 0
                strokePaint.alpha = (255 * (pass + 1f) / PASSES).toInt()
                val width = core + 2 * feather * t
                for (g in visible) {
                    canvas.save()
                    canvas.translate(
                        (g.originX * view.worldPx + view.offsetX).toFloat(),
                        (g.originY * view.worldPx + view.offsetY).toFloat(),
                    )
                    canvas.scale(scale, scale)
                    strokePaint.strokeWidth = width / scale
                    canvas.drawPath(pathFor(g), strokePaint)
                    canvas.restore()
                }
            }
            canvas.restoreToCount(maskLayer)
        }
        canvas.restoreToCount(fogLayer)
    }

    private fun pathFor(g: FogGeometry.Group): Path {
        val key = g.key * 8 + g.level
        cache[key]?.takeIf { it.signature == g.signature }?.let { return it.path }
        val path = buildPath(g)
        cache[key] = CachedPath(g.signature, path)
        return path
    }

    /** Linien relativ zum Ursprung der Gruppe; zusammenhängende Stücke werden durchgehend verbunden. */
    private fun buildPath(g: FogGeometry.Group): Path {
        val path = Path()
        val u = FogGeometry.WORLD_UNITS
        var lastX = Double.NaN
        var lastY = Double.NaN
        for (c in g.chunks) {
            val n = c.size(g.level)
            if (n == 0) continue
            val fx = c.x(g.level, 0)
            val fy = c.y(g.level, 0)
            val continues = abs(fx - lastX) < JOIN_EPS && abs(fy - lastY) < JOIN_EPS
            if (!continues) {
                path.moveTo(((fx - g.originX) * u).toFloat(), ((fy - g.originY) * u).toFloat())
                if (n == 1) path.lineTo(((fx - g.originX) * u).toFloat(), ((fy - g.originY) * u).toFloat())
            }
            for (i in 1 until n) {
                path.lineTo(((c.x(g.level, i) - g.originX) * u).toFloat(), ((c.y(g.level, i) - g.originY) * u).toFloat())
            }
            lastX = c.x(g.level, n - 1)
            lastY = c.y(g.level, n - 1)
        }
        return path
    }

    private companion object {
        const val PASSES = 6
        const val MIN_CORRIDOR_DP = 3f
        const val MAX_CACHED = 400
        const val JOIN_EPS = 1e-9
    }
}
