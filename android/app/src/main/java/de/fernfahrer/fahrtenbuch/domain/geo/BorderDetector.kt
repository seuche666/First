package de.fernfahrer.fahrtenbuch.domain.geo

/**
 * Erkennt Grenzübertritte aus der GPS-Strecke. Gegen Fehlalarme direkt an der Grenze (ungenaue
 * Ländergrenzen, Straßen entlang der Grenze) gilt ein neues Land erst, wenn man mindestens
 * [minDurationMs] lang **und** [minDistanceM] weit ununterbrochen darin unterwegs war.
 * Punkte ohne Land (z. B. auf dem Meer/einer Fähre) werden übergangen.
 */
class BorderDetector(
    /** Aktuelles Land (z. B. das zuletzt eingetragene Land des Tages); null = erstes Land übernehmen. */
    initialCountry: String? = null,
    private val minDurationMs: Long = MIN_DURATION_MS,
    private val minDistanceM: Double = MIN_DISTANCE_M,
) {
    var current: String? = initialCountry
        private set

    private var candidate: String? = null
    private var candidateSinceMs = 0L
    private var candidateDistM = 0.0
    private var lastLat = 0.0
    private var lastLon = 0.0

    /**
     * Neuer Streckenpunkt. Gibt das neue Land zurück, sobald ein Grenzübertritt feststeht, sonst null.
     */
    fun onPoint(lat: Double, lon: Double, timeMs: Long, country: String?): String? {
        if (country == null) return null
        if (current == null) {
            current = country
            return null
        }
        if (country == current) {
            candidate = null
            return null
        }
        if (country != candidate) {
            candidate = country
            candidateSinceMs = timeMs
            candidateDistM = 0.0
        } else {
            candidateDistM += Geo.distanceM(lastLat, lastLon, lat, lon)
        }
        lastLat = lat
        lastLon = lon
        if (timeMs - candidateSinceMs >= minDurationMs && candidateDistM >= minDistanceM) {
            current = country
            candidate = null
            return country
        }
        return null
    }

    companion object {
        const val MIN_DURATION_MS = 5 * 60_000L
        const val MIN_DISTANCE_M = 3_000.0
    }
}
