package com.etdofresh.rokidopenclaw.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the lifecycle of a meeting session.
 *
 * Provides a reactive [StateFlow] of [SessionState] and methods to
 * transition between states. Safe to call from any thread.
 */
class SessionManager {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)

    /** Observe the current session state. */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Convenience getter — `true` when a meeting is being recorded. */
    val isRecording: Boolean
        get() = _state.value is SessionState.Recording

    /**
     * Starts a new meeting recording session.
     * Only transitions if currently [SessionState.Idle] or [SessionState.Error].
     */
    fun startMeeting() {
        _state.update { current ->
            if (current is SessionState.Idle || current is SessionState.Error) {
                SessionState.Recording(startTime = System.currentTimeMillis())
            } else {
                current
            }
        }
    }

    /**
     * Ends the current meeting and returns the duration in seconds.
     * Returns 0 if no meeting was in progress.
     */
    fun endMeeting(): Int {
        val current = _state.value
        if (current !is SessionState.Recording) return 0
        val duration = ((System.currentTimeMillis() - current.startTime) / 1000).toInt()
        _state.value = SessionState.Ending(
            startTime = current.startTime,
            durationSeconds = duration,
        )
        return duration
    }

    /** Increments the audio frame counter (call each time an [AudioFrame] is sent). */
    fun incrementFrameCount() {
        _state.update { current ->
            if (current is SessionState.Recording) {
                current.copy(audioFrameCount = current.audioFrameCount + 1)
            } else {
                current
            }
        }
    }

    /** Resets the session to [SessionState.Idle]. */
    fun reset() {
        _state.value = SessionState.Idle
    }

    /** Sets an error state with the given [message]. */
    fun error(message: String) {
        _state.value = SessionState.Error(message)
    }

    /**
     * Returns the current meeting duration in seconds (live for Recording,
     * frozen for Ending).
     */
    fun getDurationSeconds(): Int {
        val current = _state.value
        return when (current) {
            is SessionState.Recording -> ((System.currentTimeMillis() - current.startTime) / 1000).toInt()
            is SessionState.Ending -> current.durationSeconds
            else -> 0
        }
    }
}
