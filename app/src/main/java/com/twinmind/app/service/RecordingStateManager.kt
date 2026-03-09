package com.twinmind.app.service

import com.twinmind.app.domain.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStateManager @Inject constructor() {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun updateState(transform: RecordingState.() -> RecordingState) {
        _state.value = _state.value.transform()
    }

    fun setState(newState: RecordingState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = RecordingState()
    }
}
