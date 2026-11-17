package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Eine mehrtägige Tour. Beginn und Ende ergeben sich aus den zugehörigen Tagen. */
@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** false = Tour läuft noch. */
    val ended: Boolean = false,
)
