package com.etdofresh.rokidopenclaw

import com.etdofresh.rokidopenclaw.network.AudioFrame
import com.etdofresh.rokidopenclaw.network.ErrorMsg
import com.etdofresh.rokidopenclaw.network.MeetingEndMessage
import com.etdofresh.rokidopenclaw.network.MeetingStartMessage
import com.etdofresh.rokidopenclaw.network.MessageProtocol
import com.etdofresh.rokidopenclaw.network.QuickQueryMessage
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [MessageProtocol] JSON serialization and deserialization.
 *
 * Verifies that all OutgoingMessage subtypes serialize correctly
 * and all IncomingMessage subtypes parse correctly from raw JSON.
 */
class MessageProtocolTest {

    // ──────────────────────────────────────────────────────
    // Serialize OutgoingMessage tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `serialize AudioFrame produces correct JSON structure`() {
        val frame = AudioFrame(
            data = "dGVzdA==",
            timestamp = 1710000000000L,
        )
        val json = MessageProtocol.serializeOutgoing(frame)

        assertTrue("JSON should contain type field", json.contains("\"type\":\"audio_frame\""))
        assertTrue("JSON should contain data field", json.contains("\"data\":\"dGVzdA==\""))
        assertTrue("JSON should contain timestamp field", json.contains("\"timestamp\""))
    }

    @Test
    fun `serialize MeetingStartMessage produces correct JSON structure`() {
        val msg = MeetingStartMessage(timestamp = 1710000000000L)
        val json = MessageProtocol.serializeOutgoing(msg)

        assertTrue("JSON should contain type:meeting_start", json.contains("\"type\":\"meeting_start\""))
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""))
    }

    @Test
    fun `serialize MeetingEndMessage includes durationSeconds`() {
        val msg = MeetingEndMessage(
            durationSeconds = 42,
            timestamp = 1710000000000L,
        )
        val json = MessageProtocol.serializeOutgoing(msg)

        assertTrue("JSON should contain type:meeting_end", json.contains("\"type\":\"meeting_end\""))
        assertTrue("JSON should contain durationSeconds", json.contains("\"durationSeconds\":42"))
    }

    @Test
    fun `serialize QuickQueryMessage produces correct JSON structure`() {
        val msg = QuickQueryMessage(timestamp = 1710000000000L)
        val json = MessageProtocol.serializeOutgoing(msg)

        assertTrue("JSON should contain type:quick_query", json.contains("\"type\":\"quick_query\""))
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""))
    }

    // ──────────────────────────────────────────────────────
    // Deserialize IncomingMessage tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `parseIncoming TranscriptionDelta with correct text and isFinal`() {
        val raw = """{"type":"transcription_delta","text":"Hello world","isFinal":true}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        assertTrue("Should be TranscriptionDelta", result is com.etdofresh.rokidopenclaw.network.TranscriptionDelta)
        val delta = result as com.etdofresh.rokidopenclaw.network.TranscriptionDelta
        assertEquals("Hello world", delta.text)
        assertTrue("isFinal should be true", delta.isFinal)
    }

    @Test
    fun `parseIncoming TranscriptionDelta with isFinal defaulting to false`() {
        val raw = """{"type":"transcription_delta","text":"partial text"}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        val delta = result as com.etdofresh.rokidopenclaw.network.TranscriptionDelta
        assertEquals("partial text", delta.text)
        assertFalse("isFinal should default to false", delta.isFinal)
    }

    @Test
    fun `parseIncoming QueryResultMessage with items list`() {
        val raw = """{"type":"query_result","items":["item1","item2","item3"]}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        assertTrue("Should be QueryResultMessage", result is com.etdofresh.rokidopenclaw.network.QueryResultMessage)
        val qr = result as com.etdofresh.rokidopenclaw.network.QueryResultMessage
        assertEquals(3, qr.items.size)
        assertEquals("item1", qr.items[0])
        assertEquals("item2", qr.items[1])
        assertEquals("item3", qr.items[2])
    }

    @Test
    fun `parseIncoming QueryResultMessage with empty items list`() {
        val raw = """{"type":"query_result","items":[]}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        val qr = result as com.etdofresh.rokidopenclaw.network.QueryResultMessage
        assertTrue("Items list should be empty", qr.items.isEmpty())
    }

    @Test
    fun `parseIncoming SummarySent with correct documentId`() {
        val raw = """{"type":"summary_sent","documentId":"doc-12345-abc"}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        assertTrue("Should be SummarySent", result is com.etdofresh.rokidopenclaw.network.SummarySent)
        val summary = result as com.etdofresh.rokidopenclaw.network.SummarySent
        assertEquals("doc-12345-abc", summary.documentId)
    }

    @Test
    fun `parseIncoming ErrorMsg with code and message`() {
        val raw = """{"type":"error","code":"E001","message":"Connection timeout"}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNotNull("Should parse successfully", result)
        assertTrue("Should be ErrorMsg", result is ErrorMsg)
        val error = result as ErrorMsg
        assertEquals("E001", error.code)
        assertEquals("Connection timeout", error.message)
    }

    @Test
    fun `parseIncoming unknown type returns null`() {
        val raw = """{"type":"unknown_event","payload":"something"}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNull("Unknown type should return null", result)
    }

    @Test
    fun `parseIncoming malformed JSON returns null`() {
        val raw = """{this is not valid json at all"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNull("Malformed JSON should return null", result)
    }

    @Test
    fun `parseIncoming empty string returns null`() {
        val result = MessageProtocol.parseIncoming("")

        assertNull("Empty string should return null", result)
    }

    @Test
    fun `parseIncoming missing type field returns null`() {
        val raw = """{"text":"no type here","isFinal":true}"""
        val result = MessageProtocol.parseIncoming(raw)

        assertNull("Missing type field should return null", result)
    }

    // ──────────────────────────────────────────────────────
    // Round-trip tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `round-trip AudioFrame serialize then deserialize verifies key fields`() {
        // For AudioFrame, serialize it and verify it can be parsed back
        // (Note: AudioFrame is Outgoing, parseIncoming expects IncomingMessage types)
        // We verify serialization integrity by checking JSON structure
        val frame = AudioFrame(data = "aGVsbG8=", timestamp = 1710000000001L)
        val json = MessageProtocol.serializeOutgoing(frame)

        // Verify the JSON is valid and contains expected fields
        val parsed = MessageProtocol.json.parseToJsonElement(json)
        val obj = parsed.jsonObject

        assertEquals("audio_frame", obj["type"]?.jsonPrimitive?.content)
        assertEquals("aGVsbG8=", obj["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `round-trip MeetingEndMessage serialize then verify structure`() {
        val msg = MeetingEndMessage(durationSeconds = 120, timestamp = 1710000000002L)
        val json = MessageProtocol.serializeOutgoing(msg)

        val parsed = MessageProtocol.json.parseToJsonElement(json)
        val obj = parsed.jsonObject

        assertEquals("meeting_end", obj["type"]?.jsonPrimitive?.content)
        assertEquals(120, obj["durationSeconds"]?.jsonPrimitive?.int)
    }

    @Test
    fun `round-trip TranscriptionDelta parse then verify all fields`() {
        val raw = """{"type":"transcription_delta","text":"Final answer","isFinal":true}"""
        val parsed = MessageProtocol.parseIncoming(raw)

        assertNotNull(parsed)
        val reSerialized = MessageProtocol.json.encodeToString(
            com.etdofresh.rokidopenclaw.network.IncomingMessage.serializer(),
            parsed
        )
        // Re-parse and verify consistency
        val reParsed = MessageProtocol.parseIncoming(reSerialized)
        assertNotNull(reParsed)
        val delta = reParsed as com.etdofresh.rokidopenclaw.network.TranscriptionDelta
        assertEquals("Final answer", delta.text)
        assertTrue(delta.isFinal)
    }
}
