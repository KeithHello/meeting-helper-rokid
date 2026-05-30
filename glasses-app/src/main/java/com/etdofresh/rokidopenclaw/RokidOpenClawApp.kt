package com.etdofresh.rokidopenclaw

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import com.etdofresh.rokidopenclaw.service.MeetingForegroundService
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application class for the Meeting Helper on Rokid AI Glasses.
 *
 * Initializes the notification channel for the foreground service
 * and provides a shared OkHttpClient singleton for WebSocket communication.
 */
class RokidOpenClawApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "meeting_channel"

        /**
         * Shared OkHttpClient singleton used by GatewayClient for WebSocket connections.
         * Configured with 30s connect/read/write timeouts suitable for real-time audio streaming.
         */
        lateinit var httpClient: OkHttpClient
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Create the notification channel for foreground service (required on API 26+)
        createNotificationChannel()

        // Initialize shared OkHttpClient
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout for streaming WebSocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // Set default uncaught exception handler for crash diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e(
                "MeetingHelper/Crash",
                "Uncaught exception on thread ${thread.name}: ${throwable.message}",
                throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Start foreground service early to keep process alive on Android Go
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, MeetingForegroundService::class.java))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Meeting Helper foreground service notification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
