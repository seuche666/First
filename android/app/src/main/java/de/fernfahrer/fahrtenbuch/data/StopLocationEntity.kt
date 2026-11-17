package de.fernfahrer.fahrtenbuch.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Standort beim „Arbeit beenden“ (für die Übernachtungsorte auf der Karte). Als Übernachtung
 * angezeigt wird der letzte Standort eines Tages, an dem „Übernachtung im LKW“ gilt – so passt die
 * Karte automatisch, wenn die Übernachtung später von Hand geändert wird.
 */
@Entity(tableName = "stop_locations", indices = [Index("date")])
data class StopLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Uhrzeit in Minuten ab Mitternacht. */
    val minute: Int,
    /** Position in Grad × 100 000. */
    val latE5: Int,
    val lonE5: Int,
    /** Land (ISO-Code) laut Offline-Ländergrenzen, falls bekannt. */
    val countryCode: String? = null,
)
