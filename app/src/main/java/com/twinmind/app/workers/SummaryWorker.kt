package com.twinmind.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.twinmind.app.data.repository.SummaryRepository
import com.twinmind.app.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val summaryRepository: SummaryRepository,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SummaryWorker"
        const val KEY_SESSION_ID = "session_id"
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId == -1L) {
            Log.e(TAG, "No session ID provided")
            return Result.failure()
        }

        Log.d(TAG, "Generating summary for session: $sessionId")

        return try {
            val transcript = transcriptionRepository.getFullTranscript(sessionId)
            if (transcript.isBlank()) {
                Log.e(TAG, "No transcript available for session $sessionId")
                return Result.failure()
            }

            val result = summaryRepository.generateSummaryBlocking(sessionId, transcript)
            summaryRepository.saveSummaryResult(sessionId, result)

            Log.d(TAG, "Summary generated for session $sessionId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating summary for session $sessionId", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
