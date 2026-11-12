/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.adapters

import android.content.Context
import com.example.saif.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Cleanroom Port Adapters bridging SAIF AI ports to underlying implementations.
 */
object SaifModelAdapters {

    fun toCoreMessage(
        id: String,
        conversationId:
        String,
        role: String,
        content: String,
        thought:
        String? = null,
        toolCalls: List<ToolCall> = emptyList()
    ): ChatMessage {
        val r = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant", "model" -> MessageRole.ASSISTANT
            "tool" -> MessageRole.TOOL
            else -> MessageRole.SYSTEM
        }
        return ChatMessage(
            id = id,
            conversationId =
            conversationId,
            role = r,
            content =
            content,
            thoughtText = thought,
            toolCalls = toolCalls
        )
    }

    fun toCoreToolCall(
        id: String,
        name: String,
        command: String,
        status: String,
        output: String = "",
        exitCode: Int? = null,
        durationMs: Long? = null
    ): ToolCall {
        return ToolCall(
            id = id,
            name = name,
            toolType =
            ToolType.BASH,
            command =
            command,
            status = status,
            output = output,
            exitCode =
            exitCode,
            durationMs =
            durationMs
        )
    }
}

class SaifNativeWorkspaceAdapter(private val context: Context) : WorkspaceEnvironment {
    private val engine = com.saifai.virtualization.SaifNativeWorkspaceEngine.instance

    private val _isReady = MutableStateFlow(true)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    override val workspaceDirectory: File
        get() = engine.ensureWorkspaceInitialized(context)

    override fun ensureReady(): Boolean {
        engine.ensureWorkspaceInitialized(context)
        _isReady.value = true
        return true
    }

    override fun resolvePath(linuxPath: String): File? {
        return engine.resolveHostFile(context, linuxPath)
    }

    override suspend fun executeCommand(cmd: String, onLine: (String) -> Unit): Int {
        return engine.executeScript(context, cmd, onOutputLine = onLine)
    }
}
