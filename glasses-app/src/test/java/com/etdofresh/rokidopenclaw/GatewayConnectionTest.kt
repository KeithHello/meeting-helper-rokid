package com.etdofresh.rokidopenclaw

import com.etdofresh.rokidopenclaw.network.GatewayConnection
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [GatewayConnection] exponential backoff calculation.
 *
 * Verifies that the backoff formula `min(1000 * 2^attempts, 30000)` is correct.
 *
 * Since [calculateBackoff] is a private method, these tests use
 * Java/Kotlin reflection to access and verify the implementation.
 */
class GatewayConnectionTest {

    // ──────────────────────────────────────────────────────
    // Backoff calculation tests (via reflection)
    // ──────────────────────────────────────────────────────

    /**
     * Helper: invokes the private `calculateBackoff(attempts: Int): Long` method
     * via reflection on a [GatewayConnection] instance.
     */
    private fun invokeCalculateBackoff(attempts: Int): Long {
        val instance = GatewayConnection()
        val method = GatewayConnection::class.java.getDeclaredMethod(
            "calculateBackoff",
            Int::class.java
        )
        method.isAccessible = true
        return method.invoke(instance, attempts) as Long
    }

    @Test
    fun `calculateBackoff attempt 0 returns 1000ms`() {
        val result = invokeCalculateBackoff(0)
        assertEquals(
            "Backoff for 0 attempts should be 1000ms (BASE_BACKOFF_MS)",
            1000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 1 returns 2000ms`() {
        val result = invokeCalculateBackoff(1)
        assertEquals(
            "Backoff for 1 attempt should be 2000ms (1000 * 2^1)",
            2000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 2 returns 4000ms`() {
        val result = invokeCalculateBackoff(2)
        assertEquals(
            "Backoff for 2 attempts should be 4000ms (1000 * 2^2)",
            4000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 3 returns 8000ms`() {
        val result = invokeCalculateBackoff(3)
        assertEquals(
            "Backoff for 3 attempts should be 8000ms (1000 * 2^3)",
            8000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 4 returns 16000ms`() {
        val result = invokeCalculateBackoff(4)
        assertEquals(
            "Backoff for 4 attempts should be 16000ms (1000 * 2^4)",
            16000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 5 returns 30000ms capped at max`() {
        // 1000 * 2^5 = 32000, capped at 30000
        val result = invokeCalculateBackoff(5)
        assertEquals(
            "Backoff for 5 attempts should be capped at 30000ms (MAX_BACKOFF_MS)",
            30000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 10 returns 30000ms capped at max`() {
        // 1000 * 2^10 = 1,024,000, far above cap
        val result = invokeCalculateBackoff(10)
        assertEquals(
            "Backoff for 10 attempts should be capped at 30000ms",
            30000L,
            result
        )
    }

    @Test
    fun `calculateBackoff attempt 100 returns 30000ms still capped`() {
        val result = invokeCalculateBackoff(100)
        assertEquals(
            "Backoff for many attempts should always be capped at 30000ms",
            30000L,
            result
        )
    }

    @Test
    fun `calculateBackoff is monotonically increasing then capped`() {
        val results = (0..10).map { invokeCalculateBackoff(it) }

        // Verify non-decreasing (monotonic)
        for (i in 1 until results.size) {
            assertTrue(
                "Backoff should be non-decreasing: attempt ${i - 1}=${results[i - 1]}, attempt $i=${results[i]}",
                results[i] >= results[i - 1]
            )
        }

        // Once at cap, should stay at cap
        for (i in 5..10) {
            assertEquals("After cap, all values should be 30000", 30000L, results[i])
        }
    }

    @Test
    fun `calculateBackoff values follow exact formula until cap`() {
        // min(1000 * 2^attempts, 30000)
        for (attempt in 0..4) {
            val expected = (1000L * Math.pow(2.0, attempt.toDouble())).toLong()
            val actual = invokeCalculateBackoff(attempt)
            assertEquals("Backoff for attempt $attempt should be $expected", expected, actual)
        }
    }

    @Test
    fun `GatewayConnection has expected companion constants`() {
        // Verify that the internal constants are accessible via reflection
        val baseField = GatewayConnection::class.java.getDeclaredField("BASE_BACKOFF_MS")
        baseField.isAccessible = true
        assertEquals(
            "BASE_BACKOFF_MS should be 1000",
            1000L,
            baseField.get(null)
        )

        val maxField = GatewayConnection::class.java.getDeclaredField("MAX_BACKOFF_MS")
        maxField.isAccessible = true
        assertEquals(
            "MAX_BACKOFF_MS should be 30000",
            30_000L,
            maxField.get(null)
        )
    }
}
