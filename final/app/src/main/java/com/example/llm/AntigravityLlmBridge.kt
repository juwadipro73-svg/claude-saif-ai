/* Copyright (c) SAIF AI. All rights reserved. */
package com.example.llm

import android.app.Application
import com.example.agyext.AntigravityCustomApiManager
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Compatibility entry point for in-app helpers; uses the user's API, never a removed hub. */
object AntigravityLlmBridge {
    @Volatile private var appRef: Application? = null
    fun init(app: Application) { appRef = app; AntigravityCustomApiManager.init(app) }
    fun isAvailable(): Boolean = appRef?.let {
        AntigravityCustomApiManager.getState(it).isActiveAndReady
    } == true

    suspend fun callIfAvailable(
        request: LlmRequest, preferredModel: String = "",
        onChunk: suspend (String) -> Unit = {},
        onThinking: (suspend (String) -> Unit)? = null
    ): Result<LlmResponse>? {
        val app = appRef ?: return null
        val state = AntigravityCustomApiManager.getState(app)
        if (!state.isActiveAndReady) return null
        val profile = state.activeProfile ?: return null
        if (request.imagesBase64.isNotEmpty()) return Result.failure(IllegalArgumentException(
            "This helper requires a multimodal provider key in AI Settings; image attachments are supported directly in main chat."))
        val selected = if (preferredModel in profile.effectiveModels()) profile.copy(selectedModel = preferredModel) else profile
        return try {
            val messages = buildList {
                if (request.systemPrompt.isNotBlank()) add(JSONObject().put("role", "system").put("content", request.systemPrompt))
                request.history.forEach { (role, text) ->
                    add(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", text))
                }
                add(JSONObject().put("role", "user").put("content", request.prompt))
            }
            val text = AntigravityCustomApiManager.completeChat(selected, messages,
                request.temperature ?: 0.2, request.maxTokens ?: 8192).getOrThrow()
            onChunk(text)
            Result.success(LlmResponse(text = text))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { Result.failure(error) }
    }
}
