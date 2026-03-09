package com.twinmind.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.app.data.local.entity.RecordingSession
import com.twinmind.app.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    val sessions: StateFlow<List<RecordingSession>> = recordingRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNewSession(): Long {
        var sessionId = -1L
        viewModelScope.launch {
            sessionId = recordingRepository.createSession()
        }
        return sessionId
    }

    suspend fun createSession(): Long {
        return recordingRepository.createSession()
    }

    fun deleteSession(session: RecordingSession) {
        viewModelScope.launch {
            recordingRepository.deleteSession(session)
        }
    }
}
