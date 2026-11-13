/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.core

import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 100% Original SAIF AI Core Models & Port Interfaces.
 * Written from docs/feature-spec.md under Apache-2.0 / SAIF AI License.
 */

@Serializable
enum class MessageRole
{
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM,
    SUMMARY
}

@Serializable
enum class ToolType
{
    BASH,
    VIEW_FILE,
    EDIT_FILE,
    LIST_DIR,
    GREP_SEARCH,
    FIND,
    SEARCH_WEB,
    READ_URL,
    GENERATE_IMAGE,
    MCP,
    ASK_CHOICE,
    MATH,
    SYSTEM_NOTIFICATION,
    WRITE_FILE,
    CUSTOM,
    UNKNOWN
}

@Serializable
data class ToolCall
(
    val id: String =
        UUID.randomUUID().toString(),
    val name: String =
        "bash",
    val toolType: ToolType =
        ToolType.BASH,
    val command: String =
        "",
    val status: String =
        "RUNNING",
    val output: String =
        "",
    val exitCode: Int? =
        null,
    val durationMs: Long? =
        null,
    val stepIndex: Int? = null,
    val interactionType: String? = null,
    val trajectoryId: String? = null
)

@Serializable
data class ChatAttachment
(
    val id: String =
        UUID.randomUUID().toString(),
    val name: String,
    val path: String =
        "",
    val isImage: Boolean =
        false,
    val isAudio: Boolean =
        false,
    val isVideo: Boolean =
        false,
    val localUri: String? =
        null,
    val size: Long =
        0L,
    val mimeType: String? = null,
    val base64: String? = null
)

@Serializable
data class ChatMessage
(
    val id: String =
        UUID.randomUUID().toString(),
    val conversationId: String =
        "default",
    val role: MessageRole =
        MessageRole.USER,
    val content: String =
        "",
    val thoughtText: String? =
        null,
    val thoughtDurationMs: Long? =
        null,
    val toolCalls: List<ToolCall> =
        emptyList(),
    val isStreaming: Boolean =
        false,
    val attachments: List<ChatAttachment> =
        emptyList(),
    val createdAt: Long =
        System.currentTimeMillis(),
    val modelId: String? = null,
    val tokenUsage: com.example.saif.domain.model.TokenUsage? = null
) {
    val textContent: String get() = content
    val messageId: String get() = id
    val sessionId: String get() = conversationId
    val reasoningText: String? get() = thoughtText
    val reasoningDurationMs: Long? get() = thoughtDurationMs
    val toolInvocations: List<ToolCall> get() = toolCalls
    val isStreamingActive: Boolean get() = isStreaming
    val timestampMs: Long get() = createdAt
}

data class PendingToolApproval
(
    val toolCall: ToolCall,
    val messageId:
        String
)

interface AgentBackend {
    val messages:
        StateFlow<List<ChatMessage>>
    val isStreaming:
        StateFlow<Boolean>
    val pendingApprovals: StateFlow<List<PendingToolApproval>>

    suspend fun sendMessage(prompt: String, attachments: List<ChatAttachment> = emptyList())
    fun approveTool(toolCall: ToolCall, messageId: String, scope: String? = null)
    fun rejectTool(toolCall: ToolCall, messageId: String, reason: String? = null)
    fun stop()
}

interface WorkspaceEnvironment {
    val isReady: StateFlow<Boolean>
    val workspaceDirectory: File

    fun ensureReady(): Boolean
    fun resolvePath(linuxPath: String): File?
    suspend fun executeCommand(cmd: String, onLine: (String) -> Unit = {}): Int
}

interface AccountBinding {
    val isAuthenticated: StateFlow<Boolean>
    val userEmail: StateFlow<String?>
    val displayName: StateFlow<String?>

    fun checkAuthStatus(): Boolean
}

data class SkillItem
(
    val name: String,
    val description:
        String,
    val sourceUrl:
        String,
    val instructions:
        String,
    val savedAt: Long =
        System.currentTimeMillis()
)

interface SkillStore {
    fun listSkills(): List<SkillItem>
    fun findSkill(name: String): SkillItem?
    fun saveSkill(skill: SkillItem)
    fun deleteSkill(name: String)
}
