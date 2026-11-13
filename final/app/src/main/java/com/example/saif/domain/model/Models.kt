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

@kotlinx.serialization.Serializable
data class TokenUsage
(
    val promptTokens:
    Int = 0,
    val completionTokens: Int = 0,
    val outputTokens: Int = 0,
    val durationMs: Long = 0,
    val model: String = "",
    val totalTokens: Int = 0
)

typealias MessageRole = SaifMessageRole
typealias ToolType = SaifToolType
typealias ToolCall = SaifToolCall
typealias ChatMessage = SaifChatMessage
typealias ChatAttachment = SaifChatAttachment

// Models supplied by the user's provider configuration, not a remote hub catalog.
data class AiModel(val id: String, val displayName: String, val description: String = "")
data class ModelQuota(val modelId: String, val remainingFraction: Float)
