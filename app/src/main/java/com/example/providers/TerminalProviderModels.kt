package com.example.providers

import com.example.agyext.AgyApiProtocol
import java.util.UUID

enum class TerminalProviderMode(val badgeLabel: String) {
    LOCAL_PROOT_DAEMON("Local Linux + Daemon"),
    HYBRID_API_WORKSPACE("Hybrid API + Local Shell"),
    LOCAL_SHELL_ONLY("Offline Local Linux"),
    SSH_REMOTE_HOST("Remote SSH Bridge")
}

enum class TerminalAuthScheme(val label: String) {
    OAUTH_OR_CUSTOM_API("Google OAuth or Custom API Key"),
    BEARER_TOKEN("Bearer Token (Authorization)"),
    X_API_KEY("x-api-key Header"),
    QUERY_PARAM_KEY("Query Parameter (?key=)"),
    CUSTOM_HEADER("Custom Auth Header"),
    NONE_LOCAL("No Auth (Local / Offline)")
}

enum class TerminalProviderReadiness(val displayLabel: String) {
    NOT_SELECTED("Not Selected"),
    CHECKING_PREREQUISITES("Checking Storage & Network"),
    DOWNLOADING_ENVIRONMENT("Downloading Linux Environment"),
    CONFIGURING_TOOLS("Configuring Provider Toolchain"),
    VERIFYING_HEALTH("Verifying Environment Health"),
    READY("Ready"),
    NEEDS_CONFIGURATION("Needs API Key / Sign-In"),
    DEGRADED("Degraded — Reconnecting"),
    FAILED("Setup Failed"),
    CANCELLED("Cancelled")
}

data class TerminalProviderSpec(
    val id: String = "custom_${UUID.randomUUID().toString().take(8)}",
    val name: String,
    val description: String,
    val mode: TerminalProviderMode = TerminalProviderMode.HYBRID_API_WORKSPACE,
    val protocol: AgyApiProtocol = AgyApiProtocol.OPENAI_COMPATIBLE,
    val endpointUrl: String = "",
    val apiKey: String = "",
    val authScheme: TerminalAuthScheme = TerminalAuthScheme.BEARER_TOKEN,
    val customAuthHeaderName: String = "X-Custom-Auth",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val requiresLinuxRootfs: Boolean = false,
    val minFreeStorageMb: Long = 64L,
    val platformCompatibilityNote: String = "",
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    val lastHealthOk: Boolean? = null,
    val lastHealthLatencyMs: Long = 0L,
    val lastHealthMessage: String = ""
) {
    fun isLocalEndpoint(): Boolean {
        val lower = endpointUrl.lowercase().trim()
        return lower.startsWith("http://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://192.168.") ||
            lower.startsWith("http://10.")
    }

    fun hasValidCredentials(): Boolean {
        return when (authScheme) {
            TerminalAuthScheme.NONE_LOCAL -> true
            TerminalAuthScheme.OAUTH_OR_CUSTOM_API -> true
            else -> apiKey.isNotBlank() || isLocalEndpoint()
        }
    }

    fun maskedKeyPreview(): String {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return "Not configured"
        if (trimmed.length <= 8) return "••••••••"
        return "${trimmed.take(4)}••••••••${trimmed.takeLast(4)}"
    }
}

data class ProviderInstallHistoryItem(
    val timestampMs: Long = System.currentTimeMillis(),
    val providerId: String,
    val providerName: String,
    val status: String,
    val detail: String
)

data class PersistentTerminalSession(
    val id: String = "term_${UUID.randomUUID().toString().take(6)}",
    val title: String = "Workspace #1",
    val providerId: String = "google_antigravity",
    val workingDir: String = "/root/workspace",
    val createdAtMs: Long = System.currentTimeMillis(),
    val recentCommands: List<String> = emptyList()
)

data class TerminalProviderEcosystemState(
    val providers: List<TerminalProviderSpec> = emptyList(),
    val defaultProviderId: String = "",
    val activeProviderId: String = "",
    val hasCompletedOnboardingSelection: Boolean = false,
    val readiness: TerminalProviderReadiness = TerminalProviderReadiness.NOT_SELECTED,
    val progressFraction: Float = 0f,
    val statusMessage: String = "",
    val canRetry: Boolean = false,
    val isPreparingInBackground: Boolean = false,
    val freeStorageMb: Long = 0L,
    val autoFailoverEnabled: Boolean = true,
    val installHistory: List<ProviderInstallHistoryItem> = emptyList(),
    val terminalSessions: List<PersistentTerminalSession> = emptyList(),
    val activeTerminalSessionId: String = ""
) {
    val activeProvider: TerminalProviderSpec?
        get() = providers.firstOrNull { it.id == activeProviderId }
            ?: providers.firstOrNull { it.id == defaultProviderId }
            ?: providers.firstOrNull()
}
