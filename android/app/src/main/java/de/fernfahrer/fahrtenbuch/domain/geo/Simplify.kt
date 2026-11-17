package de.fernfahrer.fahrtenbuch.domain.geo

/** Vereinfachung von Linien (Douglas-Peucker), damit die Karte auch bei vielen Punkten flüssig bleibt. */
object Simplify {
    /**
     * Gibt die Indizes der Punkte zurück, die bei einer erlaubten Abweichung [tolerance] (in den
     * Einheiten von [xs]/[ys]) erhalten bleiben. Erster und letzter Punkt bleiben immer erhalten.
     */
    fun douglasPeucker(xs: DoubleArray, ys: DoubleArray, tolerance: Double, from: Int = 0, to: Int = xs.size): IntArray {
        val n = to - from
        if (n <= 2) return IntArray(n) { from + it }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val tol2 = tolerance * tolerance
        val stack = IntArray(2 * n)
        var sp = 0
        stack[sp++] = 0
        stack[sp++] = n - 1
        while (sp > 0) {
            val last = stack[--sp]
            val first = stack[--sp]
            if (last - first < 2) continue
            val ax = xs[from + first]
            val ay = ys[from + first]
            val bx = xs[from + last]
            val by = ys[from + last]
            var maxD = -1.0
            var index = -1
            for (i in first + 1 until last) {
                val d = segmentDistance2(xs[from + i], ys[from + i], ax, ay, bx, by)
                if (d > maxD) {
                    maxD = d
                    index = i
                }
            }
            if (maxD > tol2) {
                keep[index] = true
                stack[sp++] = first
                stack[sp++] = index
                stack[sp++] = index
                stack[sp++] = last
            }
        }
        var count = 0
        for (k in keep) if (k) count++
        val out = IntArray(count)
        var j = 0
        for (i in 0 until n) if (keep[i]) out[j++] = from + i
        return out
    }

    /** Quadrat des Abstands von Punkt (px, py) zur Strecke a–b. */
    fun segmentDistance2(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0.0) 0.0 else ((px - ax) * dx + (py - ay) * dy) / len2
        if (t < 0) t = 0.0 else if (t > 1) t = 1.0
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return cx * cx + cy * cy
    }
}
