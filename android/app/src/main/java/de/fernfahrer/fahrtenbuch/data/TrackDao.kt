package de.fernfahrer.fahrtenbuch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Zugriff auf die aufgezeichneten GPS-Strecken. */
@Dao
interface TrackDao {
    @Query("SELECT id, trackId, seq, startTime, pointCount, lengthMeters, countries FROM track_chunks ORDER BY trackId, seq")
    fun observeMeta(): Flow<List<TrackChunkMeta>>

    @Query("SELECT * FROM track_chunks WHERE id IN (:ids)")
    suspend fun getChunks(ids: List<Long>): List<TrackChunkEntity>

    @Query("SELECT * FROM track_chunks ORDER BY trackId, seq")
    suspend fun getAllChunks(): List<TrackChunkEntity>

    /** Stücke, die den Zeitraum [from, to) berühren (Sekunden seit 1970, UTC). */
    @Query("SELECT * FROM track_chunks WHERE endTime >= :from AND startTime < :to ORDER BY startTime, trackId, seq")
    suspend fun getChunksBetween(from: Long, to: Long): List<TrackChunkEntity>

    @Query("SELECT * FROM track_chunks ORDER BY endTime DESC LIMIT 1")
    suspend fun getLatestChunk(): TrackChunkEntity?

    @Query("SELECT COUNT(*) FROM track_chunks")
    suspend fun count(): Int

    @Insert
    suspend fun insert(chunk: TrackChunkEntity): Long

    @Insert
    suspend fun insertAll(chunks: List<TrackChunkEntity>)

    /** Gibt die Anzahl geänderter Zeilen zurück (0, falls das Stück inzwischen gelöscht wurde). */
    @Update
    suspend fun update(chunk: TrackChunkEntity): Int

    @Query("DELETE FROM track_chunks")
    suspend fun deleteAll()
}
