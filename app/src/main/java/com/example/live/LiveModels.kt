/*
 * SAIF AI - live voice: the types shared by the engine, the service and the screens.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.live

/** What the orb shows. */
enum class LiveOrbState { IDLE, LISTENING, THINKING, SPEAKING }

/** How a live session was started. */
enum class LiveMode(val label: String, val description: String) {
    /** Talk only: microphone in, SAIF AI voice out. */
    NORMAL("Normal voice", "Sirf baat karein — mic se boliye, SAIF AI jawab dega"),

    /** Talk and share the screen: SAIF AI can also look at what is on the screen. */
    SCREEN_SHARE("Share screen", "SAIF AI aapki screen dekh kar madad karega")
}

/**
 * A problem the live screen shows to the user. [errorCode] decides which button is offered:
 * [CODE_NO_AI] opens the settings, [CODE_NO_MICROPHONE] asks for the permission, everything else offers a retry.
 */
data class LiveError(
    val title: String,
    val message: String,
    val errorCode: String,
    val actionLabel: String = "RETRY"
) {
    companion object {
        const val CODE_NO_AI = "API_KEY_MISSING"
        const val CODE_NO_MICROPHONE = "MIC_PERMISSION_REQUIRED"
        const val CODE_START_FAILED = "LIVE_START_FAILED"
        const val CODE_ENGINE_FAILED = "VOICE_ENGINE_FAILED"
    }
}

/** Receives what a running conversation does. The calls come from worker threads. */
interface LiveTurnListener {
    fun onState(state: LiveOrbState)
    fun onUserTranscript(text: String)
    fun onAssistantTranscript(text: String)
    fun onAudioLevel(level: Float)

    /** The user spoke or interrupted: the session is in use. */
    fun onActivity()
    fun onTurnCompleted(user: String, assistant: String)
    fun onError(error: LiveError)
}

/** Decides whether a live session may start, and says why not. */
object LiveStartCheck {
    /**
     * @param hasProviderKey an API key is saved in the AI provider settings
     * @param agentConnected the chat agent can answer (the user's own API key or Antigravity is connected)
     * @return null when the session may start, otherwise the reason to show
     */
    fun problem(hasMicPermission: Boolean, hasProviderKey: Boolean, agentConnected: Boolean): LiveError? = when {
        !hasMicPermission -> LiveError(
            title = "MICROPHONE PERMISSION REQUIRED",
            message = "Allow microphone access to start live baatcheet.",
            errorCode = LiveError.CODE_NO_MICROPHONE,
            actionLabel = "ALLOW MICROPHONE"
        )
        !hasProviderKey && !agentConnected -> LiveError(
            title = "AI CONNECTION REQUIRED",
            message = "Add your API key in Settings, or connect Antigravity, to start live baatcheet.",
            errorCode = LiveError.CODE_NO_AI,
            actionLabel = "OPEN SETTINGS"
        )
        else -> null
    }
}

/** Small rules of a live session that need no Android class, so they can be tested. */
object LiveSessionRules {
    /** A session nobody has spoken into for this long is ended, so the microphone is not held for nothing. */
    const val IDLE_LIMIT_MS: Long = 30L * 60L * 1000L

    /** How often the idle time is checked. */
    const val IDLE_CHECK_MS: Long = 15_000L

    /** Longest side of a shared-screen picture, in pixels. Enough to read text, small enough to send quickly. */
    const val SCREEN_FRAME_MAX_SIDE: Int = 1024

    /** At most one shared-screen picture per this many milliseconds. */
    const val SCREEN_FRAME_INTERVAL_MS: Long = 1_000L

    fun isIdleTooLong(lastActivityMs: Long, nowMs: Long, limitMs: Long = IDLE_LIMIT_MS): Boolean =
        nowMs - lastActivityMs >= limitMs

    /**
     * Size for a shared-screen picture: the longest side becomes at most [maxSide], the shape stays the same, and
     * both sides are even numbers of at least 2 (video surfaces and JPEG encoders want that).
     */
    fun frameSize(width: Int, height: Int, maxSide: Int = SCREEN_FRAME_MAX_SIDE): Pair<Int, Int> {
        val w = width.coerceAtLeast(2)
        val h = height.coerceAtLeast(2)
        val longest = maxOf(w, h)
        val scale = if (longest > maxSide) maxSide.toDouble() / longest.toDouble() else 1.0
        fun even(value: Double): Int {
            val rounded = Math.round(value).toInt().coerceAtLeast(2)
            return if (rounded % 2 == 0) rounded else rounded - 1
        }
        return even(w * scale) to even(h * scale)
    }

    /** Short line under the title of the live screen and in the notification. */
    fun statusLine(running: Boolean, sharingScreen: Boolean): String = when {
        !running -> "Ready"
        sharingScreen -> "Helping with your screen"
        else -> "Voice conversation"
    }
}
