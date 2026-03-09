package com.twinmind.app.data.local.dao

import androidx.room.*
import com.twinmind.app.data.local.entity.MeetingSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: MeetingSummary): Long

    @Update
    suspend fun update(summary: MeetingSummary)

    @Query("SELECT * FROM meeting_summaries WHERE sessionId = :sessionId")
    suspend fun getSummaryBySession(sessionId: Long): MeetingSummary?

    @Query("SELECT * FROM meeting_summaries WHERE sessionId = :sessionId")
    fun observeSummary(sessionId: Long): Flow<MeetingSummary?>

    @Query("UPDATE meeting_summaries SET status = :status, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE meeting_summaries SET title = :title, summary = :summary, actionItems = :actionItems, keyPoints = :keyPoints, status = :status, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateContent(
        sessionId: Long,
        title: String,
        summary: String,
        actionItems: String,
        keyPoints: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}
