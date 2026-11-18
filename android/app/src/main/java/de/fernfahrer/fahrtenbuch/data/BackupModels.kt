package de.fernfahrer.fahrtenbuch.data

import de.fernfahrer.fahrtenbuch.domain.CountingRules

/** Einstellungen rund um die Arbeitszeit (ab Sicherungsformat 3). */
data class WorkSettings(
    val reminderEnabled: Boolean = true,
    val reminderHours: Int = 15,
    val gpsEnabled: Boolean = true,
)

/** Einstellungen der Karte (ab Sicherungsformat 4). */
data class MapSettings(
    /** GPS-Strecke während der Arbeitszeit aufzeichnen. */
    val trackingEnabled: Boolean = true,
    /** Übernachtungsorte auf der Karte zeigen. */
    val showOvernights: Boolean = true,
    /** Breite des aufgedeckten Korridors in Metern (gesamt, also je Hälfte links und rechts). */
    val corridorMeters: Int = DEFAULT_CORRIDOR,
    /** Dieselverbrauch in Litern pro 100 km (für die Kachel „Liter Diesel“, ab Sicherungsformat 6). */
    val fuelPer100Km: Double = DEFAULT_FUEL,
) {
    companion object {
        const val DEFAULT_FUEL = 27.0
        const val MIN_FUEL = 1.0
        const val MAX_FUEL = 100.0
        fun validFuel(v: Double) = if (v.isFinite() && v in MIN_FUEL..MAX_FUEL) v else DEFAULT_FUEL
        const val DEFAULT_CORRIDOR = 100
        val CORRIDOR_OPTIONS = listOf(100, 500, 1000, 5000)
        fun validCorridor(m: Int) = if (m in CORRIDOR_OPTIONS) m else DEFAULT_CORRIDOR
    }
}

/** Automatisches Backup (ab Sicherungsformat 6). Der Speicherort gehört zum Gerät und wird nicht gesichert. */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    val interval: Interval = Interval.WEEKLY,
    /** Anzahl aufbewahrter automatischer Sicherungen; ältere werden gelöscht. */
    val keep: Int = DEFAULT_KEEP,
) {
    enum class Interval(val label: String) {
        DAILY("Täglich"), WEEKLY("Wöchentlich"), MONTHLY("Monatlich"),
    }

    companion object {
        const val DEFAULT_KEEP = 5
        val KEEP_OPTIONS = listOf(1, 3, 5, 10, 20)
        fun validKeep(n: Int) = n.coerceIn(1, 50)
    }
}

data class BackupData(
    val tours: List<TourEntity>,
    val days: List<DayEntity>,
    val driverName: String = "",
    /** Zählregeln aus der Sicherung; null bei alten Sicherungen (Version 1) ohne diese Angabe. */
    val rules: CountingRules? = null,
    /** Arbeitsschichten (ab Format 3; bei älteren Sicherungen aus der Arbeitszeit pro Tag erzeugt). */
    val shifts: List<WorkShiftEntity> = emptyList(),
    /** Einstellungen zur Arbeitszeit; null bei älteren Sicherungen. */
    val workSettings: WorkSettings? = null,
    /** GPS-Strecken (ab Format 4); null bei älteren Sicherungen – vorhandene Strecken bleiben dann erhalten. */
    val tracks: List<TrackChunkEntity>? = null,
    /** Einstellungen der Karte; null bei älteren Sicherungen. */
    val mapSettings: MapSettings? = null,
    /** Standorte beim Arbeitsende / Übernachtungsorte (ab Format 5); null bei älteren Sicherungen. */
    val stops: List<StopLocationEntity>? = null,
    /** Erreichte Meilensteine (ab Format 5); null bei älteren Sicherungen. */
    val milestones: List<MilestoneEntity>? = null,
    /** Übernachtungsplätze mit Namen, Notizen und Übernachtungen (ab Format 6); null bei älteren Sicherungen. */
    val places: List<OvernightPlace>? = null,
    /** Einstellungen des automatischen Backups (ab Format 6); null bei älteren Sicherungen. */
    val autoBackup: AutoBackupSettings? = null,
)

class BackupFormatException(message: String) : Exception(message)

/** Übernahme der Arbeitszeit pro Tag aus App-Version 1.0.6 in Schichten (wie Datenbank-Migration 2 → 3). */
object LegacyWork {
    fun toShifts(days: List<DayEntity>): List<WorkShiftEntity> =
        days.filter { it.legacyWorkStartMinute != null || it.legacyWorkEndMinute != null }
            .sortedBy { it.date }
            .map { d ->
                WorkShiftEntity(
                    startDate = d.date,
                    startMinute = d.legacyWorkStartMinute,
                    endDate = if (d.legacyWorkEndMinute != null) d.date else null,
                    endMinute = d.legacyWorkEndMinute,
                    running = false,
                )
            }
}
