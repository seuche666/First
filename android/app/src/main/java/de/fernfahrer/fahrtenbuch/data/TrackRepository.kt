package de.fernfahrer.fahrtenbuch.data

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

    suspend fun deleteAll() = dao.deleteAll()
}
