package com.example.utils

import android.content.Context
import com.example.ui.components.VOICE_PROFILES
import com.example.ui.components.VoiceProfile

/**
 * Single internal voice authority for SAIF AI.
 *
 * Kabir's voice profile ("Kabir") is permanently configured internally as the default
 * and active voice across chat responses, background TTS, live conversation, and video
 * narration, requiring zero user TTS setup and exposing no voice-picker UI.
 */
object SaifVoice {

    const val SAIF_ADVANCE = "SaifAdvance"
    const val KABIR = "Kabir"

    /** Kabir is permanently configured as the internal default voice everywhere. */
    const val DEFAULT = KABIR

    private const val VOICE_PREFS = "VoiceSettings"
    private const val BRAND_PREFS = "saif_brand_kit"
    private const val KEY_SELECTED = "selected_voice_id"

    private val LIVE_VOICE_NAMES = mapOf(
        KABIR to "Charon",
        SAIF_ADVANCE to "Puck",
        "Priya" to "Aoede"
    )

    /** Permanently returns Kabir's voice ID ("Kabir") for all speech synthesis. */
    fun selectedId(context: Context): String {
        val c = context.applicationContext
        val prefs = c.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SELECTED, null) != KABIR) {
            prefs.edit().putString(KEY_SELECTED, KABIR).apply()
        }
        return KABIR
    }

    /** Returns Kabir's VoiceProfile. */
    fun selected(context: Context): VoiceProfile =
        VOICE_PROFILES.firstOrNull { it.id == KABIR } ?: VOICE_PROFILES.first()

    /** Keeps internal storage pinned to Kabir so legacy callers remain consistent. */
    fun select(context: Context, @Suppress("UNUSED_PARAMETER") id: String) {
        val c = context.applicationContext
        c.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED, KABIR).apply()
        c.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE).edit().putString("voice", KABIR).apply()
    }

    /** Permanently uses Kabir for studio & video narration. */
    fun narrationVoiceId(context: Context): String = selectedId(context)

    fun narrationProfile(context: Context): VoiceProfile = selected(context)

    /** Gemini-Live voice mapped to Kabir ("Charon"). */
    fun liveVoiceName(context: Context): String = LIVE_VOICE_NAMES[selectedId(context)] ?: "Charon"

    fun liveVoiceFallbacks(context: Context): List<String> {
        val first = liveVoiceName(context)
        return (listOf(first) + LIVE_VOICE_NAMES.values).distinct()
    }

    fun displayName(@Suppress("UNUSED_PARAMETER") context: Context): String = "SAIF AI Voice"
}
