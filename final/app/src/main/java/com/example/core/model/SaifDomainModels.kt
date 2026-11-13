/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.core.model

import java.io.File
import java.util.UUID

/**
 * 100% Original SAIF AI Domain Models.
 * Authored from docs/spec/ports.md under SAIF AI Cleanroom Protocol.
 */

typealias SaifMessageRole = com.example.saif.core.MessageRole
typealias SaifToolType = com.example.saif.core.ToolType
typealias SaifChatAttachment = com.example.saif.core.ChatAttachment
typealias SaifToolCall = com.example.saif.core.ToolCall
typealias SaifChatMessage = com.example.saif.core.ChatMessage

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
