/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title:
        String,
    val createdAt:
        Long = System.currentTimeMillis(),
    val updatedAt:
        Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
