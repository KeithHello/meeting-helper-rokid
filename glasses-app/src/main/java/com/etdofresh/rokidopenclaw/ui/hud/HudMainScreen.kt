package com.etdofresh.rokidopenclaw.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.ui.HudUiState
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen

/**
 * Root Composable for the Meeting Helper HUD.
 *
 * Layout (vertical, top-to-bottom):
 * - [StatusBar] — connection state, WiFi, battery
 * - Spacer (weight = 1)
 * - [RecordingIndicator] — centred REC / READY indicator
 * - [SummarySentOverlay] — confirmation overlay
 * - Spacer (weight = 1)
 * - [QueryResultOverlay] — slides up from bottom for 3 seconds
 */
@Composable
fun HudMainScreen(
    uiState: HudUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HudBlack),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top: status bar
            StatusBar(
                connectionState = uiState.connectionState,
                wifiEnabled = uiState.wifiEnabled,
                batteryLevel = uiState.batteryLevel,
                modifier = Modifier.fillMaxWidth(),
            )

            // Centre: recording indicator
            Spacer(modifier = Modifier.weight(1f))

            RecordingIndicator(
                isRecording = uiState.isRecording,
                modifier = Modifier.fillMaxWidth(),
            )

            // Summary sent overlay
            if (uiState.summarySent) {
                SummarySentOverlay()
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom: query result overlay
            QueryResultOverlay(
                queryResult = uiState.queryResult,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Brief overlay shown when a summary has been sent.
 * Displays "Summary Sent ✅" in the center area.
 */
@Composable
private fun SummarySentOverlay() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Summary Sent \u2705",
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            color = HudGreen,
        )
    }
}
