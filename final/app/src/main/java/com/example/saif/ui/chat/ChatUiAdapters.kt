/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import com.example.core.model.SaifChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ChatFeedItem {
    data class User(val message: SaifChatMessage) : ChatFeedItem()
    data class AssistantBlock(val message: SaifChatMessage) : ChatFeedItem()
    data class AssistantThinking(val thoughtText: String, val durationMs: Long? = null, val isStreaming: Boolean = false) : ChatFeedItem()
    data class AssistantTyping(val modelId: String) : ChatFeedItem()
    data class AssistantFooter(val message: SaifChatMessage) : ChatFeedItem()
    data class StreamingMessage(val message: SaifChatMessage) : ChatFeedItem()
    data class Summary(val message: SaifChatMessage) : ChatFeedItem()
    data class Divider(val label: String) : ChatFeedItem()
}

object ChatFeedCache {
    fun buildFeedItems(messages: List<SaifChatMessage>, modelId: String): List<ChatFeedItem> {
        return messages.map { msg ->
            if (msg.role == com.example.core.model.SaifMessageRole.USER) {
                ChatFeedItem.User(msg)
            } else {
                ChatFeedItem.AssistantBlock(msg)
            }
        }
    }
}

class SaifSharedChatViewModel(application: Application) : ViewModel() {
    val isAuthBusy: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    val isNetworkConnectedState: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    val authFeedbackMessage: StateFlow<CharSequence> = MutableStateFlow<CharSequence>("").asStateFlow()
}

object ChatViewModelHolder {
    @Volatile private var instance: SaifSharedChatViewModel? = null

    fun get(application: Application): SaifSharedChatViewModel {
        return instance ?: synchronized(this) {
            instance ?: SaifSharedChatViewModel(application).also { instance = it }
        }
    }
}
