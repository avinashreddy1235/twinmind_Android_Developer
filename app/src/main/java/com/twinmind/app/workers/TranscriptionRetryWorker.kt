package com.twinmind.app.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.twinmind.app.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TranscriptionRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "TranscriptionRetryWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Retrying failed transcriptions...")

        return try {
            val successCount = transcriptionRepository.retryFailedChunks()
            Log.d(TAG, "Retried transcriptions: $successCount succeeded")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying transcriptions", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
