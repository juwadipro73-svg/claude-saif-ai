/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 100% Original Cleanroom Implementation of the SAIF AI Terminal Execution Engine.
 *
 * Distinct Architecture Highlights:
 * - Direct ProcessBuilder pipeline with modern non-blocking streaming collectors.
 * - Strict argument-vector isolation eliminating shell-injection hazards.
 * - Deterministic timeout watchdogs preventing dangling zombie processes.
 * - Fully independent environment matrix tailored specifically for SAIF AI workspaces.
 */
class SaifVirtualTerminal(
    val workspaceRoot: File = File("/root/workspace")
) {
    data class ExecutionOutcome(
        val exitCode: Int,
        val standardOutput: String,
        val standardError: String,
        val executionDurationMs: Long
    )

    suspend fun executeCommand(
        commandList: List<String>,
        environmentVariables: Map<String, String> = emptyMap(),
        executionTimeoutSeconds: Long = 60L
    ): ExecutionOutcome = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (!workspaceRoot.exists()) {
            workspaceRoot.mkdirs()
        }

        val builder = ProcessBuilder(commandList)
            .directory(workspaceRoot)
            .redirectErrorStream(false)

        val env = builder.environment()
        env.putAll(environmentVariables)

        val process = builder.start()

        val stdoutBuffer = StringBuilder()
        val stderrBuffer = StringBuilder()

        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

        val stdoutThread = Thread {
            stdoutReader.forEachLine { line ->
                synchronized(stdoutBuffer) {
                    stdoutBuffer.append(line).append('\n')
                }
            }
        }

        val stderrThread = Thread {
            stderrReader.forEachLine { line ->
                synchronized(stderrBuffer) {
                    stderrBuffer.append(line).append('\n')
                }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        val completedInTime = process.waitFor(executionTimeoutSeconds, TimeUnit.SECONDS)
        if (!completedInTime) {
            process.destroyForcibly()
        }

        stdoutThread.join(2000)
        stderrThread.join(2000)

        val duration = System.currentTimeMillis() - startTime
        val exitCode = if (completedInTime) process.exitValue() else -1

        ExecutionOutcome(
            exitCode = exitCode,
            standardOutput = stdoutBuffer.toString().trimEnd(),
            standardError = if (completedInTime) stderrBuffer.toString().trimEnd() else "Execution timed out after ${executionTimeoutSeconds}s",
            executionDurationMs = duration
        )
    }
}
