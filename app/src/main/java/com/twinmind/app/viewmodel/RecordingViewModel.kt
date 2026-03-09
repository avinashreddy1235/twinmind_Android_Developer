package com.twinmind.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.twinmind.app.data.local.entity.TranscriptChunk
import com.twinmind.app.data.repository.RecordingRepository
import com.twinmind.app.data.repository.TranscriptionRepository
import com.twinmind.app.domain.model.RecordingState
import com.twinmind.app.service.AudioRecordingService
import com.twinmind.app.service.RecordingStateManager
import com.twinmind.app.workers.SummaryWorker
import com.twinmind.app.workers.TranscriptionRetryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val application: Application,
    private val recordingRepository: RecordingRepository,
    private val transcriptionRepository: TranscriptionRepository,
    val stateManager: RecordingStateManager
) : AndroidViewModel(application) {

    val recordingState: StateFlow<RecordingState> = stateManager.state

    private val _transcripts = MutableStateFlow<List<TranscriptChunk>>(emptyList())
    val transcripts: StateFlow<List<TranscriptChunk>> = _transcripts.asStateFlow()

    private var transcriptCollectorJob: kotlinx.coroutines.Job? = null

    fun observeTranscripts(sessionId: Long) {
        transcriptCollectorJob?.cancel()
        transcriptCollectorJob = viewModelScope.launch {
            transcriptionRepository.observeTranscripts(sessionId).collect { chunks ->
                _transcripts.value = chunks
            }
        }
    }

    fun startRecording(sessionId: Long) {
        AudioRecordingService.startRecording(application, sessionId)
        observeTranscripts(sessionId)
    }

    fun stopRecording() {
        AudioRecordingService.stopRecording(application)
    }

    fun pauseRecording() {
        AudioRecordingService.pauseRecording(application)
    }

    fun resumeRecording() {
        AudioRecordingService.resumeRecording(application)
    }

    fun retryFailedTranscriptions() {
        val workRequest = OneTimeWorkRequestBuilder<TranscriptionRetryWorker>().build()
        WorkManager.getInstance(application).enqueue(workRequest)
    }

    fun requestSummaryGeneration(sessionId: Long) {
        val workRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(workDataOf(SummaryWorker.KEY_SESSION_ID to sessionId))
            .build()
        WorkManager.getInstance(application).enqueue(workRequest)
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        transcriptCollectorJob?.cancel()
    }
}
