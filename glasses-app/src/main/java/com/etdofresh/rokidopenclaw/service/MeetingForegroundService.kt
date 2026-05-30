package com.etdofresh.rokidopenclaw.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.etdofresh.rokidopenclaw.MainActivity
import com.etdofresh.rokidopenclaw.R
import com.etdofresh.rokidopenclaw.RokidOpenClawApp

/**
 * Minimal foreground service that keeps the Meeting Helper process alive
 * when the glasses display sleeps or the app goes to background.
 *
 * The service runs with START_STICKY so Android restarts it if killed.
 * Displays a low-priority notification so the user knows the service is active.
 */
class MeetingForegroundService : Service() {

    companion object {
        private const val TAG = "MeetingHelper/FgService"
        private const val NOTIFICATION_ID = 1

        /**
         * Convenience method to start the foreground service from any context.
         */
        fun start(context: android.content.Context) {
            val intent = Intent(context, MeetingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the service.
         */
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, MeetingForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MeetingForegroundService created")

        val notification = buildNotification(ready = true)
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand flags=$flags startId=$startId")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "MeetingForegroundService destroyed")
        super.onDestroy()
    }

    /**
     * Updates the foreground notification between "ready" and "recording" states.
     */
    fun updateNotification(recording: Boolean) {
        val notification = buildNotification(ready = !recording)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds a [Notification] for the foreground service.
     * @param ready `true` shows the standby text, `false` shows the recording text
     */
    private fun buildNotification(ready: Boolean): Notification {
        val contentTitle = getString(R.string.app_name)
        val contentText = if (ready) {
            getString(R.string.notif_standby)
        } else {
            getString(R.string.notif_recording)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags,
        )

        return NotificationCompat.Builder(this, RokidOpenClawApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
