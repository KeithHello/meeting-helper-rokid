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
 * - `onLongPress()`: toggles meeting recording (start / stop + summary request)
 * - `onDoubleTap()`: sends a [QuickQueryMessage] during active recording
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

    // ── Init ──────────────────────────────────────────────

    init {
        audioCapture = AudioCaptureService(application)

        val gatewayUrl = prefs.getString(KEY_GATEWAY_URL, null)
            ?: application.getString(R.string.gateway_default_url)

        // Connect to Gateway
        gatewayConnection.connect(gatewayUrl)

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
     * Long-press gesture handler: toggles meeting recording.
     * - Idle/Error → start meeting, send [MeetingStartMessage], begin audio streaming
     * - Recording → stop meeting, send [MeetingEndMessage], request summary
     */
    fun onLongPress() {
        if (sessionManager.isRecording) {
            stopMeeting()
        } else {
            startMeeting()
        }
    }

    /**
     * Double-tap gesture handler: sends a quick Q&A query during an active meeting.
     * Silently ignored if not currently recording.
     */
    fun onDoubleTap() {
        if (!sessionManager.isRecording) return

        viewModelScope.launch {
            gatewayConnection.send(
                QuickQueryMessage(timestamp = System.currentTimeMillis()),
            )
        }
    }

    /**
     * Persists a new Gateway URL, disconnects the current connection,
     * and reconnects to the new URL.
     */
    fun setGatewayUrl(url: String) {
        prefs.edit().putString(KEY_GATEWAY_URL, url).apply()
        gatewayConnection.disconnect()
        gatewayConnection.connect(url)
    }

    /** Returns the currently configured Gateway URL (from prefs or default). */
    fun getGatewayUrl(): String {
        return prefs.getString(KEY_GATEWAY_URL, null)
            ?: getApplication<Application>().getString(R.string.gateway_default_url)
    }

    // ── Meeting lifecycle ─────────────────────────────────

    private fun startMeeting() {
        if (!audioCapture.hasPermission()) {
            _uiState.update { it.copy(errorMessage = "Mic permission required") }
            return
        }

        sessionManager.startMeeting()

        // Notify Gateway of meeting start
        viewModelScope.launch {
            gatewayConnection.send(
                MeetingStartMessage(timestamp = System.currentTimeMillis()),
            )
        }

        // Start audio capture and push loop
        audioCollectionJob = viewModelScope.launch {
            audioCapture.audioFlow.collect { frame ->
                val encoded = AudioEncoder.encodeToBase64(frame)
                gatewayConnection.send(
                    AudioFrame(
                        data = encoded,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
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
            viewModelScope.launch {
                gatewayConnection.send(
                    MeetingEndMessage(
                        durationSeconds = duration,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        } else {
            sessionManager.reset()
        }
    }

    // ── Gateway message listener ──────────────────────────

    private fun startMessageListener() {
        viewModelScope.launch {
            gatewayConnection.incomingMessages.collect { message ->
                when (message) {
                    is TranscriptionDelta -> {
                        // Per spec: log only, no HUD update for transcriptions
                        android.util.Log.d(
                            "MeetingHelper/VM",
                            "Transcription${if (message.isFinal) " [FINAL]" else ""}: ${message.text}",
                        )
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
