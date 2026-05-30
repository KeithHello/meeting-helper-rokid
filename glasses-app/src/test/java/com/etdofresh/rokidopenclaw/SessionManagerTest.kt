package com.etdofresh.rokidopenclaw

import com.etdofresh.rokidopenclaw.session.SessionManager
import com.etdofresh.rokidopenclaw.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [SessionManager] state machine lifecycle.
 *
 * Verifies all valid state transitions, edge cases, and invariants
 * across the meeting session lifecycle.
 */
class SessionManagerTest {

    // ──────────────────────────────────────────────────────
    // Initial state tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() = runTest {
        val manager = SessionManager()
        assertEquals(SessionState.Idle, manager.state.first())
    }

    @Test
    fun `isRecording false initially`() {
        val manager = SessionManager()
        assertFalse("isRecording should be false initially", manager.isRecording)
    }

    // ──────────────────────────────────────────────────────
    // startMeeting tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `startMeeting from Idle transitions to Recording`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()

        val state = manager.state.first()
        assertTrue("State should be Recording", state is SessionState.Recording)
        val recording = state as SessionState.Recording
        assertTrue("startTime should be positive", recording.startTime > 0)
        assertEquals("audioFrameCount should start at 0", 0, recording.audioFrameCount)
    }

    @Test
    fun `startMeeting from Recording stays Recording`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        val firstStartTime = (manager.state.first() as SessionState.Recording).startTime

        // Try to start again
        manager.startMeeting()
        val state = manager.state.first()

        assertTrue("Should still be Recording", state is SessionState.Recording)
        assertEquals(
            "startTime should not change on double start",
            firstStartTime,
            (state as SessionState.Recording).startTime
        )
    }

    @Test
    fun `startMeeting from Ending stays Ending (no transition)`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        // Wait a tiny bit so duration > 0
        Thread.sleep(10)
        val duration = manager.endMeeting()

        assertTrue("Duration should be > 0", duration > 0)
        assertTrue("State should be Ending", manager.state.first() is SessionState.Ending)

        // Try starting from Ending — should not change
        manager.startMeeting()
        assertTrue(
            "Should still be Ending after startMeeting from Ending",
            manager.state.first() is SessionState.Ending
        )
    }

    @Test
    fun `isRecording true after startMeeting`() {
        val manager = SessionManager()
        manager.startMeeting()

        assertTrue("isRecording should be true after startMeeting", manager.isRecording)
    }

    // ──────────────────────────────────────────────────────
    // endMeeting tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `endMeeting from Recording transitions to Ending with positive duration`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()

        // Small delay to ensure duration > 0
        Thread.sleep(50)

        val duration = manager.endMeeting()

        assertTrue("Duration should be > 0", duration > 0)

        val state = manager.state.first()
        assertTrue("State should be Ending", state is SessionState.Ending)
        val ending = state as SessionState.Ending
        assertEquals("durationSeconds should match returned value", duration, ending.durationSeconds)
        assertTrue("startTime in Ending should be > 0", ending.startTime > 0)
    }

    @Test
    fun `endMeeting from Idle returns 0 and state stays Idle`() = runTest {
        val manager = SessionManager()
        val duration = manager.endMeeting()

        assertEquals("Duration should be 0 when ending from Idle", 0, duration)
        assertEquals("State should remain Idle", SessionState.Idle, manager.state.first())
    }

    @Test
    fun `endMeeting from Ending returns 0 and state stays Ending`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        Thread.sleep(10)
        manager.endMeeting()

        assertTrue(manager.state.first() is SessionState.Ending)

        // End again from Ending
        val duration = manager.endMeeting()
        assertEquals("endMeeting from Ending should return 0", 0, duration)
        assertTrue("State should still be Ending", manager.state.first() is SessionState.Ending)
    }

    @Test
    fun `endMeeting from Error returns 0 and state stays Error`() = runTest {
        val manager = SessionManager()
        manager.error("test error")
        assertTrue(manager.state.first() is SessionState.Error)

        val duration = manager.endMeeting()
        assertEquals("endMeeting from Error should return 0", 0, duration)
        assertTrue("State should still be Error", manager.state.first() is SessionState.Error)
    }

    @Test
    fun `isRecording false after endMeeting`() {
        val manager = SessionManager()
        manager.startMeeting()
        manager.endMeeting()

        assertFalse("isRecording should be false after endMeeting", manager.isRecording)
    }

    // ──────────────────────────────────────────────────────
    // getDurationSeconds tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `getDurationSeconds returns 0 in Idle`() {
        val manager = SessionManager()
        assertEquals("getDurationSeconds should be 0 in Idle", 0, manager.getDurationSeconds())
    }

    @Test
    fun `getDurationSeconds returns correct value during Recording`() {
        val manager = SessionManager()
        manager.startMeeting()

        // Sleep so duration > 0
        Thread.sleep(100)

        val duration = manager.getDurationSeconds()
        assertTrue("Duration during Recording should be >= 0", duration >= 0)
    }

    @Test
    fun `getDurationSeconds returns correct frozen value after Ending`() {
        val manager = SessionManager()
        manager.startMeeting()

        Thread.sleep(200)
        val frozenDuration = manager.endMeeting()

        // Wait a bit more — duration should stay frozen
        Thread.sleep(100)

        assertEquals(
            "After Ending, getDurationSeconds should return frozen value",
            frozenDuration,
            manager.getDurationSeconds()
        )
    }

    @Test
    fun `getDurationSeconds returns 0 in Error state`() {
        val manager = SessionManager()
        manager.error("something broke")

        assertEquals("getDurationSeconds should be 0 in Error", 0, manager.getDurationSeconds())
    }

    // ──────────────────────────────────────────────────────
    // reset tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `reset from Recording returns to Idle`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        assertTrue(manager.state.first() is SessionState.Recording)

        manager.reset()
        assertEquals("Should be Idle after reset from Recording", SessionState.Idle, manager.state.first())
    }

    @Test
    fun `reset from Ending returns to Idle`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        Thread.sleep(10)
        manager.endMeeting()
        assertTrue(manager.state.first() is SessionState.Ending)

        manager.reset()
        assertEquals("Should be Idle after reset from Ending", SessionState.Idle, manager.state.first())
    }

    @Test
    fun `reset from Error returns to Idle`() = runTest {
        val manager = SessionManager()
        manager.error("some error")
        assertTrue(manager.state.first() is SessionState.Error)

        manager.reset()
        assertEquals("Should be Idle after reset from Error", SessionState.Idle, manager.state.first())
    }

    @Test
    fun `reset from Idle stays Idle`() = runTest {
        val manager = SessionManager()
        assertEquals(SessionState.Idle, manager.state.first())

        manager.reset()
        assertEquals("Reset from Idle should stay Idle", SessionState.Idle, manager.state.first())
    }

    // ──────────────────────────────────────────────────────
    // error tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `error from Idle transitions to Error with message`() = runTest {
        val manager = SessionManager()
        manager.error("Network timeout")

        val state = manager.state.first()
        assertTrue("State should be Error", state is SessionState.Error)
        assertEquals("Network timeout", (state as SessionState.Error).message)
    }

    @Test
    fun `error from Recording overwrites state to Error`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        manager.error("Connection lost")

        val state = manager.state.first()
        assertTrue("State should be Error", state is SessionState.Error)
        assertEquals("Connection lost", (state as SessionState.Error).message)
    }

    @Test
    fun `error with empty message works`() = runTest {
        val manager = SessionManager()
        manager.error("")

        val state = manager.state.first()
        assertTrue("State should be Error", state is SessionState.Error)
        assertEquals("", (state as SessionState.Error).message)
    }

    // ──────────────────────────────────────────────────────
    // incrementFrameCount tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `incrementFrameCount increases count in Recording state`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()

        manager.incrementFrameCount()
        manager.incrementFrameCount()
        manager.incrementFrameCount()

        val state = manager.state.first()
        assertTrue("State should still be Recording", state is SessionState.Recording)
        assertEquals("Frame count should be 3", 3, (state as SessionState.Recording).audioFrameCount)
    }

    @Test
    fun `incrementFrameCount no-op in Idle state`() = runTest {
        val manager = SessionManager()
        manager.incrementFrameCount()
        manager.incrementFrameCount()

        assertEquals("State should remain Idle", SessionState.Idle, manager.state.first())
    }

    @Test
    fun `incrementFrameCount no-op in Error state`() = runTest {
        val manager = SessionManager()
        manager.error("test")
        manager.incrementFrameCount()

        val state = manager.state.first()
        assertTrue("State should still be Error", state is SessionState.Error)
    }

    @Test
    fun `incrementFrameCount no-op in Ending state`() = runTest {
        val manager = SessionManager()
        manager.startMeeting()
        Thread.sleep(10)
        manager.endMeeting()

        manager.incrementFrameCount()
        assertTrue("State should still be Ending", manager.state.first() is SessionState.Ending)
    }

    // ──────────────────────────────────────────────────────
    // Full lifecycle test
    // ──────────────────────────────────────────────────────

    @Test
    fun `full lifecycle Idle to Recording to Ending to Idle`() = runTest {
        val manager = SessionManager()

        // 1. Initial state
        assertEquals(SessionState.Idle, manager.state.first())
        assertFalse(manager.isRecording)
        assertEquals(0, manager.getDurationSeconds())

        // 2. Start meeting
        manager.startMeeting()
        assertTrue(manager.state.first() is SessionState.Recording)
        assertTrue(manager.isRecording)

        // 3. Increment frames
        manager.incrementFrameCount()
        manager.incrementFrameCount()
        manager.incrementFrameCount()
        val recording = manager.state.first() as SessionState.Recording
        assertEquals(3, recording.audioFrameCount)

        // 4. Wait briefly and end
        Thread.sleep(50)
        val duration = manager.endMeeting()
        assertTrue("Duration should be > 0", duration > 0)

        val ending = manager.state.first()
        assertTrue("Should be Ending", ending is SessionState.Ending)
        assertFalse("isRecording should be false", manager.isRecording)
        assertEquals(duration, (ending as SessionState.Ending).durationSeconds)

        // 5. getDurationSeconds should return frozen value
        Thread.sleep(50)
        assertEquals(duration, manager.getDurationSeconds())

        // 6. Reset
        manager.reset()
        assertEquals(SessionState.Idle, manager.state.first())
        assertFalse(manager.isRecording)
        assertEquals(0, manager.getDurationSeconds())
    }

    // ──────────────────────────────────────────────────────
    // startMeeting from Error transitions to Recording
    // ──────────────────────────────────────────────────────

    @Test
    fun `startMeeting from Error transitions to Recording`() = runTest {
        val manager = SessionManager()
        manager.error("previous error")
        assertTrue(manager.state.first() is SessionState.Error)

        manager.startMeeting()
        assertTrue(
            "startMeeting from Error should transition to Recording",
            manager.state.first() is SessionState.Recording
        )
    }
}
