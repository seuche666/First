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
import de.fernfahrer.fahrtenbuch.data.MapSettings
import de.fernfahrer.fahrtenbuch.data.OvernightPlace
import de.fernfahrer.fahrtenbuch.data.OvernightPlaceEntity
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
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
    /** Geschätzte Liter Diesel aus den Tacho-km (eingetragene Kilometerstände), gerundet. */
    val dieselLiters: Long = 0,
)

/** Kartenausschnitt, damit die Karte beim Wechsel der Reiter an derselben Stelle bleibt. */
data class Camera(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Lädt die Strecken, bereitet sie für den Nebel auf ([FogGeometry]), berechnet die Statistik und
 * stellt die gespeicherten Übernachtungsplätze bereit (Bearbeiten über [repo]).
 * Bei neuen Punkten während der Fahrt werden nur die geänderten Stücke neu gelesen.
 */
class MapViewModel(
    private val meta: Flow<List<TrackChunkMeta>>,
    private val load: suspend (List<Long>) -> List<TrackChunkEntity>,
    days: Flow<List<ClassifiedDay>> = flowOf(emptyList()),
    places: Flow<List<OvernightPlace>> = emptyFlow(),
    /** Verbrauch in Litern pro 100 km. */
    fuelPer100Km: Flow<Double> = flowOf(MapSettings.DEFAULT_FUEL),
    private val repo: Repository? = null,
) : ViewModel() {

    private val _geometry = MutableStateFlow<FogGeometry?>(null)
    val geometry: StateFlow<FogGeometry?> = _geometry.asStateFlow()

    private val trackStats = MutableStateFlow(MapStats())

    /** Statistik: Strecken (GPS) und Diesel aus den Tacho-km. */
    val stats: StateFlow<MapStats> = combine(trackStats, days, fuelPer100Km) { t, d, fuel ->
        t.copy(dieselLiters = dieselLiters(d, fuel))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapStats())

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    var camera: Camera? = null

    /** Ausgeblendete Hinweise (bis zum nächsten Start der App). */
    val dismissedHints = MutableStateFlow<Set<String>>(emptySet())

    private val cache = HashMap<Long, FogGeometry.ChunkGeom>()

    /** Gespeicherte Übernachtungsplätze (fest; entstehen nur beim „Arbeit beenden“ mit Haken). */
    val overnights: StateFlow<List<OvernightPlace>> =
        places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        removed.forEach { cache.remove(it) }

        for (chunk in load(changed.map { it.id })) {
            try {
                val d = TrackCodec.decode(chunk.data)
                if (d.size == 0) continue
                cache[chunk.id] = FogGeometry.chunk(chunk.id, chunk.trackId, chunk.seq, d.latE5, d.lonE5)
            } catch (e: Exception) {
                Log.w(TAG, "Streckenstück ${chunk.id} ist beschädigt und wird übersprungen", e)
            }
        }

        _geometry.value = FogGeometry(cache.values.toList())
        trackStats.value = MapStats(
            countries = TrackChunks.countriesOf(metaList),
            distanceKm = metaList.sumOf { it.lengthMeters } / 1000.0,
        )
    }

    fun dismissHint(id: String) {
        dismissedHints.value = dismissedHints.value + id
    }

    // ---------- Übernachtungsplätze bearbeiten ----------

    private fun edit(block: suspend Repository.() -> Unit) {
        val r = repo ?: return
        viewModelScope.launch {
            try {
                r.block()
            } catch (e: Exception) {
                Log.e(TAG, "Übernachtungsplatz konnte nicht gespeichert werden", e)
            }
        }
    }

    /** Name oder Notizen speichern. */
    fun updatePlace(place: OvernightPlaceEntity) = edit { updateOvernightPlace(place) }

    /** Platz an eine neue Position versetzen. */
    fun movePlace(place: OvernightPlaceEntity, lat: Double, lon: Double, countryCode: String?) = edit {
        updateOvernightPlace(
            place.copy(
                latE5 = Math.round(lat * 100_000).toInt(),
                lonE5 = Math.round(lon * 100_000).toInt(),
                countryCode = countryCode ?: place.countryCode,
            ),
        )
    }

    fun deletePlace(id: Long) = edit { deleteOvernightPlace(id) }

    fun deleteStay(id: Long) = edit { deleteOvernightStay(id) }

    companion object {
        private const val TAG = "MapViewModel"

        /** Liter Diesel aus allen eingetragenen Tacho-km, auf ganze Liter gerundet. */
        fun dieselLiters(days: List<ClassifiedDay>, per100Km: Double): Long =
            Math.round(days.sumOf { (it.day.distanceKm ?: 0).toLong() } * per100Km / 100.0)

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                MapViewModel(
                    app.tracks.meta, app.tracks::load, app.repository.classifiedDays, app.repository.overnightPlaces,
                    app.settings.map.map { it.fuelPer100Km }.distinctUntilChanged(), app.repository,
                )
            }
        }
    }
}
