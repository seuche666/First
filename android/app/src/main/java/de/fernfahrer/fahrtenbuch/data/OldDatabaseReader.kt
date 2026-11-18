package de.fernfahrer.fahrtenbuch.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.io.File
import java.time.LocalDate

/**
 * Liest eine Datenbank im Stand einer älteren App-Version roh per SQL (nur lesend), damit vor einer
 * Migration eine wiederherstellbare Datensicherung entsteht. Genutzt von [PreMigrationBackup].
 */
object OldDatabaseReader {
    /**
     * Liest alle Daten aus einer alten Datenbank (nur lesend). Funktioniert für jede bisherige Version:
     * Spalten und Tabellen, die es in der alten Version noch nicht gab, erhalten sinnvolle Werte.
     */
    fun read(dbFile: File, driverName: String, rules: CountingRules?): BackupData =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun hasTable(name: String) = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name),
            ).use { it.moveToFirst() }

            val tours = db.rawQuery("SELECT id, ended FROM tours", null).use { c ->
                buildList { while (c.moveToNext()) add(TourEntity(c.getLong(0), c.getInt(1) != 0)) }
            }
            val days = db.rawQuery("SELECT * FROM days ORDER BY date", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            DayEntity(
                                id = c.long("id")!!,
                                date = LocalDate.parse(c.string("date")),
                                startMinute = c.int("startMinute")!!,
                                endMinute = c.int("endMinute")!!,
                                countryCode = c.string("countryCode")!!,
                                kmStart = c.int("kmStart"),
                                kmEnd = c.int("kmEnd"),
                                note = c.string("note").orEmpty(),
                                tourId = c.long("tourId"),
                                dayTypeOverride = c.string("dayTypeOverride")
                                    ?.let { v -> DayType.entries.firstOrNull { it.name == v } },
                                legacyWorkStartMinute = c.int("workStartMinute"),
                                legacyWorkEndMinute = c.int("workEndMinute"),
                                // Ab Version 3; ältere Einträge gelten als von Hand erfasst.
                                countryManual = c.int("countryManual")?.let { it != 0 } ?: true,
                                overnightOverride = c.int("overnightOverride")?.let { it != 0 },
                                kmInterpolated = c.int("kmInterpolated")?.let { it != 0 } ?: false,
                                timesAuto = c.int("timesAuto")?.let { it != 0 } ?: false,
                                countries = c.string("countries").orEmpty(),
                            ),
                        )
                    }
                }
            }
            val shifts = if (hasTable("work_shifts")) {
                db.rawQuery("SELECT * FROM work_shifts ORDER BY startDate, startMinute", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                WorkShiftEntity(
                                    id = c.long("id")!!,
                                    startDate = LocalDate.parse(c.string("startDate")),
                                    startMinute = c.int("startMinute"),
                                    endDate = c.string("endDate")?.let(LocalDate::parse),
                                    endMinute = c.int("endMinute"),
                                    running = (c.int("running") ?: 0) != 0,
                                    kmStart = c.int("kmStart"),
                                    kmEnd = c.int("kmEnd"),
                                ),
                            )
                        }
                    }
                }
            } else {
                LegacyWork.toShifts(days)
            }
            val tracks: List<TrackChunkEntity>? = if (hasTable("track_chunks")) {
                db.rawQuery("SELECT * FROM track_chunks ORDER BY trackId, seq", null).use { c ->
                    buildList<TrackChunkEntity> {
                        while (c.moveToNext()) {
                            add(
                                TrackChunkEntity(
                                    id = c.long("id")!!,
                                    trackId = c.long("trackId")!!,
                                    seq = c.int("seq")!!,
                                    startTime = c.long("startTime")!!,
                                    endTime = c.long("endTime")!!,
                                    pointCount = c.int("pointCount")!!,
                                    minLatE5 = c.int("minLatE5")!!,
                                    minLonE5 = c.int("minLonE5")!!,
                                    maxLatE5 = c.int("maxLatE5")!!,
                                    maxLonE5 = c.int("maxLonE5")!!,
                                    lengthMeters = c.double("lengthMeters") ?: 0.0,
                                    countries = c.string("countries").orEmpty(),
                                    data = c.blob("data") ?: ByteArray(0),
                                ),
                            )
                        }
                    }
                }
            } else {
                null
            }
            val stops: List<StopLocationEntity>? = if (hasTable("stop_locations")) {
                db.rawQuery("SELECT * FROM stop_locations ORDER BY date, minute", null).use { c ->
                    buildList<StopLocationEntity> {
                        while (c.moveToNext()) {
                            add(
                                StopLocationEntity(
                                    id = c.long("id")!!,
                                    date = LocalDate.parse(c.string("date")),
                                    minute = c.int("minute") ?: 0,
                                    latE5 = c.int("latE5")!!,
                                    lonE5 = c.int("lonE5")!!,
                                    countryCode = c.string("countryCode"),
                                ),
                            )
                        }
                    }
                }
            } else {
                null
            }
            val milestones: List<MilestoneEntity>? = if (hasTable("milestones")) {
                db.rawQuery("SELECT * FROM milestones", null).use { c ->
                    buildList<MilestoneEntity> {
                        while (c.moveToNext()) add(MilestoneEntity(c.string("id")!!, LocalDate.parse(c.string("achievedOn"))))
                    }
                }
            } else {
                null
            }
            val places: List<OvernightPlace>? = if (hasTable("overnight_places") && hasTable("overnight_stays")) {
                val stays = db.rawQuery("SELECT * FROM overnight_stays ORDER BY date, minute", null).use { c ->
                    buildList<OvernightStayEntity> {
                        while (c.moveToNext()) {
                            add(
                                OvernightStayEntity(
                                    id = c.long("id")!!,
                                    placeId = c.long("placeId")!!,
                                    date = LocalDate.parse(c.string("date")),
                                    minute = c.int("minute") ?: 0,
                                    latE5 = c.int("latE5") ?: 0,
                                    lonE5 = c.int("lonE5") ?: 0,
                                ),
                            )
                        }
                    }
                }.groupBy { it.placeId }
                db.rawQuery("SELECT * FROM overnight_places ORDER BY id", null).use { c ->
                    buildList<OvernightPlace> {
                        while (c.moveToNext()) {
                            val place = OvernightPlaceEntity(
                                id = c.long("id")!!,
                                latE5 = c.int("latE5")!!,
                                lonE5 = c.int("lonE5")!!,
                                countryCode = c.string("countryCode"),
                                name = c.string("name").orEmpty(),
                                rating = c.int("rating") ?: 0,
                                shower = (c.int("shower") ?: 0) != 0,
                                toilet = (c.int("toilet") ?: 0) != 0,
                                food = (c.int("food") ?: 0) != 0,
                                secure = (c.int("secure") ?: 0) != 0,
                                quiet = (c.int("quiet") ?: 0) != 0,
                                note = c.string("note").orEmpty(),
                            )
                            add(OvernightPlace(place, stays[place.id].orEmpty()))
                        }
                    }
                }
            } else {
                null
            }
            BackupData(
                tours, days, driverName, rules, shifts, tracks = tracks, stops = stops, milestones = milestones,
                places = places,
            )
        }

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    private fun Cursor.int(name: String): Int? = index(name)?.let(::getInt)
    private fun Cursor.long(name: String): Long? = index(name)?.let(::getLong)
    private fun Cursor.string(name: String): String? = index(name)?.let(::getString)
    private fun Cursor.double(name: String): Double? = index(name)?.let(::getDouble)
    private fun Cursor.blob(name: String): ByteArray? = index(name)?.let(::getBlob)
}
