package com.etdofresh.rokidopenclaw.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.network.ConnectionState
import com.etdofresh.rokidopenclaw.ui.theme.HudDimGreen
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen

/**
 * Top-of-screen status bar showing connection state and device vitals.
 *
 * Layout (horizontal):
 * - Left: connection indicator icon
 * - Centre: "WS" label
 * - Right: "BAT 85%" battery indicator
 *
 * Designed for the 480×640 micro-LED display — compact and readable.
 */
@Composable
fun StatusBar(
    connectionState: ConnectionState,
    wifiEnabled: Boolean,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: connection indicator
        val (connIcon, connColor) = when (connectionState) {
            ConnectionState.CONNECTED -> "\u25CF" to HudGreen
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> "\u25D0" to HudGreen
            ConnectionState.DISCONNECTED -> "\u25CB" to HudDimGreen
            ConnectionState.ERROR -> "\u2715" to HudDimGreen
        }

        Text(
            text = connIcon,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = connColor,
        )

        // Centre: WS label
        Text(
            text = "WS",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = HudGreen,
        )

        // Right: battery level
        Text(
            text = "BAT $batteryLevel%",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = HudDimGreen,
        )
    }
}
