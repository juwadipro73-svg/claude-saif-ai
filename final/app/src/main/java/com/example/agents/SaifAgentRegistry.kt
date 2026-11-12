/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agents

import android.content.Context
import com.example.data.local.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SaifAgentCategory(val displayName: String) {
    CODING("Coding"),
    RESEARCH("Research"),
    AUTOMATION("Automation"),
    ANALYSIS("Analysis"),
    CONTENT("Content Creation"),
    CUSTOM("Custom Specialist")
}

data class SaifSpecialistAgent(
    val id: String,
    val slug: String,
    val name: String,
    val category: SaifAgentCategory,
    val description: String,
    val systemPrompt: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = true,
    val allowTerminal: Boolean = true,
    val allowFileEdits: Boolean = true,
    val allowWebFetch: Boolean = true,
    val temperature: Float = 0.3f
) {
    val slashTrigger: String get() = "/agent-${slug.lowercase()}"
}

data class SaifAgentRegistryState(
    val agents: List<SaifSpecialistAgent> = emptyList(),
    /** Null means general assistant (or smart auto-routing when [autoRouteByIntent] is true). */
    val activeAgentId: String? = null,
    val autoRouteByIntent: Boolean = true
) {
    val activeAgent: SaifSpecialistAgent?
        get() = activeAgentId?.let { id -> agents.firstOrNull { it.id == id && it.enabled } }

    val enabledAgents: List<SaifSpecialistAgent>
        get() = agents.filter { it.enabled }
}

/**
 * Central registry and orchestration engine for SAIF AI Specialist Agents.
 *
 * Manages built-in Coding, Research, Automation, Analysis, and Content Creation agents
 * alongside user-created custom agents, per-account persistence, slash-command routing,
 * and live system-instruction & tool-policy injection into ChatViewModel.
 */
object SaifAgentRegistry {

    private const val PREFS_PREFIX = "saif_specialist_agents_v1"
    private const val KEY_AGENTS_JSON = "agents_json"
    private const val KEY_ACTIVE_AGENT_ID = "active_agent_id"
    private const val KEY_AUTO_ROUTE = "auto_route_by_intent"

    val BUILT_IN_AGENTS: List<SaifSpecialistAgent> = listOf(
        SaifSpecialistAgent(
            id = "builtin_coding",
            slug = "coding",
            name = "Coding Architect",
            category = SaifAgentCategory.CODING,
            description = "Full-stack software engineering, Android/Kotlin/Web development, clean refactoring, unit testing, and APK builds.",
            systemPrompt = "You are SAIF AI's Coding Architect Agent. Prioritize production-grade, clean, modular code without placeholders. Inspect relevant files first, preserve existing behavior, write deterministic tests, and verify builds inside /root/workspace.",
            enabled = true,
            isBuiltIn = true,
            allowTerminal = true,
            allowFileEdits = true,
            allowWebFetch = true,
            temperature = 0.2f
        ),
        SaifSpecialistAgent(
            id = "builtin_research",
            slug = "research",
            name = "Deep Research Analyst",
            category = SaifAgentCategory.RESEARCH,
            description = "Multi-source technical research, documentation verification, architecture comparisons, and structured synthesis.",
            systemPrompt = "You are SAIF AI's Deep Research Analyst Agent. Break complex questions into verifiable sub-questions, inspect official documentation or URLs when needed, compare trade-offs objectively, and present clear, well-structured findings.",
            enabled = true,
            isBuiltIn = true,
            allowTerminal = false,
            allowFileEdits = true,
            allowWebFetch = true,
            temperature = 0.3f
        ),
        SaifSpecialistAgent(
            id = "builtin_automation",
            slug = "automation",
            name = "Terminal & DevOps Automation",
            category = SaifAgentCategory.AUTOMATION,
            description = "Shell & Python automation pipelines, Git/CI workflows, environment setup, background jobs, and error recovery.",
            systemPrompt = "You are SAIF AI's Terminal & DevOps Automation Agent. Design idempotent, safe shell and Python automation scripts with explicit error handling, preflight checks, and clear execution summaries inside /root/workspace.",
            enabled = true,
            isBuiltIn = true,
            allowTerminal = true,
            allowFileEdits = true,
            allowWebFetch = true,
            temperature = 0.15f
        ),
        SaifSpecialistAgent(
            id = "builtin_analysis",
            slug = "analysis",
            name = "Code, Log & Media Diagnostic",
            category = SaifAgentCategory.ANALYSIS,
            description = "Root-cause crash & stacktrace debugging, security/performance audits, and uploaded media/archive inspection.",
            systemPrompt = "You are SAIF AI's Code, Log & Media Analysis Agent. Perform rigorous root-cause diagnostics on logs, codebases, APKs, videos, and archives. Identify exact failure points, quantify impact, and provide concrete remediation steps.",
            enabled = true,
            isBuiltIn = true,
            allowTerminal = true,
            allowFileEdits = false,
            allowWebFetch = true,
            temperature = 0.2f
        ),
        SaifSpecialistAgent(
            id = "builtin_content",
            slug = "content",
            name = "Content & Script Director",
            category = SaifAgentCategory.CONTENT,
            description = "High-retention video scripts, Hinglish & English storytelling, technical documentation, READMEs, and UI copy.",
            systemPrompt = "You are SAIF AI's Content & Script Director Agent. Craft engaging, clear, audience-tailored scripts, documentation, and creative copy in English or natural Hinglish as requested, structured with strong hooks and actionable clarity.",
            enabled = true,
            isBuiltIn = true,
            allowTerminal = false,
            allowFileEdits = true,
            allowWebFetch = true,
            temperature = 0.65f
        )
    )

    private val _state = MutableStateFlow(SaifAgentRegistryState(agents = BUILT_IN_AGENTS))
    val state: StateFlow<SaifAgentRegistryState> = _state.asStateFlow()

    @Volatile
    private var boundScopeKey: String? = null

    private fun scopeKeyForUser(uid: String?): String {
        val clean = uid?.trim()?.takeIf { it.isNotEmpty() } ?: "guest"
        return "${PREFS_PREFIX}_$clean"
    }

    private fun currentScopeKey(): String {
        val uid = runCatching { AuthManager.currentUser.value?.uid }.getOrNull()
        return scopeKeyForUser(uid)
    }

    @Synchronized
    fun bindUserAccount(context: Context, uid: String?, previousUid: String? = null): SaifAgentRegistryState {
        val app = context.applicationContext
        val targetKey = scopeKeyForUser(uid)
        val targetPrefs = app.getSharedPreferences(targetKey, Context.MODE_PRIVATE)
        if (!uid.isNullOrBlank() && !uid.startsWith("guest:") && !targetPrefs.contains(KEY_AGENTS_JSON)) {
            val sourceUid = previousUid?.takeIf { it.startsWith("guest:") }
            val sourcePrefs = sourceUid?.let { app.getSharedPreferences(scopeKeyForUser(it), Context.MODE_PRIVATE) }
            if (sourcePrefs != null && sourcePrefs.contains(KEY_AGENTS_JSON)) {
                targetPrefs.edit()
                    .putString(KEY_AGENTS_JSON, sourcePrefs.getString(KEY_AGENTS_JSON, null))
                    .putString(KEY_ACTIVE_AGENT_ID, sourcePrefs.getString(KEY_ACTIVE_AGENT_ID, null))
                    .putBoolean(KEY_AUTO_ROUTE, sourcePrefs.getBoolean(KEY_AUTO_ROUTE, true))
                    .apply()
            }
        }
        return reload(app, uid)
    }

    @Synchronized
    fun ensureLoaded(context: Context, explicitUid: String? = null): SaifAgentRegistryState {
        val key = if (explicitUid != null) scopeKeyForUser(explicitUid) else currentScopeKey()
        if (boundScopeKey == key && _state.value.agents.isNotEmpty()) {
            return _state.value
        }
        return reload(context, explicitUid)
    }

    @Synchronized
    fun reload(context: Context, explicitUid: String? = null): SaifAgentRegistryState {
        val app = context.applicationContext
        val key = if (explicitUid != null) scopeKeyForUser(explicitUid) else currentScopeKey()
        boundScopeKey = key
        val prefs = app.getSharedPreferences(key, Context.MODE_PRIVATE)

        val savedJson = prefs.getString(KEY_AGENTS_JSON, null)
        val parsed = parseAgentsJson(savedJson)
        val merged = mergeWithBuiltIns(parsed)
        val activeId = prefs.getString(KEY_ACTIVE_AGENT_ID, null)?.takeIf { id ->
            merged.any { it.id == id && it.enabled }
        }
        val autoRoute = prefs.getBoolean(KEY_AUTO_ROUTE, true)

        val next = SaifAgentRegistryState(
            agents = merged,
            activeAgentId = activeId,
            autoRouteByIntent = autoRoute
        )
        _state.value = next
        return next
    }

    @Synchronized
    private fun persist(context: Context, next: SaifAgentRegistryState) {
        val app = context.applicationContext
        val key = boundScopeKey ?: currentScopeKey().also { boundScopeKey = it }
        val prefs = app.getSharedPreferences(key, Context.MODE_PRIVATE)
        val arr = JSONArray()
        next.agents.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("slug", a.slug)
                    put("name", a.name)
                    put("category", a.category.name)
                    put("description", a.description)
                    put("systemPrompt", a.systemPrompt)
                    put("enabled", a.enabled)
                    put("isBuiltIn", a.isBuiltIn)
                    put("allowTerminal", a.allowTerminal)
                    put("allowFileEdits", a.allowFileEdits)
                    put("allowWebFetch", a.allowWebFetch)
                    put("temperature", a.temperature.toDouble())
                }
            )
        }
        prefs.edit()
            .putString(KEY_AGENTS_JSON, arr.toString())
            .putString(KEY_ACTIVE_AGENT_ID, next.activeAgentId ?: "")
            .putBoolean(KEY_AUTO_ROUTE, next.autoRouteByIntent)
            .apply()
        _state.value = next
    }

    fun setAgentEnabled(context: Context, agentId: String, enabled: Boolean) {
        val cur = ensureLoaded(context)
        val updated = cur.agents.map { if (it.id == agentId) it.copy(enabled = enabled) else it }
        val nextActive = if (!enabled && cur.activeAgentId == agentId) null else cur.activeAgentId
        persist(context, cur.copy(agents = updated, activeAgentId = nextActive))
    }

    fun setActiveAgent(context: Context, agentId: String?) {
        val cur = ensureLoaded(context)
        val valid = agentId?.takeIf { id -> cur.agents.any { it.id == id && it.enabled } }
        persist(context, cur.copy(activeAgentId = valid))
    }

    fun setAutoRouteByIntent(context: Context, enabled: Boolean) {
        val cur = ensureLoaded(context)
        persist(context, cur.copy(autoRouteByIntent = enabled))
    }

    fun saveAgent(context: Context, agent: SaifSpecialistAgent): SaifSpecialistAgent {
        val cur = ensureLoaded(context)
        val normalizedSlug = agent.slug.trim().lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-")
            .trim('-')
            .ifBlank { "custom-${cur.agents.size + 1}" }
        val normalized = agent.copy(
            id = agent.id.ifBlank { "agent_${UUID.randomUUID().toString().take(8)}" },
            slug = normalizedSlug,
            name = agent.name.trim().ifBlank { "Custom Agent" },
            description = agent.description.trim().ifBlank { "Custom SAIF AI specialist agent" },
            systemPrompt = agent.systemPrompt.trim().ifBlank { "You are a helpful SAIF AI specialist agent." },
            temperature = agent.temperature.coerceIn(0f, 1.2f)
        )
        val exists = cur.agents.any { it.id == normalized.id }
        val nextAgents = if (exists) {
            cur.agents.map { if (it.id == normalized.id) normalized else it }
        } else {
            cur.agents + normalized
        }
        persist(context, cur.copy(agents = nextAgents))
        return normalized
    }

    fun deleteCustomAgent(context: Context, agentId: String) {
        val cur = ensureLoaded(context)
        val target = cur.agents.firstOrNull { it.id == agentId } ?: return
        if (target.isBuiltIn) return
        val nextAgents = cur.agents.filterNot { it.id == agentId }
        val nextActive = if (cur.activeAgentId == agentId) null else cur.activeAgentId
        persist(context, cur.copy(agents = nextAgents, activeAgentId = nextActive))
    }

    fun resetBuiltInDefaults(context: Context) {
        val cur = ensureLoaded(context)
        val customs = cur.agents.filterNot { it.isBuiltIn }
        persist(context, cur.copy(agents = BUILT_IN_AGENTS + customs))
    }

    /** Compatibility name for the live chat-turn resolver. */
    fun resolveAgentForTurn(context: Context, rawPrompt: String): Pair<SaifSpecialistAgent?, String> =
        resolveAgentForPrompt(context, rawPrompt)

    /**
     * Resolves which specialist agent should guide a given user prompt:
     * 1. Explicit `/agent-<slug>` prefix in the prompt
     * 2. Pinned [SaifAgentRegistryState.activeAgent]
     * 3. Intent-based smart routing when [SaifAgentRegistryState.autoRouteByIntent] is enabled
     */
    fun resolveAgentForPrompt(context: Context, rawPrompt: String): Pair<SaifSpecialistAgent?, String> {
        val cur = ensureLoaded(context)
        val trimmed = rawPrompt.trim()

        // 1. Explicit /agent-<slug> trigger
        if (trimmed.startsWith("/agent-", ignoreCase = true)) {
            val spaceIdx = trimmed.indexOfFirst { it.isWhitespace() }
            val token = if (spaceIdx == -1) trimmed else trimmed.substring(0, spaceIdx)
            val rest = if (spaceIdx == -1) "" else trimmed.substring(spaceIdx).trim()
            val slug = token.removePrefix("/agent-").lowercase()
            val matched = cur.agents.firstOrNull { it.slug.equals(slug, ignoreCase = true) && it.enabled }
            if (matched != null) {
                setActiveAgent(context, matched.id)
                return matched to rest.ifBlank { "Introduce your ${matched.name} capabilities and ask what task we should tackle first." }
            }
        }

        // 2. Pinned active agent
        cur.activeAgent?.let { return it to rawPrompt }

        // 3. Smart auto-routing across enabled specialist agents
        if (!cur.autoRouteByIntent) return null to rawPrompt
        val lower = trimmed.lowercase()
        val candidateCategory = when {
            Regex("\\b(script|storyboard|youtube|reel|caption|copywriting|blog|article|story|hinglish script)\\b").containsMatchIn(lower) ->
                SaifAgentCategory.CONTENT
            Regex("\\b(stacktrace|exception|crash|logcat|memory leak|vulnerability|audit|analyze log|ffprobe|inspect apk)\\b").containsMatchIn(lower) ->
                SaifAgentCategory.ANALYSIS
            Regex("\\b(cron|systemd|bash script|shell script|github actions|ci/cd|automate|pipeline|ssh|dockerfile)\\b").containsMatchIn(lower) ->
                SaifAgentCategory.AUTOMATION
            Regex("\\b(compare|benchmark|research|literature|state of the art|pros and cons|market analysis|citations)\\b").containsMatchIn(lower) ->
                SaifAgentCategory.RESEARCH
            Regex("\\b(kotlin|java|python|typescript|react|gradle|compile|refactor|unit test|function|class|build apk|bug fix)\\b").containsMatchIn(lower) ->
                SaifAgentCategory.CODING
            else -> null
        }
        val routed = candidateCategory?.let { cat ->
            cur.enabledAgents.firstOrNull { it.category == cat }
        }
        return routed to rawPrompt
    }

    /** Builds the live system/context directive injected when an agent handles a prompt. */
    fun buildAgentDirective(agent: SaifSpecialistAgent): String {
        val allowedTools = buildList {
            if (agent.allowTerminal) add("terminal shell execution")
            if (agent.allowFileEdits) add("workspace file creation/editing")
            if (agent.allowWebFetch) add("web & URL verification")
        }
        val restrictedTools = buildList {
            if (!agent.allowTerminal) add("do NOT execute shell commands unless explicitly overridden by the user")
            if (!agent.allowFileEdits) add("do NOT modify workspace files (read-only analysis mode)")
            if (!agent.allowWebFetch) add("do NOT make external web requests")
        }
        return buildString {
            appendLine("[SAIF AI Specialist Agent Active: ${agent.name} (${agent.category.displayName})]")
            appendLine(agent.systemPrompt)
            if (allowedTools.isNotEmpty()) {
                appendLine("Permitted capabilities: ${allowedTools.joinToString(", ")}.")
            }
            if (restrictedTools.isNotEmpty()) {
                appendLine("Tool guardrails: ${restrictedTools.joinToString("; ")}.")
            }
        }.trim()
    }

    private fun mergeWithBuiltIns(saved: List<SaifSpecialistAgent>): List<SaifSpecialistAgent> {
        if (saved.isEmpty()) return BUILT_IN_AGENTS
        val byId = saved.associateBy { it.id }
        val builtIns = BUILT_IN_AGENTS.map { def ->
            byId[def.id]?.copy(isBuiltIn = true, category = def.category) ?: def
        }
        val customs = saved.filter { s -> BUILT_IN_AGENTS.none { it.id == s.id } }
            .map { it.copy(isBuiltIn = false) }
        return builtIns + customs
    }

    private fun parseAgentsJson(raw: String?): List<SaifSpecialistAgent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val catName = o.optString("category", SaifAgentCategory.CUSTOM.name)
                    val category = runCatching { SaifAgentCategory.valueOf(catName) }
                        .getOrDefault(SaifAgentCategory.CUSTOM)
                    add(
                        SaifSpecialistAgent(
                            id = o.optString("id"),
                            slug = o.optString("slug"),
                            name = o.optString("name"),
                            category = category,
                            description = o.optString("description"),
                            systemPrompt = o.optString("systemPrompt"),
                            enabled = o.optBoolean("enabled", true),
                            isBuiltIn = o.optBoolean("isBuiltIn", false),
                            allowTerminal = o.optBoolean("allowTerminal", true),
                            allowFileEdits = o.optBoolean("allowFileEdits", true),
                            allowWebFetch = o.optBoolean("allowWebFetch", true),
                            temperature = o.optDouble("temperature", 0.3).toFloat()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
