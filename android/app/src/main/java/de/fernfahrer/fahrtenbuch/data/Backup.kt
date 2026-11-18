package de.fernfahrer.fahrtenbuch.data

import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wandelt alle Daten in eine JSON-Datei um und zurück (für Handywechsel).
 *
 * Versionen:
 * - 1: Tage, Touren, Fahrername
 * - 2: zusätzlich Arbeitsbeginn/-ende pro Tag und die Zählregel (Firmenregel)
 * - 3: Arbeitsschichten (über Mitternacht), Land von Hand gewählt, Übernachtung im LKW,
 *      geschätzte km, Einstellungen zur Arbeitszeit
 * - 4: GPS-Strecken der Karte (Punkte kompakt als Base64) und Einstellungen der Karte
 * - 5: Standorte beim Arbeitsende (Übernachtungsorte) und erreichte Meilensteine
 * - 6: mehrere Länder pro Tag, Übernachtungsplätze mit Namen, Notizen und einzelnen Übernachtungen,
 *      Dieselverbrauch, Einstellungen des automatischen Backups
 * Ältere Sicherungen werden weiterhin gelesen; fehlende Felder erhalten sinnvolle Werte.
 */
object Backup {
    private const val APP_ID = "fahrtenbuch-android"
    const val VERSION = 6

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", VERSION)
        root.put("exportedAt", java.time.LocalDateTime.now().withNano(0).toString())
        root.put("driverName", data.driverName)
        data.rules?.let { root.put("rules", JSONObject().put("shortSingleDayAsHalf", it.shortSingleDayAsHalf)) }
        data.workSettings?.let {
            root.put(
                "workSettings",
                JSONObject()
                    .put("reminderEnabled", it.reminderEnabled)
                    .put("reminderHours", it.reminderHours)
                    .put("gpsEnabled", it.gpsEnabled),
            )
        }
        data.mapSettings?.let {
            root.put(
                "mapSettings",
                JSONObject().put("trackingEnabled", it.trackingEnabled).put("corridorMeters", it.corridorMeters)
                    .put("showOvernights", it.showOvernights).put("fuelPer100Km", it.fuelPer100Km),
            )
        }
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
                        .put("kmStart", d.kmStart.orNull())
                        .put("kmEnd", d.kmEnd.orNull())
                        .put("note", d.note)
                        .put("tourId", d.tourId.orNull())
                        .put("dayTypeOverride", d.dayTypeOverride?.name.orNull())
                        // Feldnamen wie in Format 2 (alte Arbeitszeit pro Tag, unverändert aufbewahrt)
                        .put("workStartMinute", d.legacyWorkStartMinute.orNull())
                        .put("workEndMinute", d.legacyWorkEndMinute.orNull())
                        .put("countryManual", d.countryManual)
                        .put("overnightOverride", d.overnightOverride.orNull())
                        .put("kmInterpolated", d.kmInterpolated)
                        .put("timesAuto", d.timesAuto)
                        .put("countries", d.countries),
                )
            }
        })
        root.put("shifts", JSONArray().apply {
            data.shifts.forEach { s ->
                put(
                    JSONObject()
                        .put("id", s.id)
                        .put("startDate", s.startDate.toString())
                        .put("startMinute", s.startMinute.orNull())
                        .put("endDate", s.endDate?.toString().orNull())
                        .put("endMinute", s.endMinute.orNull())
                        .put("running", s.running)
                        .put("kmStart", s.kmStart.orNull())
                        .put("kmEnd", s.kmEnd.orNull()),
                )
            }
        })
        data.tracks?.let { tracks ->
            val encoder = Base64.getEncoder()
            root.put("tracks", JSONArray().apply {
                tracks.forEach { t ->
                    put(
                        JSONObject()
                            .put("trackId", t.trackId)
                            .put("seq", t.seq)
                            .put("countries", t.countries)
                            .put("points", encoder.encodeToString(t.data)),
                    )
                }
            })
        }
        data.stops?.let { stops ->
            root.put("stops", JSONArray().apply {
                stops.forEach { st ->
                    put(
                        JSONObject()
                            .put("id", st.id)
                            .put("date", st.date.toString())
                            .put("minute", st.minute)
                            .put("latE5", st.latE5)
                            .put("lonE5", st.lonE5)
                            .put("countryCode", st.countryCode.orNull()),
                    )
                }
            })
        }
        data.milestones?.let { list ->
            root.put("milestones", JSONArray().apply {
                list.forEach { put(JSONObject().put("id", it.id).put("achievedOn", it.achievedOn.toString())) }
            })
        }
        data.places?.let { places ->
            root.put("overnightPlaces", JSONArray().apply {
                places.forEach { (p, stays) ->
                    put(
                        JSONObject()
                            .put("id", p.id)
                            .put("latE5", p.latE5)
                            .put("lonE5", p.lonE5)
                            .put("countryCode", p.countryCode.orNull())
                            .put("name", p.name)
                            .put("rating", p.rating)
                            .put("shower", p.shower)
                            .put("toilet", p.toilet)
                            .put("food", p.food)
                            .put("secure", p.secure)
                            .put("quiet", p.quiet)
                            .put("note", p.note)
                            .put("stays", JSONArray().apply {
                                stays.forEach { st ->
                                    put(
                                        JSONObject()
                                            .put("id", st.id)
                                            .put("date", st.date.toString())
                                            .put("minute", st.minute)
                                            .put("latE5", st.latE5)
                                            .put("lonE5", st.lonE5),
                                    )
                                }
                            }),
                    )
                }
            })
        }
        data.autoBackup?.let {
            root.put(
                "autoBackup",
                JSONObject().put("enabled", it.enabled).put("interval", it.interval.name).put("keep", it.keep),
            )
        }
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
        val version = root.optInt("version")
        if (version > VERSION) {
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
                    // Format 2: Arbeitszeit pro Tag; in älteren Sicherungen nicht vorhanden → leer.
                    legacyWorkStartMinute = d.optIntOrNull("workStartMinute")?.coerceIn(0, 1439),
                    legacyWorkEndMinute = d.optIntOrNull("workEndMinute")?.coerceIn(1, 1440),
                    // Ab Format 3; ältere Einträge gelten als von Hand erfasst (GPS ändert sie nie).
                    countryManual = if (d.has("countryManual")) d.getBoolean("countryManual") else true,
                    overnightOverride = d.optBooleanOrNull("overnightOverride"),
                    kmInterpolated = d.optBoolean("kmInterpolated", false),
                    timesAuto = d.optBoolean("timesAuto", false),
                    // Ab Format 6; ältere Sicherungen: nur ein Land pro Tag.
                    countries = d.optString("countries", ""),
                )
            }
            if (days.map { it.date }.toSet().size != days.size) {
                throw BackupFormatException("Die Sicherung enthält einen Tag doppelt.")
            }
            val shiftsJson = root.optJSONArray("shifts")
            val shifts = if (shiftsJson == null) {
                LegacyWork.toShifts(days)
            } else {
                (0 until shiftsJson.length()).map { i ->
                    val s = shiftsJson.getJSONObject(i)
                    WorkShiftEntity(
                        id = s.getLong("id"),
                        startDate = LocalDate.parse(s.getString("startDate")),
                        startMinute = s.optIntOrNull("startMinute")?.coerceIn(0, 1439),
                        endDate = s.optStringOrNull("endDate")?.let(LocalDate::parse),
                        endMinute = s.optIntOrNull("endMinute")?.coerceIn(0, 1440),
                        running = s.optBoolean("running", false),
                        kmStart = s.optIntOrNull("kmStart"),
                        kmEnd = s.optIntOrNull("kmEnd"),
                    )
                }
            }
            if (shifts.count { it.running } > 1) {
                throw BackupFormatException("Die Sicherung enthält mehrere laufende Arbeitszeiten.")
            }
            val rules = root.optJSONObject("rules")?.let {
                CountingRules(shortSingleDayAsHalf = it.optBoolean("shortSingleDayAsHalf", false))
            }
            val workSettings = root.optJSONObject("workSettings")?.let {
                WorkSettings(
                    reminderEnabled = it.optBoolean("reminderEnabled", true),
                    reminderHours = it.optInt("reminderHours", 15).coerceIn(1, 24),
                    gpsEnabled = it.optBoolean("gpsEnabled", true),
                )
            }
            val mapSettings = root.optJSONObject("mapSettings")?.let {
                MapSettings(
                    trackingEnabled = it.optBoolean("trackingEnabled", true),
                    corridorMeters = MapSettings.validCorridor(it.optInt("corridorMeters", MapSettings.DEFAULT_CORRIDOR)),
                    showOvernights = it.optBoolean("showOvernights", true),
                    fuelPer100Km = MapSettings.validFuel(it.optDouble("fuelPer100Km", MapSettings.DEFAULT_FUEL)),
                )
            }
            val tracks = root.optJSONArray("tracks")?.let { arr ->
                val decoder = Base64.getDecoder()
                (0 until arr.length()).map { i ->
                    val t = arr.getJSONObject(i)
                    // Abgeleitete Felder (Begrenzung, Länge, Anzahl) werden aus den Punkten neu berechnet.
                    TrackChunks.rebuild(
                        TrackChunkEntity(
                            trackId = t.getLong("trackId"),
                            seq = t.getInt("seq"),
                            startTime = 0, endTime = 0, pointCount = 0,
                            minLatE5 = 0, minLonE5 = 0, maxLatE5 = 0, maxLonE5 = 0, lengthMeters = 0.0,
                            countries = t.optString("countries", ""),
                            data = decoder.decode(t.getString("points")),
                        ),
                    )
                }
            }
            val stops = root.optJSONArray("stops")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val st = arr.getJSONObject(i)
                    StopLocationEntity(
                        id = st.getLong("id"),
                        date = LocalDate.parse(st.getString("date")),
                        minute = st.getInt("minute").coerceIn(0, 1440),
                        latE5 = st.getInt("latE5").also { require(it in -9_000_000..9_000_000) },
                        lonE5 = st.getInt("lonE5").also { require(it in -18_000_000..18_000_000) },
                        countryCode = st.optStringOrNull("countryCode"),
                    )
                }
            }
            val milestones = root.optJSONArray("milestones")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val m = arr.getJSONObject(i)
                    MilestoneEntity(m.getString("id"), LocalDate.parse(m.getString("achievedOn")))
                }
            }
            val places = root.optJSONArray("overnightPlaces")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    val place = OvernightPlaceEntity(
                        id = p.getLong("id"),
                        latE5 = p.getInt("latE5").also { require(it in -9_000_000..9_000_000) },
                        lonE5 = p.getInt("lonE5").also { require(it in -18_000_000..18_000_000) },
                        countryCode = p.optStringOrNull("countryCode"),
                        name = p.optString("name", ""),
                        rating = p.optInt("rating", 0).coerceIn(0, 5),
                        shower = p.optBoolean("shower", false),
                        toilet = p.optBoolean("toilet", false),
                        food = p.optBoolean("food", false),
                        secure = p.optBoolean("secure", false),
                        quiet = p.optBoolean("quiet", false),
                        note = p.optString("note", ""),
                    )
                    val staysJson = p.optJSONArray("stays") ?: JSONArray()
                    val stays = (0 until staysJson.length()).map { j ->
                        val st = staysJson.getJSONObject(j)
                        OvernightStayEntity(
                            id = st.getLong("id"),
                            placeId = place.id,
                            date = LocalDate.parse(st.getString("date")),
                            minute = st.optInt("minute", 0).coerceIn(0, 1440),
                            latE5 = st.optInt("latE5", place.latE5),
                            lonE5 = st.optInt("lonE5", place.lonE5),
                        )
                    }
                    OvernightPlace(place, stays)
                }
            }
            val autoBackup = root.optJSONObject("autoBackup")?.let {
                AutoBackupSettings(
                    enabled = it.optBoolean("enabled", false),
                    interval = AutoBackupSettings.Interval.entries.firstOrNull { v -> v.name == it.optString("interval") }
                        ?: AutoBackupSettings.Interval.WEEKLY,
                    keep = AutoBackupSettings.validKeep(it.optInt("keep", AutoBackupSettings.DEFAULT_KEEP)),
                )
            }
            return BackupData(
                tours, days, root.optString("driverName", ""), rules, shifts, workSettings, tracks, mapSettings,
                stops, milestones, places, autoBackup,
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("Die Datensicherung ist beschädigt und kann nicht gelesen werden.")
        }
    }

    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (!has(key) || isNull(key)) null else getBoolean(key)
}
