package de.fernfahrer.fahrtenbuch.domain.geo

/**
 * Entscheidet, welche GPS-Messungen als Streckenpunkt gespeichert werden:
 * - ungenaue Messungen (schlechter Empfang) werden verworfen,
 * - ein neuer Punkt erst nach [minSpacingM]; wäre der Abstand größer als [maxSpacingM], wird die
 *   vorige (übersprungene) Messung zusätzlich übernommen → Punkte etwa alle 30–50 m,
 * - unmögliche Sprünge (Ausreißer) werden verworfen,
 * - nach einer längeren Lücke (Funkloch, Tunnel) wird nur verbunden, wenn das plausibel ist;
 *   sonst beginnt ein neues Teilstück, damit keine falsche gerade Linie aufgedeckt wird.
 */
class TrackFilter(
    private val maxAccuracyM: Float = 25f,
    private val minSpacingM: Double = 30.0,
    private val maxSpacingM: Double = 55.0,
    private val maxSpeedMs: Double = 70.0,
    private val gapSeconds: Double = 120.0,
    private val maxGapSpeedMs: Double = 40.0,
    private val maxGapDistanceM: Double = 25_000.0,
    private val maxJumpsInRow: Int = 5,
) {
    /** Eine GPS-Messung. */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float?, val timeMs: Long)

    enum class Decision(val keep: Boolean) {
        /** Erster Punkt einer Aufzeichnung. */
        FIRST(true),
        ACCEPT(true),

        /** Punkt übernehmen, aber nicht mit dem vorigen verbinden (neues Teilstück). */
        BREAK(true),
        INACCURATE(false),
        TOO_CLOSE(false),
        JUMP(false),
        OUT_OF_ORDER(false),
    }

    private companion object {
        /** Eine übersprungene Messung wird nur eingefügt, wenn sie mindestens so weit vom letzten Punkt weg ist. */
        const val MIN_INSERT_M = 20.0
    }

    private var last: Fix? = null
    private var pending: Fix? = null
    private var jumpsInRow = 0

    /**
     * Wurde vor der zuletzt übernommenen Messung noch eine übersprungene Messung eingefügt (damit
     * der Abstand nicht über [maxSpacingM] steigt), steht sie hier – sie gehört vor die aktuelle.
     */
    var inserted: Fix? = null
        private set

    fun check(fix: Fix): Decision {
        inserted = null
        val acc = fix.accuracyM
        if (acc == null || acc.isNaN() || acc > maxAccuracyM) return Decision.INACCURATE
        if (fix.lat !in -85.0..85.0 || fix.lon !in -180.0..180.0 || (fix.lat == 0.0 && fix.lon == 0.0)) {
            return Decision.INACCURATE
        }
        val prev = last ?: return accept(fix, Decision.FIRST)
        val dt = (fix.timeMs - prev.timeMs) / 1000.0
        if (dt <= 0) return Decision.OUT_OF_ORDER
        val d = Geo.distanceM(prev.lat, prev.lon, fix.lat, fix.lon)
        if (d < minSpacingM) {
            jumpsInRow = 0
            pending = fix
            return Decision.TOO_CLOSE
        }
        if (dt <= gapSeconds) {
            if (d / dt > maxSpeedMs) {
                // Viele „Sprünge“ hintereinander: dann war eher der letzte Punkt falsch → neu ansetzen.
                if (++jumpsInRow >= maxJumpsInRow) return accept(fix, Decision.BREAK)
                return Decision.JUMP
            }
            val p = pending
            if (d > maxSpacingM && p != null && Geo.distanceM(prev.lat, prev.lon, p.lat, p.lon) >= MIN_INSERT_M) {
                inserted = p
            }
            return accept(fix, Decision.ACCEPT)
        }
        return if (d <= maxGapDistanceM && d / dt <= maxGapSpeedMs) accept(fix, Decision.ACCEPT) else accept(fix, Decision.BREAK)
    }

    /** Neue Aufzeichnung: nichts mit früheren Punkten verbinden. */
    fun reset() {
        last = null
        pending = null
        inserted = null
        jumpsInRow = 0
    }

    private fun accept(fix: Fix, d: Decision): Decision {
        last = fix
        pending = null
        jumpsInRow = 0
        return d
    }
}
