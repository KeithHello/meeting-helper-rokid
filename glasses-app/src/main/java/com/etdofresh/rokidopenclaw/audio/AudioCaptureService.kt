package com.etdofresh.rokidopenclaw.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Captures raw PCM audio from the device microphone (mono, 24kHz, 16-bit)
 * and emits fixed-size frames as a [Flow] of [ByteArray].
 *
 * Each frame is ~100ms of audio (4800 bytes at 24kHz/16bit/mono).
 * The flow runs on [Dispatchers.IO] and stops when the coroutine is cancelled
 * or [stop] is called.
 */
class AudioCaptureService(private val context: Context) {

    companion object {
        private const val TAG = "MeetingHelper/Audio"

        /** Sample rate in Hz. OpenAI Realtime API requires 24kHz */
        const val SAMPLE_RATE = 24000

        /** Mono channel configuration. */
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        /** 16-bit PCM encoding. */
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** Duration of each audio frame in milliseconds. */
        const val FRAME_INTERVAL_MS = 100L

        /**
         * Frame size in bytes for one 100ms chunk at 24kHz/16bit/mono:
         * 24000 samples/s × 2 bytes/sample × 0.1s = 4800 bytes
         */
        val FRAME_SIZE: Int = (SAMPLE_RATE * 2 * FRAME_INTERVAL_MS / 1000).toInt()
    }

    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isCapturing = false

    /**
     * Continuous [Flow] of raw PCM byte arrays (one frame per ~100ms).
     * Collect this flow to start capturing. Cancel the collecting coroutine to stop.
     */
    val audioFlow: Flow<ByteArray> = flow {
        if (!hasPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return@flow
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(FRAME_SIZE * 2)
        val buffer = ByteArray(FRAME_SIZE)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialise")
                    return@flow
                }
                record.startRecording()
                isCapturing = true
            }

            Log.i(TAG, "Audio capture started — ${FRAME_SIZE}B frames at ${FRAME_INTERVAL_MS}ms")

            while (currentCoroutineContext().isActive && isCapturing) {
                val bytesRead = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                if (bytesRead > 0) {
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION")
                    break
                } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    Log.e(TAG, "AudioRecord ERROR_DEAD_OBJECT")
                    break
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — missing RECORD_AUDIO permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture error: ${e.message}", e)
        } finally {
            release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Returns `true` if the RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns `true` if permission is available (signal for caller to proceed).
     * The audio flow must be collected separately; this just validates permissions.
     */
    fun start(): Boolean {
        if (!hasPermission()) return false
        return true
    }

    /**
     * Signals the capture loop to stop emitting.
     */
    fun stop() {
        Log.i(TAG, "Stopping audio capture")
        isCapturing = false
    }

    /**
     * Releases the [AudioRecord] resource immediately.
     * Safe to call multiple times.
     */
    fun release() {
        isCapturing = false
        audioRecord?.apply {
            try {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
            } catch (_: Exception) {
                // Ignore — already stopped
            }
            try {
                release()
            } catch (_: Exception) {
                // Ignore — resource may already be freed
            }
        }
        audioRecord = null
        Log.i(TAG, "AudioRecord released")
    }
}
