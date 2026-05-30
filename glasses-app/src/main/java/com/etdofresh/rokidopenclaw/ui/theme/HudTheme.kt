package com.etdofresh.rokidopenclaw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Primary green color for HUD elements (bright terminal-style green). */
val HudGreen = Color(0xFF00FF41)

/** Dimmed green for secondary / less prominent HUD elements. */
val HudDimGreen = Color(0xFF00AA2A)

/** True black background for micro-LED display. */
val HudBlack = Color.Black

/** Monospace font used throughout the HUD for retro terminal aesthetic. */
val HudFont = FontFamily.Monospace

private val HudColorScheme = darkColorScheme(
    primary = HudGreen,
    secondary = HudDimGreen,
    background = HudBlack,
    surface = HudBlack,
    onPrimary = HudBlack,
    onSecondary = HudBlack,
    onBackground = HudGreen,
    onSurface = HudGreen,
)

/**
 * Typography scale optimised for the 480×640 monochrome green micro-LED display.
 * Uses monospace font for a terminal-like aesthetic with large readable sizes.
 */
val HudTypography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = HudFont,
        fontSize = 18.sp,
        color = HudGreen,
    ),
    bodyMedium = TextStyle(
        fontFamily = HudFont,
        fontSize = 14.sp,
        color = HudGreen,
    ),
    bodySmall = TextStyle(
        fontFamily = HudFont,
        fontSize = 12.sp,
        color = HudDimGreen,
    ),
    titleLarge = TextStyle(
        fontFamily = HudFont,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = HudGreen,
    ),
)

/**
 * Applies the HUD monochrome green-on-black theme to all children.
 *
 * Usage:
 * ```
 * HudTheme {
 *     // Composable content here
 * }
 * ```
 */
@Composable
fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = HudTypography,
        content = content,
    )
}
