/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.data.remote

import android.app.Application
import android.content.Context
import com.saifai.bridge.SaifLocalBridgeServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object AndroidLocalBridgeServer {
    val instance: SaifLocalBridgeServer get() = SaifLocalBridgeServer.instance
}

enum class SystemStatus(val label: String, val dotColor: androidx.compose.ui.graphics.Color) {
    READY("Provider configured", androidx.compose.ui.graphics.Color(0xFF42C9A3)),
    UNAUTHENTICATED("Configure API provider", androidx.compose.ui.graphics.Color(0xFFF3B65A)),
    OFFLINE("Offline", androidx.compose.ui.graphics.Color.Gray),
    ERROR("Connection error", androidx.compose.ui.graphics.Color.Red),
    CHECKING_AUTH("Checking configuration", androidx.compose.ui.graphics.Color.Gray),
    ACQUIRING_CSRF("Preparing", androidx.compose.ui.graphics.Color.Gray),
    STARTING("Starting", androidx.compose.ui.graphics.Color.Gray)
}

data class SystemConnectionState(val status: SystemStatus = SystemStatus.UNAUTHENTICATED) {
    val isReady: Boolean get() = status == SystemStatus.READY
    val dotColor get() = status.dotColor
}

object AgyHubClient {
    // Retained data name for stored/UI compatibility, not a hub client.
    data class AgyAuthInfo(val email: String = "", val displayName: String = "",
        val profilePictureUrl: String = "", val fullName: String = "", val isLoggedIn: Boolean = false,
        val planName: String = "Own API key")
}

object GoogleAccountBinding {
    fun denialReason(user: com.example.data.local.AuthUser?, authInfo: AgyHubClient.AgyAuthInfo): String? =
        if (user == null || !user.isActive || user.isBanned) "Sign in to an active SAIF AI account to use chat history." else null
}

object AntigravityAppBinding /* SAIF Binding */ {
    fun init(context: Context) {}
    fun clear() {}
}

object SkillSlashParsing /* SAIF Parsing */ {
    data class ParsedSlash(val command:
        String, val args: String)
    fun parse(text: String): ParsedSlash? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val parts = t.removePrefix("/").split(" ", limit = 2)
        return ParsedSlash(command = parts[0], args = parts.getOrNull(1) ?: "")
    }
}

data class SlashCommandItem
(
    val command:
        String,
    val displayCommand: String,
    val description: String,
    val type: String = "command",
    val argumentHint:
        String = ""
)

object CustomSlashCommands /* SAIF Commands */ {
    fun init(app: Context) {}
    fun customCommands(): List<SlashCommandItem> = emptyList()
}

object PromptExpansion /* SAIF Expansion */ {
    fun expand(text: String): String {
        val parsed = SkillSlashParsing.parse(text) ?: return text
        val skill = SavedAiSkills.find(parsed.command) ?: return text
        return "User-selected skill /${skill.name}:\n${skill.instructions}\n\nUser request: ${parsed.args}"
    }
    fun pluginSuggestions(query: String): List<SlashCommandItem> = emptyList()
    fun baseSlashItems(): List<SlashCommandItem> = SavedAiSkills.visibleSkills().map {
        SlashCommandItem("/${it.name}", "/${it.name}", it.description, type = "skill")
    }
}

object SlashCommandsCache /* SAIF Cache */ {
    val DEFAULT_SKILLS = emptyList<SlashCommandItem>()
    val BUILTIN_COMMANDS = emptyList<SlashCommandItem>()
    fun getCachedSync(): List<SlashCommandItem> = emptyList()
}
