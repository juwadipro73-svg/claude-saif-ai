package com.example.util

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

/** Visibility follows all activities, not just the chat composable. */
object TaskNotifications : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val activeTasks = mutableSetOf<String>()
    fun initialize(app: Application) { app.registerActivityLifecycleCallbacks(this) }
    @Synchronized fun begin(taskId: String) { activeTasks.add(taskId) }
    @Synchronized fun finish(context: Context, taskId: String, successful: Boolean) {
        if (!activeTasks.remove(taskId) || !successful || started > 0) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("task_completion", "Completed tasks", NotificationManager.IMPORTANCE_DEFAULT))
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, "task_completion")
            .setSmallIcon(com.saifai.app.R.drawable.ic_launcher_monochrome)
            .setContentTitle("SAIF AI")
            .setContentText("Work done sir")
            .setAutoCancel(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        try { notifications.notify(taskId, 9002, notification) } catch (_: SecurityException) {
            // Notification permission is optional; never fail the completed task.
        }
    }
    @Synchronized override fun onActivityStarted(activity: Activity) { started++ }
    @Synchronized override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
