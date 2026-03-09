package com.twinmind.app.data.repository

import com.google.gson.Gson
import com.twinmind.app.data.local.dao.RecordingSessionDao
import com.twinmind.app.data.local.dao.SummaryDao
import com.twinmind.app.data.local.entity.MeetingSummary
import com.twinmind.app.data.local.entity.RecordingSession
import com.twinmind.app.data.remote.SummaryApiService
import com.twinmind.app.domain.model.SummaryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val summaryDao: SummaryDao,
    private val sessionDao: RecordingSessionDao,
    private val summaryApiService: SummaryApiService,
    private val gson: Gson
) {
    fun observeSummary(sessionId: Long): Flow<MeetingSummary?> =
        summaryDao.observeSummary(sessionId)

    suspend fun getSummary(sessionId: Long): MeetingSummary? =
        summaryDao.getSummaryBySession(sessionId)

    fun generateSummaryStream(sessionId: Long, transcript: String): Flow<SummaryResult> =
        summaryApiService.generateSummary(transcript)
            .onStart {
                ensureSummaryExists(sessionId)
                summaryDao.updateStatus(sessionId, MeetingSummary.STATUS_GENERATING)
                sessionDao.updateSummaryStatus(sessionId, RecordingSession.SUMMARY_GENERATING)
            }
            .catch { e ->
                summaryDao.updateStatus(sessionId, MeetingSummary.STATUS_FAILED)
                sessionDao.updateSummaryStatus(sessionId, RecordingSession.SUMMARY_FAILED)
                throw e
            }

    suspend fun generateSummaryBlocking(sessionId: Long, transcript: String): SummaryResult {
        ensureSummaryExists(sessionId)
        summaryDao.updateStatus(sessionId, MeetingSummary.STATUS_GENERATING)
        sessionDao.updateSummaryStatus(sessionId, RecordingSession.SUMMARY_GENERATING)

        return try {
            val result = summaryApiService.generateSummaryBlocking(transcript)
            saveSummaryResult(sessionId, result)
            result
        } catch (e: Exception) {
            summaryDao.updateStatus(sessionId, MeetingSummary.STATUS_FAILED)
            sessionDao.updateSummaryStatus(sessionId, RecordingSession.SUMMARY_FAILED)
            throw e
        }
    }

    suspend fun saveSummaryResult(sessionId: Long, result: SummaryResult) {
        summaryDao.updateContent(
            sessionId = sessionId,
            title = result.title,
            summary = result.summary,
            actionItems = gson.toJson(result.actionItems),
            keyPoints = gson.toJson(result.keyPoints),
            status = MeetingSummary.STATUS_COMPLETED
        )
        sessionDao.updateSummaryStatus(sessionId, RecordingSession.SUMMARY_COMPLETED)

        // Also update session title if available
        if (result.title.isNotBlank()) {
            sessionDao.updateTitle(sessionId, result.title)
        }
    }

    private suspend fun ensureSummaryExists(sessionId: Long) {
        if (summaryDao.getSummaryBySession(sessionId) == null) {
            summaryDao.insert(MeetingSummary(sessionId = sessionId))
        }
    }
}
