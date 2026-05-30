package com.etdofresh.rokidopenclaw.network

import android.util.Log
import com.etdofresh.rokidopenclaw.RokidOpenClawApp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based WebSocket client for communicating with the OpenClaw Gateway.
 *
 * Manages a single WebSocket connection with typed message flows:
 * - [connectionState] — current connection status
 * - [incomingMessages] — hot flow of parsed [IncomingMessage] events
 *
 * All outgoing messages are serialised via [MessageProtocol.serializeOutgoing].
 * All incoming frames are parsed via [MessageProtocol.parseIncoming].
 */
class GatewayClient {

    companion object {
        private const val TAG = "MeetingHelper/Gateway"
    }

    // ── State ──────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /** Observe the current connection state of the WebSocket. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot flow of parsed incoming messages from the Gateway. */
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    // ── Internals ──────────────────────────────────────────

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient get() = RokidOpenClawApp.httpClient

    // ── Public API ─────────────────────────────────────────

    /**
     * Opens a WebSocket connection to [url].
     * Sets state to CONNECTING immediately; transitions to CONNECTED on success.
     */
    fun connect(url: String, apiKey: String? = null) {
        disconnect(code = 1000, reason = "reconnect")
        _connectionState.value = ConnectionState.CONNECTING

        val requestBuilder = Request.Builder()
            .url(url)
            
        if (apiKey != null) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            requestBuilder.addHeader("OpenAI-Beta", "realtime=v1")
        }
            
        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected: ${response.message}")
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = MessageProtocol.parseIncoming(text)
                if (message != null) {
                    _incomingMessages.tryEmit(message)
                } else {
                    Log.w(TAG, "Unrecognised incoming message: $text")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }

    /**
     * Sends a raw string over the WebSocket.
     */
    fun sendRaw(json: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(json)
    }

    /**
     * Sends a serialised [OutgoingMessage] over the WebSocket.
     * If not connected the message is silently dropped.
     */
    fun send(message: OutgoingMessage): Boolean {
        val ws = webSocket ?: return false
        val json = MessageProtocol.serializeOutgoing(message)
        return ws.send(json)
    }

    /**
     * Gracefully close the WebSocket connection.
     */
    fun disconnect(code: Int = 1000, reason: String = "manual") {
        try {
            webSocket?.close(code, reason)
        } catch (_: Exception) {
            // Already closed or closing
        }
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
