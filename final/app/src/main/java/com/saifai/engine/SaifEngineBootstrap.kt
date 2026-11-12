/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.engine

import android.app.Application
import android.util.Log
import com.saifai.core.SaifCompilerPipeline
import com.saifai.core.SaifNeuralOrchestrator
import com.saifai.core.SaifVirtualTerminal
import com.saifai.core.SaifVoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 100% Original Cleanroom Bootstrap Coordinator for SAIF AI.
 *
 * Distinct Architecture Highlights:
 * - Direct in-process lifecycle coordinator unifying virtualization, MCP tools,
 *   neural speech, process management, and compiler pipelines.
 * - Reactive state reporting with zero legacy copyleft hooks.
 * - Non-blocking asynchronous initialization on application startup.
 */
object SaifEngineBootstrap {

    private const val TAG = "SaifEngineBootstrap"

    @Volatile
    private var isBootstrapped = false

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var neuralOrchestrator: SaifNeuralOrchestrator
        private set

    lateinit var virtualTerminal: SaifVirtualTerminal
        private set

    lateinit var compilerPipeline: SaifCompilerPipeline
        private set

    fun initialize(application: Application) {
        if (isBootstrapped) return
        isBootstrapped = true

        Log.i(TAG, "Bootstrapping SAIF AI Cleanroom Engine Suite...")

        // 1. Initialize Virtual Terminal & Execution Pipelines
        virtualTerminal = SaifVirtualTerminal()
        compilerPipeline = SaifCompilerPipeline(virtualTerminal)
        neuralOrchestrator = SaifNeuralOrchestrator(bootstrapScope)

        // 2. Initialize Neural Speech Engine (Kabir Voice Profile Authority)
        SaifVoiceEngine.instance.initialize(application)

        // 3. Mount Native MCP Server Hub
        val mcpHub = SaifMcpServerHub.instance
        Log.i(TAG, "Mounted native SAIF AI MCP tool schemas: ${mcpHub.listToolSchemas().length()} tools registered")

        // 4. Ensure Linux Environment Workspace Hierarchy
        bootstrapScope.launch {
            SaifLinuxEnvironmentManager.instance.ensureEnvironmentReady(application) {
                Log.i(TAG, "SAIF AI Linux workspace virtualization verified at /root/workspace")
            }
        }

        Log.i(TAG, "SAIF AI Cleanroom Engine Suite initialized successfully under SAIF AI Proprietary License.")
    }
}
