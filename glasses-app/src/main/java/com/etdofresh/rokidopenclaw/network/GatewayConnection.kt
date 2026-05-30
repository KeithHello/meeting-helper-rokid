package com.etdofresh.rokidopenclaw.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

/**
 * Connection manager that wraps [GatewayClient] with exponential backoff
 * reconnection logic.
 *
 * On failure, waits `min(1000 * 2^attempt, 30000)` ms before retrying.
 * The retry counter resets on a successful connection.
 */
class GatewayConnection {

    companion object {
        private const val TAG = "MeetingHelper/GatewayConn"

        /** Minimum backoff interval in milliseconds. */
        private const val BASE_BACKOFF_MS = 1000L

        /** Maximum backoff interval in milliseconds. */
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val client = GatewayClient()

    /** Exposes the underlying client's connection state. */
    val connectionState: StateFlow<ConnectionState> = client.connectionState

    /** Forwards incoming messages from the underlying [GatewayClient]. */
    val incomingMessages: SharedFlow<IncomingMessage> = client.incomingMessages

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var currentUrl: String? = null
    private var currentApiKey: String? = null

    /**
     * Connects to [url] with automatic reconnection on failure.
     * Resets the retry counter.
     */
    fun connect(url: String, apiKey: String? = null) {
        currentUrl = url
        currentApiKey = apiKey
        reconnectAttempts = 0
        client.connect(url, apiKey)
        startReconnectWatcher(url)
    }

    /**
     * Disconnects and cancels any pending reconnection attempts.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        client.disconnect()
        currentUrl = null
    }

    /**
     * Sends a message through the underlying client.
     * @return true if the message was queued successfully
     */
    fun send(message: OutgoingMessage): Boolean = client.send(message)
    
    /**
     * Sends a raw JSON string through the underlying client.
     * @return true if the message was queued successfully
     */
    fun sendRaw(json: String): Boolean = client.sendRaw(json)

    // ── Internal ───────────────────────────────────────────

    private fun startReconnectWatcher(url: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            client.connectionState.collect { state ->
                if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                    val urlStillValid = currentUrl
                    if (urlStillValid != null) {
                        reconnectAttempts++
                        val backoff = calculateBackoff(reconnectAttempts)
                        Log.i(
                            TAG,
                            "Reconnect attempt $reconnectAttempts in ${backoff}ms",
                        )
                        delay(backoff)
                        client.connect(urlStillValid, currentApiKey)
                    }
                }
                if (state == ConnectionState.CONNECTED) {
                    reconnectAttempts = 0
                }
            }
        }
    }

    /**
     * Calculates the exponential backoff delay in milliseconds:
     * `min(1000 * 2^attempts, 30000)`, capped at 30 seconds.
     */
    private fun calculateBackoff(attempts: Int): Long {
        val raw = BASE_BACKOFF_MS * 2.0.pow(attempts.toDouble()).toLong()
        return min(raw, MAX_BACKOFF_MS)
    }
}
