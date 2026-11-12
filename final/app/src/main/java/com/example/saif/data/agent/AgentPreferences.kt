/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.data.agent

import android.content.Context
import com.example.core.model.*
import com.example.saif.domain.model.AgentKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 100% Original SAIF AI Agent Preferences & Session Adapter.
 */
class AgentPreferences(private val context: Context) {

    companion object /* SAIF Preferences */ {
        private val _enabledFlow = MutableStateFlow<Set<AgentKind>>(setOf(AgentKind.AGY, AgentKind.CLAUDE))
        val enabledAgents: StateFlow<Set<AgentKind>> = _enabledFlow.asStateFlow()

        fun enabledSync(context: Context): Set<AgentKind> = _enabledFlow.value

        fun bridgeFlags(context: Context): String = ""
    }

    fun isAgentEnabled(kind: AgentKind): Boolean = kind in _enabledFlow.value

    fun setEnabled(set: Set<AgentKind>) {
        _enabledFlow.value = set
    }
}

class ChatSessionStore /* SAIF Chat Session Store */ {
    private val _messages = MutableStateFlow<List<SaifChatMessage>>(emptyList())
    val messages: StateFlow<List<SaifChatMessage>> = _messages.asStateFlow()

    fun appendMessage(msg: SaifChatMessage) {
        _messages.value = _messages.value + msg
    }

    fun clear() {
        _messages.value =
    emptyList()
    }
}
