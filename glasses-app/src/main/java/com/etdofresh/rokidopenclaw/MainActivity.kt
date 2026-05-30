package com.etdofresh.rokidopenclaw

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.etdofresh.rokidopenclaw.ui.MainViewModel
import com.etdofresh.rokidopenclaw.ui.hud.HudMainScreen
import com.etdofresh.rokidopenclaw.ui.theme.HudTheme
import com.etdofresh.rokidopenclaw.ui.theme.HudBlack
import androidx.compose.ui.graphics.toArgb

import android.annotation.SuppressLint

/**
 * Main Activity for the Meeting Helper on Rokid AI Glasses.
 *
 * Displays a minimalist HUD (green-on-black monochrome) showing
 * recording status, connection state, and Q&A results. Supports
 * gesture-based control via glasses touchpad/button.
 *
 * Key insight: The Rokid glasses physical button sends KeyEvent
 * (KEYCODE_ENTER for single press, KEYCODE_BACK for double-click),
 * NOT Ordered Broadcast. Override onKeyDown to intercept.
 *
 * Gesture mappings:
 * - Single press (KEYCODE_ENTER): start / stop meeting recording
 * - Two-finger swipe forward: trigger quick Q&A query
 * - Two-finger swipe back: end meeting + generate summary
 *
 * Reference: https://github.com/eikachiu/rokid-glass-skill
 */
@SuppressLint("GestureBackNavigation")
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var wakeLock: PowerManager.WakeLock? = null

    /** Receiver for Rokid glasses touchpad / button events (Ordered Broadcast). */
    private val spriteButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Broadcast received: $action")

            when (action) {
                // Short press / single click: toggle recording
                ACTION_SPRITE_BUTTON_UP, ACTION_SPRITE_BUTTON_CLICK -> {
                    Log.i(TAG, "→ Tap/Click — toggleRecording()")
                    viewModel.toggleRecording()
                }
                // Two-finger swipe forward: quick Q&A
                ACTION_TWO_FINGER_SWIPE_FORWARD -> {
                    Log.i(TAG, "→ Swipe forward — quickQuery()")
                    viewModel.quickQuery()
                }
                // Long press / two-finger swipe back: end meeting + summary
                ACTION_SPRITE_BUTTON_LONG_PRESS, ACTION_TWO_FINGER_SWIPE_BACK -> {
                    Log.i(TAG, "→ Long-press / Swipe-back — endMeeting()")
                    viewModel.endMeeting()
                }
                // Block double-click and system actions silently
                ACTION_SPRITE_BUTTON_DOUBLE_CLICK -> {
                    Log.d(TAG, "→ Double-click — blocked")
                }
                ACTION_AI_START, ACTION_SETTINGS_KEY -> {
                    Log.d(TAG, "→ Blocked system action: $action")
                }
                else -> Log.d(TAG, "→ Unhandled action: $action")
            }
            abortBroadcast()
        }
    }

    /** Launcher for runtime permission requests. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* permissions handled silently */ }

    /** Receiver for ADB-triggered control commands (explicit broadcasts). */
    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_MEETING -> {
                    Log.i(TAG, "ADB command: STOP_MEETING")
                    viewModel.endMeeting()
                }
                ACTION_QUICK_QUERY -> {
                    Log.i(TAG, "ADB command: QUICK_QUERY")
                    viewModel.quickQuery()
                }
                ACTION_TOGGLE_RECORDING -> {
                    Log.i(TAG, "ADB command: TOGGLE_RECORDING")
                    viewModel.toggleRecording()
                }
            }
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

        // Acquire partial wake lock to keep CPU alive (YodaOS kills idle processes aggressively)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MeetingHelper:WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes
        }
        Log.i(TAG, "WakeLock acquired")

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

    /**
     * Handle physical button key events.
     * KEYCODE_ENTER is the Rokid glasses button press (if system routes it to us).
     * KEYCODE_BACK is allowed through to enable exiting the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                Log.i(TAG, "→ ENTER key — toggleRecording()")
                viewModel.toggleRecording()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "WakeLock released")
        try {
            unregisterReceiver(spriteButtonReceiver)
        } catch (_: IllegalArgumentException) { }
        try {
            unregisterReceiver(controlReceiver)
        } catch (_: IllegalArgumentException) { }
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

    /**
     * Register BroadcastReceiver for Rokid glasses touchpad gestures.
     *
     * The actions registered match what com.rokid.os.sprite.assistserver
     * actually listens for on this device (verified via dumpsys).
     * Additionally, we register for DOUBLE_CLICK as a safety net
     * (block it to prevent app exit).
     */
    private fun registerSpriteButtonReceiver() {
        val filter = IntentFilter().apply {
            // Primary: what the system actually sends
            addAction(ACTION_SPRITE_BUTTON_UP)
            addAction(ACTION_SPRITE_BUTTON_LONG_PRESS)
            addAction(ACTION_TWO_FINGER_SWIPE_FORWARD)
            addAction(ACTION_TWO_FINGER_SWIPE_BACK)
            // Block these to prevent system takeover
            addAction(ACTION_SPRITE_BUTTON_DOUBLE_CLICK)
            addAction(ACTION_AI_START)
            addAction(ACTION_SETTINGS_KEY)
            priority = 100
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(spriteButtonReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(spriteButtonReceiver, filter)
        }

        // ADB control commands (explicit broadcasts)
        val controlFilter = IntentFilter().apply {
            addAction(ACTION_STOP_MEETING)
            addAction(ACTION_QUICK_QUERY)
            addAction(ACTION_TOGGLE_RECORDING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, controlFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(controlReceiver, controlFilter)
        }
    }

    companion object {
        private const val TAG = "MeetingHelper/MainActivity"

        // ── ADB control commands (explicit broadcasts) ──
        const val ACTION_TOGGLE_RECORDING = "com.etdofresh.rokidopenclaw.TOGGLE_RECORDING"
        const val ACTION_STOP_MEETING = "com.etdofresh.rokidopenclaw.STOP_MEETING"
        const val ACTION_QUICK_QUERY = "com.etdofresh.rokidopenclaw.QUICK_QUERY"

        // ── Actions actually sent by this Rokid device (verified via dumpsys) ──
        const val ACTION_SPRITE_BUTTON_CLICK =
            "com.android.action.ACTION_SPRITE_BUTTON_CLICK"
        const val ACTION_SPRITE_BUTTON_UP =
            "com.android.action.ACTION_SPRITE_BUTTON_UP"
        const val ACTION_SPRITE_BUTTON_LONG_PRESS =
            "com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"

        // ── Two-finger touchpad gestures ──
        const val ACTION_TWO_FINGER_SWIPE_FORWARD =
            "com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"
        const val ACTION_TWO_FINGER_SWIPE_BACK =
            "com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"

        // ── Blocked: prevent system takeover ──
        const val ACTION_SPRITE_BUTTON_DOUBLE_CLICK =
            "com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"
        const val ACTION_AI_START =
            "com.android.action.ACTION_AI_START"
        const val ACTION_SETTINGS_KEY =
            "com.android.action.ACTION_SETTINGS_KEY"
    }
}
