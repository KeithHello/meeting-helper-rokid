package com.etdofresh.rokidopenclaw

import com.etdofresh.rokidopenclaw.session.SessionState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [SessionState] sealed class equality and properties.
 *
 * Verifies data class equality semantics, singleton behavior of Idle,
 * and structural comparison of state variants.
 */
class SessionStateTest {

    // ──────────────────────────────────────────────────────
    // Recording equality tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `Recording states with same startTime and frameCount are equal`() {
        val a = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 5)
        val b = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 5)

        assertEquals("Equal Recording states should be equal", a, b)
        assertEquals("Hash codes should match", a.hashCode(), b.hashCode())
    }

    @Test
    fun `Recording states with different startTime are not equal`() {
        val a = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 5)
        val b = SessionState.Recording(startTime = 1700000000001L, audioFrameCount = 5)

        assertNotEquals("Different startTime should not be equal", a, b)
    }

    @Test
    fun `Recording states with different audioFrameCount are not equal`() {
        val a = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 5)
        val b = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 10)

        assertNotEquals("Different audioFrameCount should not be equal", a, b)
    }

    @Test
    fun `Recording not equal to Idle`() {
        val recording = SessionState.Recording(startTime = 1700000000000L)
        val idle = SessionState.Idle

        assertNotEquals("Recording should not equal Idle", recording, idle)
    }

    @Test
    fun `Recording not equal to Ending`() {
        val recording = SessionState.Recording(startTime = 1700000000000L)
        val ending = SessionState.Ending(startTime = 1700000000000L, durationSeconds = 30)

        assertNotEquals("Recording should not equal Ending", recording, ending)
    }

    @Test
    fun `Recording not equal to Error`() {
        val recording = SessionState.Recording(startTime = 1700000000000L)
        val error = SessionState.Error(message = "test")

        assertNotEquals("Recording should not equal Error", recording, error)
    }

    // ──────────────────────────────────────────────────────
    // Idle singleton tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `Idle is referentially equal to itself`() {
        val a = SessionState.Idle
        val b = SessionState.Idle

        assertSame("Idle should be the same instance (singleton)", a, b)
    }

    @Test
    fun `Idle equals Idle`() {
        assertEquals("Idle should equal Idle", SessionState.Idle, SessionState.Idle)
    }

    @Test
    fun `Idle hashCode is consistent`() {
        assertEquals(
            "Idle hashCode should be consistent",
            SessionState.Idle.hashCode(),
            SessionState.Idle.hashCode()
        )
    }

    // ──────────────────────────────────────────────────────
    // Ending equality tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `Ending states with same fields are equal`() {
        val a = SessionState.Ending(startTime = 1700000000000L, durationSeconds = 42)
        val b = SessionState.Ending(startTime = 1700000000000L, durationSeconds = 42)

        assertEquals("Equal Ending states should be equal", a, b)
    }

    @Test
    fun `Ending states with different durationSeconds are not equal`() {
        val a = SessionState.Ending(startTime = 1700000000000L, durationSeconds = 42)
        val b = SessionState.Ending(startTime = 1700000000000L, durationSeconds = 99)

        assertNotEquals("Different durationSeconds should not be equal", a, b)
    }

    // ──────────────────────────────────────────────────────
    // Error equality tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `Error states with same message are equal`() {
        val a = SessionState.Error(message = "Network error")
        val b = SessionState.Error(message = "Network error")

        assertEquals("Equal Error states should be equal", a, b)
    }

    @Test
    fun `Error states with different messages are not equal`() {
        val a = SessionState.Error(message = "Network error")
        val b = SessionState.Error(message = "Audio error")

        assertNotEquals("Different error messages should not be equal", a, b)
    }

    // ──────────────────────────────────────────────────────
    // Cross-type inequality tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `Idle not equal to null`() {
        assertNotEquals("Idle should not equal null", null, SessionState.Idle)
    }

    @Test
    fun `Idle not equal to arbitrary object`() {
        assertNotEquals("Idle should not equal arbitrary string", SessionState.Idle, Any())
    }

    // ──────────────────────────────────────────────────────
    // Data class property access
    // ──────────────────────────────────────────────────────

    @Test
    fun `Recording copy preserves startTime and increments frameCount`() {
        val original = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 3)
        val copied = original.copy(audioFrameCount = original.audioFrameCount + 1)

        assertEquals(1700000000000L, copied.startTime)
        assertEquals(4, copied.audioFrameCount)
    }

    @Test
    fun `Recording has correct toString representation`() {
        val recording = SessionState.Recording(startTime = 1700000000000L, audioFrameCount = 0)
        val str = recording.toString()

        assertTrue("toString should contain Recording", str.contains("Recording"))
        assertTrue("toString should contain startTime", str.contains("1700000000000"))
    }
}
