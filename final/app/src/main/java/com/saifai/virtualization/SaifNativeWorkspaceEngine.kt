/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.virtualization

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 100% Original Cleanroom Virtualization Coordinator for SAIF AI.
 * Completely replaces legacy LocalEnvironmentManager & LocalTerminalBridge.
 */
class SaifNativeWorkspaceEngine private constructor() {

    companion object {
        const val WORKSPACE_PATH_LINUX = "/root/workspace"
        const val UPLOADS_PATH_LINUX = "/root/uploads"

        val instance: SaifNativeWorkspaceEngine by lazy { SaifNativeWorkspaceEngine() }
    }

    sealed class EngineStatus {
        object Standby : EngineStatus()
        object Initializing : EngineStatus()
        data class Active(val workspaceDirectory: File, val activeProcesses: Int) : EngineStatus()
        data class Failure(val errorDetail: String) : EngineStatus()
    }

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Standby)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val activeProcessRegistry = ConcurrentHashMap<String, Process>()

    fun ensureWorkspaceInitialized(context: Context): File {
        val rootDir = File(context.filesDir, "saif_root")
        val workspaceDir = File(rootDir, "workspace").apply { mkdirs() }
        File(rootDir, "uploads").apply { mkdirs() }
        File(rootDir, "bin").apply { mkdirs() }

        _status.value = EngineStatus.Active(workspaceDir, activeProcessRegistry.size)
        return workspaceDir
    }

    fun resolveHostFile(context: Context, linuxPath: String): File? {
        val baseDir = ensureWorkspaceInitialized(context)
        val clean = linuxPath.trim()

        val relativePath = when {
            clean.startsWith(WORKSPACE_PATH_LINUX) -> clean.removePrefix(WORKSPACE_PATH_LINUX).removePrefix("/")
            clean.startsWith("/root/workspace") -> clean.removePrefix("/root/workspace").removePrefix("/")
            clean.startsWith(UPLOADS_PATH_LINUX) -> "uploads/" + clean.removePrefix(UPLOADS_PATH_LINUX).removePrefix("/")
            else -> return null
        }

        val target = File(baseDir, relativePath)
        return try {
            val baseCanonical = baseDir.canonicalPath
            val targetCanonical = target.canonicalPath
            if (targetCanonical.startsWith(baseCanonical) || targetCanonical.startsWith(File(context.filesDir, "saif_root").canonicalPath)) {
                target
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun executeScript(
        context: Context,
        scriptCommand: String,
        environmentOverrides: Map<String, String> = emptyMap(),
        onOutputLine: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val wsDir = ensureWorkspaceInitialized(context)
        val processBuilder = ProcessBuilder("sh", "-c", scriptCommand)
            .directory(wsDir)
            .redirectErrorStream(true)

        val env = processBuilder.environment()
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["WORKSPACE"] = wsDir.absolutePath
        env["PATH"] = "${File(context.filesDir, "saif_root/bin").absolutePath}:/system/bin:/system/xbin:/usr/bin:/bin"
        env.putAll(environmentOverrides)

        val procId = java.util.UUID.randomUUID().toString()
        val process = processBuilder.start()
        activeProcessRegistry[procId] = process
        _status.value = EngineStatus.Active(wsDir, activeProcessRegistry.size)

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                withContext(context = Dispatchers.Main) {
                    onOutputLine(line!!)
                }
                line = reader.readLine()
            }
            process.waitFor(120, TimeUnit.SECONDS)
            process.exitValue()
        } catch (err: Exception) {
            process.destroyForcibly()
            -1
        } finally {
            activeProcessRegistry.remove(procId)
            _status.value = EngineStatus.Active(wsDir, activeProcessRegistry.size)
        }
    }

    fun terminateAllRunningProcesses() {
        for ((_, p) in activeProcessRegistry) {
            try { p.destroyForcibly() } catch (_: Throwable) {}
        }
        activeProcessRegistry.clear()
    }
}
