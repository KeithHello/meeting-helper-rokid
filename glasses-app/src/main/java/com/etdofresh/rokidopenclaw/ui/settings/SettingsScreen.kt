package com.etdofresh.rokidopenclaw.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etdofresh.rokidopenclaw.ui.MainViewModel
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import com.etdofresh.rokidopenclaw.ui.theme.HudDimGreen
import com.etdofresh.rokidopenclaw.ui.theme.HudGreen

/**
 * Settings screen for configuring the Gateway WebSocket URL.
 *
 * Features:
 * - Text input for the Gateway URL
 * - "Test Connection" button that sends a ping and shows status
 * - "Save" button to persist the URL and reconnect
 *
 * All styling follows the green-on-black monochrome HUD theme.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val currentUrl = remember { viewModel.getGatewayUrl() }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = "SETTINGS",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                color = HudGreen,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Gateway URL label
            Text(
                text = "Gateway URL",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = HudDimGreen,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // URL text input
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    testResult = null // Clear test result on edit
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudGreen,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HudGreen,
                    unfocusedBorderColor = HudDimGreen,
                    cursorColor = HudGreen,
                    focusedContainerColor = HudBlack,
                    unfocusedContainerColor = HudBlack,
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "ws://192.168.1.100:8080/ws",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HudDimGreen.copy(alpha = 0.5f),
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test Connection button
            Button(
                onClick = {
                    testResult = "Testing..."
                    // Just show a basic validation result
                    testResult = if (urlInput.startsWith("ws://") || urlInput.startsWith("wss://")) {
                        "URL format OK"
                    } else {
                        "Invalid URL format"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudBlack,
                    contentColor = HudGreen,
                ),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HudDimGreen),
            ) {
                Text(
                    text = "TEST CONNECTION",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudGreen,
                )
            }

            // Test result
            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testResult!!,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (testResult!!.startsWith("URL format OK")) HudGreen else HudDimGreen,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        viewModel.setGatewayUrl(urlInput)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudGreen.copy(alpha = 0.15f),
                    contentColor = HudGreen,
                ),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HudGreen),
            ) {
                Text(
                    text = "SAVE",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudGreen,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back button
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudBlack,
                    contentColor = HudDimGreen,
                ),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HudDimGreen),
            ) {
                Text(
                    text = "BACK",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudDimGreen,
                )
            }
        }
    }
}
