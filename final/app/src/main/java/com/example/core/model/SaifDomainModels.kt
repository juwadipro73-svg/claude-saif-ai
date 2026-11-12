/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.core.model

import java.io.File
import java.util.UUID

/**
 * 100% Original SAIF AI Domain Models.
 * Authored from docs/spec/ports.md under SAIF AI Cleanroom Protocol.
 */

enum class SaifMessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM,
    SUMMARY
}

enum class SaifToolType {
    BASH,
    VIEW_FILE,
    EDIT_FILE,
    WRITE_FILE,
    LIST_DIR,
    SEARCH_WEB,
    READ_URL,
    ASK_CHOICE,
    SYSTEM_NOTIFY,
    CUSTOM
}

data class SaifChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val isMedia: Boolean = false
)

data class SaifToolCall(
    val callId: String = UUID.randomUUID().toString(),
    val toolType: SaifToolType = SaifToolType.BASH,
    val operationName: String = "bash",
    val commandText: String = "",
    val executionStatus: String = "PENDING_APPROVAL",
    val standardOutput: String = "",
    val standardError: String = "",
    val exitCode: Int? = null,
    val durationMs: Long? = null
)

data class SaifChatMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val sessionId: String = "default",
    val role: SaifMessageRole = SaifMessageRole.USER,
    val textContent: String = "",
    val reasoningText: String? = null,
    val reasoningDurationMs: Long? = null,
    val toolInvocations: List<SaifToolCall> = emptyList(),
    val isStreamingActive: Boolean = false,
    val attachments: List<SaifChatAttachment> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

enum class SaifApprovalScope {
    ONCE,
    ALWAYS_SESSION,
    REJECT
}

data class SaifPendingApproval(
    val toolCall: SaifToolCall,
    val messageId: String,
    val explanationReason: String? = null
)

data class SaifChoiceOption(
    val optionId: String,
    val labelText: String,
    val descriptionText: String? = null
)

data class SaifQuestionnaire(
    val promptText: String,
    val options: List<SaifChoiceOption>,
    val allowMultiSelect: Boolean = false,
    val allowCustomInput: Boolean = true
)
