/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.studio

import android.content.Context
import com.example.data.local.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdvancedEditingEngineConfig(
    // Video Rendering & Export Pipeline
    val defaultResolution: String = "1080p", // "720p", "1080p", "4K"
    val defaultFps: Int = 30, // 24, 30, 60
    val defaultBitrateTier: String = "Standard", // "Standard", "High", "Cinema Master"
    val hardwareAcceleration: Boolean = true,
    val strictPreExportQualityCheck: Boolean = true,

    // AI Timeline, Captions & Audio Mastering
    val protectManualEdits: Boolean = true,
    val autoGenerateCaptions: Boolean = true,
    val defaultCaptionStyle: String = "Karaoke", // "Karaoke", "Minimal", "Neon Outline"
    val autoAudioDucking: Boolean = true,
    val silenceTrimThresholdSec: Float = 0.8f, // 0.5f .. 2.5f
    val antiJitterStabilization: Boolean = false,
    val defaultColorLut: String = "Original" // "Original", "Teal & Orange", "Cyberpunk Night", "Kodak 500T", "Golden Hour", "Moody Noir"
)

/**
 * Persistent configuration store for the SAIF AI Advanced Editing Engine.
 *
 * Scoped per user account and directly wired into [com.example.studio.export.StudioExportEngine],
 * [com.example.studio.ui.StudioExportDialog], and [com.example.ui.components.SaifAiEditingEngine].
 */
object AdvancedEditingEnginePrefs {

    private const val PREFS_PREFIX = "saif_advanced_editing_engine_v1"

    val RESOLUTIONS = listOf("720p", "1080p", "4K")
    val FPS_OPTIONS = listOf(24, 30, 60)
    val BITRATE_TIERS = listOf("Standard", "High", "Cinema Master")
    val CAPTION_STYLES = listOf("Karaoke", "Minimal", "Neon Outline")
    val COLOR_LUTS = listOf("Original", "Teal & Orange", "Cyberpunk Night", "Kodak 500T", "Golden Hour", "Moody Noir")

    private val _state = MutableStateFlow(AdvancedEditingEngineConfig())
    val state: StateFlow<AdvancedEditingEngineConfig> = _state.asStateFlow()

    @Volatile
    private var boundKey: String? = null

    private fun keyForUser(uid: String?): String {
        val clean = uid?.trim()?.takeIf { it.isNotEmpty() } ?: "guest"
        return "${PREFS_PREFIX}_$clean"
    }

    @Synchronized
    fun bindUserAccount(context: Context, uid: String?, previousUid: String? = null): AdvancedEditingEngineConfig {
        val app = context.applicationContext
        val targetKey = keyForUser(uid)
        val targetPrefs = app.getSharedPreferences(targetKey, Context.MODE_PRIVATE)
        val sourceUid = previousUid?.takeIf { it.startsWith("guest:") }
        if (sourceUid != null && !targetPrefs.contains("defaultResolution")) {
            val sourcePrefs = app.getSharedPreferences(keyForUser(sourceUid), Context.MODE_PRIVATE)
            if (sourcePrefs.contains("defaultResolution")) {
                targetPrefs.edit().apply {
                    putString("defaultResolution", sourcePrefs.getString("defaultResolution", "1080p"))
                    putInt("defaultFps", sourcePrefs.getInt("defaultFps", 30))
                    putString("defaultBitrateTier", sourcePrefs.getString("defaultBitrateTier", "Standard"))
                    putBoolean("hardwareAcceleration", sourcePrefs.getBoolean("hardwareAcceleration", true))
                    putBoolean("strictPreExportQualityCheck", sourcePrefs.getBoolean("strictPreExportQualityCheck", true))
                    putBoolean("protectManualEdits", sourcePrefs.getBoolean("protectManualEdits", true))
                    putBoolean("autoGenerateCaptions", sourcePrefs.getBoolean("autoGenerateCaptions", true))
                    putString("defaultCaptionStyle", sourcePrefs.getString("defaultCaptionStyle", "Karaoke"))
                    putBoolean("autoAudioDucking", sourcePrefs.getBoolean("autoAudioDucking", true))
                    putFloat("silenceTrimThresholdSec", sourcePrefs.getFloat("silenceTrimThresholdSec", 0.8f))
                    putBoolean("antiJitterStabilization", sourcePrefs.getBoolean("antiJitterStabilization", false))
                    putString("defaultColorLut", sourcePrefs.getString("defaultColorLut", "Original"))
                }.apply()
            }
        }
        return load(app, uid)
    }

    @Synchronized
    fun load(context: Context, explicitUid: String? = null): AdvancedEditingEngineConfig {
        val uid = explicitUid ?: runCatching { AuthManager.currentUser.value?.uid }.getOrNull()
        val key = keyForUser(uid)
        boundKey = key
        val prefs = context.applicationContext.getSharedPreferences(key, Context.MODE_PRIVATE)
        val cfg = AdvancedEditingEngineConfig(
            defaultResolution = prefs.getString("defaultResolution", "1080p") ?: "1080p",
            defaultFps = prefs.getInt("defaultFps", 30).let { if (it in FPS_OPTIONS) it else 30 },
            defaultBitrateTier = prefs.getString("defaultBitrateTier", "Standard") ?: "Standard",
            hardwareAcceleration = prefs.getBoolean("hardwareAcceleration", true),
            strictPreExportQualityCheck = prefs.getBoolean("strictPreExportQualityCheck", true),
            protectManualEdits = prefs.getBoolean("protectManualEdits", true),
            autoGenerateCaptions = prefs.getBoolean("autoGenerateCaptions", true),
            defaultCaptionStyle = prefs.getString("defaultCaptionStyle", "Karaoke") ?: "Karaoke",
            autoAudioDucking = prefs.getBoolean("autoAudioDucking", true),
            silenceTrimThresholdSec = prefs.getFloat("silenceTrimThresholdSec", 0.8f).coerceIn(0.4f, 3.0f),
            antiJitterStabilization = prefs.getBoolean("antiJitterStabilization", false),
            defaultColorLut = prefs.getString("defaultColorLut", "Original") ?: "Original"
        )
        _state.value = cfg
        return cfg
    }

    @Synchronized
    fun update(
        context: Context,
        transform: (AdvancedEditingEngineConfig) -> AdvancedEditingEngineConfig
    ): AdvancedEditingEngineConfig {
        val cur = load(context)
        val next = transform(cur)
        val key = boundKey ?: keyForUser(runCatching { AuthManager.currentUser.value?.uid }.getOrNull())
        context.applicationContext.getSharedPreferences(key, Context.MODE_PRIVATE).edit()
            .putString("defaultResolution", next.defaultResolution)
            .putInt("defaultFps", next.defaultFps)
            .putString("defaultBitrateTier", next.defaultBitrateTier)
            .putBoolean("hardwareAcceleration", next.hardwareAcceleration)
            .putBoolean("strictPreExportQualityCheck", next.strictPreExportQualityCheck)
            .putBoolean("protectManualEdits", next.protectManualEdits)
            .putBoolean("autoGenerateCaptions", next.autoGenerateCaptions)
            .putString("defaultCaptionStyle", next.defaultCaptionStyle)
            .putBoolean("autoAudioDucking", next.autoAudioDucking)
            .putFloat("silenceTrimThresholdSec", next.silenceTrimThresholdSec)
            .putBoolean("antiJitterStabilization", next.antiJitterStabilization)
            .putString("defaultColorLut", next.defaultColorLut)
            .apply()
        _state.value = next
        return next
    }
}
