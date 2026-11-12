/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.terminal

import android.content.Context
import com.saifai.virtualization.SaifNativeWorkspaceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% Original Cleanroom Terminal Session Manager for SAIF AI.
 * Completely replaces legacy terminal session backends.
 */
class SaifTerminalSessionManager private constructor() {

    companion object {
        val instance: SaifTerminalSessionManager by lazy { SaifTerminalSessionManager() }
    }

    data class TerminalSession(
        val sessionId: String = UUID.randomUUID().toString(),
        val title: String,
        val workingDirectory: String = "/root/workspace",
        val createdAtMs: Long = System.currentTimeMillis()
    )

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionsMap = ConcurrentHashMap<String, TerminalSession>()
    private val outputBuffers = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val inputWriters = ConcurrentHashMap<String, BufferedWriter>()
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    private val _activeSessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val activeSessions: StateFlow<List<TerminalSession>> = _activeSessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    fun createSession(context: Context, customTitle: String? = null): TerminalSession {
        val sessionIndex = sessionsMap.size + 1
        val title = customTitle ?: "Terminal $sessionIndex"
        val session = TerminalSession(title = title)

        sessionsMap[session.sessionId] = session
        val bufferFlow = MutableStateFlow("SAIF AI Terminal [${session.title}]\n$ ")
        outputBuffers[session.sessionId] = bufferFlow

        val wsDir = SaifNativeWorkspaceEngine.instance.ensureWorkspaceInitialized(context)

        sessionScope.launch {
            try {
                val pb = ProcessBuilder("sh")
                    .directory(wsDir)
                    .redirectErrorStream(true)

                val env = pb.environment()
                env["TERM"] = "xterm-256color"
                env["HOME"] = "/root"
                env["PS1"] = "saif@terminal:\\w$ "
                env["PATH"] = "${File(context.filesDir, "saif_root/bin").absolutePath}:/system/bin:/system/xbin:/usr/bin:/bin"

                val proc = pb.start()
                activeProcesses[session.sessionId] = proc

                val writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                inputWriters[session.sessionId] = writer

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val readBuf = CharArray(1024)
                var readCount = reader.read(readBuf)
                while (readCount != -1) {
                    val chunk = String(readBuf, 0, readCount)
                    bufferFlow.value = (bufferFlow.value + chunk).takeLast(60000)
                    readCount = reader.read(readBuf)
                }
            } catch (t: Throwable) {
                bufferFlow.value += "\nSession terminated: ${t.localizedMessage}\n"
            } finally {
                inputWriters.remove(session.sessionId)
                activeProcesses.remove(session.sessionId)
            }
        }

        _activeSessions.value = sessionsMap.values.toList()
        if (_currentSessionId.value == null) {
            _currentSessionId.value = session.sessionId
        }
        return session
    }

    fun getOutputFlow(sessionId: String): StateFlow<String>? {
        return outputBuffers[sessionId]?.asStateFlow()
    }

    fun selectSession(sessionId: String) {
        if (sessionsMap.containsKey(sessionId)) {
            _currentSessionId.value = sessionId
        }
    }

    fun sendInput(sessionId: String, text: String) {
        sessionScope.launch {
            val writer = inputWriters[sessionId] ?: return@launch
            try {
                writer.write(text)
                writer.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendCtrlC(sessionId: String) {
        sendInput(sessionId, "\u0003")
    }

    fun closeSession(sessionId: String) {
        activeProcesses[sessionId]?.destroyForcibly()
        activeProcesses.remove(sessionId)
        inputWriters.remove(sessionId)
        outputBuffers.remove(sessionId)
        sessionsMap.remove(sessionId)

        val remaining = sessionsMap.values.toList()
        _activeSessions.value = remaining
        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = remaining.firstOrNull()?.sessionId
        }
    }
}
