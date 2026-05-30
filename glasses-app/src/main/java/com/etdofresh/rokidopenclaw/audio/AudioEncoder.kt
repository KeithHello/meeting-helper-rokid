package com.etdofresh.rokidopenclaw.audio

import android.util.Base64

/**
 * Encodes raw PCM audio byte arrays to Base64 strings for transmission
 * over the WebSocket Gateway protocol.
 */
object AudioEncoder {

    /**
     * Encodes a raw PCM [ByteArray] into a Base64-encoded [String]
     * suitable for the `audio_frame.data` field.
     *
     * Uses [Base64.NO_WRAP] to produce a single-line string.
     */
    fun encodeToBase64(pcmData: ByteArray): String =
        Base64.encodeToString(pcmData, Base64.NO_WRAP)
}
