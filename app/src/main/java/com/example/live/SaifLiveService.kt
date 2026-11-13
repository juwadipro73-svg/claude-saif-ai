/*
 * SAIF AI - live voice: the service that owns the microphone during a conversation.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import com.example.util.SaifMemoryCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps a live voice conversation running while the app is open or in the background.
 *
 * It is a foreground service of type "microphone" with a visible notification, so Android lets it keep listening
 * and the user always sees that the microphone is in use. While the screen is shared it is also of type
 * "mediaProjection". The conversation itself is [LiveTurnLoop]; what it does is published through [SaifLive].
 */
class SaifLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionLock = Any()
    private val generation = AtomicLong(0)

    private var loop: LiveTurnLoop? = null
    private var capture: LiveScreenCapture? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var active = false
    @Volatile private var muted = false
    @Volatile private var lastActivityAt = 0L
    private var inForeground = false
    private var latestStartId = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        current = this
        // A service started for the foreground has to show its notification at once.
        inForeground = goForeground(withScreen = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_START) {
            // Every start "for the foreground" has to be answered with the notification, also when the service is
            // still alive from a session that has just ended.
            inForeground = goForeground(withScreen = capture != null)
            beginSession()
        } else {
            shutdown()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        endSession()
        scope.cancel()
        if (current === this) current = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ session

    private fun beginSession() {
        if (!inForeground) {
            // Android did not let the service use the microphone in the foreground (permission taken away, or the
            // app is not visible). Without that it must not listen.
            SaifLive.showError(
                LiveError(
                    title = "LIVE SESSION COULD NOT START",
                    message = "Android did not allow the microphone service. Open SAIF AI and try again.",
                    errorCode = LiveError.CODE_START_FAILED
                )
            )
            shutdown()
            return
        }
        synchronized(sessionLock) {
            if (active) return
            active = true
        }
        val session = generation.incrementAndGet()
        lastActivityAt = SystemClock.elapsedRealtime()
        SaifLive.sessionStarting()
        holdWakeLock()
        playStartCue()

        scope.launch(Dispatchers.IO) {
            try {
                val prompt = LivePrompt.build(
                    memory = SaifMemoryCore.promptBlock(SaifMemoryCore.Features.LIVE),
                    autoLanguage = SaifMemoryCore.autoLanguage.value
                )
                if (!isCurrent(session)) return@launch
                val conversation = LiveTurnLoop(applicationContext, prompt, listenerFor(session))
                val accepted = synchronized(sessionLock) {
                    if (isCurrent(session)) { loop = conversation; true } else false
                }
                if (!accepted) return@launch
                if (muted) conversation.setMicMuted(true)
                if (!conversation.start()) {
                    // The loop has already said why (microphone busy or not allowed).
                    endFromEngine(session)
                    return@launch
                }
                if (!isCurrent(session)) {
                    conversation.stop()
                    return@launch
                }
                SaifLive.sessionLive()
                mainHandler.post { if (isCurrent(session)) refreshNotification() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Live voice did not start", e)
                if (isCurrent(session)) {
                    SaifLive.showError(
                        LiveError("VOICE ENGINE FAILED", e.message ?: "Could not start live voice.", LiveError.CODE_ENGINE_FAILED)
                    )
                    endFromEngine(session)
                }
            }
        }

        // End a session nobody uses, and keep the processor awake while one is used.
        scope.launch {
            while (isActive && isCurrent(session)) {
                delay(LiveSessionRules.IDLE_CHECK_MS)
                if (!isCurrent(session)) break
                if (LiveSessionRules.isIdleTooLong(lastActivityAt, SystemClock.elapsedRealtime())) {
                    endFromEngine(session)
                    break
                }
                mainHandler.post { if (isCurrent(session)) holdWakeLock() }
            }
        }
    }

    private fun isCurrent(session: Long): Boolean = active && generation.get() == session

    private fun listenerFor(session: Long) = object : LiveTurnListener {
        override fun onState(state: LiveOrbState) {
            if (isCurrent(session)) SaifLive.showState(state)
        }

        override fun onUserTranscript(text: String) {
            if (isCurrent(session)) SaifLive.showUserText(text)
        }

        override fun onAssistantTranscript(text: String) {
            if (isCurrent(session)) SaifLive.showAssistantText(text)
        }

        override fun onAudioLevel(level: Float) {
            if (isCurrent(session)) SaifLive.showLevel(level)
        }

        override fun onActivity() {
            if (isCurrent(session)) lastActivityAt = SystemClock.elapsedRealtime()
        }

        override fun onTurnCompleted(user: String, assistant: String) {
            if (!isCurrent(session) || user.isBlank() || assistant.isBlank()) return
            lastActivityAt = SystemClock.elapsedRealtime()
            // What the user tells SAIF AI by voice is remembered like what they type in the chat.
            try { SaifMemoryCore.learn(SaifMemoryCore.Features.LIVE, user, assistant) } catch (_: Throwable) {}
        }

        override fun onError(error: LiveError) {
            if (isCurrent(session)) SaifLive.showError(error)
        }
    }

    /** The engine gave up (start failed, or nobody spoke for a long time): close down, keep the reason on screen. */
    private fun endFromEngine(session: Long) {
        mainHandler.post { if (isCurrent(session)) shutdown() }
    }

    /** Ends the session and the service. Safe to call more than once. */
    fun shutdown() {
        endSession()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        // With the start number, a new start that arrived in the meantime is not stopped by mistake.
        if (latestStartId >= 0) stopSelf(latestStartId) else stopSelf()
    }

    private fun endSession() {
        val running: LiveTurnLoop?
        synchronized(sessionLock) {
            if (!active && loop == null && capture == null) return
            active = false
            generation.incrementAndGet()
            running = loop
            loop = null
        }
        val sharing = capture
        capture = null
        try { sharing?.stop() } catch (_: Exception) {}
        try { running?.stop() } catch (e: Exception) { Log.w(TAG, "The conversation did not stop cleanly", e) }
        muted = false
        releaseWakeLock()
        SaifLive.sessionEnded()
    }

    fun setMuted(value: Boolean) {
        muted = value
        loop?.setMicMuted(value)
    }

    fun interrupt() {
        lastActivityAt = SystemClock.elapsedRealtime()
        loop?.interrupt()
    }

    // ------------------------------------------------------------------ screen sharing

    /** Makes this a screen-capture service. Android asks for that before it hands out a [MediaProjection]. */
    fun enterScreenShareMode(): Boolean = active && goForeground(withScreen = true)

    fun leaveScreenShareMode() {
        if (capture == null && active) goForeground(withScreen = false)
    }

    fun startScreenShare(projection: MediaProjection, metrics: DisplayMetrics): Boolean {
        if (!active) {
            SaifLive.lastScreenShareError = "The live session is not running."
            return false
        }
        capture?.stop()
        capture = null
        val session = generation.get()
        val sharing = LiveScreenCapture(
            projection = projection,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            onFrame = { jpeg -> if (isCurrent(session)) loop?.onScreenFrame(jpeg) },
            onEnded = { mainHandler.post { if (isCurrent(session)) stopScreenShare() } }
        )
        // The conversation keeps a picture only while this is true, so it is set before the first one arrives.
        SaifLive.showScreenSharing(true)
        return if (sharing.start()) {
            capture = sharing
            SaifLive.lastScreenShareError = ""
            SaifLive.liveMode.value = LiveMode.SCREEN_SHARE
            refreshNotification()
            true
        } else {
            SaifLive.showScreenSharing(false)
            SaifLive.lastScreenShareError = sharing.lastError.ifBlank { "The screen could not be captured." }
            false
        }
    }

    fun stopScreenShare() {
        val sharing = capture
        capture = null
        try { sharing?.stop() } catch (_: Exception) {}
        SaifLive.showScreenSharing(false)
        SaifLive.liveMode.value = LiveMode.NORMAL
        loop?.onScreenFrame(null)
        if (active) goForeground(withScreen = false)
    }

    // ------------------------------------------------------------------ notification, wake lock, start sound

    private fun goForeground(withScreen: Boolean): Boolean = try {
        val notification = buildNotification(withScreen)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (withScreen) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not show the live voice notification", e)
        false
    }

    private fun refreshNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(capture != null))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(sharingScreen: Boolean): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Live voice", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shown while SAIF AI is listening in a live conversation"
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SAIF AI Live")
            .setContentText(
                if (sharingScreen) "Live baatcheet chal rahi hai - screen share on"
                else "Live baatcheet chal rahi hai - mic on"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        packageManager.getLaunchIntentForPackage(packageName)?.let { open ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, open, immutable))
        }
        val stop = Intent(this, SaifLiveService::class.java).setAction(ACTION_STOP)
        builder.addAction(
            Notification.Action.Builder(null, "Stop", PendingIntent.getService(this, 1, stop, immutable)).build()
        )
        return builder.build()
    }

    private fun holdWakeLock() {
        try {
            val held = wakeLock
            if (held != null && held.isHeld) return
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SaifAI:LiveVoice")
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_MS)
            wakeLock = lock
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    /** A short, quiet tone that tells the user the conversation has started. */
    private fun playStartCue() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
            mainHandler.postDelayed({ try { tone.release() } catch (_: Exception) {} }, 400L)
        } catch (_: Exception) {
            // No tone is not a reason to refuse the session.
        }
    }

    companion object {
        private const val TAG = "SaifLiveService"
        private const val CHANNEL_ID = "saif_live_voice"
        private const val NOTIFICATION_ID = 7421
        private const val WAKE_LOCK_MS = 10L * 60L * 1000L

        const val ACTION_START = "com.example.live.action.START"
        const val ACTION_STOP = "com.example.live.action.STOP"

        /** The running service, or null. The screens reach it through [SaifLive]. */
        @Volatile var current: SaifLiveService? = null
            private set
    }
}
