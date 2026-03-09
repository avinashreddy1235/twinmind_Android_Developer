package com.twinmind.app.ui.recording

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twinmind.app.domain.model.RecordingState
import com.twinmind.app.ui.theme.*
import com.twinmind.app.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    onViewSummary: (Long) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.observeTranscripts(sessionId)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recording",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Timer Display
            TimerDisplay(
                elapsedMs = recordingState.elapsedTimeMs,
                isRecording = recordingState.isRecording,
                isPaused = recordingState.isPaused
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status indicator
            StatusIndicator(state = recordingState)

            Spacer(modifier = Modifier.height(8.dp))

            // Silence warning
            AnimatedVisibility(
                visible = recordingState.silenceWarning,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentOrange.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "No audio detected - Check microphone",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentOrange
                        )
                    }
                }
            }

            // Error message
            recordingState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Control Buttons
            RecordingControls(
                state = recordingState,
                onStart = { viewModel.startRecording(sessionId) },
                onStop = { viewModel.stopRecording() },
                onPause = { viewModel.pauseRecording() },
                onResume = { viewModel.resumeRecording() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Generate Summary button (shown when stopped with transcripts)
            if (!recordingState.isRecording && transcripts.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.requestSummaryGeneration(sessionId)
                        onViewSummary(sessionId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary
                    )
                ) {
                    Icon(Icons.Filled.Summarize, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Summary", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Transcript section
            if (transcripts.isNotEmpty()) {
                Text(
                    text = "Live Transcript",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transcripts) { chunk ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Chunk ${chunk.chunkIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chunk.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDisplay(
    elapsedMs: Long,
    isRecording: Boolean,
    isPaused: Boolean
) {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeStr = String.format("%02d:%02d", minutes, seconds)

    // Pulsing animation for recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording && !isPaused) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRecording && !isPaused) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = if (isRecording && !isPaused) {
                            listOf(
                                RecordingRed.copy(alpha = glowAlpha),
                                Primary.copy(alpha = glowAlpha)
                            )
                        } else {
                            listOf(
                                TextTertiary.copy(alpha = 0.3f),
                                TextTertiary.copy(alpha = 0.3f)
                            )
                        }
                    ),
                    shape = CircleShape
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DarkSurfaceVariant,
                            DarkSurface
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeStr,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 2.sp
                )
                if (isRecording && !isPaused) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RecordingRed)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(state: RecordingState) {
    val statusColor = when {
        state.isRecording && !state.isPaused -> RecordingRed
        state.isPaused -> PausedAmber
        else -> StoppedGray
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.labelLarge,
            color = statusColor
        )
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isRecording) {
            // Pause/Resume button
            FilledIconButton(
                onClick = if (state.isPaused) onResume else onPause,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Stop button (large, red)
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = RecordingRed,
                    contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            // Record button (large, gradient-like)
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Primary,
                    contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
