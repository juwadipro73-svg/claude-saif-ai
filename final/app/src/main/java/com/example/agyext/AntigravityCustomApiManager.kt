/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agyext

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.ProviderSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class AgyApiProtocol(val label: String) {
    AUTO("Auto Detect"),
    OPENAI_COMPATIBLE("OpenAI Compatible (/chat/completions)"),
    ANTHROPIC("Anthropic (/v1/messages)"),
    GEMINI_NATIVE("Google Gemini (v1beta)")
}

data class AgyApiPreset(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val protocol: AgyApiProtocol,
    val defaultModels: List<String>,
    val keyPlaceholder: String,
    val docsUrl: String = ""
)

data class AgyCustomApiProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "OpenRouter",
    val presetId: String = "openrouter",
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val protocol: AgyApiProtocol = AgyApiProtocol.AUTO,
    val selectedModel: String = "anthropic/claude-sonnet-4",
    val models: List<String> = listOf(
        "anthropic/claude-sonnet-4",
        "google/gemini-2.5-pro",
        "openai/gpt-4.1",
        "deepseek/deepseek-chat-v3-0324"
    ),
    val extraHeaders: Map<String, String> = emptyMap()
) {
    fun isLocalEndpoint(): Boolean {
        val lower = baseUrl.lowercase().trim()
        return lower.startsWith("http://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://192.168.") ||
            lower.startsWith("http://10.")
    }

    fun isReady(): Boolean {
        return baseUrl.isNotBlank() &&
            selectedModel.isNotBlank() &&
            (apiKey.isNotBlank() || isLocalEndpoint())
    }

    fun effectiveModels(): List<String> {
        val merged = LinkedHashSet<String>()
        if (selectedModel.isNotBlank()) merged.add(selectedModel.trim())
        models.map { it.trim() }.filter { it.isNotBlank() }.forEach { merged.add(it) }
        return merged.toList()
    }
}

data class AgyCustomApiState(
    val enabled: Boolean = false,
    val activeProfileId: String = "",
    val profiles: List<AgyCustomApiProfile> = emptyList()
) {
    val activeProfile: AgyCustomApiProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    val isActiveAndReady: Boolean
        get() = enabled && (activeProfile?.isReady() == true)
}

data class AgyConnectionTestResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)

object AntigravityCustomApiManager {
    private const val PREFS_NAME = "saif_agy_custom_api_ext"
    private const val KEY_STATE_JSON = "state_json_v1"

    val PRESETS: List<AgyApiPreset> = listOf(
        AgyApiPreset(
            id = "openrouter",
            label = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "anthropic/claude-sonnet-4",
                "google/gemini-2.5-pro",
                "openai/gpt-4.1",
                "deepseek/deepseek-chat-v3-0324",
                "qwen/qwen3-coder"
            ),
            keyPlaceholder = "sk-or-v1-...",
            docsUrl = "https://openrouter.ai/keys"
        ),
        AgyApiPreset(
            id = "gemini",
            label = "Gemini API",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            protocol = AgyApiProtocol.GEMINI_NATIVE,
            defaultModels = listOf(
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-2.0-flash"
            ),
            keyPlaceholder = "AIzaSy...",
            docsUrl = "https://aistudio.google.com/app/apikey"
        ),
        AgyApiPreset(
            id = "openai",
            label = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "gpt-4.1",
                "gpt-4o",
                "o4-mini",
                "gpt-4.1-mini"
            ),
            keyPlaceholder = "sk-proj-...",
            docsUrl = "https://platform.openai.com/api-keys"
        ),
        AgyApiPreset(
            id = "anthropic",
            label = "Anthropic",
            defaultBaseUrl = "https://api.anthropic.com/v1",
            protocol = AgyApiProtocol.ANTHROPIC,
            defaultModels = listOf(
                "claude-sonnet-4-20250514",
                "claude-3-7-sonnet-latest",
                "claude-3-5-haiku-latest"
            ),
            keyPlaceholder = "sk-ant-...",
            docsUrl = "https://console.anthropic.com/settings/keys"
        ),
        AgyApiPreset(
            id = "groq",
            label = "Groq",
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "llama-3.3-70b-versatile",
                "deepseek-r1-distill-llama-70b",
                "qwen-qwq-32b"
            ),
            keyPlaceholder = "gsk_...",
            docsUrl = "https://console.groq.com/keys"
        ),
        AgyApiPreset(
            id = "deepseek",
            label = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "deepseek-chat",
                "deepseek-reasoner"
            ),
            keyPlaceholder = "sk-...",
            docsUrl = "https://platform.deepseek.com/api_keys"
        ),
        AgyApiPreset(
            id = "xai",
            label = "xAI Grok",
            defaultBaseUrl = "https://api.x.ai/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "grok-3",
                "grok-3-mini"
            ),
            keyPlaceholder = "xai-...",
            docsUrl = "https://console.x.ai"
        ),
        AgyApiPreset(
            id = "nvidia",
            label = "NVIDIA NIM",
            defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "deepseek-ai/deepseek-v3.1",
                "meta/llama-3.3-70b-instruct",
                "qwen/qwen2.5-coder-32b-instruct"
            ),
            keyPlaceholder = "nvapi-...",
            docsUrl = "https://build.nvidia.com"
        ),
        AgyApiPreset(
            id = "custom",
            label = "Custom URL",
            defaultBaseUrl = "https://api.openai.com/v1",
            protocol = AgyApiProtocol.AUTO,
            defaultModels = emptyList(),
            keyPlaceholder = "Bearer / API Key (optional for local)"
        )
    )

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _stateFlow = MutableStateFlow(AgyCustomApiState())
    val stateFlow: StateFlow<AgyCustomApiState> = _stateFlow.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val raw = sp.getString(KEY_STATE_JSON, null)
        if (!raw.isNullOrBlank()) {
            _stateFlow.value = parseStateJson(raw)
        }
    }

    fun getState(context: Context? = null): AgyCustomApiState {
        if (context != null && prefs == null) init(context)
        return _stateFlow.value
    }

    fun saveState(context: Context?, newState: AgyCustomApiState) {
        if (context != null && prefs == null) init(context)
        val normalizedProfiles = newState.profiles.map { profile ->
            profile.copy(
                name = profile.name.trim().ifBlank { "Custom API" },
                baseUrl = profile.baseUrl.trim(),
                apiKey = profile.apiKey.trim(),
                selectedModel = profile.selectedModel.trim(),
                models = profile.effectiveModels()
            )
        }
        val activeId = when {
            normalizedProfiles.any { it.id == newState.activeProfileId } -> newState.activeProfileId
            normalizedProfiles.isNotEmpty() -> normalizedProfiles.first().id
            else -> ""
        }
        val finalState = newState.copy(
            activeProfileId = activeId,
            profiles = normalizedProfiles
        )
        _stateFlow.value = finalState
        prefs?.edit()?.putString(KEY_STATE_JSON, encodeStateJson(finalState))?.apply()
    }

    fun setEnabled(context: Context?, enabled: Boolean) {
        val cur = getState(context)
        saveState(context, cur.copy(enabled = enabled))
    }

    fun upsertProfile(
        context: Context?,
        profile: AgyCustomApiProfile,
        activate: Boolean = true,
        enableExtension: Boolean = true
    ): AgyCustomApiState {
        val cur = getState(context)
        val updatedList = cur.profiles.toMutableList()
        val idx = updatedList.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            updatedList[idx] = profile
        } else {
            updatedList.add(profile)
        }
        val next = cur.copy(
            enabled = if (enableExtension) true else cur.enabled,
            activeProfileId = if (activate) profile.id else cur.activeProfileId.ifBlank { profile.id },
            profiles = updatedList
        )
        saveState(context, next)
        return _stateFlow.value
    }

    fun deleteProfile(context: Context?, profileId: String) {
        val cur = getState(context)
        val remaining = cur.profiles.filterNot { it.id == profileId }
        val nextActive = if (cur.activeProfileId == profileId) remaining.firstOrNull()?.id.orEmpty() else cur.activeProfileId
        saveState(
            context,
            cur.copy(
                enabled = cur.enabled && remaining.any { it.isReady() },
                activeProfileId = nextActive,
                profiles = remaining
            )
        )
    }

    fun selectActiveProfile(context: Context?, profileId: String) {
        val cur = getState(context)
        if (cur.profiles.none { it.id == profileId }) return
        saveState(context, cur.copy(activeProfileId = profileId))
    }

    /**
     * Allows switching models directly from the SAIF AI chat composer Model Chip when
     * Antigravity Custom API Extension is active. Models formatted as `<profileId>::<modelId>`
     * or bare `<modelId>` are both supported.
     */
    fun selectModelFromComposer(context: Context?, rawModelId: String) {
        val cur = getState(context)
        val trimmed = rawModelId.trim()
        if (trimmed.isBlank()) return
        if (trimmed.contains("::")) {
            val profileId = trimmed.substringBefore("::")
            val modelId = trimmed.substringAfter("::")
            val target = cur.profiles.firstOrNull { it.id == profileId } ?: return
            val updated = target.copy(selectedModel = modelId, models = (listOf(modelId) + target.models).distinct())
            upsertProfile(context, updated, activate = true, enableExtension = cur.enabled)
            return
        }
        val active = cur.activeProfile ?: return
        val updated = active.copy(selectedModel = trimmed, models = (listOf(trimmed) + active.models).distinct())
        upsertProfile(context, updated, activate = true, enableExtension = cur.enabled)
    }

    fun resetForTests() {
        prefs = null
        _stateFlow.value = AgyCustomApiState()
    }

    fun presetById(id: String): AgyApiPreset {
        return PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PRESETS.first()
    }

    fun createProfileFromPreset(presetId: String): AgyCustomApiProfile {
        val preset = presetById(presetId)
        return AgyCustomApiProfile(
            id = UUID.randomUUID().toString(),
            name = preset.label,
            presetId = preset.id,
            baseUrl = preset.defaultBaseUrl,
            apiKey = "",
            protocol = preset.protocol,
            selectedModel = preset.defaultModels.firstOrNull().orEmpty(),
            models = preset.defaultModels
        )
    }

    fun detectPresetFromApiKey(apiKey: String): AgyApiPreset? {
        val k = apiKey.trim()
        return when {
            k.startsWith("sk-or-") -> presetById("openrouter")
            k.startsWith("AIza") -> presetById("gemini")
            k.startsWith("sk-ant-") -> presetById("anthropic")
            k.startsWith("gsk_") -> presetById("groq")
            k.startsWith("xai-") -> presetById("xai")
            k.startsWith("nvapi-") -> presetById("nvidia")
            k.startsWith("sk-") && k.length > 20 -> presetById("openai")
            else -> null
        }
    }

    fun resolveProtocol(profile: AgyCustomApiProfile): AgyApiProtocol {
        if (profile.protocol != AgyApiProtocol.AUTO) return profile.protocol
        val url = profile.baseUrl.lowercase().trim()
        val key = profile.apiKey.trim()
        return when {
            url.contains("openrouter.ai") || url.contains("/openai") || url.endsWith("/chat/completions") ->
                AgyApiProtocol.OPENAI_COMPATIBLE
            url.contains("api.anthropic.com") || url.endsWith("/v1/messages") ||
                (key.startsWith("sk-ant-") && !url.contains("openrouter")) ->
                AgyApiProtocol.ANTHROPIC
            url.contains("generativelanguage.googleapis.com") && !url.contains("/openai") ->
                AgyApiProtocol.GEMINI_NATIVE
            key.startsWith("AIza") && (url.isBlank() || url.contains("googleapis.com")) ->
                AgyApiProtocol.GEMINI_NATIVE
            else -> AgyApiProtocol.OPENAI_COMPATIBLE
        }
    }

    fun buildOpenAiChatCompletionsUrl(rawBaseUrl: String): String {
        val clean = rawBaseUrl.trim().trimEnd('/')
        if (clean.endsWith("/chat/completions", ignoreCase = true)) return clean
        val lower = clean.lowercase()
        val hasVersionSegment = lower.endsWith("/v1") ||
            lower.endsWith("/v4") ||
            lower.endsWith("/openai") ||
            lower.contains("/v1/") ||
            lower.contains("/v1beta/openai")
        return if (hasVersionSegment) "$clean/chat/completions" else "$clean/v1/chat/completions"
    }

    fun buildOpenAiModelsUrl(rawBaseUrl: String): String {
        var clean = rawBaseUrl.trim().trimEnd('/')
        if (clean.endsWith("/chat/completions", ignoreCase = true)) {
            clean = clean.dropLast("/chat/completions".length).trimEnd('/')
        }
        if (clean.endsWith("/models", ignoreCase = true)) return clean
        val lower = clean.lowercase()
        val hasVersionSegment = lower.endsWith("/v1") ||
            lower.endsWith("/v4") ||
            lower.endsWith("/openai") ||
            lower.contains("/v1/") ||
            lower.contains("/v1beta/openai")
        return if (hasVersionSegment) "$clean/models" else "$clean/v1/models"
    }

    fun buildAnthropicMessagesUrl(rawBaseUrl: String): String {
        val clean = rawBaseUrl.trim().trimEnd('/')
        return when {
            clean.endsWith("/messages", ignoreCase = true) -> clean
            clean.endsWith("/v1", ignoreCase = true) -> "$clean/messages"
            else -> "$clean/v1/messages"
        }
    }

    fun buildGeminiGenerateContentUrl(rawBaseUrl: String, modelId: String, apiKey: String): String {
        var clean = rawBaseUrl.trim().trimEnd('/')
        if (clean.isBlank()) clean = "https://generativelanguage.googleapis.com/v1beta"
        if (!clean.contains("/v1beta") && !clean.contains("/v1")) {
            clean = "$clean/v1beta"
        }
        val cleanModel = modelId.trim().removePrefix("models/")
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "$clean/models/$cleanModel:generateContent?key=$encodedKey"
    }

    /**
     * Imports any existing API key & provider saved in legacy ProviderSettingsManager
     * (`AI PROVIDER & MODELS` section) into Antigravity Custom API Extension in 1 click.
     */
    fun importFromLegacyProviderSettings(context: Context?): AgyCustomApiProfile? {
        val legacy = runCatching { ProviderSettingsManager.loadState() }.getOrNull() ?: return null
        val key = legacy.apiKeys.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (key.isBlank()) return null

        val detectedPreset = detectPresetFromApiKey(key) ?: when {
            legacy.providerName.contains("OpenRouter", true) -> presetById("openrouter")
            legacy.providerName.contains("Gemini", true) -> presetById("gemini")
            legacy.providerName.contains("Anthropic", true) -> presetById("anthropic")
            legacy.providerName.contains("Groq", true) -> presetById("groq")
            legacy.providerName.contains("DeepSeek", true) -> presetById("deepseek")
            legacy.providerName.contains("xAI", true) -> presetById("xai")
            legacy.providerName.contains("OpenAI", true) -> presetById("openai")
            else -> presetById("custom")
        }

        val baseUrl = detectedPreset.defaultBaseUrl
        val model = legacy.activeModel.trim()
            .ifBlank { detectedPreset.defaultModels.firstOrNull() ?: "gpt-4o" }
        val profile = AgyCustomApiProfile(
            id = UUID.randomUUID().toString(),
            name = legacy.providerName.ifBlank { detectedPreset.label },
            presetId = detectedPreset.id,
            baseUrl = baseUrl,
            apiKey = key,
            protocol = detectedPreset.protocol,
            selectedModel = model,
            models = (listOf(model) + detectedPreset.defaultModels).filter { it.isNotBlank() }.distinct()
        )
        upsertProfile(context, profile, activate = true, enableExtension = true)
        return profile
    }

    suspend fun fetchModels(profile: AgyCustomApiProfile): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(profile.baseUrl.isNotBlank()) { "Enter a Base URL first." }
            val protocol = resolveProtocol(profile)
            when (protocol) {
                AgyApiProtocol.GEMINI_NATIVE -> {
                    require(profile.apiKey.isNotBlank()) { "Enter your Gemini API key first." }
                    var root = profile.baseUrl.trim().trimEnd('/')
                    if (!root.contains("/v1beta") && !root.contains("/v1")) root = "$root/v1beta"
                    val url = "$root/models?key=${URLEncoder.encode(profile.apiKey.trim(), "UTF-8")}"
                    val req = Request.Builder().url(url).get().build()
                    httpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) error("HTTP ${resp.code}: ${extractApiError(body)}")
                        val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val methods = obj.optJSONArray("supportedGenerationMethods")
                            val supportsGenerate = methods == null || (0 until methods.length()).any {
                                methods.optString(it).contains("generateContent", ignoreCase = true)
                            }
                            if (!supportsGenerate) continue
                            val id = obj.optString("name").removePrefix("models/").trim()
                            if (id.isNotBlank()) list.add(id)
                        }
                        list.ifEmpty { error("No models returned from Gemini endpoint.") }
                    }
                }
                AgyApiProtocol.ANTHROPIC -> {
                    val clean = profile.baseUrl.trim().trimEnd('/').removeSuffix("/messages")
                    val url = if (clean.endsWith("/v1", true)) "$clean/models" else "$clean/v1/models"
                    val reqBuilder = Request.Builder().url(url).get()
                        .header("anthropic-version", "2023-06-01")
                    if (profile.apiKey.isNotBlank()) reqBuilder.header("x-api-key", profile.apiKey.trim())
                    profile.extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                    httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            return@runCatching presetById("anthropic").defaultModels
                        }
                        val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val list = (0 until arr.length()).mapNotNull { idx ->
                            arr.optJSONObject(idx)?.optString("id")?.takeIf { it.isNotBlank() }
                        }
                        list.ifEmpty { presetById("anthropic").defaultModels }
                    }
                }
                else -> {
                    val url = buildOpenAiModelsUrl(profile.baseUrl)
                    val reqBuilder = Request.Builder().url(url).get()
                    if (profile.apiKey.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer ${profile.apiKey.trim()}")
                    }
                    if (profile.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                        reqBuilder.header("HTTP-Referer", "https://saif.ai")
                        reqBuilder.header("X-Title", "SAIF AI Antigravity")
                    }
                    profile.extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                    httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) error("HTTP ${resp.code}: ${extractApiError(body)}")
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val item = arr.opt(i)
                            val id = when (item) {
                                is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                                is String -> item
                                else -> ""
                            }.removePrefix("models/").trim()
                            if (id.isNotBlank()) list.add(id)
                        }
                        list.ifEmpty { error("Endpoint responded, but no model IDs were found.") }
                    }
                }
            }
        }
    }

    /** Minimal single-turn text completion for trusted in-app helpers (for example, the terminal command assistant). */
    suspend fun completeChat(
        profile: AgyCustomApiProfile,
        messages: List<JSONObject>,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(profile.isReady()) { "Select a ready AI provider and model first." }
            val protocol = resolveProtocol(profile)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = when (protocol) {
                AgyApiProtocol.GEMINI_NATIVE -> {
                    val contents = JSONArray()
                    val systemText = messages.filter { it.optString("role") == "system" }
                        .joinToString("\n\n") { it.optString("content") }.trim()
                    messages.filter { it.optString("role") != "system" }.forEach { message ->
                        contents.put(JSONObject()
                            .put("role", if (message.optString("role") == "assistant") "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", message.optString("content")))))
                    }
                    val payload = JSONObject().put("contents", contents)
                        .put("generationConfig", JSONObject().put("temperature", temperature).put("maxOutputTokens", 512))
                    if (systemText.isNotBlank()) {
                        payload.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
                    }
                    Request.Builder().url(buildGeminiGenerateContentUrl(profile.baseUrl, profile.selectedModel, profile.apiKey))
                        .post(payload.toString().toRequestBody(mediaType)).build()
                }
                AgyApiProtocol.ANTHROPIC -> {
                    val systemText = messages.filter { it.optString("role") == "system" }
                        .joinToString("\n\n") { it.optString("content") }.trim()
                    val chatMessages = JSONArray()
                    messages.filter { it.optString("role") != "system" }.forEach { message ->
                        chatMessages.put(JSONObject()
                            .put("role", if (message.optString("role") == "assistant") "assistant" else "user")
                            .put("content", message.optString("content")))
                    }
                    val payload = JSONObject().put("model", profile.selectedModel)
                        .put("max_tokens", 512).put("temperature", temperature).put("messages", chatMessages)
                    if (systemText.isNotBlank()) payload.put("system", systemText)
                    Request.Builder().url(buildAnthropicMessagesUrl(profile.baseUrl))
                        .header("x-api-key", profile.apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .apply { profile.extraHeaders.forEach { (k, v) -> header(k, v) } }
                        .post(payload.toString().toRequestBody(mediaType)).build()
                }
                else -> {
                    val chatMessages = JSONArray()
                    messages.forEach { message ->
                        chatMessages.put(JSONObject()
                            .put("role", message.optString("role").takeIf { it in setOf("system", "assistant") } ?: "user")
                            .put("content", message.optString("content")))
                    }
                    val payload = JSONObject().put("model", profile.selectedModel)
                        .put("messages", chatMessages).put("temperature", temperature).put("max_tokens", 512)
                    Request.Builder().url(buildOpenAiChatCompletionsUrl(profile.baseUrl))
                        .apply {
                            if (profile.apiKey.isNotBlank()) header("Authorization", "Bearer ${profile.apiKey}")
                            if (profile.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                                header("HTTP-Referer", "https://saif.ai")
                                header("X-Title", "SAIF AI Terminal Assistant")
                            }
                            profile.extraHeaders.forEach { (k, v) -> header(k, v) }
                        }
                        .post(payload.toString().toRequestBody(mediaType)).build()
                }
            }
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Provider API error (HTTP ${response.code}): ${extractApiError(com.example.llm.KeyStoreCrypto.redact(body))}")
                }
                val root = JSONObject(body)
                val answer = when (protocol) {
                    AgyApiProtocol.GEMINI_NATIVE -> root.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                            (0 until parts.length()).joinToString("\n") { parts.optJSONObject(it)?.optString("text").orEmpty() }
                        }.orEmpty()
                    AgyApiProtocol.ANTHROPIC -> root.optJSONArray("content")?.let { parts ->
                        (0 until parts.length()).joinToString("\n") { parts.optJSONObject(it)?.optString("text").orEmpty() }
                    }.orEmpty()
                    else -> root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                }
                answer.trim().ifBlank { error("Provider returned an empty response.") }
            }
        }
    }

    suspend fun testConnection(profile: AgyCustomApiProfile): AgyConnectionTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!profile.isReady()) {
            return@withContext AgyConnectionTestResult(
                ok = false,
                message = "Fill in Base URL, API Key, and Model ID first."
            )
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val protocol = resolveProtocol(profile)
        try {
            val req = when (protocol) {
                AgyApiProtocol.GEMINI_NATIVE -> {
                    val url = buildGeminiGenerateContentUrl(profile.baseUrl, profile.selectedModel, profile.apiKey)
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().put(
                            JSONObject().put("role", "user").put(
                                "parts", JSONArray().put(JSONObject().put("text", "Reply with OK"))
                            )
                        ))
                        put("generationConfig", JSONObject().put("maxOutputTokens", 16))
                    }
                    Request.Builder().url(url).post(payload.toString().toRequestBody(mediaType)).build()
                }
                AgyApiProtocol.ANTHROPIC -> {
                    val url = buildAnthropicMessagesUrl(profile.baseUrl)
                    val payload = JSONObject().apply {
                        put("model", profile.selectedModel)
                        put("max_tokens", 24)
                        put("messages", JSONArray().put(
                            JSONObject().put("role", "user").put("content", "Reply with OK")
                        ))
                    }
                    Request.Builder().url(url)
                        .header("x-api-key", profile.apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .apply { profile.extraHeaders.forEach { (k, v) -> header(k, v) } }
                        .post(payload.toString().toRequestBody(mediaType))
                        .build()
                }
                else -> {
                    val url = buildOpenAiChatCompletionsUrl(profile.baseUrl)
                    val payload = JSONObject().apply {
                        put("model", profile.selectedModel)
                        put("max_tokens", 24)
                        put("messages", JSONArray().put(
                            JSONObject().put("role", "user").put("content", "Reply with OK")
                        ))
                    }
                    Request.Builder().url(url)
                        .apply {
                            if (profile.apiKey.isNotBlank()) {
                                header("Authorization", "Bearer ${profile.apiKey}")
                            }
                            if (profile.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                                header("HTTP-Referer", "https://saif.ai")
                                header("X-Title", "SAIF AI Antigravity")
                            }
                            profile.extraHeaders.forEach { (k, v) -> header(k, v) }
                        }
                        .post(payload.toString().toRequestBody(mediaType))
                        .build()
                }
            }
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AgyConnectionTestResult(
                        ok = false,
                        message = "HTTP ${resp.code}: ${extractApiError(body)}",
                        latencyMs = elapsed
                    )
                }
                AgyConnectionTestResult(
                    ok = true,
                    message = "Connected to ${profile.selectedModel} (${elapsed}ms)",
                    latencyMs = elapsed
                )
            }
        } catch (err: Exception) {
            AgyConnectionTestResult(
                ok = false,
                message = e.localizedMessage ?: "Connection failed",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    internal fun extractApiError(rawBody: String): String {
        if (rawBody.isBlank()) return "Empty error response"
        return runCatching {
            val obj = JSONObject(rawBody)
            when {
                obj.optJSONObject("error") != null -> {
                    val err = obj.getJSONObject("error")
                    err.optString("message").ifBlank { err.toString() }
                }
                obj.has("message") -> obj.optString("message")
                else -> rawBody.take(220)
            }
        }.getOrDefault(rawBody.take(220))
    }

    internal fun encodeStateJson(state: AgyCustomApiState): String {
        val root = JSONObject()
        root.put("enabled", state.enabled)
        root.put("activeProfileId", state.activeProfileId)
        val arr = JSONArray()
        for (p in state.profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("presetId", p.presetId)
            obj.put("baseUrl", p.baseUrl)
            obj.put("apiKey", com.example.llm.KeyStoreCrypto.encrypt(p.apiKey))
            obj.put("protocol", p.protocol.name)
            obj.put("selectedModel", p.selectedModel)
            val mArr = JSONArray()
            p.effectiveModels().forEach { mArr.put(it) }
            obj.put("models", mArr)
            val hObj = JSONObject()
            p.extraHeaders.forEach { (k, v) -> hObj.put(k, v) }
            obj.put("extraHeaders", hObj)
            arr.put(obj)
        }
        root.put("profiles", arr)
        return root.toString()
    }

    internal fun parseStateJson(raw: String): AgyCustomApiState {
        return runCatching {
            val root = JSONObject(raw)
            val enabled = root.optBoolean("enabled", false)
            val activeId = root.optString("activeProfileId", "")
            val arr = root.optJSONArray("profiles") ?: JSONArray()
            val profiles = mutableListOf<AgyCustomApiProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val modelsArr = obj.optJSONArray("models") ?: JSONArray()
                val models = (0 until modelsArr.length())
                    .mapNotNull { idx -> modelsArr.optString(idx).takeIf { it.isNotBlank() } }
                val headersObj = obj.optJSONObject("extraHeaders")
                val headers = mutableMapOf<String, String>()
                headersObj?.keys()?.forEach { k ->
                    headers[k] = headersObj.optString(k)
                }
                val protocol = runCatching {
                    AgyApiProtocol.valueOf(obj.optString("protocol", AgyApiProtocol.AUTO.name))
                }.getOrDefault(AgyApiProtocol.AUTO)
                profiles.add(
                    AgyCustomApiProfile(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name", "Custom API"),
                        presetId = obj.optString("presetId", "custom"),
                        baseUrl = obj.optString("baseUrl", ""),
                        apiKey = com.example.llm.KeyStoreCrypto.decrypt(obj.optString("apiKey", "")),
                        protocol = protocol,
                        selectedModel = obj.optString("selectedModel", models.firstOrNull().orEmpty()),
                        models = models,
                        extraHeaders = headers
                    )
                )
            }
            AgyCustomApiState(
                enabled = enabled,
                activeProfileId = activeId,
                profiles = profiles
            )
        }.getOrDefault(AgyCustomApiState())
    }
}
