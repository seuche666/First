package de.fernfahrer.fahrtenbuch.data

import androidx.room.withTransaction
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayCountries
import de.fernfahrer.fahrtenbuch.domain.KmShare
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.domain.geo.Geo
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class Repository(
    private val db: AppDatabase,
    /** Aktive Zählregeln; Änderungen wirken sofort auf alle Einordnungen. */
    private val rules: Flow<CountingRules>,
) {
    private val dao = db.dao()

    /** Alle Tage mit Einordnung, neueste zuerst. */
    val classifiedDays: Flow<List<ClassifiedDay>> =
        combine(dao.observeDays(), dao.observeTours(), rules) { days, tours, r ->
            Classifier.classifyAll(days, tours, r)
        }

    /** Alle Tage mit Einordnung, einmalig gelesen. */
    suspend fun classifiedDaysNow(): List<ClassifiedDay> = classifiedDays.first()

    val tours: Flow<List<TourEntity>> = dao.observeTours()

    /** Alle Arbeitsschichten. */
    val shifts: Flow<List<WorkShiftEntity>> = dao.observeShifts()

    fun recentCountries(limit: Int = 6): Flow<List<String>> = dao.observeRecentCountries(limit)

    suspend fun getDay(id: Long) = dao.getDay(id)
    suspend fun getDayByDate(date: LocalDate) = dao.getDayByDate(date)
    suspend fun getDayBefore(date: LocalDate) = dao.getDayBefore(date)
    suspend fun getLatestSingleDay() = dao.getLatestSingleDay()
    suspend fun getActiveTour() = dao.getActiveTour()
    suspend fun getTour(id: Long) = dao.getTour(id)
    suspend fun getDaysOfTour(tourId: Long) = dao.getDaysOfTour(tourId)
    suspend fun getRunningShift() = dao.getRunningShift()
    suspend fun getShift(id: Long) = dao.getShift(id)
    suspend fun getAllShifts() = dao.getAllShifts()
    suspend fun getAllDays() = dao.getAllDays()
    suspend fun getAllTours() = dao.getAllTours()

    /**
     * Speichert einen Tag.
     *
     * @param startTour legt eine neue Tour an und ordnet den Tag ihr zu.
     * @param endTour beendet die Tour des Tages.
     * @param fillGaps ergänzt fehlende Tage der Tour vor diesem Tag als volle Tage.
     */
    suspend fun saveDay(day: DayEntity, startTour: Boolean, endTour: Boolean, fillGaps: Boolean): Long =
        db.withTransaction {
            var tourId = day.tourId
            if (startTour && tourId == null) tourId = dao.insertTour(TourEntity())
            val toSave = day.copy(tourId = tourId)
            val id = if (toSave.id == 0L) {
                dao.insertDay(toSave)
            } else {
                dao.updateDay(toSave)
                toSave.id
            }
            if (tourId != null) {
                if (fillGaps) fillTourGaps(tourId, toSave.date)
                if (endTour) dao.getTour(tourId)?.let { dao.updateTour(it.copy(ended = true)) }
            }
            id
        }

    /** Tage zwischen Tourbeginn und [upTo] (exklusiv), für die es noch keinen Eintrag gibt. */
    suspend fun missingTourDates(tourId: Long, upTo: LocalDate): List<LocalDate> {
        val first = dao.getDaysOfTour(tourId).minOfOrNull { it.date } ?: return emptyList()
        val missing = mutableListOf<LocalDate>()
        var d = first.plusDays(1)
        while (d.isBefore(upTo)) {
            if (dao.getDayByDate(d) == null) missing += d
            d = d.plusDays(1)
        }
        return missing
    }

    private suspend fun fillTourGaps(tourId: Long, upTo: LocalDate) {
        for (date in missingTourDates(tourId, upTo)) {
            val previous = dao.getDayBefore(date)
            dao.insertDay(
                DayEntity(
                    date = date,
                    startMinute = 0,
                    endMinute = 24 * 60,
                    countryCode = previous?.lastCountry ?: Stats.HOME_COUNTRY,
                    note = "Automatisch ergänzt",
                    tourId = tourId,
                ),
            )
        }
    }

    /** Löscht einen Tag. Eine Tour ohne Tage wird ebenfalls gelöscht. */
    suspend fun deleteDay(day: DayEntity) = db.withTransaction {
        dao.deleteDay(day)
        day.tourId?.let { tourId ->
            if (dao.getDaysOfTour(tourId).isEmpty()) dao.deleteTour(tourId)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Arbeitszeit
    // ---------------------------------------------------------------------------------------------

    /**
     * Letzter bekannter km-Stand: der zeitlich jüngste km-Wert aus Tagen (morgens/abends) und
     * Arbeitsschichten (Beginn/Ende). Vorschlag für den Start-km eines neuen Tages oder Arbeitsbeginns.
     */
    suspend fun lastKnownKm(): Int? {
        var bestTime: LocalDateTime? = null
        var bestKm: Int? = null
        fun consider(time: LocalDateTime?, km: Int?) {
            if (time == null || km == null) return
            val bt = bestTime
            if (bt == null || time.isAfter(bt) || (time == bt && km > (bestKm ?: Int.MIN_VALUE))) {
                bestTime = time
                bestKm = km
            }
        }
        for (d in dao.getAllDays()) {
            consider(d.date.atStartOfDay(), d.kmStart)
            consider(d.date.atTime(23, 59, 59), d.kmEnd)
        }
        for (s in dao.getAllShifts()) {
            consider(s.start, s.kmStart)
            consider(s.end, s.kmEnd)
        }
        return bestKm
    }

    /**
     * Stellt sicher, dass es für [date] einen Tageseintrag gibt.
     * - während einer laufenden Tour: Tourtag 00:00–24:00 (fehlende Tourtage werden ergänzt)
     * - sonst: Abwesenheit ab [workStartMinute] (oder 00:00, wenn die Arbeit schon seit dem Vortag
     *   läuft) bis 24:00 als automatischer Vorschlag
     * Land wie am Ende des Vortags (nicht von Hand gewählt, damit GPS es verbessern darf). Der Start-km wird
     * nur bei Arbeitsbeginn vorbelegt; Tage mitten in einer Schicht erhalten ihre km beim Arbeitsende.
     */
    private suspend fun ensureDay(date: LocalDate, workStartMinute: Int?): Pair<DayEntity, Boolean> {
        dao.getDayByDate(date)?.let { return it to false }
        val tour = dao.getActiveTour()
        val tourStart = tour?.let { t -> dao.getDaysOfTour(t.id).minOfOrNull { it.date } }
        val inTour = tour != null && tourStart != null && !date.isBefore(tourStart)
        if (inTour) fillTourGaps(tour!!.id, date)
        val previous = dao.getDayBefore(date)
        val day = DayEntity(
            date = date,
            startMinute = if (inTour) 0 else (workStartMinute ?: 0),
            endMinute = 24 * 60,
            // Land, in dem man am Ende des Vortags war
            countryCode = previous?.lastCountry ?: Stats.HOME_COUNTRY,
            countryManual = false,
            kmStart = if (workStartMinute != null) lastKnownKm() else null,
            tourId = if (inTour) tour!!.id else null,
            timesAuto = !inTour,
        )
        val id = dao.insertDay(day)
        return day.copy(id = id) to true
    }

    /** Arbeit beginnen zum Zeitpunkt [at]. */
    suspend fun startWork(at: LocalDateTime): StartResult = db.withTransaction {
        dao.getRunningShift()?.let { return@withTransaction StartResult.AlreadyRunning(it) }
        val date = at.toLocalDate()
        val minute = at.hour * 60 + at.minute
        val km = lastKnownKm()
        val (day, created) = ensureDay(date, minute)
        if (!created && day.kmStart == null && km != null) dao.updateDay(day.copy(kmStart = km))
        val shift = WorkShiftEntity(
            startDate = date, startMinute = minute, endDate = null, endMinute = null,
            running = true, kmStart = km,
        )
        StartResult.Started(shift.copy(id = dao.insertShift(shift)), created)
    }

    /**
     * Legt während einer laufenden Arbeitszeit die Einträge für jeden neu begonnenen Tag an
     * (Teilung an der Tagesgrenze). Gibt die neu angelegten Tage zurück.
     */
    suspend fun syncRunningWork(now: LocalDateTime): List<LocalDate> = db.withTransaction {
        val shift = dao.getRunningShift() ?: return@withTransaction emptyList()
        val created = mutableListOf<LocalDate>()
        var d = shift.startDate.plusDays(1)
        while (!d.isAfter(now.toLocalDate())) {
            if (ensureDay(d, null).second) created += d
            d = d.plusDays(1)
        }
        created
    }

    /**
     * Arbeit beenden zum Zeitpunkt [at] mit km-Stand [km] (null = später nachtragen).
     * Gibt die beendete Schicht zurück oder null, wenn keine Arbeit lief.
     */
    suspend fun endWork(at: LocalDateTime, km: Int?): WorkShiftEntity? = db.withTransaction {
        val shift = dao.getRunningShift() ?: return@withTransaction null
        syncRunningWork(at)
        val start = shift.start ?: at
        val end = if (at.isBefore(start)) start else at
        // Ende genau um 00:00 zählt als 24:00 des Vortags (kein leerer Folgetag)
        val (endDate, endMinute) = if (end.hour == 0 && end.minute == 0 && end.toLocalDate().isAfter(shift.startDate)) {
            end.toLocalDate().minusDays(1) to 24 * 60
        } else {
            end.toLocalDate() to end.hour * 60 + end.minute
        }
        val ended = shift.copy(running = false, endDate = endDate, endMinute = endMinute, kmEnd = km)
        dao.updateShift(ended)

        // Automatisch vorgeschlagene Abwesenheit des letzten Tages an das Arbeitsende anpassen
        dao.getDayByDate(endDate)?.let { day ->
            if (day.timesAuto && day.tourId == null && day.endMinute == 24 * 60 &&
                endMinute < 24 * 60 && endMinute > day.startMinute
            ) {
                dao.updateDay(day.copy(endMinute = endMinute))
            }
        }
        applyShiftKm(old = null, new = ended)
        ended
    }

    /**
     * Speichert eine von Hand geänderte oder neu angelegte Schicht und übernimmt ihre km in die Tage.
     * Gibt die ID zurück.
     */
    suspend fun saveShift(shift: WorkShiftEntity): Long = db.withTransaction {
        if (shift.running) {
            val other = dao.getRunningShift()
            require(other == null || other.id == shift.id) { "Es läuft bereits eine Arbeitszeit." }
        }
        val old = if (shift.id != 0L) dao.getShift(shift.id) else null
        val id = if (shift.id == 0L) dao.insertShift(shift) else shift.id.also { dao.updateShift(shift) }
        val saved = shift.copy(id = id)
        // Für jeden Tag der Schicht einen Eintrag sicherstellen
        val last = saved.endDate ?: saved.startDate
        var d = saved.startDate
        while (!d.isAfter(last)) {
            ensureDay(d, if (d == saved.startDate) saved.startMinute else null)
            d = d.plusDays(1)
        }
        applyShiftKm(old, saved)
        id
    }

    suspend fun deleteShift(id: Long) = dao.deleteShift(id)

    /**
     * Überträgt die km einer Schicht in die Tage: bei Schichten über Mitternacht anteilig nach
     * Arbeitszeit. Werte, die von dieser Schicht stammen ([old]), werden ersetzt; sonst gilt
     * km morgens = kleinster, km abends = größter bekannter Wert. So wird nichts doppelt gezählt.
     */
    private suspend fun applyShiftKm(old: WorkShiftEntity?, new: WorkShiftEntity) {
        val newShares = WorkSplitter.distributeKm(new) ?: run {
            // Nur km-Ende bekannt: dem letzten Tag zuordnen
            val km = new.kmEnd ?: return
            val date = new.endDate ?: return
            dao.getDayByDate(date)?.let { day ->
                dao.updateDay(day.copy(kmEnd = maxOf(day.kmEnd ?: km, km)))
            }
            return
        }
        val oldShares: Map<LocalDate, KmShare> =
            old?.let { WorkSplitter.distributeKm(it) }.orEmpty().associateBy { it.date }
        for (share in newShares) {
            val day = dao.getDayByDate(share.date) ?: ensureDay(share.date, null).first
            val previous = oldShares[share.date]
            val kmStart = when {
                day.kmStart == null -> share.kmAtStart
                previous != null && day.kmStart == previous.kmAtStart -> share.kmAtStart
                else -> minOf(day.kmStart, share.kmAtStart)
            }
            val kmEnd = when {
                day.kmEnd == null -> share.kmAtEnd
                previous != null && day.kmEnd == previous.kmAtEnd -> share.kmAtEnd
                else -> maxOf(day.kmEnd, share.kmAtEnd)
            }
            val estimated = (kmStart == share.kmAtStart && share.startEstimated) ||
                (kmEnd == share.kmAtEnd && share.endEstimated)
            dao.updateDay(day.copy(kmStart = kmStart, kmEnd = kmEnd, kmInterpolated = day.kmInterpolated || estimated))
        }
    }

    /**
     * Tour beenden zum Zeitpunkt [at]. Eine laufende Arbeitszeit wird mit beendet; [km] gilt für beide.
     */
    suspend fun endTour(at: LocalDateTime, km: Int?): EndTourResult = db.withTransaction {
        val tour = dao.getActiveTour() ?: return@withTransaction EndTourResult.NoActiveTour
        val date = at.toLocalDate()
        val minute = at.hour * 60 + at.minute
        val endedShift = if (dao.getRunningShift() != null) endWork(at, km) else null
        val filled = if (dao.getDayByDate(date) == null) missingTourDates(tour.id, date).size else 0
        var day = ensureDay(date, null).first
        day = day.copy(tourId = day.tourId ?: tour.id)
        if (day.endMinute == 24 * 60) day = day.copy(endMinute = maxOf(minute, day.startMinute + 1))
        if (km != null && endedShift == null) {
            day = day.copy(kmEnd = maxOf(day.kmEnd ?: km, km), kmStart = day.kmStart ?: lastKnownKmBefore(date))
        }
        dao.updateDay(day)
        dao.updateTour(tour.copy(ended = true))
        EndTourResult.Ended(endedShift, filled)
    }

    private suspend fun lastKnownKmBefore(date: LocalDate): Int? = dao.getDayBefore(date)?.kmEnd

    /**
     * Übernimmt ein per GPS ermitteltes Land für [date] – aber nur, wenn das Land dort nicht von
     * Hand gewählt wurde. Gibt true zurück, wenn das Land neu eingetragen wurde.
     *
     * @param replaceSingle bei Arbeitsbeginn: Steht für den Tag bisher nur ein (vom Vortag
     *   übernommenes) Land, wird es ersetzt statt ergänzt.
     */
    suspend fun applyDetectedCountry(date: LocalDate, code: String, replaceSingle: Boolean = false): Boolean =
        db.withTransaction {
            val day = dao.getDayByDate(date) ?: return@withTransaction false
            if (day.countryManual) return@withTransaction false
            val route = day.countryRoute
            val newRoute = if (replaceSingle && route.size == 1) listOf(code) else DayCountries.withVisit(route, code)
            if (newRoute == route) return@withTransaction false
            dao.updateDay(day.withCountries(newRoute))
            true
        }

    /**
     * Grenzübertritt während der Arbeitszeit: [code] wird als weiteres Land des Tages eingetragen
     * (Einträge für neu begonnene Tage einer laufenden Schicht werden vorher angelegt). Ein von Hand
     * gewähltes Land bleibt unverändert. Gibt true zurück, wenn sich etwas geändert hat.
     */
    suspend fun recordBorderCrossing(at: LocalDateTime, code: String): Boolean = db.withTransaction {
        syncRunningWork(at)
        applyDetectedCountry(at.toLocalDate(), code)
    }

    /**
     * Von Hand geänderter Text „Reiseanlass und Reiseweg“ (Reisekostenabrechnung); leer oder null =
     * wieder automatisch.
     */
    suspend fun setTravelText(date: LocalDate, text: String?) =
        dao.setTravelText(date, text?.trim()?.takeIf { it.isNotEmpty() })

    /** Land, in dem man zuletzt war (Ende des jüngsten Tages bis [date]). */
    suspend fun lastCountryUpTo(date: LocalDate): String? =
        (dao.getDayByDate(date) ?: dao.getDayBefore(date))?.lastCountry

    // ---------------------------------------------------------------------------------------------
    // Datensicherung
    // ---------------------------------------------------------------------------------------------

    // ---------- Standorte beim Arbeitsende, Meilensteine ----------

    /** Standorte beim Arbeitsende aus Version 5 (werden nicht mehr neu angelegt, bleiben aber erhalten). */
    val stops: Flow<List<StopLocationEntity>> = db.stopDao().observeStops()
    suspend fun getStops(): List<StopLocationEntity> = db.stopDao().getAllStops()

    val milestones: Flow<List<MilestoneEntity>> = db.stopDao().observeMilestones()
    suspend fun getMilestones(): List<MilestoneEntity> = db.stopDao().getAllMilestones()
    suspend fun addMilestones(list: List<MilestoneEntity>) = db.stopDao().insertMilestones(list)

    // ---------- Übernachtungsplätze ----------

    private val stopDao = db.stopDao()

    /** Alle Übernachtungsplätze mit ihren Übernachtungen. */
    val overnightPlaces: Flow<List<OvernightPlace>> =
        combine(stopDao.observePlaces(), stopDao.observeStays()) { places, stays -> group(places, stays) }

    suspend fun getOvernightPlaces(): List<OvernightPlace> = group(stopDao.getAllPlaces(), stopDao.getAllStays())

    private fun group(places: List<OvernightPlaceEntity>, stays: List<OvernightStayEntity>): List<OvernightPlace> {
        val byPlace = stays.groupBy { it.placeId }
        return places.map { OvernightPlace(it, byPlace[it.id].orEmpty()) }
    }

    /**
     * Speichert eine Übernachtung an der Position ([latE5], [lonE5]). Liegt im Umkreis von
     * [Overnights.CLUSTER_RADIUS_M] schon ein Platz, wird sie dem nächstgelegenen zugeordnet
     * (Name, Notizen und Position des Platzes bleiben), sonst entsteht ein neuer Platz.
     * Gibt den Platz zurück.
     */
    suspend fun addOvernightStay(
        date: LocalDate, minute: Int, latE5: Int, lonE5: Int, countryCode: String?,
    ): OvernightPlaceEntity = db.withTransaction {
        val lat = latE5 / 100_000.0
        val lon = lonE5 / 100_000.0
        val near = stopDao.getAllPlaces()
            .map { it to Geo.distanceM(it.lat, it.lon, lat, lon) }
            .filter { it.second <= Overnights.CLUSTER_RADIUS_M }
            .minByOrNull { it.second }?.first
        val place = near ?: OvernightPlaceEntity(latE5 = latE5, lonE5 = lonE5, countryCode = countryCode)
            .let { it.copy(id = stopDao.insertPlace(it)) }
        stopDao.insertStay(OvernightStayEntity(placeId = place.id, date = date, minute = minute, latE5 = latE5, lonE5 = lonE5))
        place
    }

    /** Name, Notizen oder Position eines Platzes ändern. */
    suspend fun updateOvernightPlace(place: OvernightPlaceEntity) = stopDao.updatePlace(place)

    /** Löscht einen Platz mit allen Übernachtungen dort (nur Karte, nicht die Steuer-Zählung). */
    suspend fun deleteOvernightPlace(id: Long) = db.withTransaction {
        for (stay in stopDao.getStaysOfPlace(id)) stopDao.deleteStay(stay.id)
        stopDao.deletePlace(id)
    }

    /** Löscht eine einzelne Übernachtung; war es die letzte des Platzes, verschwindet der Platz. */
    suspend fun deleteOvernightStay(id: Long) = db.withTransaction {
        val stay = stopDao.getStay(id) ?: return@withTransaction
        stopDao.deleteStay(id)
        if (stopDao.getStaysOfPlace(stay.placeId).isEmpty()) stopDao.deletePlace(stay.placeId)
    }

    suspend fun hasOvernightPlaces(): Boolean = stopDao.countPlaces() > 0

    /** Plätze einfügen (z. B. Übernahme der früher berechneten Plätze); neue IDs werden vergeben. */
    suspend fun insertOvernightPlaces(list: List<OvernightPlace>) = db.withTransaction {
        for (p in list) {
            val id = stopDao.insertPlace(p.place.copy(id = 0))
            stopDao.insertStays(p.stays.map { it.copy(id = 0, placeId = id) })
        }
    }

    suspend fun exportData(): BackupData = BackupData(
        dao.getAllTours(), dao.getAllDays(), shifts = dao.getAllShifts(), tracks = db.trackDao().getAllChunks(),
        stops = stopDao.getAllStops(), milestones = stopDao.getAllMilestones(), places = getOvernightPlaces(),
        expenseYears = ExpenseRepository(db).getAll(),
    )

    /**
     * Ersetzt alle Daten durch eine Sicherung. Enthält die Sicherung keine Strecken, Standorte,
     * Meilensteine, Übernachtungsplätze oder Spesentabellen (ältere Formate), bleiben die vorhandenen erhalten.
     */
    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        dao.deleteAllShifts()
        dao.deleteAllDays()
        dao.deleteAllTours()
        dao.insertTours(data.tours)
        dao.insertDays(data.days)
        dao.insertShifts(data.shifts)
        data.tracks?.let { tracks ->
            db.trackDao().deleteAll()
            tracks.chunked(200).forEach { db.trackDao().insertAll(it) }
        }
        data.stops?.let {
            stopDao.deleteAllStops()
            stopDao.insertStops(it)
        }
        data.milestones?.let {
            stopDao.deleteAllMilestones()
            stopDao.insertMilestones(it)
        }
        data.places?.let { places ->
            stopDao.deleteAllStays()
            stopDao.deleteAllPlaces()
            stopDao.insertPlaces(places.map { it.place })
            stopDao.insertStays(places.flatMap { it.stays })
        }
        data.expenseYears?.let { years ->
            val expenseDao = db.expenseDao()
            expenseDao.deleteAllRates()
            expenseDao.deleteAllYears()
            expenseDao.insertYears(years.map { it.year })
            expenseDao.insertRates(years.flatMap { it.rates })
        }
    }
}
