package com.twinmind.app.data.remote

import com.twinmind.app.domain.model.SummaryResult
import kotlinx.coroutines.flow.Flow

interface SummaryApiService {
    fun generateSummary(transcript: String): Flow<SummaryResult>
    suspend fun generateSummaryBlocking(transcript: String): SummaryResult
}
