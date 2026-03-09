package com.twinmind.app.domain.model

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val sessionId: Long = -1L,
    val elapsedTimeMs: Long = 0L,
    val statusMessage: String = "Ready",
    val chunkIndex: Int = 0,
    val silenceWarning: Boolean = false,
    val errorMessage: String? = null
)

data class SummaryResult(
    val title: String = "",
    val summary: String = "",
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList()
)
