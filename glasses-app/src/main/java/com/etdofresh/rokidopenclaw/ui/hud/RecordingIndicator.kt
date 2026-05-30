package com.etdofresh.rokidopenclaw.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.ui.theme.HudDimGreen
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen

/**
 * Displays the recording status in the centre of the HUD.
 *
 * When recording:
 * - Shows "● REC" with a blinking alpha effect (0.3 ↔ 1.0, 500ms cycle)
 *
 * When idle:
 * - Shows "◉ READY" in dim green, static
 */
@Composable
fun RecordingIndicator(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            // Blinking REC indicator
            val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "blink_alpha",
            )

            Text(
                text = "\u25CF REC",
                fontSize = 36.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = HudGreen,
                modifier = Modifier.alpha(alpha),
            )
        } else {
            // Static READY indicator
            Text(
                text = "\u25C9 READY",
                fontSize = 36.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = HudDimGreen,
            )
        }
    }
}
