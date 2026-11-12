/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui.components

import android.content.Context
import android.widget.Toast
import com.saifai.core.SaifVoiceEngine

/**
 * 100% Cleanroom SAIF AI Live Voice session stub and session holder.
 * Replaces legacy automation session without any third-party dependencies.
 */
object SaifLiveSession {
    @Volatile var pendingScreenShare: Boolean = false
}

/**
 * Starts a cleanroom SAIF AI voice session with Kabir voice authority.
 */
fun startNormal(context: Context, isLightMode: Boolean = false, onStarted: () -> Unit = {}) {
    Toast.makeText(context, "SAIF AI Kabir Voice Active", Toast.LENGTH_SHORT).show()
    SaifVoiceEngine.initialize(context)
    SaifVoiceEngine.speak("SAIF AI ready.")
    onStarted()
}
