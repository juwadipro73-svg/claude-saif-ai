/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.chat

import com.saifai.models.SaifAttachment
import com.saifai.models.SaifConversation
import com.saifai.models.SaifMessage
import com.saifai.models.SaifToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 100% Original Cleanroom Chat Session Store for SAIF AI.
 * Completely replaces legacy ChatSessionStore.
 */
class SaifChatSessionStore {
    val conversations = MutableStateFlow<List<SaifConversation>>(emptyList())
    val currentConversation = MutableStateFlow<SaifConversation?>(null)
    val messages = MutableStateFlow<List<SaifMessage>>(emptyList())
    val conversationError =
        MutableStateFlow<String?>(null)
    val userEmail =
        MutableStateFlow<String?>(null)
    val attachments = MutableStateFlow<List<SaifAttachment>>(emptyList())
    val terminatedToolDialog = MutableStateFlow<Pair<SaifToolCall, String>?>(null)

    fun clear() {
        messages.value = emptyList()
        conversationError.value = null
        attachments.value = emptyList()
        terminatedToolDialog.value = null
    }

    fun addMessage(msg: SaifMessage) {
        messages.value = messages.value + msg
    }

    fun updateLastMessage(transform: (SaifMessage) -> SaifMessage) {
        val list = messages.value
        if (list.size > 0) {
            messages.value = list.dropLast(1) + transform(list.last())
        }
    }
}
