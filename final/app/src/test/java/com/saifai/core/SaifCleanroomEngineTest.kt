/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.core

import com.saifai.engine.SaifMcpServerHub
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SaifCleanroomEngineTest {

    @Test
    fun voiceEnginePermanentlyBindsToKabir() {
        assertEquals("Kabir", SaifVoiceEngine.PERMANENT_VOICE_ID)
        assertTrue(SaifVoiceEngine.VOICE_DISPLAY_NAME.contains("Kabir"))
    }

    @Test
    fun mcpServerHubListsRegisteredTools() = runBlocking {
        val hub = SaifMcpServerHub.instance
        val schemas = hub.listToolSchemas()
        assertTrue("MCP Hub should register built-in tools", schemas.length() > 0)
        
        val firstTool = schemas.getJSONObject(0)
        assertEquals("saif-core-mcp", firstTool.getString("server"))
        assertEquals("saif_read_workspace", firstTool.getString("name"))

        val result = hub.executeTool("saif-core-mcp", "saif_read_workspace", JSONObject())
        assertTrue(result.contains("SAIF AI MCP read"))
    }

    @Test
    fun virtualTerminalExecutesCommandsSafely() = runBlocking {
        val terminal = SaifVirtualTerminal(File("/tmp"))
        val outcome = terminal.executeCommand(listOf("echo", "SAIF_AI_PROPRIETARY_TEST"))
        assertEquals(0, outcome.exitCode)
        assertEquals("SAIF_AI_PROPRIETARY_TEST", outcome.standardOutput)
    }

    @Test
    fun compilerPipelineProcessesBuildConfig() = runBlocking {
        val pipeline = SaifCompilerPipeline()
        val config = SaifCompilerPipeline.BuildConfig(
            projectDirectory = File("/tmp"),
            applicationName = "TestApp",
            packageName = "com.saifai.test"
        )
        val report = pipeline.compileProject(config)
        assertNotNull(report)
        assertTrue(report.durationMs >= 0)
    }

    @Test
    fun modelGatewayStreamsInferenceChunks() = runBlocking {
        val gateway = SaifModelGateway()
        val spec = SaifModelGateway.ModelSpec(
            providerSlug = "saif-universal",
            modelIdentifier = "saif-flash"
        )
        val prompt = SaifModelGateway.GenerationPrompt(
            systemPrompt = "You are SAIF AI",
            userQuery = "Status report"
        )
        val firstChunk = gateway.streamInference(spec, prompt).first()
        assertTrue(firstChunk.contains("SAIF AI Gateway connected"))
    }
}
