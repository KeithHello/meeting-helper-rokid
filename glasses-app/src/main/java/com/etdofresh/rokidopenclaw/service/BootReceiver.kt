package com.etdofresh.rokidopenclaw.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.etdofresh.rokidopenclaw.MainActivity

/**
 * BroadcastReceiver that listens for [android.intent.action.BOOT_COMPLETED]
 * and launches the Meeting Helper after device boot.
 *
 * Starts the foreground service to keep the process alive, then launches
 * the MainActivity so the HUD is visible on the glasses display.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MeetingHelper/Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "BOOT_COMPLETED received — starting Meeting Helper")

            // Start the foreground service to keep the process alive
            MeetingForegroundService.start(context)

            // Launch the main HUD activity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
