/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agyext

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.saif.data.agent.ChatSessionStore
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AntigravityCustomApiExtensionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        AntigravityCustomApiManager.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("saif_agy_custom_api_ext", Context.MODE_PRIVATE)
            .edit().clear().commit()
        AntigravityCustomApiManager.init(context)
    }

    @After
    fun tearDown() {
        AntigravityCustomApiManager.resetForTests()
    }

    @Test
    fun detectsApiKeyPrefixesAndResolvesProtocolsAndUrls() {
        assertEquals("openrouter", AntigravityCustomApiManager.detectPresetFromApiKey("sk-or-v1-abc123456")?.id)
        assertEquals("gemini", AntigravityCustomApiManager.detectPresetFromApiKey("AIzaSyD-123456789")?.id)
        assertEquals("anthropic", AntigravityCustomApiManager.detectPresetFromApiKey("sk-ant-api03-xyz")?.id)
        assertEquals("groq", AntigravityCustomApiManager.detectPresetFromApiKey("gsk_abcdef12345")?.id)
        assertEquals("xai", AntigravityCustomApiManager.detectPresetFromApiKey("xai-123456789")?.id)
        assertEquals("nvidia", AntigravityCustomApiManager.detectPresetFromApiKey("nvapi-123456789")?.id)
        assertEquals("openai", AntigravityCustomApiManager.detectPresetFromApiKey("sk-proj-123456789012345678901234")?.id)

        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            AntigravityCustomApiManager.buildOpenAiChatCompletionsUrl("https://openrouter.ai/api/v1/")
        )
        assertEquals(
            "https://custom.gateway.example/v1/chat/completions",
            AntigravityCustomApiManager.buildOpenAiChatCompletionsUrl("https://custom.gateway.example/v1/chat/completions")
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AntigravityCustomApiManager.buildAnthropicMessagesUrl("https://api.anthropic.com")
        )
        assertTrue(
            AntigravityCustomApiManager.buildGeminiGenerateContentUrl(
                "https://generativelanguage.googleapis.com/v1beta",
                "models/gemini-2.5-pro",
                "AIzaTest"
            ).endsWith("/v1beta/models/gemini-2.5-pro:generateContent?key=AIzaTest")
        )
    }

    @Test
    fun persistsMultiProfileStateAndSwitchesModelsFromComposer() {
        val orProfile = AntigravityCustomApiManager.createProfileFromPreset("openrouter")
            .copy(apiKey = "sk-or-v1-test", selectedModel = "anthropic/claude-sonnet-4")
        val customProfile = AgyCustomApiProfile(
            name = "My Proxy",
            presetId = "custom",
            baseUrl = "https://proxy.example.com/v1",
            apiKey = "secret-token",
            selectedModel = "qwen-coder-plus",
            models = listOf("qwen-coder-plus", "deepseek-v3")
        )

        AntigravityCustomApiManager.upsertProfile(context, orProfile, activate = true, enableExtension = true)
        AntigravityCustomApiManager.upsertProfile(context, customProfile, activate = false, enableExtension = true)

        val state1 = AntigravityCustomApiManager.getState(context)
        assertTrue(state1.isActiveAndReady)
        assertEquals(2, state1.profiles.size)
        assertEquals(orProfile.id, state1.activeProfile?.id)

        // Switch model across profiles via composer "<profileId>::<modelId>"
        AntigravityCustomApiManager.selectModelFromComposer(context, "${customProfile.id}::deepseek-v3")
        val state2 = AntigravityCustomApiManager.getState(context)
        assertEquals(customProfile.id, state2.activeProfile?.id)
        assertEquals("deepseek-v3", state2.activeProfile?.selectedModel)

        // Verify JSON persistence round-trip
        val encoded = AntigravityCustomApiManager.encodeStateJson(state2)
        val decoded = AntigravityCustomApiManager.parseStateJson(encoded)
        assertEquals(2, decoded.profiles.size)
        assertEquals("deepseek-v3", decoded.activeProfile?.selectedModel)
    }

    @Test
    fun executesFullAntigravityToolLoopAgainstCustomApiEndpoint() = runBlocking {
        val turnCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val step = turnCount.incrementAndGet()
            val responseJson = if (step == 1) {
                // Step 1: Model calls write_file in /root/workspace
                JSONObject().put(
                    "choices",
                    JSONArray().put(
                        JSONObject().put(
                            "message",
                            JSONObject()
                                .put("role", "assistant")
                                .put("reasoning_content", "Creating hello.txt inside Antigravity workspace.")
                                .put("content", "Writing workspace file now.")
                                .put(
                                    "tool_calls",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("id", "call_write_1")
                                            .put("type", "function")
                                            .put(
                                                "function",
                                                JSONObject()
                                                    .put("name", "write_file")
                                                    .put(
                                                        "arguments",
                                                        JSONObject()
                                                            .put("path", "/root/workspace/custom_api_test/hello.txt")
                                                            .put("content", "Hello from Custom API Antigravity!")
                                                            .toString()
                                                    )
                                            )
                                    )
                                )
                        )
                    )
                )
            } else {
                // Step 2: Model reads tool output and produces final answer
                JSONObject().put(
                    "choices",
                    JSONArray().put(
                        JSONObject().put(
                            "message",
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", "Created `/root/workspace/custom_api_test/hello.txt` successfully!")
                        )
                    )
                )
            }
            val bytes = responseJson.toString().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val port = server.address.port
            val profile = AgyCustomApiProfile(
                name = "Loopback Custom API",
                presetId = "custom",
                baseUrl = "http://127.0.0.1:$port/v1",
                apiKey = "test-key",
                protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
                selectedModel = "custom-coder-v1"
            )
            val store = ChatSessionStore()
            val engine = AntigravityCustomApiAgentEngine(
                appContext = context,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                store = store,
                isAutoApproveProvider = { true }
            )

            val reply = engine.executeTurn(
                sessionId = "sess_custom_1",
                profile = profile,
                displayUserText = "Create hello.txt in workspace",
                expandedAgentPrompt = "Create hello.txt in workspace",
                inlineAttachments = emptyList(),
                temperature = 0.2f,
                onStreamingText = {}
            )

            assertTrue(reply.contains("hello.txt"))
            val writtenFile = File(context.filesDir, "home/custom_api_test/hello.txt")
            assertTrue("Expected workspace file to be written by Custom API agent", writtenFile.isFile)
            assertEquals("Hello from Custom API Antigravity!", writtenFile.readText())

            val assistantMsg = store.messages.value.last()
            assertEquals(1, assistantMsg.toolCalls.size)
            assertEquals("SUCCESS", assistantMsg.toolCalls.first().status)
            assertTrue(assistantMsg.content.contains("<!-- tool_call:"))
            assertEquals("Creating hello.txt inside Antigravity workspace.", assistantMsg.thoughtText)

            // Verify transcript reloads cleanly
            val freshStore = ChatSessionStore()
            val freshEngine = AntigravityCustomApiAgentEngine(
                appContext = context,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                store = freshStore,
                isAutoApproveProvider = { true }
            )
            assertTrue(freshEngine.loadSessionMessages("sess_custom_1"))
            assertEquals(2, freshStore.messages.value.size)
        } finally {
            server.stop(0)
        }
    }
}
