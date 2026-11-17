package de.fernfahrer.fahrtenbuch.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Selbst gezeichnete Kartensymbole (gut sichtbar auf dunklem Nebel und heller Karte). */
object MapIcons {
    /** Akzentfarbe der App (Blau) und dunkle Kontur. */
    private val BLUE = Color.rgb(0x1E, 0x4F, 0xD8)
    private val BLUE_DARK = Color.rgb(0x0F, 0x2C, 0x86)
    private val GLASS = Color.rgb(0xBF, 0xD6, 0xFF)

    /**
     * Sattelzug von oben, Fahrerhaus zeigt nach oben (Norden). Die Karte dreht ihn in Fahrtrichtung.
     * Weißer Rand und Schatten, damit er auf hellem und dunklem Grund auffällt.
     */
    fun truck(density: Float): Bitmap {
        // Gezeichnet auf einem Raster von 30 × 64 Einheiten, dargestellt ca. 23 × 50 dp
        val d = density * 0.78f
        val w = (30 * d).toInt()
        val h = (64 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(3 * d, 0f, 1 * d, Color.argb(140, 0, 0, 0))
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE_DARK }
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLASS }
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }

        val trailerW = 18 * d
        val cabW = 16 * d
        val cab = RectF(cx - cabW / 2, 4 * d, cx + cabW / 2, 20 * d)
        val trailer = RectF(cx - trailerW / 2, 22 * d, cx + trailerW / 2, h - 4 * d)
        val border = 2.2f * d

        // Weißer Rand mit Schatten
        c.drawRoundRect(RectF(trailer.left - border, trailer.top - border, trailer.right + border, trailer.bottom + border), 4 * d, 4 * d, outline)
        c.drawRoundRect(RectF(cab.left - border, cab.top - border, cab.right + border, cab.bottom + border), 6 * d, 6 * d, outline)
        // Sattelkupplung zwischen Fahrerhaus und Auflieger
        c.drawRect(cx - 3 * d, 18 * d, cx + 3 * d, 24 * d, dark)

        // Auflieger mit Längsstreifen
        c.drawRoundRect(trailer, 3 * d, 3 * d, fill)
        c.drawRect(trailer.left + 4 * d, trailer.top + 3 * d, trailer.left + 6 * d, trailer.bottom - 3 * d, stripe)
        c.drawRect(trailer.right - 6 * d, trailer.top + 3 * d, trailer.right - 4 * d, trailer.bottom - 3 * d, stripe)

        // Fahrerhaus: vorne abgerundet, Windschutzscheibe, Spiegel
        c.drawRoundRect(cab, 5 * d, 5 * d, dark)
        c.drawRoundRect(RectF(cab.left + 1.5f * d, cab.top + 1.5f * d, cab.right - 1.5f * d, cab.bottom - 1 * d), 4 * d, 4 * d, fill)
        val shield = Path().apply {
            moveTo(cab.left + 3 * d, cab.top + 4 * d)
            lineTo(cab.right - 3 * d, cab.top + 4 * d)
            lineTo(cab.right - 4 * d, cab.top + 8 * d)
            lineTo(cab.left + 4 * d, cab.top + 8 * d)
            close()
        }
        c.drawPath(shield, glass)
        c.drawRoundRect(RectF(cab.left - 3 * d, cab.top + 5 * d, cab.left, cab.top + 7 * d), d, d, dark)
        c.drawRoundRect(RectF(cab.right, cab.top + 5 * d, cab.right + 3 * d, cab.top + 7 * d), d, d, dark)
        return bmp
    }

    /** Rundes Symbol mit Mond für Übernachtungsplätze; ab 2 Übernachtungen mit Anzahl. */
    fun overnight(density: Float, count: Int): Bitmap {
        val d = density
        val size = (30 * d).toInt()
        val badge = if (count > 1) (11 * d) else 0f
        val bmp = Bitmap.createBitmap((size + badge).toInt(), (size + badge).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = badge + size / 2f
        val r = size / 2f - 2 * d
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(3 * d, 0f, 1 * d, Color.argb(140, 0, 0, 0))
        })
        c.drawCircle(cx, cy, r - 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x2F, 0x86) })
        // Mondsichel: gelber Kreis, davon ein versetzter Kreis abgezogen
        val moon = Path().apply {
            addCircle(cx - 0.5f * d, cy, 7 * d, Path.Direction.CW)
            val cut = Path().apply { addCircle(cx + 3.5f * d, cy - 3 * d, 6.5f * d, Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        c.drawPath(moon, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD5, 0x4F) })
        if (count > 1) {
            val bx = cx + r * 0.8f
            val by = badge + 1 * d
            val br = 9 * d
            c.drawCircle(bx, by + br - 2 * d, br, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            c.drawCircle(bx, by + br - 2 * d, br - 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE })
            val text = if (count > 99) "99+" else count.toString()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = (if (text.length > 2) 8 else 10) * d
            }
            c.drawText(text, bx, by + br - 2 * d + tp.textSize * 0.36f, tp)
        }
        return bmp
    }
}
