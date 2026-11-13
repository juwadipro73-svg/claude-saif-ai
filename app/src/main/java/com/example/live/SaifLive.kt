/*
 * SAIF AI - live voice: the one place the screens talk to.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.widget.Toast
import com.example.data.local.ProviderSettingsManager
import com.example.stt.OfflineSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SAIF AI's own live voice conversation (the purple microphone).
 *
 * The screens read the state below and call the functions; [SaifLiveService] owns the microphone and runs the
 * conversation ([LiveTurnLoop]: listen, recognise, answer, speak). Nothing here is tied to one AI provider: the
 * answer comes from the provider key saved in Settings, or from the connected chat agent.
 */
object SaifLive {
    const val STATUS_READY = "READY"
    const val STATUS_STARTING = "STARTING"
    const val STATUS_LIVE = "LIVE"

    private val _isLiveRunning = MutableStateFlow(false)
    private val _orbState = MutableStateFlow(LiveOrbState.IDLE)
    private val _connectionStatus = MutableStateFlow(STATUS_READY)
    private val _audioLevel = MutableStateFlow(0f)
    private val _userTranscript = MutableStateFlow("")
    private val _assistantTranscript = MutableStateFlow("")
    private val _isScreenSharing = MutableStateFlow(false)
    private val _liveError = MutableStateFlow<LiveError?>(null)

    /** True from the moment a session starts until it has ended. */
    val isLiveRunning: StateFlow<Boolean> = _isLiveRunning.asStateFlow()

    /** What the orb shows: idle, listening, thinking or speaking. */
    val orbState: StateFlow<LiveOrbState> = _orbState.asStateFlow()

    /** [STATUS_READY], [STATUS_STARTING] or [STATUS_LIVE]. */
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    /** Loudness of the microphone, 0 to 1, for the orb. */
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /** What the user just said and what SAIF AI answers, for the captions. */
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()
    val assistantTranscript: StateFlow<String> = _assistantTranscript.asStateFlow()

    /** True while pictures of the screen are shared with the conversation. */
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    /** A problem to show, or null. */
    val liveError: StateFlow<LiveError?> = _liveError.asStateFlow()

    /** The mode of the running session. */
    val liveMode = MutableStateFlow(LiveMode.NORMAL)

    /** Why the last attempt to share the screen failed; empty when it worked. */
    @Volatile var lastScreenShareError: String = ""
        internal set

    // ------------------------------------------------------------------ checks

    fun hasMicPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True when an API key is saved in the AI provider settings. */
    fun hasProviderKey(): Boolean =
        try { ProviderSettingsManager.loadState().apiKeys.any { it.isNotBlank() } } catch (_: Throwable) { false }

    /** True when the chat agent can answer: the user's own API key or Antigravity is connected. */
    fun hasConnectedAgent(): Boolean = try { LiveCodingBridge.available() } catch (_: Throwable) { false }

    /** True when a spoken question can be answered at all. */
    fun canAnswer(): Boolean = hasProviderKey() || hasConnectedAgent()

    // ------------------------------------------------------------------ control

    /**
     * Starts a live session. Returns false, and sets [liveError], when it cannot start; the reason is also shown
     * as a short message.
     */
    fun startLive(context: Context, mode: LiveMode): Boolean {
        val app = context.applicationContext
        liveMode.value = mode
        val problem = LiveStartCheck.problem(hasMicPermission(app), hasProviderKey(), hasConnectedAgent())
        if (problem != null) {
            _liveError.value = problem
            Toast.makeText(app, problem.message, Toast.LENGTH_LONG).show()
            return false
        }
        _liveError.value = null
        // Providers without a speech-to-text service are served by the on-device model.
        try { OfflineSpeech.prepare(app) } catch (_: Throwable) {}
        return try {
            app.startForegroundService(Intent(app, SaifLiveService::class.java).setAction(SaifLiveService.ACTION_START))
            true
        } catch (e: Exception) {
            _liveError.value = LiveError(
                title = "LIVE SESSION COULD NOT START",
                message = "The microphone service did not start: ${e.localizedMessage ?: e.javaClass.simpleName}",
                errorCode = LiveError.CODE_START_FAILED
            )
            Toast.makeText(app, "Live session start nahi ho payi.", Toast.LENGTH_LONG).show()
            false
        }
    }

    /** Ends the session and any screen sharing. A problem that was shown is cleared: the user chose to stop. */
    fun stopLive(context: Context) {
        val service = SaifLiveService.current
        if (service != null) {
            service.shutdown()
        } else {
            try {
                context.applicationContext.stopService(Intent(context.applicationContext, SaifLiveService::class.java))
            } catch (_: Exception) {
            }
            sessionEnded()
        }
        _liveError.value = null
    }

    fun setMicMuted(muted: Boolean) {
        SaifLiveService.current?.setMuted(muted)
    }

    /** Stops what SAIF AI is saying right now, so the user can speak. */
    fun interrupt() {
        SaifLiveService.current?.interrupt()
    }

    fun clearLiveError() {
        _liveError.value = null
    }

    /**
     * Must be called after the user allowed screen capture and before the capture permission is turned into a
     * [MediaProjection]: Android only hands it out to a running screen-capture service.
     */
    fun enterScreenShareMode(): Boolean = SaifLiveService.current?.enterScreenShareMode() ?: false

    /** Undoes [enterScreenShareMode] when sharing did not start after all. */
    fun leaveScreenShareMode() {
        SaifLiveService.current?.leaveScreenShareMode()
    }

    /** Starts sharing the screen with the running session. */
    fun startScreenShare(projection: MediaProjection, metrics: DisplayMetrics): Boolean =
        SaifLiveService.current?.startScreenShare(projection, metrics) ?: false

    fun stopScreenShare() {
        SaifLiveService.current?.stopScreenShare()
        liveMode.value = LiveMode.NORMAL
    }

    // ------------------------------------------------------------------ written by the service

    internal fun sessionStarting() {
        _userTranscript.value = ""
        _assistantTranscript.value = ""
        _audioLevel.value = 0f
        _orbState.value = LiveOrbState.IDLE
        _connectionStatus.value = STATUS_STARTING
        _isLiveRunning.value = true
    }

    internal fun sessionLive() {
        _connectionStatus.value = STATUS_LIVE
    }

    internal fun sessionEnded() {
        _isLiveRunning.value = false
        _isScreenSharing.value = false
        _connectionStatus.value = STATUS_READY
        _orbState.value = LiveOrbState.IDLE
        _audioLevel.value = 0f
        liveMode.value = LiveMode.NORMAL
    }

    internal fun showState(state: LiveOrbState) { _orbState.value = state }
    internal fun showLevel(level: Float) { _audioLevel.value = level.coerceIn(0f, 1f) }
    internal fun showUserText(text: String) {
        _userTranscript.value = text
        if (text.isNotBlank()) _liveError.value = null
    }
    internal fun showAssistantText(text: String) { _assistantTranscript.value = text }
    internal fun showError(error: LiveError) { _liveError.value = error }
    internal fun showScreenSharing(sharing: Boolean) { _isScreenSharing.value = sharing }
}
