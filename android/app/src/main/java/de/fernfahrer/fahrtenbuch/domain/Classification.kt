package de.fernfahrer.fahrtenbuch.domain

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity

/** Einordnung eines Tages für die Steuererklärung. */
enum class DayType(val label: String, val shortLabel: String) {
    FULL("Voller Tag", "Voll"),
    HALF("Halber Tag", "Halb"),
    NONE("Zählt nicht", "–"),
}

/** Ein Tag mit seiner automatischen und tatsächlich verwendeten Einordnung. */
data class ClassifiedDay(
    val day: DayEntity,
    /** Automatisch ermittelte Einordnung. */
    val autoType: DayType,
    /** Begründung der automatischen Einordnung, für die Anzeige. */
    val reason: String,
) {
    /** Verwendete Einordnung: manuelle Änderung oder automatisch. */
    val type: DayType get() = day.dayTypeOverride ?: autoType
    val isManual: Boolean get() = day.dayTypeOverride != null && day.dayTypeOverride != autoType
}

object Classifier {
    /** Mehr als 8 Stunden Abwesenheit für einen halben Tag bei eintägigen Fahrten. */
    const val MIN_MINUTES_SINGLE_DAY = 8 * 60

    /**
     * Ordnet einen Tag automatisch ein.
     *
     * @param tourDays alle Tage der Tour, zu der der Tag gehört (inklusive des Tages selbst),
     *                 oder null, wenn der Tag zu keiner Tour gehört.
     * @param tourEnded ob die Tour bereits beendet ist.
     */
    fun autoType(day: DayEntity, tourDays: List<DayEntity>?, tourEnded: Boolean): Pair<DayType, String> {
        if (tourDays.isNullOrEmpty()) return singleDay(day)

        val first = tourDays.minOf { it.date }
        val last = if (tourEnded) tourDays.maxOf { it.date } else null

        return when {
            tourEnded && first == last -> singleDay(day)
            day.date == first -> DayType.HALF to "Anreisetag der Tour"
            day.date == last -> DayType.HALF to "Rückreisetag der Tour"
            day.date.isBefore(first) || (last != null && day.date.isAfter(last)) -> singleDay(day)
            else -> DayType.FULL to "24 Stunden unterwegs (Tag mitten in der Tour)"
        }
    }

    private fun singleDay(day: DayEntity): Pair<DayType, String> =
        if (day.absenceMinutes > MIN_MINUTES_SINGLE_DAY) {
            DayType.HALF to "Eintägige Fahrt über 8 Stunden"
        } else {
            DayType.NONE to "Eintägige Fahrt bis 8 Stunden"
        }

    /** Ordnet alle Tage ein. */
    fun classifyAll(days: List<DayEntity>, tours: List<TourEntity>): List<ClassifiedDay> {
        val endedById = tours.associate { it.id to it.ended }
        val daysByTour = days.filter { it.tourId != null }.groupBy { it.tourId!! }
        return days.map { day ->
            val tourId = day.tourId
            val (type, reason) = if (tourId == null) {
                autoType(day, null, false)
            } else {
                autoType(day, daysByTour[tourId], endedById[tourId] ?: true)
            }
            ClassifiedDay(day, type, reason)
        }
    }
}
