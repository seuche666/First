package de.fernfahrer.fahrtenbuch.data

import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint
import kotlinx.coroutines.flow.Flow

/** Speichern und Laden der GPS-Strecken für die Karte. */
class TrackRepository(db: AppDatabase) {
    private val dao = db.trackDao()

    /** Alle Streckenstücke ohne Punkte; meldet jede Änderung. */
    val meta: Flow<List<TrackChunkMeta>> = dao.observeMeta()

    /** Speichert ein Stück und gibt seine id zurück (legt es neu an, falls es inzwischen gelöscht wurde). */
    suspend fun save(chunk: TrackChunkEntity): Long {
        if (chunk.id != 0L && dao.update(chunk) > 0) return chunk.id
        return dao.insert(chunk.copy(id = 0))
    }

    suspend fun load(ids: List<Long>): List<TrackChunkEntity> =
        ids.chunked(500).flatMap { dao.getChunks(it) }

    suspend fun count(): Int = dao.count()

    /** Letzter aufgezeichneter Punkt, falls er höchstens [maxAgeSec] alt ist. */
    suspend fun lastPoint(nowSec: Long = System.currentTimeMillis() / 1000, maxAgeSec: Long = 1800): TrackPoint? {
        val chunk = dao.getLatestChunk() ?: return null
        val d = runCatching { TrackCodec.decode(chunk.data) }.getOrNull() ?: return null
        if (d.size == 0) return null
        val p = d.point(d.size - 1)
        return if (nowSec - p.timeSec <= maxAgeSec) p else null
    }

    suspend fun deleteAll() = dao.deleteAll()
}
