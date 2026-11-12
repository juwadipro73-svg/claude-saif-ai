/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId:
        String,
    val role: String,
    val content:
        String,
    val timestamp:
        Long = System.currentTimeMillis(),
    val thoughtText: String? = null,
    val isError:
        Boolean = false,
    val toolCallsJson: String? = null,
    val attachmentsJson: String? = null
)
