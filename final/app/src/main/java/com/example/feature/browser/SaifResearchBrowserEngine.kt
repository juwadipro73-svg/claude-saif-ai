/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.feature.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 100% Original SAIF AI Research Browser State.
 * Implements docs/spec/browser_bridge.md.
 */
data class SaifBrowserState(
    val currentUrl: String = "http://127.0.0.1:8767/status",
    val pageTitle: String = "SAIF Research Browser",
    val loadProgress: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val consoleLogs: List<String> = emptyList()
)

class SaifResearchBrowserEngine {

    private val _state = MutableStateFlow(SaifBrowserState())
    val state: StateFlow<SaifBrowserState> = _state.asStateFlow()

    fun updateUrl(newUrl: String) {
        _state.value = _state.value.copy(currentUrl = newUrl)
    }

    fun updateProgress(progress: Int) {
        _state.value = _state.value.copy(loadProgress = progress)
    }

    fun updateTitle(title: String) {
        _state.value = _state.value.copy(pageTitle = title)
    }

    fun appendConsoleLog(logMessage: String) {
        _state.value = _state.value.copy(consoleLogs = _state.value.consoleLogs + logMessage)
    }
}
