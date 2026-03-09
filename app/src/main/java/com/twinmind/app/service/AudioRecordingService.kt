package com.twinmind.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.twinmind.app.MainActivity
import com.twinmind.app.R
import com.twinmind.app.data.local.entity.AudioChunk
import com.twinmind.app.data.local.entity.RecordingSession
import com.twinmind.app.data.repository.RecordingRepository
import com.twinmind.app.domain.model.RecordingState
import com.twinmind.app.workers.ChunkUploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AudioRecordingService : Service() {

    companion object {
        const val TAG = "AudioRecordingService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "recording_channel"
        const val CHANNEL_NAME = "Recording"

        const val ACTION_START = "com.twinmind.app.START_RECORDING"
        const val ACTION_STOP = "com.twinmind.app.STOP_RECORDING"
        const val ACTION_PAUSE = "com.twinmind.app.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.twinmind.app.RESUME_RECORDING"

        const val EXTRA_SESSION_ID = "session_id"

        // Audio config
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Chunking
        const val CHUNK_DURATION_MS = 30_000L
        const val OVERLAP_DURATION_MS = 2_000L

        // Silence detection
        const val SILENCE_THRESHOLD = 200
        const val SILENCE_WARNING_MS = 10_000L

        // Storage check
        const val MIN_STORAGE_MB = 50L

        fun startRecording(context: Context, sessionId: Long) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pauseRecording(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeRecording(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var stateManager: RecordingStateManager
    @Inject lateinit var audioManager: AudioManager
    @Inject lateinit var telephonyManager: TelephonyManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var recordingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var currentSessionId: Long = -1L
    private var chunkIndex = 0

    // Timer
    private var recordingStartTimeMs = 0L
    private var accumulatedTimeMs = 0L
    private var pauseStartTimeMs = 0L

    // Chunks
    private var currentChunkStartTime = 0L
    private var currentChunkFile: File? = null
    private var currentChunkStream: FileOutputStream? = null
    private var overlapBuffer: ByteArray? = null

    // Silence detection
    private var silenceStartTime = 0L
    private var isSilenceWarning = false

    // Edge case handlers
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var phoneCallHandler: PhoneCallHandler
    private var pauseReason: String? = null

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    val name = if (state == 1) "connected" else "disconnected"
                    Log.d(TAG, "Wired headset $name")
                    updateNotification("Audio source changed - Wired headset $name")
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                    val name = if (state == BluetoothAdapter.STATE_CONNECTED) "connected" else "disconnected"
                    Log.d(TAG, "Bluetooth $name")
                    updateNotification("Audio source changed - Bluetooth $name")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        audioFocusManager = AudioFocusManager(this, audioManager) { focusChange ->
            handleAudioFocusChange(focusChange)
        }

        phoneCallHandler = PhoneCallHandler(this, telephonyManager) { inCall ->
            if (inCall) {
                pauseForReason("Paused - Phone call")
            } else if (pauseReason == "Paused - Phone call") {
                resumeFromPause()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                if (sessionId != -1L) {
                    startRecording(sessionId)
                }
            }
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseForReason("Paused")
            ACTION_RESUME -> resumeFromPause()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(sessionId: Long) {
        if (isRecording) return

        // Check storage
        if (!hasEnoughStorage()) {
            stateManager.updateState {
                copy(errorMessage = "Recording stopped - Low storage")
            }
            stopSelf()
            return
        }

        currentSessionId = sessionId
        chunkIndex = 0

        // Request audio focus
        audioFocusManager.requestFocus()

        // Register phone call handler
        phoneCallHandler.register()

        // Register headset receiver
        registerHeadsetReceiver()

        // Start foreground
        startForeground(NOTIFICATION_ID, createNotification("Recording..."))

        // Initialize audio record
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            stateManager.updateState {
                copy(errorMessage = "Failed to initialize microphone")
            }
            stopSelf()
            return
        }

        isRecording = true
        isPaused = false
        recordingStartTimeMs = System.currentTimeMillis()
        accumulatedTimeMs = 0L

        stateManager.setState(
            RecordingState(
                isRecording = true,
                sessionId = sessionId,
                statusMessage = "Recording..."
            )
        )

        // Update session status
        serviceScope.launch {
            recordingRepository.updateSessionStatus(
                sessionId, RecordingSession.STATUS_RECORDING, null, 0
            )
        }

        // Start recording thread
        startAudioCapture()

        // Start timer
        startTimer()
    }

    private fun startAudioCapture() {
        audioRecord?.startRecording()

        recordingJob = serviceScope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val buffer = ShortArray(bufferSize / 2)

            // Start first chunk
            startNewChunk()

            while (isActive && isRecording) {
                if (isPaused) {
                    delay(100)
                    continue
                }

                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readCount > 0) {
                    // Write to current chunk file
                    writeToChunk(buffer, readCount)

                    // Silence detection
                    checkSilence(buffer, readCount)

                    // Check chunk duration
                    val chunkElapsed = System.currentTimeMillis() - currentChunkStartTime
                    if (chunkElapsed >= CHUNK_DURATION_MS) {
                        // Save overlap buffer (last 2 seconds)
                        saveOverlapBuffer(buffer, readCount)
                        // Finalize current chunk and start new one
                        finalizeChunk()
                        startNewChunk()
                    }

                    // Check storage
                    if (!hasEnoughStorage()) {
                        withContext(Dispatchers.Main) {
                            stateManager.updateState {
                                copy(errorMessage = "Recording stopped - Low storage")
                            }
                        }
                        stopRecording()
                        break
                    }
                }
            }
        }
    }

    private fun startNewChunk() {
        val chunkDir = File(filesDir, "recordings/$currentSessionId")
        chunkDir.mkdirs()

        val chunkFile = File(chunkDir, "chunk_${currentSessionId}_$chunkIndex.wav")
        currentChunkFile = chunkFile
        currentChunkStream = FileOutputStream(chunkFile)
        currentChunkStartTime = System.currentTimeMillis()

        // Write WAV header placeholder (will update later)
        writeWavHeader(currentChunkStream!!, 0)

        // Write overlap from previous chunk
        overlapBuffer?.let { overlap ->
            currentChunkStream?.write(overlap)
            overlapBuffer = null
        }
    }

    private fun writeToChunk(buffer: ShortArray, readCount: Int) {
        val byteBuffer = ByteArray(readCount * 2)
        for (i in 0 until readCount) {
            val value = buffer[i]
            byteBuffer[i * 2] = (value.toInt() and 0xFF).toByte()
            byteBuffer[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        currentChunkStream?.write(byteBuffer)
    }

    private fun saveOverlapBuffer(buffer: ShortArray, readCount: Int) {
        // Calculate overlap samples (2 seconds of audio)
        val overlapSamples = SAMPLE_RATE * (OVERLAP_DURATION_MS / 1000).toInt()
        val overlapBytes = overlapSamples * 2 // 16-bit mono

        // For simplicity, save the entire last buffer as overlap
        // In practice, we'd accumulate a ring buffer of the last 2 seconds
        val byteBuffer = ByteArray(minOf(readCount * 2, overlapBytes))
        val startIdx = maxOf(0, readCount - overlapSamples)
        for (i in startIdx until readCount) {
            val localIdx = i - startIdx
            if (localIdx * 2 + 1 < byteBuffer.size) {
                val value = buffer[i]
                byteBuffer[localIdx * 2] = (value.toInt() and 0xFF).toByte()
                byteBuffer[localIdx * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
            }
        }
        overlapBuffer = byteBuffer
    }

    private fun finalizeChunk() {
        currentChunkStream?.close()
        currentChunkFile?.let { file ->
            // Update WAV header with actual data size
            updateWavHeader(file)

            // Save chunk to database
            serviceScope.launch {
                val chunk = AudioChunk(
                    sessionId = currentSessionId,
                    filePath = file.absolutePath,
                    chunkIndex = chunkIndex,
                    duration = System.currentTimeMillis() - currentChunkStartTime
                )
                val chunkId = recordingRepository.insertChunk(chunk)

                // Enqueue transcription worker
                val workRequest = OneTimeWorkRequestBuilder<ChunkUploadWorker>()
                    .setInputData(workDataOf("chunk_id" to chunkId))
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(workRequest)
            }

            chunkIndex++
            stateManager.updateState { copy(chunkIndex = chunkIndex) }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        isPaused = false

        // Stop audio capture
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Finalize last chunk
        finalizeChunk()

        // Stop timer
        timerJob?.cancel()

        // Clean up
        audioFocusManager.abandonFocus()
        phoneCallHandler.unregister()
        unregisterHeadsetReceiver()

        // Update state
        val totalDuration = accumulatedTimeMs + (System.currentTimeMillis() - recordingStartTimeMs)
        serviceScope.launch {
            recordingRepository.updateSessionStatus(
                currentSessionId,
                RecordingSession.STATUS_STOPPED,
                System.currentTimeMillis(),
                totalDuration
            )
        }

        stateManager.setState(
            RecordingState(
                isRecording = false,
                sessionId = currentSessionId,
                elapsedTimeMs = totalDuration,
                statusMessage = "Stopped"
            )
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseForReason(reason: String) {
        if (!isRecording || isPaused) return

        isPaused = true
        pauseReason = reason
        pauseStartTimeMs = System.currentTimeMillis()

        stateManager.updateState {
            copy(isPaused = true, statusMessage = reason)
        }

        serviceScope.launch {
            recordingRepository.updateSessionStatus(
                currentSessionId, RecordingSession.STATUS_PAUSED, null,
                accumulatedTimeMs + (pauseStartTimeMs - recordingStartTimeMs)
            )
        }

        updateNotification(reason)
    }

    private fun resumeFromPause() {
        if (!isRecording || !isPaused) return

        // Accumulate paused time
        accumulatedTimeMs += (pauseStartTimeMs - recordingStartTimeMs)
        recordingStartTimeMs = System.currentTimeMillis()

        isPaused = false
        pauseReason = null

        stateManager.updateState {
            copy(isPaused = false, statusMessage = "Recording...")
        }

        serviceScope.launch {
            recordingRepository.updateSessionStatus(
                currentSessionId, RecordingSession.STATUS_RECORDING, null, accumulatedTimeMs
            )
        }

        updateNotification("Recording...")
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseForReason("Paused - Audio focus lost")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pauseReason == "Paused - Audio focus lost") {
                    resumeFromPause()
                }
            }
        }
    }

    private fun checkSilence(buffer: ShortArray, readCount: Int) {
        var maxAmplitude = 0
        for (i in 0 until readCount) {
            val amp = abs(buffer[i].toInt())
            if (amp > maxAmplitude) maxAmplitude = amp
        }

        if (maxAmplitude < SILENCE_THRESHOLD) {
            if (silenceStartTime == 0L) {
                silenceStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - silenceStartTime >= SILENCE_WARNING_MS && !isSilenceWarning) {
                isSilenceWarning = true
                stateManager.updateState {
                    copy(silenceWarning = true)
                }
                updateNotification("No audio detected - Check microphone")
            }
        } else {
            silenceStartTime = 0L
            if (isSilenceWarning) {
                isSilenceWarning = false
                stateManager.updateState {
                    copy(silenceWarning = false)
                }
                if (isRecording && !isPaused) {
                    updateNotification("Recording...")
                }
            }
        }
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive && isRecording) {
                delay(1000)
                if (!isPaused) {
                    val elapsed = accumulatedTimeMs + (System.currentTimeMillis() - recordingStartTimeMs)
                    stateManager.updateState {
                        copy(elapsedTimeMs = elapsed)
                    }
                    updateNotification(stateManager.state.value.statusMessage)
                }
            }
        }
    }

    private fun hasEnoughStorage(): Boolean {
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableMb = availableBytes / (1024 * 1024)
        return availableMb >= MIN_STORAGE_MB
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(headsetReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(headsetReceiver, filter)
        }
    }

    private fun unregisterHeadsetReceiver() {
        try {
            unregisterReceiver(headsetReceiver)
        } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio recording status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val elapsed = stateManager.state.value.elapsedTimeMs
        val minutes = (elapsed / 60000).toInt()
        val seconds = ((elapsed % 60000) / 1000).toInt()
        val timeStr = String.format("%02d:%02d", minutes, seconds)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TwinMind Recording")
            .setContentText("$status • $timeStr")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Stop action
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(R.drawable.ic_stop, "Stop", stopIntent)

        // Pause/Resume action
        if (isPaused) {
            val resumeIntent = PendingIntent.getService(
                this, 2,
                Intent(this, AudioRecordingService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_mic, "Resume", resumeIntent)
        } else {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, AudioRecordingService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_pause, "Pause", pauseIntent)
        }

        return builder.build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun writeWavHeader(out: FileOutputStream, dataSize: Int) {
        val header = ByteArray(44)
        val totalSize = dataSize + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2
        val blockAlign = channels * 2

        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalSize)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // chunk size
        writeShort(header, 20, 1) // PCM format
        writeShort(header, 22, channels)
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, blockAlign)
        writeShort(header, 34, 16) // bits per sample

        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeInt(header, 40, dataSize)

        out.write(header)
    }

    private fun updateWavHeader(file: File) {
        try {
            val raf = RandomAccessFile(file, "rw")
            val dataSize = (file.length() - 44).toInt()
            val totalSize = dataSize + 36

            raf.seek(4)
            raf.write(intToByteArray(totalSize))
            raf.seek(40)
            raf.write(intToByteArray(dataSize))
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        serviceScope.cancel()
    }
}
