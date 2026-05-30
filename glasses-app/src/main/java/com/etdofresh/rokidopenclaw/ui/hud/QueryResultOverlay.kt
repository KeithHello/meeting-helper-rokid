package com.etdofresh.rokidopenclaw.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.network.QueryResultMessage
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen

/**
 * Animated overlay that slides up from the bottom of the screen to display
 * quick Q&A results during a meeting.
 *
 * Each result item is prefixed with "▸" and limited to 3 items.
 * The overlay auto-dismisses after 3 seconds (managed by [MainViewModel]).
 */
@Composable
fun QueryResultOverlay(
    queryResult: QueryResultMessage?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = queryResult != null && queryResult.items.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudBlack.copy(alpha = 0.92f))
                .padding(16.dp),
        ) {
            queryResult?.items?.take(3)?.forEach { item ->
                Text(
                    text = "\u25B8 $item",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudGreen,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
