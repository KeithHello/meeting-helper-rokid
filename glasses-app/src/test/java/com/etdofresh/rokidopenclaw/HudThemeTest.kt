package com.etdofresh.rokidopenclaw

import androidx.compose.ui.graphics.Color
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import com.etdofresh.rokidopenclaw.ui.theme.HudDimGreen
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen
import com.etdofresh.rokidopenclaw.ui.theme.HudFont
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HUD theme color constants and font definitions.
 *
 * Verifies that the HUD color values match the specification
 * and that the monospace font family is correctly assigned.
 */
class HudThemeTest {

    // ──────────────────────────────────────────────────────
    // HudGreen color tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `HudGreen has correct ARGB value`() {
        val expected = Color(0xFF00FF41)
        assertEquals("HudGreen should match Color(0xFF00FF41)", expected, HudGreen)
    }

    @Test
    fun `HudGreen red channel is 0`() {
        assertEquals("HudGreen red should be 0.0", 0.0f, HudGreen.red)
    }

    @Test
    fun `HudGreen green channel is 1_0`() {
        assertEquals("HudGreen green should be 1.0", 1.0f, HudGreen.green)
    }

    @Test
    fun `HudGreen blue channel is approximately 0_255`() {
        // 0x41 / 255 ≈ 0.255
        assertEquals("HudGreen blue should be ~0.255", 0.25490198f, HudGreen.blue)
    }

    @Test
    fun `HudGreen alpha is fully opaque`() {
        assertEquals("HudGreen alpha should be 1.0", 1.0f, HudGreen.alpha)
    }

    // ──────────────────────────────────────────────────────
    // HudDimGreen color tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `HudDimGreen has correct ARGB value`() {
        val expected = Color(0xFF00AA2A)
        assertEquals("HudDimGreen should match Color(0xFF00AA2A)", expected, HudDimGreen)
    }

    @Test
    fun `HudDimGreen is darker than HudGreen`() {
        // HudDimGreen (0xAA) < HudGreen (0xFF) for the green channel
        assertTrue(
            "HudDimGreen green channel should be less than HudGreen",
            HudDimGreen.green < HudGreen.green
        )
    }

    @Test
    fun `HudDimGreen red channel is 0`() {
        assertEquals("HudDimGreen red should be 0.0", 0.0f, HudDimGreen.red)
    }

    // ──────────────────────────────────────────────────────
    // HudBlack color tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `HudBlack equals Color_Black`() {
        assertEquals("HudBlack should equal Color.Black", Color.Black, HudBlack)
    }

    @Test
    fun `HudBlack all channels are 0`() {
        assertEquals("HudBlack red should be 0", 0.0f, HudBlack.red)
        assertEquals("HudBlack green should be 0", 0.0f, HudBlack.green)
        assertEquals("HudBlack blue should be 0", 0.0f, HudBlack.blue)
        assertEquals("HudBlack alpha should be 1.0", 1.0f, HudBlack.alpha)
    }

    // ──────────────────────────────────────────────────────
    // HudFont tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `HudFont is FontFamily_Monospace`() {
        assertEquals(
            "HudFont should be FontFamily.Monospace",
            androidx.compose.ui.text.font.FontFamily.Monospace,
            HudFont
        )
    }

    // ──────────────────────────────────────────────────────
    // Contrast / relationship tests
    // ──────────────────────────────────────────────────────

    @Test
    fun `HudGreen and HudBlack have enough contrast`() {
        // HudGreen has a non-zero luminance; HudBlack is zero
        val greenLuminance = HudGreen.green  // 1.0 — this is very bright
        assertTrue("HudGreen should be visibly distinct from HudBlack", greenLuminance > 0.5f)
    }

    @Test
    fun `HudDimGreen and HudGreen are distinct colors`() {
        assertNotEquals("HudDimGreen should be different from HudGreen", HudGreen, HudDimGreen)
    }
}
