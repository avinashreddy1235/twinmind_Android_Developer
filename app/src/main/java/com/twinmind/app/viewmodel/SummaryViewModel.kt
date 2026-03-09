package com.twinmind.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinmind.app.data.local.entity.MeetingSummary
import com.twinmind.app.data.repository.SummaryRepository
import com.twinmind.app.data.repository.TranscriptionRepository
import com.twinmind.app.domain.model.SummaryResult
import com.twinmind.app.workers.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val isLoading: Boolean = false,
    val summary: SummaryResult? = null,
    val error: String? = null,
    val isStreaming: Boolean = false
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val application: Application,
    private val summaryRepository: SummaryRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val gson: Gson
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    fun loadSummary(sessionId: Long) {
        viewModelScope.launch {
            // First check if summary already exists in DB
            summaryRepository.observeSummary(sessionId).collect { summary ->
                if (summary != null) {
                    _uiState.value = SummaryUiState(
                        isLoading = summary.status == MeetingSummary.STATUS_GENERATING,
                        summary = mapToResult(summary),
                        error = if (summary.status == MeetingSummary.STATUS_FAILED) "Summary generation failed" else null
                    )
                }
            }
        }
    }

    fun generateSummary(sessionId: Long) {
        viewModelScope.launch {
            _uiState.value = SummaryUiState(isLoading = true, isStreaming = true)

            try {
                val transcript = transcriptionRepository.getFullTranscript(sessionId)
                if (transcript.isBlank()) {
                    _uiState.value = SummaryUiState(
                        error = "No transcript available. Please wait for transcription to complete."
                    )
                    return@launch
                }

                // Also enqueue a WorkManager job as backup
                val workRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
                    .setInputData(workDataOf(SummaryWorker.KEY_SESSION_ID to sessionId))
                    .build()
                WorkManager.getInstance(application).enqueue(workRequest)

                // Stream summary in UI
                summaryRepository.generateSummaryStream(sessionId, transcript)
                    .catch { e ->
                        _uiState.value = SummaryUiState(error = e.message ?: "Failed to generate summary")
                    }
                    .collect { result ->
                        _uiState.value = SummaryUiState(
                            isLoading = false,
                            summary = result,
                            isStreaming = true
                        )
                    }

                // Streaming complete
                _uiState.update { it.copy(isStreaming = false) }

                // Save final result
                _uiState.value.summary?.let {
                    summaryRepository.saveSummaryResult(sessionId, it)
                }
            } catch (e: Exception) {
                _uiState.value = SummaryUiState(error = e.message ?: "Failed to generate summary")
            }
        }
    }

    private fun mapToResult(summary: MeetingSummary): SummaryResult {
        val actionItemsType = object : TypeToken<List<String>>() {}.type
        val actionItems: List<String> = try {
            gson.fromJson(summary.actionItems, actionItemsType) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val keyPoints: List<String> = try {
            gson.fromJson(summary.keyPoints, actionItemsType) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        return SummaryResult(
            title = summary.title,
            summary = summary.summary,
            actionItems = actionItems,
            keyPoints = keyPoints
        )
    }
}
