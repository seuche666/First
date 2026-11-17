package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Ein erreichter Meilenstein. */
@Entity(tableName = "milestones")
data class MilestoneEntity(
    /** Kennung, z. B. "km_1000", "land_AT", "nacht_10". */
    @PrimaryKey val id: String,
    /** Datum, an dem der Meilenstein erreicht wurde (aus den Daten ermittelt). */
    val achievedOn: LocalDate,
)
