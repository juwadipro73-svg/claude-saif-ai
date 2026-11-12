/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.facade

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.saifai.browser.SaifResearchBrowserView
import com.saifai.ide.SaifCodeStudioView
import com.saifai.notes.SaifNotesView
import com.saifai.terminal.SaifTerminalView

/**
 * 100% Original Cleanroom Workspace View Switcher for SAIF AI.
 * Completely replaces legacy EmbeddedEngineFeatures.
 *
 * Supported Modes:
 * - "TERMINAL": Native SAIF AI Interactive Linux Shell
 * - "IDE": Native SAIF AI Code Editor & File Studio
 * - "BROWSER": Native SAIF AI Research & Preview Browser
 * - "NOTES": Native SAIF AI Knowledge Workspace
 * - null: Main Chat
 */
@Composable
fun SaifEmbeddedEngineView(
    activeMode:
    String?,
    isLightMode:
    Boolean,
    onModeChange:
    (String?) -> Unit,
    modifier: Modifier =
    Modifier
) {
    var loadedModes by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(activeMode) {
        if (activeMode != null) {
            loadedModes = loadedModes + activeMode
        }
    }

    if (loadedModes.isEmpty()) return

    Box(modifier =
    modifier.fillMaxSize()) {
        // 1. Native Terminal View
        if ("TERMINAL" in loadedModes) {
            val visible = activeMode == "TERMINAL"
            Box(
                modifier =
    Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (visible) 1.0f else 0.0f
                        translationX = if (visible) 0.0f else 19000f
                    }
            ) {
                SaifTerminalView(onClose = { onModeChange(null) })
            }
        }

        // 2. Native IDE View
        if ("IDE" in loadedModes) {
            val visible = activeMode == "IDE"
            Box(
                modifier =
    Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (visible) 1.0f else 0.0f
                        translationX = if (visible) 0.0f else 19000f
                    }
            ) {
                SaifCodeStudioView(
                    onClose = { onModeChange(null) },
                    onNavigateToTerminal = { onModeChange("TERMINAL") }
                )
            }
        }

        // 3. Native Browser View
        if ("BROWSER" in loadedModes) {
            val visible = activeMode == "BROWSER"
            Box(
                modifier =
    Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (visible) 1.0f else 0.0f
                        translationX = if (visible) 0.0f else 19000f
                    }
            ) {
                SaifResearchBrowserView(onClose = { onModeChange(null) })
            }
        }

        // 4. Native Notes View
        if ("NOTES" in loadedModes) {
            val visible = activeMode == "NOTES"
            Box(
                modifier =
    Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (visible) 1.0f else 0.0f
                        translationX = if (visible) 0.0f else 19000f
                    }
            ) {
                SaifNotesView(onClose = { onModeChange(null) })
            }
        }
    }
}
