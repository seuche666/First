package de.fernfahrer.fahrtenbuch.data

import de.fernfahrer.fahrtenbuch.domain.DayType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class BackupData(
    val tours: List<TourEntity>,
    val days: List<DayEntity>,
    val driverName: String = "",
)

class BackupFormatException(message: String) : Exception(message)

/** Wandelt alle Daten in eine JSON-Datei um und zurück (für Handywechsel). */
object Backup {
    private const val APP_ID = "fahrtenbuch-android"
    private const val VERSION = 1

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", VERSION)
        root.put("exportedAt", java.time.LocalDateTime.now().withNano(0).toString())
        root.put("driverName", data.driverName)
        root.put("tours", JSONArray().apply {
            data.tours.forEach { put(JSONObject().put("id", it.id).put("ended", it.ended)) }
        })
        root.put("days", JSONArray().apply {
            data.days.forEach { d ->
                put(
                    JSONObject()
                        .put("id", d.id)
                        .put("date", d.date.toString())
                        .put("startMinute", d.startMinute)
                        .put("endMinute", d.endMinute)
                        .put("countryCode", d.countryCode)
                        .put("kmStart", d.kmStart ?: JSONObject.NULL)
                        .put("kmEnd", d.kmEnd ?: JSONObject.NULL)
                        .put("note", d.note)
                        .put("tourId", d.tourId ?: JSONObject.NULL)
                        .put("dayTypeOverride", d.dayTypeOverride?.name ?: JSONObject.NULL),
                )
            }
        })
        return root.toString(2)
    }

    fun fromJson(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupFormatException("Die Datei ist keine gültige Datensicherung.")
        }
        if (root.optString("app") != APP_ID) {
            throw BackupFormatException("Die Datei ist keine Datensicherung dieser App.")
        }
        if (root.optInt("version") > VERSION) {
            throw BackupFormatException("Die Sicherung stammt aus einer neueren App-Version. Bitte die App aktualisieren.")
        }
        try {
            val toursJson = root.getJSONArray("tours")
            val tours = (0 until toursJson.length()).map { i ->
                val t = toursJson.getJSONObject(i)
                TourEntity(id = t.getLong("id"), ended = t.getBoolean("ended"))
            }
            val tourIds = tours.map { it.id }.toSet()
            val daysJson = root.getJSONArray("days")
            val days = (0 until daysJson.length()).map { i ->
                val d = daysJson.getJSONObject(i)
                val tourId = d.optLongOrNull("tourId")?.takeIf { it in tourIds }
                DayEntity(
                    id = d.getLong("id"),
                    date = LocalDate.parse(d.getString("date")),
                    startMinute = d.getInt("startMinute").coerceIn(0, 1439),
                    endMinute = d.getInt("endMinute").coerceIn(1, 1440),
                    countryCode = d.getString("countryCode"),
                    kmStart = d.optIntOrNull("kmStart"),
                    kmEnd = d.optIntOrNull("kmEnd"),
                    note = d.optString("note", ""),
                    tourId = tourId,
                    dayTypeOverride = d.optStringOrNull("dayTypeOverride")
                        ?.let { v -> DayType.entries.firstOrNull { it.name == v } },
                )
            }
            if (days.map { it.date }.toSet().size != days.size) {
                throw BackupFormatException("Die Sicherung enthält einen Tag doppelt.")
            }
            return BackupData(tours, days, root.optString("driverName", ""))
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("Die Datensicherung ist beschädigt und kann nicht gelesen werden.")
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
}
