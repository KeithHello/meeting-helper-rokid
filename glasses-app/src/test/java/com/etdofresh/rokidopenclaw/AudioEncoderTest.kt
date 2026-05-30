package com.etdofresh.rokidopenclaw

import com.etdofresh.rokidopenclaw.audio.AudioEncoder
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [AudioEncoder] Base64 encoding.
 *
 * Verifies that raw PCM byte arrays are correctly encoded to
 * Base64 strings using Android's Base64.NO_WRAP flag.
 *
 * NOTE: These tests require the Android SDK (android.util.Base64)
 * to be on the classpath. Run with Robolectric or on an Android device/emulator.
 */
class AudioEncoderTest {

    // ──────────────────────────────────────────────────────
    // Basic encoding tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `encodeToBase64 empty byte array returns empty string`() {
        val result = AudioEncoder.encodeToBase64(ByteArray(0))

        assertNotNull("Result should not be null", result)
        assertEquals("Empty byte array should encode to empty string", "", result)
    }

    @Test
    fun `encodeToBase64 known PCM data produces expected Base64`() {
        // "test" in bytes = [116, 101, 115, 116]
        val pcmData = "test".toByteArray(Charsets.UTF_8)
        val result = AudioEncoder.encodeToBase64(pcmData)

        // Expected Base64 of "test" with NO_WRAP
        assertEquals("dGVzdA==", result)
    }

    @Test
    fun `encodeToBase64 single byte produces correct encoding`() {
        val pcmData = byteArrayOf(0)
        val result = AudioEncoder.encodeToBase64(pcmData)

        assertEquals("AA==", result)
    }

    @Test
    fun `encodeToBase64 produces valid Base64 string`() {
        val pcmData = ByteArray(256) { it.toByte() }
        val result = AudioEncoder.encodeToBase64(pcmData)

        assertNotNull("Result should not be null", result)
        assertTrue("Result should not be empty", result.isNotEmpty())

        // Verify it's valid Base64 (no line breaks per NO_WRAP)
        val base64Pattern = Regex("^[A-Za-z0-9+/]*=*$")
        assertTrue(
            "Result should match Base64 pattern (no line breaks)",
            result.matches(base64Pattern)
        )
    }

    @Test
    fun `encodeToBase64 large byte array produces valid Base64`() {
        // 10KB of random-ish data
        val pcmData = ByteArray(10_000) { (it * 7 % 256).toByte() }
        val result = AudioEncoder.encodeToBase64(pcmData)

        assertNotNull("Result should not be null", result)
        assertTrue("Result for large input should not be empty", result.isNotEmpty())

        // Should be longer than input (Base64 expands ~33%)
        assertTrue(
            "Base64 output should be longer than raw input",
            result.length > pcmData.size
        )

        // Verify no line breaks (NO_WRAP)
        assertFalse("Output should not contain newlines with NO_WRAP", result.contains('\n'))
        assertFalse("Output should not contain carriage returns", result.contains('\r'))
    }

    @Test
    fun `encodeToBase64 consistency same input produces same output`() {
        val pcmData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result1 = AudioEncoder.encodeToBase64(pcmData)
        val result2 = AudioEncoder.encodeToBase64(pcmData)

        assertEquals("Same input should always produce same output", result1, result2)
    }

    @Test
    fun `encodeToBase64 different inputs produce different outputs`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x04, 0x05, 0x06)

        val result1 = AudioEncoder.encodeToBase64(data1)
        val result2 = AudioEncoder.encodeToBase64(data2)

        assertNotEquals("Different inputs should produce different Base64", result1, result2)
    }

    @Test
    fun `encodeToBase64 all zero bytes produces correct repeated pattern`() {
        val pcmData = ByteArray(3) // 3 zero bytes
        val result = AudioEncoder.encodeToBase64(pcmData)

        // 3 zero bytes = 4 Base64 chars (AAAA) with NO_WRAP
        assertEquals("AAAA", result)

        // Verify all characters are 'A'
        result.forEach { assertEquals('A', it) }
    }

    @Test
    fun `encodeToBase64 all 0xFF bytes produces correct encoding`() {
        val pcmData = ByteArray(3) { 0xFF.toByte() }
        val result = AudioEncoder.encodeToBase64(pcmData)

        // 0xFF 0xFF 0xFF = //8= in Base64
        assertEquals("//8=", result)
    }
}
