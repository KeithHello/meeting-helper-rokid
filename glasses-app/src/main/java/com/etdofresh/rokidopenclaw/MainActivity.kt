package com.etdofresh.rokidopenclaw

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.etdofresh.rokidopenclaw.service.MeetingForegroundService
import com.etdofresh.rokidopenclaw.ui.MainViewModel
import com.etdofresh.rokidopenclaw.ui.hud.HudMainScreen
import com.etdofresh.rokidopenclaw.ui.theme.HudTheme
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import androidx.compose.ui.graphics.toArgb

/**
 * Main Activity for the Meeting Helper on Rokid AI Glasses.
 *
 * Displays a minimalist HUD (green-on-black monochrome) showing
 * recording status, connection state, and Q&A results. Supports
 * gesture-based control via glasses physical buttons.
 *
 * Gesture mappings:
 * - Long press: start / stop meeting recording
 * - Double click: trigger quick Q&A query during a meeting
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    /** Receiver for Rokid glasses physical button events. */
    private val spriteButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SPRITE_BUTTON_LONG_PRESS -> viewModel.onLongPress()
                ACTION_SPRITE_BUTTON_DOUBLE_CLICK -> viewModel.onDoubleTap()
            }
        }
    }

    /** Launcher for runtime permission requests. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted — start the foreground service
            MeetingForegroundService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the micro-LED screen on while this activity is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set window background to black to match HUD theme
        window.decorView.setBackgroundColor(HudBlack.toArgb())

        // Initialise ViewModel with AndroidViewModelFactory (required for Application constructor)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[MainViewModel::class.java]

        // Request required runtime permissions
        requestPermissions()

        // Register button gesture receiver
        registerSpriteButtonReceiver()

        // Start the foreground service to keep the process alive
        MeetingForegroundService.start(this)

        // Render the HUD UI
        setContent {
            HudTheme {
                val uiState by viewModel.uiState.collectAsState()
                HudMainScreen(
                    uiState = uiState,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(spriteButtonReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    /** Request RECORD_AUDIO and POST_NOTIFICATIONS at runtime. */
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /** Register BroadcastReceiver for Rokid glasses sprite button gestures. */
    private fun registerSpriteButtonReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SPRITE_BUTTON_LONG_PRESS)
            addAction(ACTION_SPRITE_BUTTON_DOUBLE_CLICK)
            priority = 100
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(spriteButtonReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(spriteButtonReceiver, filter)
        }
    }

    companion object {
        /** Rokid glasses: long press action string — matches official CXR-S SDK broadcast. */
        const val ACTION_SPRITE_BUTTON_LONG_PRESS =
            "com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"

        /** Rokid glasses: double-click action string — matches official CXR-S SDK broadcast. */
        const val ACTION_SPRITE_BUTTON_DOUBLE_CLICK =
            "com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"
    }
}
