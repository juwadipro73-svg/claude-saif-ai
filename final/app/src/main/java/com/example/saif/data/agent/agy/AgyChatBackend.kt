/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.data.agent.agy

import com.example.core.model.SaifChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgyChatBackend {
    private val _state = MutableStateFlow<List<SaifChatMessage>>(emptyList())
    val messages: StateFlow<List<SaifChatMessage>> = _state.asStateFlow()
}
