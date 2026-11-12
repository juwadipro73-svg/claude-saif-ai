/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 100% Original SAIF AI Settings Engine.
 * Implements docs/spec/accounts_settings.md.
 */
data class SaifAppSettings(
    val activeProviderId: String = "saif_universal",
    val activeModelId: String = "saif-advance-v1",
    val themeMode: String = "SYSTEM",
    val autoApproveSafeTools: Boolean = false,
    val autoScrollChat: Boolean = true,
    val voiceAuthority: String = "Kabir (Locked Internal Authority)"
)

class SaifSettingsEngine {

    private val _settings = MutableStateFlow(SaifAppSettings())
    val settings: StateFlow<SaifAppSettings> = _settings.asStateFlow()

    fun setProvider(providerId: String) {
        _settings.value = _settings.value.copy(activeProviderId = providerId)
    }

    fun setModel(modelId: String) {
        _settings.value = _settings.value.copy(activeModelId = modelId)
    }

    fun setTheme(theme: String) {
        _settings.value = _settings.value.copy(themeMode = theme)
    }

    fun toggleAutoApprove(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoApproveSafeTools = enabled)
    }
}
