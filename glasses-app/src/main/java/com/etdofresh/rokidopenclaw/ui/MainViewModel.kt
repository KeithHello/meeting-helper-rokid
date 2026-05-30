package com.etdofresh.rokidopenclaw.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.etdofresh.rokidopenclaw.R
import com.etdofresh.rokidopenclaw.audio.AudioCaptureService
import com.etdofresh.rokidopenclaw.audio.AudioEncoder
import com.etdofresh.rokidopenclaw.network.AudioFrame
import com.etdofresh.rokidopenclaw.network.ConnectionState
import com.etdofresh.rokidopenclaw.network.ErrorMsg
import com.etdofresh.rokidopenclaw.network.GatewayConnection
import com.etdofresh.rokidopenclaw.network.MeetingEndMessage
import com.etdofresh.rokidopenclaw.network.MeetingStartMessage
import com.etdofresh.rokidopenclaw.network.QueryResultMessage
import com.etdofresh.rokidopenclaw.network.QuickQueryMessage
import com.etdofresh.rokidopenclaw.network.SummarySent
import com.etdofresh.rokidopenclaw.network.TranscriptionDelta
import com.etdofresh.rokidopenclaw.session.SessionManager
import com.etdofresh.rokidopenclaw.session.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/**
 * Immutable snapshot of the HUD UI state, combining all sub-states into one data class.
 */
data class HudUiState(
    val sessionState: SessionState = SessionState.Idle,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isRecording: Boolean = false,
    val wifiEnabled: Boolean = false,
    val batteryLevel: Int = 100,
    val queryResult: QueryResultMessage? = null,
    val summarySent: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Central ViewModel coordinating the meeting session lifecycle,
 * audio capture, Gateway WebSocket communication, and device status.
 *
 * ## Lifecycle
 * - `init {}`: reads Gateway URL from SharedPreferences, connects, starts listeners
 * - `toggleRecording()`: toggles meeting recording (start / stop + summary request)
 * - `quickQuery()`: sends a [QuickQueryMessage] during active recording
 * - `endMeeting()`: stops meeting if currently recording (no toggle)
 * - `setGatewayUrl(url)`: persists URL, disconnects + reconnects
 *
 * ## Incoming Message Dispatching
 * - [TranscriptionDelta] → logged only (no HUD update per spec)
 * - [QueryResultMessage] → shown on HUD for 3 seconds
 * - [SummarySent] → summary flag set for 2 seconds, then session resets
 * - [ErrorMsg] → error message set on HUD
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "meeting_helper_prefs"
        private const val KEY_GATEWAY_URL = "gateway_url"
        private const val STATUS_UPDATE_INTERVAL_MS = 5000L
    }

    // ── Sub-components ────────────────────────────────────

    private val sessionManager = SessionManager()
    private val gatewayConnection = GatewayConnection()
    private val audioCapture: AudioCaptureService
    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── UI State ──────────────────────────────────────────

    private val _uiState = MutableStateFlow(HudUiState())

    /** Combined HUD UI state, observed by the Compose layer. */
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    // ── Job tracking ──────────────────────────────────────

    private var audioCollectionJob: Job? = null
    private var queryClearJob: Job? = null
    private var summaryClearJob: Job? = null
    private var statusUpdateJob: Job? = null
    
    // ── Transcript tracking ───────────────────────────────
    private val fullTranscript = java.lang.StringBuilder()

    // ── Init ──────────────────────────────────────────────

    init {
        audioCapture = AudioCaptureService(application)

        val gatewayUrl = prefs.getString(KEY_GATEWAY_URL, null)
            ?: "wss://api.openai.com/v1/realtime?model=gpt-realtime-1.5"
            
        val apiKey = com.etdofresh.rokidopenclaw.BuildConfig.OPENAI_API_KEY

        // Connect to Gateway / OpenAI Realtime
        gatewayConnection.connect(gatewayUrl, apiKey)

        // Observe connection state changes
        viewModelScope.launch {
            gatewayConnection.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }

        // Observe session state changes
        viewModelScope.launch {
            sessionManager.state.collect { state ->
                _uiState.update {
                    it.copy(
                        sessionState = state,
                        isRecording = state is SessionState.Recording,
                    )
                }
            }
        }

        // Observe incoming Gateway messages
        startMessageListener()

        // Start periodic device status updates
        startStatusUpdates()
    }

    // ── Public API ────────────────────────────────────────

    /**
     * Single-click gesture handler: toggles meeting recording.
     * - Idle/Error → start meeting, send [MeetingStartMessage], begin audio streaming
     * - Recording → stop meeting, send [MeetingEndMessage], request summary
     */
    fun toggleRecording() {
        android.util.Log.i("MeetingHelper/VM", "toggleRecording() — currently recording: ${sessionManager.isRecording}")
        if (sessionManager.isRecording) {
            stopMeeting()
        } else {
            startMeeting()
        }
    }

    /**
     * Two-finger swipe forward gesture handler: sends a quick Q&A query during an active meeting.
     * Silently ignored if not currently recording.
     */
    fun quickQuery() {
        android.util.Log.i("MeetingHelper/VM", "quickQuery() — recording: ${sessionManager.isRecording}")
        if (!sessionManager.isRecording) {
            android.util.Log.w("MeetingHelper/VM", "quickQuery() — ignored, not recording")
            return
        }

        viewModelScope.launch {
            gatewayConnection.send(
                QuickQueryMessage(timestamp = System.currentTimeMillis()),
            )
        }
    }

    /**
     * Two-finger swipe back gesture handler: ends the current meeting if one is active.
     * This is a stop-only operation (no toggle). Silently ignored if not recording.
     */
    fun endMeeting() {
        android.util.Log.i("MeetingHelper/VM", "endMeeting() — recording: ${sessionManager.isRecording}")
        if (!sessionManager.isRecording) {
            android.util.Log.w("MeetingHelper/VM", "endMeeting() — ignored, not recording")
            return
        }
        stopMeeting()
    }

    /**
     * Persists a new Gateway URL, disconnects the current connection,
     * and reconnects to the new URL.
     */
    fun setGatewayUrl(url: String) {
        prefs.edit().putString(KEY_GATEWAY_URL, url).apply()
        gatewayConnection.disconnect()
        val apiKey = com.etdofresh.rokidopenclaw.BuildConfig.OPENAI_API_KEY
        gatewayConnection.connect(url, apiKey)
    }

    /** Returns the currently configured Gateway URL (from prefs or default). */
    fun getGatewayUrl(): String {
        return prefs.getString(KEY_GATEWAY_URL, null)
            ?: getApplication<Application>().getString(R.string.gateway_default_url)
    }

    // ── Meeting lifecycle ─────────────────────────────────

    private fun startMeeting() {
        android.util.Log.i("MeetingHelper/VM", "startMeeting() called")
        if (!audioCapture.hasPermission()) {
            android.util.Log.e("MeetingHelper/VM", "startMeeting() — no mic permission!")
            _uiState.update { it.copy(errorMessage = "Mic permission required") }
            return
        }

        fullTranscript.clear()
        sessionManager.startMeeting()

        // Notify Gateway of meeting start / Configure OpenAI Realtime session
        viewModelScope.launch {
            // Update OpenAI session to enable audio transcription
            val sessionConfig = """
                {
                    "type": "session.update",
                    "session": {
                        "type": "realtime",
                        "instructions": "You are a helpful meeting assistant. TRANSCRIBE and RESPOND in English ONLY, regardless of what language the speaker uses. Summarize meetings in concise English bullet points.",
                        "audio": {
                            "input": {
                                "transcription": {
                                    "model": "whisper-1",
                                    "language": "en"
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
            gatewayConnection.sendRaw(sessionConfig)
        }

        // Start audio capture and push loop
        audioCollectionJob = viewModelScope.launch {
            audioCapture.audioFlow.collect { frame ->
                val encoded = AudioEncoder.encodeToBase64(frame)
                
                // Send raw OpenAI realtime event
                val audioEvent = """
                    {
                        "type": "input_audio_buffer.append",
                        "audio": "$encoded"
                    }
                """.trimIndent()
                gatewayConnection.sendRaw(audioEvent)
                
                sessionManager.incrementFrameCount()
            }
        }
    }

    private fun stopMeeting() {
        val duration = sessionManager.endMeeting()

        // Stop audio collection
        audioCollectionJob?.cancel()
        audioCollectionJob = null
        audioCapture.stop()

        if (duration > 0) {
            val transcriptText = fullTranscript.toString()
            if (transcriptText.isNotBlank()) {
                generateSummary(transcriptText)
            }
        } else {
            sessionManager.reset()
        }
    }
    
    private fun generateSummary(transcript: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = com.etdofresh.rokidopenclaw.BuildConfig.OPENAI_API_KEY
                
                // Escape transcript for JSON
                val escapedTranscript = transcript
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    
                val json = """
                    {
                        "model": "gpt-5.4",
                        "messages": [
                            {
                                "role": "system",
                                "content": "You are a helpful assistant. Summarize the following meeting transcript into 1-3 short bullet points in English. Be extremely concise."
                            },
                            {
                                "role": "user",
                                "content": "$escapedTranscript"
                            }
                        ]
                    }
                """.trimIndent()
                
                val mediaType = "application/json".toMediaType()
                val requestBody = json.toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${"$"}apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                    
                val response = com.etdofresh.rokidopenclaw.RokidOpenClawApp.httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val root = org.json.JSONObject(responseBody)
                    val summary = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    
                    _uiState.update { it.copy(queryResult = QueryResultMessage(items = summary.split("\n").filter { it.isNotBlank() })) }
                    
                    // Auto-dismiss after 10 seconds
                    queryClearJob?.cancel()
                    queryClearJob = viewModelScope.launch {
                        delay(10000L)
                        _uiState.update { it.copy(queryResult = null) }
                    }
                } else {
                    android.util.Log.e("MeetingHelper/VM", "Summary failed: ${"$"}responseBody")
                }
            } catch (e: Exception) {
                android.util.Log.e("MeetingHelper/VM", "Summary error", e)
            }
        }
    }

    // ── Gateway message listener ──────────────────────────

    private fun startMessageListener() {
        viewModelScope.launch {
            gatewayConnection.incomingMessages.collect { message ->
                when (message) {
                    is TranscriptionDelta -> {
                        // Log the transcription
                        android.util.Log.d(
                            "MeetingHelper/VM",
                            "Transcription${if (message.isFinal) " [FINAL]" else ""}: ${message.text}",
                        )
                        
                        // Append to full transcript if it's a final segment
                        if (message.isFinal && message.text.isNotBlank()) {
                            fullTranscript.append(message.text).append(" ")
                        }
                        
                        // Update HUD with subtitle text
                        if (message.text.isNotBlank()) {
                            _uiState.update { it.copy(queryResult = QueryResultMessage(items = listOf(message.text))) }
                            
                            // Auto-dismiss subtitles after 4 seconds of silence
                            queryClearJob?.cancel()
                            queryClearJob = launch {
                                delay(4000L)
                                _uiState.update { it.copy(queryResult = null) }
                            }
                        }
                    }

                    is QueryResultMessage -> {
                        queryClearJob?.cancel()
                        _uiState.update { it.copy(queryResult = message) }
                        // Auto-dismiss after 3 seconds
                        queryClearJob = launch {
                            delay(3000L)
                            _uiState.update { it.copy(queryResult = null) }
                        }
                    }

                    is SummarySent -> {
                        _uiState.update { it.copy(summarySent = true) }
                        sessionManager.reset()
                        // Auto-clear after 2 seconds
                        summaryClearJob?.cancel()
                        summaryClearJob = launch {
                            delay(2000L)
                            _uiState.update { it.copy(summarySent = false) }
                        }
                    }

                    is ErrorMsg -> {
                        _uiState.update {
                            it.copy(errorMessage = "${message.code}: ${message.message}")
                        }
                        // Auto-clear error after 5 seconds
                        launch {
                            delay(5000L)
                            _uiState.update { current ->
                                if (current.errorMessage == "${message.code}: ${message.message}") {
                                    current.copy(errorMessage = null)
                                } else current
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Device status updates ─────────────────────────────

    private fun startStatusUpdates() {
        statusUpdateJob = viewModelScope.launch {
            while (true) {
                updateConnectivityState()
                updateBatteryLevel()
                delay(STATUS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun updateConnectivityState() {
        val ctx = getApplication<Application>()
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val network = cm.activeNetwork ?: run {
            _uiState.update { it.copy(wifiEnabled = false) }
            return
        }
        val caps = cm.getNetworkCapabilities(network) ?: run {
            _uiState.update { it.copy(wifiEnabled = false) }
            return
        }
        val hasConnectivity = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        _uiState.update { it.copy(wifiEnabled = hasConnectivity) }
    }

    private fun updateBatteryLevel() {
        val ctx = getApplication<Application>()
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        _uiState.update { it.copy(batteryLevel = level.coerceIn(0, 100)) }
    }

    // ── Cleanup ───────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        audioCollectionJob?.cancel()
        queryClearJob?.cancel()
        summaryClearJob?.cancel()
        statusUpdateJob?.cancel()
        audioCapture.release()
        gatewayConnection.disconnect()
    }
}
