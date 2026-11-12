/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.models

import com.example.core.model.*
import java.util.UUID

/**
 * 100% Original Cleanroom Model Bindings for SAIF AI Engine.
 * Decoupled domain models authored under SAIF cleanroom guidelines.
 */

typealias SaifRole = SaifMessageRole
typealias SaifToolType = com.example.core.model.SaifToolType
typealias SaifAttachment = SaifChatAttachment
typealias SaifMessage = SaifChatMessage
typealias SaifToolCall = com.example.core.model.SaifToolCall

data class SaifConversation(
    val conversationId: String = UUID.randomUUID().toString(),
    val displayTitle: String = "New Chat",
    val messagesList: List<SaifChatMessage> = emptyList(),
    val createdEpochMs: Long = System.currentTimeMillis()
)
