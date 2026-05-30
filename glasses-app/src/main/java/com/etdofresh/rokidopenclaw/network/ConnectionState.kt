package com.etdofresh.rokidopenclaw.network

/**
 * Connection state of the Gateway WebSocket client.
 */
enum class ConnectionState {
    /** No connection and not attempting to connect. */
    DISCONNECTED,

    /** Connection attempt in progress. */
    CONNECTING,

    /** Successfully connected to the Gateway. */
    CONNECTED,

    /** Connection lost; reconnecting with backoff. */
    RECONNECTING,

    /** Connection failed with an error. */
    ERROR,
}
