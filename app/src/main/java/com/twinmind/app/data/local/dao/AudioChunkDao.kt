package com.twinmind.app.data.local.dao

import androidx.room.*
import com.twinmind.app.data.local.entity.AudioChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: AudioChunk): Long

    @Update
    suspend fun update(chunk: AudioChunk)

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getChunksBySession(sessionId: Long): List<AudioChunk>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    fun observeChunksBySession(sessionId: Long): Flow<List<AudioChunk>>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND transcribed = 0 ORDER BY chunkIndex ASC")
    suspend fun getUntranscribedChunks(sessionId: Long): List<AudioChunk>

    @Query("SELECT * FROM audio_chunks WHERE transcribed = 0 AND transcriptionFailed = 1 ORDER BY chunkIndex ASC")
    suspend fun getFailedChunks(): List<AudioChunk>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): AudioChunk?

    @Query("UPDATE audio_chunks SET transcribed = 1, transcriptionFailed = 0 WHERE id = :id")
    suspend fun markTranscribed(id: Long)

    @Query("UPDATE audio_chunks SET transcriptionFailed = 1 WHERE id = :id")
    suspend fun markTranscriptionFailed(id: Long)

    @Query("UPDATE audio_chunks SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE sessionId = :sessionId")
    suspend fun getChunkCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE sessionId = :sessionId AND transcribed = 1")
    suspend fun getTranscribedChunkCount(sessionId: Long): Int

    @Query("SELECT MAX(chunkIndex) FROM audio_chunks WHERE sessionId = :sessionId")
    suspend fun getMaxChunkIndex(sessionId: Long): Int?
}
