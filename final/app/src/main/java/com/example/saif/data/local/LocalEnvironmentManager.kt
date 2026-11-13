/* Copyright (c) SAIF AI. All rights reserved. */
package com.example.saif.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class LocalInstallerState {
    data object Idle : LocalInstallerState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long,
        val currentPackageName: String, val speedText: String, val progressFraction: Float) : LocalInstallerState()
    data class Extracting(val extractedFilesCount: Int, val progressFraction: Float) : LocalInstallerState()
    data class Configuring(val stepDescription: String) : LocalInstallerState()
    data class Verifying(val testName: String) : LocalInstallerState()
    data object Success : LocalInstallerState()
    data class Error(val errorMessage: String) : LocalInstallerState()
}

/** Android's native shell is available; a Linux distribution must not be reported installed. */
object LocalEnvironmentManager {
    private val state = MutableStateFlow<LocalInstallerState>(LocalInstallerState.Idle)
    val installerState = state.asStateFlow()
    fun isInstalled(context: Context): Boolean = File("/system/bin/sh").canExecute()
    fun isUbuntuInstalled(context: Context): Boolean = false
    fun hasVerifiedEnvironment(context: Context): Boolean = false
    fun isTermuxPackage(context: Context): Boolean = false
    fun getBinDir(context: Context): File = File(context.filesDir, "bin").apply { mkdirs() }
    fun ensureInstalledInBackground(context: Context) {
        getBinDir(context)
        state.value = LocalInstallerState.Error("Linux toolchain installation is not implemented in this native build. Android shell commands remain available; Linux CLI providers require a real runtime.")
    }
    fun retryBackgroundInstall(context: Context) = ensureInstalledInBackground(context)
    fun cancelBackgroundInstall(context: Context) { state.value = LocalInstallerState.Idle }

    data class CommandResult(val output: String, val exitCode: Int)
    suspend fun executeCommand(context: Context, command: String, timeoutSeconds: Long = 900,
        onOutputLine: (String) -> Unit = {}): CommandResult = withContext(Dispatchers.IO) {
        require(timeoutSeconds in 1..3600) { "Command timeout must be between 1 and 3600 seconds" }
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val process = ProcessBuilder("/system/bin/sh", "-c", command).directory(home)
            .redirectErrorStream(true).apply { environment()["HOME"] = home.absolutePath }.start()
        val output = StringBuilder()
        coroutineScope {
            val drain = launch(Dispatchers.IO) {
                process.inputStream.reader().use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val n = try { reader.read(buffer) } catch (_: java.io.IOException) { break }
                        if (n < 0) break
                        val chunk = String(buffer, 0, n)
                        synchronized(output) {
                            output.append(chunk)
                            if (output.length > 131072) output.delete(0, output.length - 131072)
                        }
                        onOutputLine(chunk)
                    }
                }
            }
            try {
                val finished = withTimeoutOrNull(timeoutSeconds * 1000) {
                    while (process.isAlive) delay(100)
                    true
                } ?: false
                if (!finished) process.destroyForcibly()
                if (withTimeoutOrNull(2000) { drain.join(); true } != true) {
                    process.inputStream.close()
                    drain.cancel()
                }
                CommandResult(synchronized(output) { output.toString() }, if (finished) process.exitValue() else 124)
            } finally {
                if (process.isAlive) process.destroyForcibly()
                process.inputStream.close()
                drain.cancel()
            }
        }
    }
}

object AuthenticatedWorkspace {
    fun onSignedOut() {
        com.saifai.virtualization.SaifNativeWorkspaceEngine.instance.terminateAllRunningProcesses()
    }
    fun ensureStarted(context: Context) {
        com.saifai.virtualization.SaifNativeWorkspaceEngine.instance.ensureWorkspaceInitialized(context)
    }
}
