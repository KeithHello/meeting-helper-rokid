package com.etdofresh.rokidopenclaw.session

/**
 * Represents the current state of a meeting session.
 *
 * State transitions:
 *   Idle → Recording (long press to start)
 *   Recording → Ending (long press to stop)
 *   Ending → Idle (after summary sent confirmation)
 *   Recording → Error (on gateway disconnect)
 *   Error → Idle (manual reset)
 */
sealed class SessionState {
    /** No active meeting session. */
    object Idle : SessionState()

    /**
     * Meeting recording is in progress.
     * @param startTime epoch millis when recording started
     * @param audioFrameCount number of audio frames sent so far
     */
    data class Recording(
        val startTime: Long,
        val audioFrameCount: Int = 0,
    ) : SessionState()

    /**
     * Meeting has ended; summary is being requested.
     * @param startTime epoch millis when the meeting started
     * @param durationSeconds total meeting duration in seconds
     */
    data class Ending(
        val startTime: Long,
        val durationSeconds: Int,
    ) : SessionState()

    /**
     * An unrecoverable error occurred.
     * @param message human-readable error description
     */
    data class Error(
        val message: String,
    ) : SessionState()
}
