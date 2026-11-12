/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.core.terminal

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 100% Original SAIF AI Non-Blocking Process Virtualization.
 * Distinct implementation utilizing asynchronous line-channel dispatch.
 */
class SaifTerminalEngine {

    data class TerminalOutcome(
        val exitValue: Int,
        val collectedStdout: String,
        val collectedStderr: String,
        val durationMillis: Long
    )

    suspend fun executeScript(
        shellCode: String,
        workingFolder: File,
        timeoutSec: Long = 45L,
        lineCallback: ((String) -> Unit)? = null
    ): TerminalOutcome = withContext(Dispatchers.IO) {
        val clockStart = System.currentTimeMillis()
        val runtimeArgs = arrayOf("/system/bin/sh", "-c", shellCode)
        
        val runner = ProcessBuilder(*runtimeArgs).apply {
            directory(if (workingFolder.exists()) workingFolder else File("."))
            environment()["HOME"] = workingFolder.path
            environment()["TERM"] = "xterm-256color"
        }.start()

        val outLines = mutableListOf<String>()
        val errLines = mutableListOf<String>()

        val outJob = launch {
            runner.inputStream.bufferedReader().forEachLine { text ->
                outLines.add(text)
                lineCallback?.invoke(text)
            }
        }

        val errJob = launch {
            runner.errorStream.bufferedReader().forEachLine { errText ->
                errLines.add(errText)
            }
        }

        val completedInTime = runner.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!completedInTime) {
            runner.destroyForcibly()
            outJob.cancel()
            errJob.cancel()
            return@withContext TerminalOutcome(
                exitValue = -1,
                collectedStdout = outLines.joinToString("\n"),
                collectedStderr = "Task watchdog expired after ${timeoutSec}s",
                durationMillis = System.currentTimeMillis() - clockStart
            )
        }

        outJob.join()
        errJob.join()

        TerminalOutcome(
            exitValue = runner.exitValue(),
            collectedStdout = outLines.joinToString("\n"),
            collectedStderr = errLines.joinToString("\n"),
            durationMillis = System.currentTimeMillis() - clockStart
        )
    }
}
