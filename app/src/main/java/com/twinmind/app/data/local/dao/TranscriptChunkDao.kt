package com.twinmind.app.data.local.dao

import androidx.room.*
import com.twinmind.app.data.local.entity.TranscriptChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: TranscriptChunk): Long

    @Query("SELECT * FROM transcript_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getTranscriptsBySession(sessionId: Long): List<TranscriptChunk>

    @Query("SELECT * FROM transcript_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    fun observeTranscriptsBySession(sessionId: Long): Flow<List<TranscriptChunk>>

    @Query("SELECT GROUP_CONCAT(text, ' ') FROM transcript_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getFullTranscript(sessionId: Long): String?

    @Query("DELETE FROM transcript_chunks WHERE sessionId = :sessionId AND chunkIndex = :chunkIndex")
    suspend fun deleteByChunkIndex(sessionId: Long, chunkIndex: Int)
}
