package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/** Foreground protection for a live task. Does not pretend to recover after force-stop. */
class ChatTaskService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("active_chat_task", "Active workspace tasks", NotificationManager.IMPORTANCE_LOW))
        startForeground(9003, NotificationCompat.Builder(this, "active_chat_task")
            .setSmallIcon(com.saifai.app.R.drawable.ic_launcher_monochrome)
            .setContentTitle("SAIF AI is working")
            .setContentText("Your workspace task is continuing")
            .setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saif:workspace-task").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
