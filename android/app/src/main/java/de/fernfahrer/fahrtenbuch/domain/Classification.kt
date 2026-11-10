package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity

/** Einordnung eines Tages für die Steuererklärung. */
enum class DayType(val label: String, val shortLabel: String) {
    FULL("Voller Tag", "Voll"),
    HALF("Halber Tag", "Halb"),
    NONE("Zählt nicht", "–"),
}

/** Einstellbare Zählregeln. */
data class CountingRules(
    /**
     * Firmenregel: Eine eintägige Fahrt zählt immer als halber Tag, auch bei 8 Stunden oder weniger.
     * Das Finanzamt verlangt dafür mehr als 8 Stunden Abwesenheit.
     */
    val shortSingleDayAsHalf: Boolean = false,
) {
    /** Beschreibung der aktiven Regel für Anzeige und Export. */
    val singleDayRuleText: String
        get() = if (shortSingleDayAsHalf) {
            "Firmenregel: eintägige Fahrten zählen immer als halber Tag, auch bei 8 Stunden oder weniger " +
                "(Finanzamt: erst bei mehr als 8 Stunden)"
        } else {
            "Gesetzliche Regel: eintägige Fahrten zählen erst bei mehr als 8 Stunden als halber Tag"
        }
}

/** Ergebnis der automatischen Einordnung. */
data class AutoClassification(
    val type: DayType,
    val reason: String,
    /** true, wenn der Tag nur wegen der Firmenregel als halber Tag zählt. */
    val companyRule: Boolean = false,
)

/** Ein Tag mit seiner automatischen und tatsächlich verwendeten Einordnung. */
data class ClassifiedDay(
    val day: DayEntity,
    /** Automatisch ermittelte Einordnung. */
    val autoType: DayType,
    /** Begründung der automatischen Einordnung, für die Anzeige. */
    val reason: String,
    /** Automatische Einordnung beruht auf der Firmenregel (eintägig, 8 Std. oder weniger). */
    val companyRule: Boolean = false,
) {
    /** Verwendete Einordnung: manuelle Änderung oder automatisch. */
    val type: DayType get() = day.dayTypeOverride ?: autoType
    val isManual: Boolean get() = day.dayTypeOverride != null && day.dayTypeOverride != autoType

    /** Der Tag zählt (ohne manuelle Änderung) nur wegen der Firmenregel als halber Tag. */
    val byCompanyRule: Boolean get() = companyRule && !isManual

    /** Begründung inklusive Hinweis auf manuelle Änderung, für Anzeige und Export. */
    val fullReason: String
        get() = if (isManual) "Manuell geändert (automatisch wäre: ${autoType.label} – $reason)" else reason
}

object Classifier {
    /** Mehr als 8 Stunden Abwesenheit für einen halben Tag bei eintägigen Fahrten (gesetzliche Regel). */
    const val MIN_MINUTES_SINGLE_DAY = 8 * 60

    /**
     * Ordnet einen Tag automatisch ein.
     *
     * @param tourDays alle Tage der Tour, zu der der Tag gehört (inklusive des Tages selbst),
     *                 oder null, wenn der Tag zu keiner Tour gehört.
     * @param tourEnded ob die Tour bereits beendet ist.
     */
    fun autoType(
        day: DayEntity,
        tourDays: List<DayEntity>?,
        tourEnded: Boolean,
        rules: CountingRules = CountingRules(),
    ): AutoClassification {
        if (tourDays.isNullOrEmpty()) return singleDay(day, rules)

        val first = tourDays.minOf { it.date }
        val last = if (tourEnded) tourDays.maxOf { it.date } else null

        return when {
            tourEnded && first == last -> singleDay(day, rules)
            day.date == first -> AutoClassification(DayType.HALF, "Anreisetag der Tour")
            day.date == last -> AutoClassification(DayType.HALF, "Rückreisetag der Tour")
            day.date.isBefore(first) || (last != null && day.date.isAfter(last)) -> singleDay(day, rules)
            else -> AutoClassification(DayType.FULL, "24 Stunden unterwegs (Tag mitten in der Tour)")
        }
    }

    private fun singleDay(day: DayEntity, rules: CountingRules): AutoClassification = when {
        day.absenceMinutes > MIN_MINUTES_SINGLE_DAY ->
            AutoClassification(DayType.HALF, "Eintägige Fahrt über 8 Stunden")
        rules.shortSingleDayAsHalf -> AutoClassification(
            DayType.HALF,
            "Eintägige Fahrt bis 8 Stunden – halber Tag nach Firmenregel (Finanzamt: erst über 8 Stunden)",
            companyRule = true,
        )
        else -> AutoClassification(DayType.NONE, "Eintägige Fahrt bis 8 Stunden")
    }

    /** Ordnet alle Tage ein. */
    fun classifyAll(
        days: List<DayEntity>,
        tours: List<TourEntity>,
        rules: CountingRules = CountingRules(),
    ): List<ClassifiedDay> {
        val endedById = tours.associate { it.id to it.ended }
        val daysByTour = days.filter { it.tourId != null }.groupBy { it.tourId!! }
        return days.map { day ->
            val tourId = day.tourId
            val auto = if (tourId == null) {
                autoType(day, null, false, rules)
            } else {
                autoType(day, daysByTour[tourId], endedById[tourId] ?: true, rules)
            }
            ClassifiedDay(day, auto.type, auto.reason, auto.companyRule)
        }
    }
}
