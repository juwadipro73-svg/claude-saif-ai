package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mode: String = "general", // general, coding, summary, creative, research
    // Legacy sessions cannot be assigned to a Google account without proof of ownership.
    @ColumnInfo(defaultValue = "''") val ownerUid: String = ""
)
