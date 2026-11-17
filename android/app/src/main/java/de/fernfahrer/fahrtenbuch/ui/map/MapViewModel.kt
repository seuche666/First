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
import de.fernfahrer.fahrtenbuch.domain.geo.CoverageRaster
import de.fernfahrer.fahrtenbuch.domain.geo.FogGeometry
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Zahlen unter der Karte. */
data class MapStats(
    /** Aufgedeckte Fläche in km²; null solange noch gerechnet wird. */
    val areaKm2: Double? = null,
    val countries: Set<String> = emptySet(),
    val distanceKm: Double = 0.0,
)

/** Kartenausschnitt, damit die Karte beim Wechsel der Reiter an derselben Stelle bleibt. */
data class Camera(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Lädt die Strecken, bereitet sie für den Nebel auf ([FogGeometry]) und berechnet die Statistik.
 * Bei neuen Punkten während der Fahrt werden nur die geänderten Stücke neu gelesen.
 */
class MapViewModel(
    private val meta: Flow<List<TrackChunkMeta>>,
    corridor: Flow<Int>,
    private val load: suspend (List<Long>) -> List<TrackChunkEntity>,
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
    private var raster: CoverageRaster? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            combine(meta, corridor.distinctUntilChanged()) { m, c -> m to c }
                .conflate()
                .collect { (m, c) ->
                    try {
                        update(m, c)
                    } catch (e: Exception) {
                        Log.e(TAG, "Strecken konnten nicht geladen werden", e)
                    } finally {
                        _loading.value = false
                    }
                }
        }
    }

    private suspend fun update(metaList: List<TrackChunkMeta>, corridorM: Int) {
        val ids = metaList.map { it.id }.toHashSet()
        val removed = cache.keys.filter { it !in ids }
        val changed = metaList.filter { m -> cache[m.id]?.pointCount != m.pointCount }
        val shrunk = changed.any { m -> (cache[m.id]?.pointCount ?: 0) > m.pointCount }
        removed.forEach { cache.remove(it) }

        val fresh = ArrayList<FogGeometry.ChunkGeom>()
        for (chunk in load(changed.map { it.id })) {
            try {
                val d = TrackCodec.decode(chunk.data)
                if (d.size == 0) continue
                FogGeometry.chunk(chunk.id, chunk.trackId, chunk.seq, d.latE5, d.lonE5).also {
                    cache[chunk.id] = it
                    fresh += it
                }
            } catch (e: Exception) {
                Log.w(TAG, "Streckenstück ${chunk.id} ist beschädigt und wird übersprungen", e)
            }
        }

        _geometry.value = FogGeometry(cache.values.toList())

        // Fläche: bei neuen Punkten nur ergänzen, sonst (gelöscht, andere Breite) neu berechnen
        var r = raster
        val rebuild = r == null || r.corridorM != corridorM || removed.isNotEmpty() || shrunk
        if (rebuild) {
            if (r == null || r.corridorM != corridorM) _stats.value = _stats.value.copy(areaKm2 = null)
            r = CoverageRaster(corridorM)
            cache.values.forEach { addToRaster(r, it) }
            raster = r
        } else {
            fresh.forEach { addToRaster(r!!, it) }
        }
        _stats.value = MapStats(
            areaKm2 = r!!.areaKm2,
            countries = TrackChunks.countriesOf(metaList),
            distanceKm = metaList.sumOf { it.lengthMeters } / 1000.0,
        )
    }

    /** Fügt die feinste Stufe eines Stücks hinzu (mehrfaches Hinzufügen ändert nichts). */
    private fun addToRaster(r: CoverageRaster, c: FogGeometry.ChunkGeom) {
        val level = FogGeometry.LEVELS - 1
        val n = c.size(level)
        val xs = DoubleArray(n) { c.x(level, it) }
        val ys = DoubleArray(n) { c.y(level, it) }
        r.addPolyline(xs, ys)
    }

    fun dismissHint(id: String) {
        dismissedHints.value = dismissedHints.value + id
    }

    companion object {
        private const val TAG = "MapViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                MapViewModel(app.tracks.meta, app.settings.map.map { it.corridorMeters }, app.tracks::load)
            }
        }
    }
}
