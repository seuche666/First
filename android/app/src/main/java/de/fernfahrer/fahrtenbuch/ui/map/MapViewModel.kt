package de.fernfahrer.fahrtenbuch.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.TrackChunkEntity
import de.fernfahrer.fahrtenbuch.data.TrackChunkMeta
import de.fernfahrer.fahrtenbuch.data.TrackChunks
import de.fernfahrer.fahrtenbuch.data.StopLocationEntity
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** Zahlen unter der Karte. */
data class MapStats(
    val countries: Set<String> = emptySet(),
    val distanceKm: Double = 0.0,
)

/** Kartenausschnitt, damit die Karte beim Wechsel der Reiter an derselben Stelle bleibt. */
data class Camera(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Lädt die Strecken, bereitet sie für den Nebel auf ([FogGeometry]), berechnet die Statistik und
 * die Übernachtungsplätze.
 * Bei neuen Punkten während der Fahrt werden nur die geänderten Stücke neu gelesen.
 */
class MapViewModel(
    private val meta: Flow<List<TrackChunkMeta>>,
    private val load: suspend (List<Long>) -> List<TrackChunkEntity>,
    days: Flow<List<ClassifiedDay>> = emptyFlow(),
    stops: Flow<List<StopLocationEntity>> = emptyFlow(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _geometry = MutableStateFlow<FogGeometry?>(null)
    val geometry: StateFlow<FogGeometry?> = _geometry.asStateFlow()

    private val _stats = MutableStateFlow(MapStats())
    val stats: StateFlow<MapStats> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    var camera: Camera? = null

    /** Ausgeblendete Hinweise (bis zum nächsten Start der App). */
    val dismissedHints = MutableStateFlow<Set<String>>(emptySet())

    private val cache = HashMap<Long, FogGeometry.ChunkGeom>()

    /** Letzter GPS-Punkt je Tag und Streckenstück (für Übernachtungen ohne gespeicherten Standort). */
    private val dayEnds = HashMap<Long, Map<LocalDate, Overnights.Spot>>()
    private val trackDayEnds = MutableStateFlow<Map<LocalDate, Overnights.Spot>>(emptyMap())

    /** Übernachtungsplätze, zu Stammplätzen (Umkreis 200 m) zusammengefasst. */
    val overnights: StateFlow<List<Overnights.Cluster>> =
        combine(days, stops, trackDayEnds) { d, st, ends ->
            val stopsByDate = st.groupBy { it.date }.mapValues { (_, list) ->
                val last = list.maxBy { it.minute }
                Overnights.Spot(last.date, last.latE5, last.lonE5, last.countryCode)
            }
            val nights = d.filter { it.overnight }.map { it.day.date }
            Overnights.cluster(Overnights.spots(nights, stopsByDate, ends))
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.Default) {
            meta.conflate().collect { m ->
                try {
                    update(m)
                } catch (e: Exception) {
                    Log.e(TAG, "Strecken konnten nicht geladen werden", e)
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    private suspend fun update(metaList: List<TrackChunkMeta>) {
        val ids = metaList.map { it.id }.toHashSet()
        val removed = cache.keys.filter { it !in ids }
        val changed = metaList.filter { m -> cache[m.id]?.pointCount != m.pointCount }
        removed.forEach {
            cache.remove(it)
            dayEnds.remove(it)
        }

        for (chunk in load(changed.map { it.id })) {
            try {
                val d = TrackCodec.decode(chunk.data)
                if (d.size == 0) continue
                cache[chunk.id] = FogGeometry.chunk(chunk.id, chunk.trackId, chunk.seq, d.latE5, d.lonE5)
                dayEnds[chunk.id] = lastPointPerDay(d)
            } catch (e: Exception) {
                Log.w(TAG, "Streckenstück ${chunk.id} ist beschädigt und wird übersprungen", e)
            }
        }

        _geometry.value = FogGeometry(cache.values.toList())

        if (changed.isNotEmpty() || removed.isNotEmpty()) {
            val merged = HashMap<LocalDate, Overnights.Spot>()
            for (m in dayEnds.values) for ((date, spot) in m) {
                val prev = merged[date]
                if (prev == null || spot.timeKey > prev.timeKey) merged[date] = spot
            }
            trackDayEnds.value = merged
        }
        _stats.value = MapStats(
            countries = TrackChunks.countriesOf(metaList),
            distanceKm = metaList.sumOf { it.lengthMeters } / 1000.0,
        )
    }

    private fun lastPointPerDay(d: TrackCodec.Decoded): Map<LocalDate, Overnights.Spot> {
        val out = HashMap<LocalDate, Overnights.Spot>()
        val times = HashMap<LocalDate, Long>()
        for (i in 0 until d.size) {
            val date = Instant.ofEpochSecond(d.timeSec[i]).atZone(zone).toLocalDate()
            if ((times[date] ?: Long.MIN_VALUE) <= d.timeSec[i]) {
                times[date] = d.timeSec[i]
                out[date] = Overnights.Spot(date, d.latE5[i], d.lonE5[i], null, d.timeSec[i])
            }
        }
        return out
    }

    fun dismissHint(id: String) {
        dismissedHints.value = dismissedHints.value + id
    }

    companion object {
        private const val TAG = "MapViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                MapViewModel(app.tracks.meta, app.tracks::load, app.repository.classifiedDays, app.repository.stops)
            }
        }
    }
}
