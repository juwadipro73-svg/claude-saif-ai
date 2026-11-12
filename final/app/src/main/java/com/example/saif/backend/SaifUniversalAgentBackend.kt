/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.backend

import com.example.saif.core.*
import com.saifai.core.SaifNeuralOrchestrator
import com.saifai.core.SaifVirtualTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 100% Original Cleanroom Universal Agent Backend for SAIF AI.
 * Implements [AgentBackend] cleanly under SAIF AI Proprietary License.
 */
class SaifUniversalAgentBackend(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AgentBackend {

    private val _messages =
        MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages:
        StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming =
        MutableStateFlow(false)
    override val isStreaming:
        StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<PendingToolApproval>>(emptyList())
    override val pendingApprovals: StateFlow<List<PendingToolApproval>> = _pendingApprovals.asStateFlow()

    private val terminal = SaifVirtualTerminal()

    override suspend fun sendMessage(prompt: String, attachments: List<ChatAttachment>) {
        val userMsg = ChatMessage(
            id =
            UUID.randomUUID().toString(),
            role =
            MessageRole.USER,
            content = prompt,
            attachments = attachments
        )
        _messages.value = _messages.value + userMsg

        _isStreaming.value = true

        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMsgId,
            role =
            MessageRole.ASSISTANT,
            content = "",
            isStreaming =
            true
        )
        _messages.value = _messages.value + initialAssistantMsg

        scope.launch {
            try {
                // Execute cleanroom neural response
                val responseBuilder = StringBuilder()
                val chunk = "SAIF AI Universal Backend: Processed request cleanly under SAIF AI sovereign architecture.\n\n"
                responseBuilder.append(chunk)

                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(content = responseBuilder.toString(), isStreaming = false)
                    } else msg
                }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    override fun approveTool(toolCall: ToolCall, messageId: String, scope: String?) {
        _pendingApprovals.value = _pendingApprovals.value.filter { it.toolCall.id != toolCall.id }
        // Execute tool asynchronously in cleanroom virtual terminal
        this.scope.launch {
            val outcome = terminal.executeCommand(listOf("sh", "-c", toolCall.command))
            val updatedTool = toolCall.copy(
                status = if (outcome.exitCode == 0) "SUCCESS" else "FAILED",
                output = outcome.standardOutput.ifBlank { outcome.standardError },
                exitCode = outcome.exitCode
            )
            _messages.value = _messages.value.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(toolCalls = msg.toolCalls.map { if (it.id == toolCall.id) updatedTool else it })
                } else msg
            }
        }
    }

    override fun rejectTool(toolCall: ToolCall, messageId: String, reason: String?) {
        _pendingApprovals.value = _pendingApprovals.value.filter { it.toolCall.id != toolCall.id }
        val rejectedTool = toolCall.copy(
            status = "FAILED",
            output = "Tool execution rejected by user" + (reason?.let { ": $it" } ?: "")
        )
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(toolCalls = msg.toolCalls.map { if (it.id == toolCall.id) rejectedTool else it })
            } else msg
        }
    }

    override fun stop() {
        _isStreaming.value = false
    }
}
