package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The owner's requests of 2026-11-12, pinned so that a later change cannot quietly bring the old behaviour back:
 * no provider chooser on first run, no editing-engine block in Settings, no Antigravity sign-in forced on a user
 * who brought an API key, the storage clean-up wired in, and the credits kept everywhere.
 */
class OwnerRequests20261112Test {
    /** Gradle runs unit tests in the module folder; an IDE may run them in the repository root. */
    private val repoRoot: File = listOf(File("."), File("..")).first { File(it, "settings.gradle.kts").isFile }
    private fun text(path: String): String = File(repoRoot, path).readText()
    private fun exists(path: String): Boolean = File(repoRoot, path).exists()
    private val ui = "app/src/main/java/com/example"

    @Test fun firstRunOpensTheChatWithoutAProviderChooser() {
        assertFalse(exists("$ui/providers/TerminalProviderSelectionScreen.kt"))
        val activity = text("$ui/MainActivity.kt")
        assertFalse(activity.contains("TerminalProviderSelectionScreen"))
        assertFalse(activity.contains("providerSetupComplete"))
        assertTrue(activity.contains("ensureDefaultProviderSelected(applicationContext, user.uid)"))
        val manager = text("$ui/providers/TerminalProviderManager.kt")
        assertTrue(manager.contains("const val DEFAULT_PROVIDER_ID = \"local_ubuntu\""))
    }

    @Test fun settingsPanelHasNoEditingEngineBlock() {
        val settings = text("$ui/ui/components/SettingsSheet.kt")
        assertFalse(settings.contains("EDITING ENGINE", ignoreCase = true))
        assertFalse(settings.contains("AdvancedEditingEnginePrefs"))
        assertTrue(settings.contains("SectionHeader(\"STORAGE\")"))
        assertTrue(settings.contains("SectionHeader(\"ABOUT & LICENCES\")"))
    }

    @Test fun anApiKeyNeverLeadsToAnAntigravitySignInScreen() {
        val app = text("$ui/ui/SaifAiApp.kt")
        assertTrue(app.contains("OwnApiKeyPolicy.antigravityAccountIssue("))
        assertTrue(app.contains("usingOwnApiKey = customApiExtState.isActiveAndReady"))
        // The old check ran against the stand-in "connected" state of the user's own key and blocked the app.
        assertFalse(app.contains("denialReason(appUser, antigravityAuthInfo)"))
        assertTrue(app.contains("onUseOwnApiKey = { showCustomApiFromHome = true }"))
        val chat = text("$ui/ui/ChatViewModel.kt")
        assertTrue(chat.contains("AntigravityCustomApiManager.adoptSavedProviderKey(context)"))
    }

    @Test fun everyChatPromptCarriesTheIdentityAndStorageRules() {
        val chat = text("$ui/ui/ChatViewModel.kt")
        assertEquals(2, Regex("append\\(com\\.example\\.util\\.SaifIdentity\\.RULE\\)").findAll(chat).count())
        assertEquals(2, Regex("append\\(com\\.example\\.storage\\.ModuleCleanupRules\\.AGENT_RULE\\)").findAll(chat).count())
        assertTrue(chat.contains("ModuleAutoCleaner.onWorkStarted(context)"))
        assertTrue(chat.contains("ModuleAutoCleaner.onWorkFinished(context)"))
        listOf(
            "$ui/agyext/AntigravityCustomApiAgentEngine.kt",
            "$ui/data/remote/AIApiUtility.kt",
            "$ui/data/remote/GeminiService.kt",
            "$ui/LiveVoiceSessionManager.kt",
            "$ui/ui/components/GeminiAssistantSheet.kt"
        ).forEach { path -> assertTrue(path, text(path).contains("SaifIdentity.")) }
        // The live voice conversation has its own prompt; it ends with the same identity rule.
        val voice = text("$ui/live/LivePrompt.kt")
        assertTrue(voice.contains("append(SaifIdentity.RULE)"))
    }
}
