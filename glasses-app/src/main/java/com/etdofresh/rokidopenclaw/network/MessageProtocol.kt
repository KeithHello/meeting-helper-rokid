package com.etdofresh.rokidopenclaw.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ──────────────────────────────────────────────
// Outgoing messages (Glasses → Gateway)
// ──────────────────────────────────────────────

/** Base type for all messages sent from the glasses to the Gateway. */
@Serializable
sealed interface OutgoingMessage {
    val type: String
    val timestamp: Long
}

/** PCM audio frame encoded as Base64 string. Sent every ~100ms while recording. */
@Serializable
@SerialName("audio_frame")
data class AudioFrame(
    override val type: String = "audio_frame",
    val data: String,
    override val timestamp: Long = System.currentTimeMillis(),
) : OutgoingMessage

/** Signals the start of a new meeting recording session. */
@Serializable
@SerialName("meeting_start")
data class MeetingStartMessage(
    override val type: String = "meeting_start",
    override val timestamp: Long = System.currentTimeMillis(),
) : OutgoingMessage

/** Signals the end of a meeting and requests summary generation. */
@Serializable
@SerialName("meeting_end")
data class MeetingEndMessage(
    override val type: String = "meeting_end",
    override val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
) : OutgoingMessage

/** Requests a quick Q&A response based on the meeting context so far. */
@Serializable
@SerialName("quick_query")
data class QuickQueryMessage(
    override val type: String = "quick_query",
    override val timestamp: Long = System.currentTimeMillis(),
) : OutgoingMessage

// ──────────────────────────────────────────────
// Incoming messages (Gateway → Glasses)
// ──────────────────────────────────────────────

/** Base type for all messages received from the Gateway. */
@Serializable
sealed interface IncomingMessage {
    val type: String
}

/** Streaming transcription delta from the Gateway (Whisper). */
@Serializable
@SerialName("transcription_delta")
data class TranscriptionDelta(
    override val type: String = "transcription_delta",
    val text: String,
    val isFinal: Boolean = false,
) : IncomingMessage

/** Result of a quick Q&A query during a meeting. */
@Serializable
@SerialName("query_result")
data class QueryResultMessage(
    override val type: String = "query_result",
    val items: List<String>,
) : IncomingMessage

/** Confirmation that the meeting summary was generated and emailed. */
@Serializable
@SerialName("summary_sent")
data class SummarySent(
    override val type: String = "summary_sent",
    val documentId: String,
) : IncomingMessage

/** Error response from the Gateway. */
@Serializable
@SerialName("error")
data class ErrorMsg(
    override val type: String = "error",
    val code: String,
    val message: String,
) : IncomingMessage

// ──────────────────────────────────────────────
// JSON protocol helper
// ──────────────────────────────────────────────

/**
 * Shared [Json] instance and message parsing utilities for the Gateway protocol.
 */
object MessageProtocol {

    /** Pre-configured kotlinx.serialization Json instance for Gateway messages. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * Separate Json instance for outgoing serialization.
     * Uses a different discriminator name to avoid conflict with the
     * `type` property on [OutgoingMessage] subclasses.
     */
    private val outgoingJson: Json = Json {
        encodeDefaults = true
        classDiscriminator = "_msgtype"
    }

    /**
     * Parses a raw JSON string into an [IncomingMessage].
     * Returns `null` if the JSON is malformed or the type is unrecognised.
     */
    fun parseIncoming(raw: String): IncomingMessage? {
        return try {
            val element = json.parseToJsonElement(raw)
            val type = element.jsonObject["type"]?.jsonPrimitive?.content
            when (type) {
                "transcription_delta" -> json.decodeFromJsonElement<TranscriptionDelta>(element)
                "query_result" -> json.decodeFromJsonElement<QueryResultMessage>(element)
                "summary_sent" -> json.decodeFromJsonElement<SummarySent>(element)
                "error" -> json.decodeFromJsonElement<ErrorMsg>(element)
                
                // Handle OpenAI GA Realtime transcription (GA event names)
                "conversation.item.input_audio_transcription.delta" -> {
                    val delta = element.jsonObject["delta"]?.jsonPrimitive?.content ?: ""
                    TranscriptionDelta(text = delta, isFinal = false)
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = element.jsonObject["transcript"]?.jsonPrimitive?.content ?: ""
                    TranscriptionDelta(text = transcript, isFinal = true)
                }
                // Handle OpenAI Realtime transcription (beta event names)
                "response.audio_transcript.delta" -> {
                    val delta = element.jsonObject["delta"]?.jsonPrimitive?.content ?: ""
                    TranscriptionDelta(text = delta, isFinal = false)
                }
                "response.audio_transcript.done" -> {
                    val transcript = element.jsonObject["transcript"]?.jsonPrimitive?.content ?: ""
                    TranscriptionDelta(text = transcript, isFinal = true)
                }
                // VAD events (for debugging)
                "input_audio_buffer.speech_started" -> {
                    android.util.Log.i("MeetingHelper/Gateway", "VAD: speech started")
                    null
                }
                "input_audio_buffer.speech_stopped" -> {
                    android.util.Log.i("MeetingHelper/Gateway", "VAD: speech stopped")
                    null
                }
                "response.done" -> {
                    android.util.Log.d("MeetingHelper/Gateway", "Response done")
                    null
                }
                
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serialises an [OutgoingMessage] to its JSON string representation.
     */
    fun serializeOutgoing(msg: OutgoingMessage): String = outgoingJson.encodeToString(
        OutgoingMessage.serializer(),
        msg,
    )
}
