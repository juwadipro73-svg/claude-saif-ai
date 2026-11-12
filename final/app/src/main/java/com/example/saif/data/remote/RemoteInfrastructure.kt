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

data class SystemStatus(
    val isConnected: Boolean = true,
    val activeEngine: String = "SAIF Cleanroom Engine"
)

sealed class SystemConnectionState /* SAIF Connection */ {
    data object Connected : SystemConnectionState()
    data object Disconnected : SystemConnectionState()
    data class Connecting(val attempt: Int = 1) : SystemConnectionState()
}

object AgyHubClient {
    data class AgyAuthInfo
(
        val email:
        String? = null,
        val displayName:
        String? = null,
        val profilePictureUrl: String? = null
    )
}

object GoogleAccountBinding /* SAIF Binding */ {
    fun denialReason(user: Any?, authInfo: Any?): String? = null
}

object AntigravityAppBinding /* SAIF Binding */ {
    fun init(context: Context) {}
    fun clear() {}
}

data class SkillItemModel(
    val name: String,
    val description:
        String = "",
    val instructions: String = "",
    val githubUrl: String = ""
)

object SavedAiSkills /* SAIF Skills */ {
    private val _skills = MutableStateFlow<List<SkillItemModel>>(emptyList())
    val skills: StateFlow<List<SkillItemModel>> = _skills.asStateFlow()

    fun bind(context: Context, uid: String?) {}
    fun migrateAccount(context: Context, prev: String?, current: String?) {}
    fun visibleSkills(): List<SkillItemModel> = _skills.value
    fun find(name: String): SkillItemModel? = _skills.value.find { it.name.equals(name, ignoreCase = true) }
    fun delete(skillName: String) {
        _skills.value = _skills.value.filterNot { it.name.equals(skillName, ignoreCase = true) }
    }
    fun importFromGitHub(url: String, name: String): Boolean {
        _skills.value = _skills.value + SkillItemModel(name = name, githubUrl = url)
        return true
    }
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
    fun init(app: Application) {}
    fun customCommands(): List<SlashCommandItem> = emptyList()
}

object PromptExpansion /* SAIF Expansion */ {
    fun pluginSuggestions(query: String): List<SlashCommandItem> = emptyList()
    fun baseSlashItems(): List<SlashCommandItem> = emptyList()
}

object SlashCommandsCache /* SAIF Cache */ {
    val DEFAULT_SKILLS = emptyList<SlashCommandItem>()
    val BUILTIN_COMMANDS = emptyList<SlashCommandItem>()
    fun getCachedSync(): List<SlashCommandItem>? = emptyList()
}
