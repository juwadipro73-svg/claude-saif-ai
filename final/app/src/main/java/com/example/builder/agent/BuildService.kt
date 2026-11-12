/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.builder.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Keeps the app process alive and shows a progress notification while an app is being generated.
 * Start it with [start], refresh the text with [updateProgress] and finish with [stop].
 */
class BuildService : Service() {

    companion object {
        const val CHANNEL_ID = "saif_build_agent_channel"
        const val NOTIF_ID = 9001
        const val ACTION_STOP = "com.example.builder.agent.ACTION_STOP"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_STATUS = "extra_status"

        fun start(context: Context, appName: String, initialStatus: String) {
            val request = Intent(context, BuildService::class.java)
                .putExtra(EXTRA_PROJECT_NAME, appName)
                .putExtra(EXTRA_STATUS, initialStatus)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(request)
            } else {
                context.startService(request)
            }
        }

        fun updateProgress(context: Context, status: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIF_ID, buildNotification(context, status))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BuildService::class.java))
        }

        private fun buildNotification(context: Context, status: String) =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("SAIF AI is building your app")
                .setContentText(status)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                    PendingIntent.getService(
                        context, 1,
                        Intent(context, BuildService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App builds", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Building application..."
        startForeground(NOTIF_ID, buildNotification(this, status))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
