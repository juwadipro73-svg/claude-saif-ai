/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.slash

import android.content.Context
import com.example.agents.SaifAgentRegistry
import com.example.saif.data.remote.CustomSlashCommands
import com.example.saif.data.remote.PromptExpansion
import com.example.saif.data.remote.SavedAiSkills
import com.example.saif.data.remote.SlashCommandItem
import com.example.saif.data.remote.SlashCommandsCache

enum class SaifAppSlashAction {
    OPEN_VIDEO_STUDIO,
    OPEN_TERMINAL,
    OPEN_CODE_IDE,
    OPEN_BROWSER,
    OPEN_NOTES,
    NEW_CHAT,
    CLEAR_CHAT,
    OPEN_AGENTS,
    OPEN_SKILLS,
    OPEN_PROVIDERS,
    OPEN_GITHUB,
    OPEN_SETTINGS
}

data class ActiveSlashToken(
    val startIndex: Int,
    val endIndex: Int,
    val rawToken: String, // e.g. "/pl" or "/plu:demo"
    val queryWithoutLeadingSlash: String // e.g. "pl" or "plu:demo"
)

/**
 * Unified SAIF AI Slash Command Engine (Section 2).
 *
 * Repairs and connects all slash command sources on the main screen:
 * 1. Immediate app actions (/terminal, /ide, /code, /browser, /video, /notes, /clear, /new, /agents, /skills, /providers, /github, /settings)
 * 2. Specialist AI agents (/agent-coding, /agent-research, /agent-automation, /agent-analysis, /agent-content + custom agents)
 * 3. Built-in workflow & engineering commands (/plan, /goal, /boost, /grill-me, /teamwork-preview, /learn, /schedule, /apk, /debug, /review, /research, /explain, /test)
 * 4. Built-in & live Hub skills (/android-cli, /modern-web-guidance, /generative_ui, /permissioned-github, etc.)
 * 5. Saved GitHub AI Skills (SavedAiSkills)
 * 6. User-created Custom Slash Commands (CustomSlashCommands)
 * 7. Custom SAIF AI Plugins (/plu:<plugin>:<skill>)
 */
object SaifSlashCommandRegistry {

    val APP_COMMANDS: List<SlashCommandItem> = listOf(
        SlashCommandItem("terminal", "/terminal", "Open the Linux & Multi-Provider Terminal", type = "app"),
        SlashCommandItem("ide", "/ide", "Open the SAIF AI Code Editor & Workspace IDE", type = "app"),
        SlashCommandItem("code", "/code", "Open the SAIF AI Code Editor & Workspace IDE", type = "app"),
        SlashCommandItem("browser", "/browser", "Open the In-App Research & Preview Browser", type = "app"),
        SlashCommandItem("video", "/video", "Open SAIF AI Video Studio", type = "app"),
        SlashCommandItem("notes", "/notes", "Open SAIF AI Notes Workspace", type = "app"),
        SlashCommandItem("agents", "/agents", "Manage Coding, Research, Automation, Analysis & Content AI Agents", type = "app"),
        SlashCommandItem("skills", "/skills", "Manage AI Skills & Import Skills from GitHub", type = "app"),
        SlashCommandItem("providers", "/providers", "Configure Terminal & Custom AI Providers", type = "app"),
        SlashCommandItem("github", "/github", "Connect GitHub Account & Sync Repositories", type = "app"),
        SlashCommandItem("new", "/new", "Start a fresh chat conversation", type = "app"),
        SlashCommandItem("clear", "/clear", "Clear messages in the current chat", type = "app"),
        SlashCommandItem("settings", "/settings", "Open SAIF AI Settings", type = "app")
    )

    val WORKFLOW_COMMANDS: List<SlashCommandItem> = listOf(
        SlashCommandItem("plan", "/plan", "Create a step-by-step architectural & execution plan before making changes", type = "command", argumentHint = "<goal>"),
        SlashCommandItem("goal", "/goal", "Set or update the high-level session objective and success criteria", type = "command", argumentHint = "<objective>"),
        SlashCommandItem("boost", "/boost", "Optimize performance, clean bottlenecks, and harden edge cases", type = "command", argumentHint = "[target]"),
        SlashCommandItem("grill-me", "/grill-me", "Stress-test an idea or design with tough engineering questions", type = "command", argumentHint = "<idea>"),
        SlashCommandItem("teamwork-preview", "/teamwork-preview", "Coordinate multi-agent breakdown across Coding, Research & QA", type = "command", argumentHint = "<project>"),
        SlashCommandItem("learn", "/learn", "Summarize lessons learned and save reusable workspace notes", type = "command", argumentHint = "[topic]"),
        SlashCommandItem("schedule", "/schedule", "Create or manage scheduled recurring automation tasks", type = "command", argumentHint = "<task>"),
        SlashCommandItem("apk", "/apk", "Scaffold, build, and sign an Android APK inside /root/workspace", type = "command", argumentHint = "<app description>"),
        SlashCommandItem("debug", "/debug", "Diagnose root cause of an error/stacktrace and implement a verified fix", type = "command", argumentHint = "<error or file>"),
        SlashCommandItem("review", "/review", "Audit code for bugs, security risks, regressions, and edge cases", type = "command", argumentHint = "[file or diff]"),
        SlashCommandItem("research", "/research", "Run a structured multi-angle technical research & comparison report", type = "command", argumentHint = "<topic>"),
        SlashCommandItem("explain", "/explain", "Explain code, commands, or architecture clearly step by step", type = "command", argumentHint = "<subject>"),
        SlashCommandItem("test", "/test", "Write and execute deterministic unit/integration tests for workspace code", type = "command", argumentHint = "[target]")
    )

    private val BUILT_IN_EXPANSIONS: Map<String, (String) -> String> = mapOf(
        "/plan" to { arg ->
            buildString {
                appendLine("Create a structured, concrete implementation plan before editing files.")
                appendLine("Include: 1) Root-cause / current-state summary, 2) Exact files & modules affected, 3) Ordered implementation steps, 4) Verification & test strategy.")
                if (arg.isNotBlank()) appendLine("\nUser task:\n$arg")
                else appendLine("\nInspect the current workspace context and ask what goal to plan if not yet specified.")
            }
        },
        "/goal" to { arg ->
            "Establish and track the primary objective for this session, including explicit acceptance criteria, constraints, and verification checkpoints.\n\nObjective:\n${arg.ifBlank { "Define our session objective based on recent context." }}"
        },
        "/boost" to { arg ->
            "Analyze and optimize performance, responsiveness, memory/CPU efficiency, and reliability without breaking existing functionality.\n\nTarget:\n${arg.ifBlank { "Inspect the active project in /root/workspace and identify the highest-impact optimizations." }}"
        },
        "/grill-me" to { arg ->
            "Act as a rigorous staff engineer reviewing my proposal. Ask pointed, constructive questions exposing hidden assumptions, edge cases, security issues, and scaling bottlenecks before we build.\n\nProposal / Topic:\n${arg.ifBlank { "Ask me to share the design or idea I want stress-tested." }}"
        },
        "/teamwork-preview" to { arg ->
            "Coordinate a multi-specialist breakdown across Architecture, Implementation, Automation, and QA. Present each specialist track clearly and execute the agreed steps systematically.\n\nProject:\n${arg.ifBlank { "Break down our current workspace project into coordinated specialist tracks." }}"
        },
        "/learn" to { arg ->
            "Distill key technical takeaways, root causes, and reusable patterns from this work into clear, structured notes.\n\nFocus:\n${arg.ifBlank { "Summarize the key engineering lessons from our current session." }}"
        },
        "/schedule" to { arg ->
            "Design an automated recurring task or shell workflow (with safe prechecks, logging, and retry handling) inside /root/workspace.\n\nTask:\n${arg.ifBlank { "Describe options for scheduling automated scripts in the workspace." }}"
        },
        "/apk" to { arg ->
            "Design and build a complete, installable Android APK inside `/root/workspace` using `saif-build-apk` (or Gradle). Verify compilation and report the exact output `.apk` path.\n\nApp specification:\n${arg.ifBlank { "Ask what Android app to build and package as an APK." }}"
        },
        "/debug" to { arg ->
            "Perform systematic root-cause debugging: reproduce/trace the failure, inspect the relevant source files and logs, implement a minimal clean fix, and verify with tests.\n\nIssue:\n${arg.ifBlank { "Inspect recent errors or logs in /root/workspace and diagnose the failure." }}"
        },
        "/review" to { arg ->
            "Conduct a thorough code & security review checking correctness, concurrency, resource leaks, secret handling, and edge cases. Provide concrete file/line findings and fixes.\n\nScope:\n${arg.ifBlank { "Review recent changes (`git status` / `git diff`) in /root/workspace." }}"
        },
        "/research" to { arg ->
            "Conduct deep technical research: break down the question, verify facts/APIs, compare alternatives in a clear table, and recommend the best engineering approach.\n\nTopic:\n${arg.ifBlank { "Ask which technical topic to research." }}"
        },
        "/explain" to { arg ->
            "Explain clearly from first principles with concrete examples and execution flow.\n\nSubject:\n${arg.ifBlank { "Explain the current file or recent output step by step." }}"
        },
        "/test" to { arg ->
            "Write comprehensive, deterministic unit/integration tests covering happy paths, edge cases, and regressions, then run the test suite to verify green results.\n\nTarget:\n${arg.ifBlank { "Identify untested or recently modified code in /root/workspace and add tests." }}"
        },
        "/android-cli" to { arg ->
            "Use Android CLI / ADB / SDK command-line workflows cleanly and safely.\n\nRequest:\n${arg.ifBlank { "Show available Android CLI / APK build capabilities in this environment." }}"
        },
        "/chrome-extensions" to { arg ->
            "Design and build Manifest V3 Chrome Extensions following modern service-worker, content-script, and least-privilege permission best practices.\n\nRequest:\n${arg.ifBlank { "Ask what Chrome Extension to build." }}"
        },
        "/modern-web-guidance" to { arg ->
            "Apply modern responsive web architecture, clean semantic HTML/CSS/TypeScript, accessibility, and fast bundling inside `/root/workspace`.\n\nRequest:\n${arg.ifBlank { "Ask what modern web app or UI component to create." }}"
        },
        "/agy-customizations" to { arg ->
            "Configure Antigravity & SAIF AI skills, workflows, custom commands, and MCP integrations.\n\nRequest:\n${arg.ifBlank { "Explain how to customize skills, agents, and workflows in SAIF AI." }}"
        },
        "/antigravity-guide" to { arg ->
            "Provide accurate guidance on SAIF AI + Antigravity workspace tools, terminal sessions, providers, and multi-agent workflows.\n\nQuestion:\n${arg.ifBlank { "Give a quick tour of SAIF AI's workspace, agents, slash commands, and terminal features." }}"
        },
        "/generative_ui" to { arg ->
            "Design polished, interactive, responsive UI components with clean visual hierarchy, smooth motion, and zero dead controls.\n\nUI Brief:\n${arg.ifBlank { "Ask what interactive UI to design and build." }}"
        },
        "/migrate-workflows" to { arg ->
            "Migrate legacy scripts, CI pipelines, or prompt workflows into clean, modular, verified SAIF AI skills/workflows.\n\nTarget:\n${arg.ifBlank { "Ask which workflow or script to migrate." }}"
        },
        "/permissioned-github" to { arg ->
            "Execute Git & GitHub repository operations safely (branch status, clean commits, PR inspection, never exposing tokens).\n\nGitHub Task:\n${arg.ifBlank { "Check `git status` and connected GitHub repository state." }}"
        }
    )

    /** Ensures custom commands, saved skills, and specialist agents are initialized. */
    fun init(context: Context) {
        val app = context.applicationContext
        CustomSlashCommands.init(app)
        SaifAgentRegistry.ensureLoaded(app)
    }

    /**
     * Detects an active `/token` at the start of input OR immediately after whitespace/newline
     * relative to [cursorIndex] so autocomplete works anywhere in the composer.
     */
    fun extractActiveSlashToken(text: String, cursorIndex: Int = text.length): ActiveSlashToken? {
        if (text.isEmpty()) return null
        val safeCursor = cursorIndex.coerceIn(0, text.length)
        val beforeCursor = text.substring(0, safeCursor)

        var tokenStart = beforeCursor.length
        while (tokenStart > 0 && !beforeCursor[tokenStart - 1].isWhitespace()) {
            tokenStart--
        }
        if (tokenStart >= beforeCursor.length) return null
        val candidate = beforeCursor.substring(tokenStart)
        if (!candidate.startsWith("/")) return null
        val query = candidate.removePrefix("/")
        if (query.contains('/')) return null
        return ActiveSlashToken(
            startIndex = tokenStart,
            endIndex = safeCursor,
            rawToken = candidate,
            queryWithoutLeadingSlash = query
        )
    }

    /** Maps immediate app slash commands to [SaifAppSlashAction]. */
    fun resolveAppAction(rawInput: String): SaifAppSlashAction? {
        val trimmed = rawInput.trim()
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size > 1) return null
        val firstToken = tokens.firstOrNull()?.lowercase() ?: return null
        return when (firstToken) {
            "/video" -> SaifAppSlashAction.OPEN_VIDEO_STUDIO
            "/terminal", "/term" -> SaifAppSlashAction.OPEN_TERMINAL
            "/ide", "/code", "/editor" -> SaifAppSlashAction.OPEN_CODE_IDE
            "/browser", "/web" -> SaifAppSlashAction.OPEN_BROWSER
            "/notes", "/note" -> SaifAppSlashAction.OPEN_NOTES
            "/new" -> SaifAppSlashAction.NEW_CHAT
            "/clear" -> SaifAppSlashAction.CLEAR_CHAT
            "/agents", "/agent" -> SaifAppSlashAction.OPEN_AGENTS
            "/skills", "/skill" -> SaifAppSlashAction.OPEN_SKILLS
            "/providers", "/provider" -> SaifAppSlashAction.OPEN_PROVIDERS
            "/github", "/gh" -> SaifAppSlashAction.OPEN_GITHUB
            "/settings", "/help" -> SaifAppSlashAction.OPEN_SETTINGS
            else -> null
        }
    }

    fun isImmediateTapCommand(item: SlashCommandItem): Boolean {
        if (item.command == "/plu:" || (item.command.startsWith("/plu:") && item.command.endsWith(":"))) {
            return false // Drill down in composer first
        }
        return item.type == "app" && resolveAppAction(item.command) != null
    }

    /**
     * Builds the ranked autocomplete suggestion list for [rawQueryWithoutSlash].
     */
    fun buildSuggestions(
        context: Context,
        rawQueryWithoutSlash: String,
        hubCommands: List<SlashCommandItem> = SlashCommandsCache.getCachedSync() ?: emptyList()
    ): List<SlashCommandItem> {
        init(context)

        // Dedicated `/plu:` plugin namespace drill-down
        if (rawQueryWithoutSlash.startsWith("plu:", ignoreCase = true)) {
            val afterExt = rawQueryWithoutSlash.substring(4)
            return PromptExpansion.pluginSuggestions(afterExt)
        }

        val q = rawQueryWithoutSlash.trim().lowercase()

        // 1. Enabled Specialist Agents
        val agentItems = SaifAgentRegistry.state.value.enabledAgents.map { agent ->
            SlashCommandItem(
                name = "agent-${agent.slug}",
                command = agent.slashTrigger,
                description = "${agent.name} (${agent.category.displayName}) — ${agent.description}",
                type = "agent",
                argumentHint = "[prompt]"
            )
        }

        // 2. Saved GitHub Skills + Custom Slash Commands + /plu: entry from PromptExpansion
        val baseExpansionItems = PromptExpansion.baseSlashItems()

        // 3. Merge APP_COMMANDS + agentItems + baseExpansionItems + WORKFLOW_COMMANDS + SlashCommandsCache
        val seen = HashSet<String>()
        val all = ArrayList<SlashCommandItem>(64)

        fun addUnique(items: Iterable<SlashCommandItem>) {
            for (item in items) {
                val key = item.command.lowercase()
                if (seen.add(key)) {
                    all.add(item)
                }
            }
        }

        addUnique(APP_COMMANDS)
        addUnique(agentItems)
        addUnique(baseExpansionItems)
        addUnique(WORKFLOW_COMMANDS)
        addUnique(SlashCommandsCache.DEFAULT_SKILLS)
        addUnique(SlashCommandsCache.BUILTIN_COMMANDS)
        addUnique(hubCommands)

        if (q.isEmpty()) return all

        return all.filter { item ->
            val cmdNoSlash = item.command.removePrefix("/").lowercase()
            cmdNoSlash.contains(q) || item.description.lowercase().contains(q)
        }.sortedWith(
            compareBy<SlashCommandItem>(
                { !it.command.removePrefix("/").lowercase().startsWith(q) },
                { it.command.length }
            )
        )
    }

    /**
     * Expands built-in workflow & skill slash commands (e.g. `/plan Build login screen`, `/apk Calculator`, `/debug`)
     * into structured `<saif-expansion command="...">` blocks so the chat bubble stays clean
     * while the AI agent receives full actionable instructions.
     */
    fun expandBuiltInCommandIfMatched(rawText: String): String? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("/")) return null
        val wsIndex = trimmed.indexOfFirst { it.isWhitespace() }
        val cmdToken = (if (wsIndex == -1) trimmed else trimmed.substring(0, wsIndex)).lowercase()
        val argument = if (wsIndex == -1) "" else trimmed.substring(wsIndex).trim()

        val expander = BUILT_IN_EXPANSIONS[cmdToken] ?: return null
        val displayHeader = if (argument.isBlank()) cmdToken else "$cmdToken $argument"
        val body = expander(argument).trim()
        return "<saif-expansion command=\"${escapeAttr(displayHeader)}\">\n$body\n</saif-expansion>"
    }

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
