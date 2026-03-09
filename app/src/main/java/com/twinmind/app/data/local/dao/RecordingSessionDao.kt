package com.twinmind.app.data.local.dao

import androidx.room.*
import com.twinmind.app.data.local.entity.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecordingSession): Long

    @Update
    suspend fun update(session: RecordingSession)

    @Query("SELECT * FROM recording_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): RecordingSession?

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<RecordingSession?>

    @Query("SELECT * FROM recording_sessions WHERE status = :status")
    suspend fun getSessionsByStatus(status: String): List<RecordingSession>

    @Query("UPDATE recording_sessions SET status = :status, endTime = :endTime, duration = :duration WHERE id = :id")
    suspend fun updateSessionStatus(id: Long, status: String, endTime: Long?, duration: Long)

    @Query("UPDATE recording_sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE recording_sessions SET totalChunks = :total, transcribedChunks = :transcribed WHERE id = :id")
    suspend fun updateChunkCounts(id: Long, total: Int, transcribed: Int)

    @Query("UPDATE recording_sessions SET summaryStatus = :status WHERE id = :id")
    suspend fun updateSummaryStatus(id: Long, status: String)

    @Delete
    suspend fun delete(session: RecordingSession)
}
