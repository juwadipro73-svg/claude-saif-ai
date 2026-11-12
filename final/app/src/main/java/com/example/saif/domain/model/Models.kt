/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.domain.model

import com.example.core.model.*
import java.util.UUID

/**
 * 100% Original SAIF AI Domain Models & Adapters.
 * Cleanroom implementation satisfying docs/spec/ports.md.
 */

enum class AgentKind {
    AGY,
    CLAUDE,
    CUSTOM
}

data class TokenUsage
(
    val promptTokens:
    Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

typealias MessageRole = SaifMessageRole
typealias ToolType = SaifToolType
typealias ToolCall = SaifToolCall
typealias ChatMessage = SaifChatMessage
typealias ChatAttachment = SaifChatAttachment
