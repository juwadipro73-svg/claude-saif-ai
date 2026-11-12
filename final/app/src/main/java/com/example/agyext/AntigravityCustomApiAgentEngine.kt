/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agyext

import android.content.Context
import com.example.compiler.LinuxApkBuildBridge
import com.example.saif.data.agent.ChatSessionStore
import com.example.saif.data.local.LocalEnvironmentManager
import com.example.saif.data.remote.AndroidLocalBridgeServer
import com.example.saif.domain.model.ChatAttachment
import com.example.saif.domain.model.ChatMessage
import com.example.saif.domain.model.MessageRole
import com.example.saif.domain.model.TokenUsage
import com.example.saif.domain.model.ToolCall
import com.example.saif.domain.model.ToolType
import com.example.saif.ui.chat.PendingToolApproval
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AntigravityCustomApiAgentEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val store: ChatSessionStore,
    private val isAutoApproveProvider: () -> Boolean
) {
    data class ParsedModelTurn(
        val text: String,
        val thought: String?,
        val toolInvocations: List<AgentToolInvocation>,
        val promptTokens: Int = 0,
        val outputTokens: Int = 0
    )

    data class AgentToolInvocation(
        val callId: String,
        val name: String,
        val arguments: JSONObject
    )

    private sealed class ApprovalDecision {
        data object Approved : ApprovalDecision()
        data class Rejected(val reason: String?) : ApprovalDecision()
    }

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val pendingDecisions = ConcurrentHashMap<String, CompletableDeferred<ApprovalDecision>>()
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    @Volatile
    private var sessionAllowAll = false

    val pendingApprovals: StateFlow<List<PendingToolApproval>> = store.messages.map { msgs ->
        msgs.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { msg ->
                msg.toolCalls.filter { it.status == "PENDING_APPROVAL" && it.toolType != ToolType.ASK_CHOICE }
                    .map { PendingToolApproval(it, msg.id) }
            }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun loadSessionMessages(sessionId: String): Boolean {
        val file = sessionTranscriptFile(sessionId)
        if (!file.isFile) return false
        return runCatching {
            val loaded = jsonCodec.decodeFromString<List<ChatMessage>>(file.readText())
            store.messages.value = loaded
            true
        }.getOrDefault(false)
    }

    fun deleteSessionTranscript(sessionId: String) {
        runCatching { sessionTranscriptFile(sessionId).delete() }
    }

    fun clearAllSessionTranscripts() {
        runCatching { File(appContext.filesDir, "agy_custom_sessions").deleteRecursively() }
    }

    private fun persistSessionMessages(sessionId: String) {
        runCatching {
            val file = sessionTranscriptFile(sessionId)
            file.parentFile?.mkdirs()
            file.writeText(jsonCodec.encodeToString(store.messages.value))
        }
    }

    private fun sessionTranscriptFile(sessionId: String): File {
        val safe = sessionId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(File(appContext.filesDir, "agy_custom_sessions"), "$safe.json")
    }

    fun approveTool(tool: ToolCall, scopeMode: String?) {
        if (scopeMode?.contains("CONVERSATION", true) == true || scopeMode?.contains("GLOBAL", true) == true) {
            sessionAllowAll = true
        }
        pendingDecisions.remove(tool.id)?.complete(ApprovalDecision.Approved)
    }

    fun rejectTool(tool: ToolCall, reason: String?) {
        pendingDecisions.remove(tool.id)?.complete(ApprovalDecision.Rejected(reason))
    }

    fun approveAllPending(items: List<PendingToolApproval>) {
        sessionAllowAll = true
        items.forEach { pendingDecisions.remove(it.toolCall.id)?.complete(ApprovalDecision.Approved) }
    }

    fun rejectAllPending(items: List<PendingToolApproval>, reason: String? = null) {
        items.forEach { pendingDecisions.remove(it.toolCall.id)?.complete(ApprovalDecision.Rejected(reason)) }
    }

    fun terminateRunningTool(tool: ToolCall) {
        runningProcesses.remove(tool.id)?.destroyForcibly()
        pendingDecisions.remove(tool.id)?.complete(ApprovalDecision.Rejected("Terminated by user"))
    }

    fun stopActiveExecution() {
        runningProcesses.values.forEach { runCatching { it.destroyForcibly() } }
        runningProcesses.clear()
        pendingDecisions.values.forEach { it.complete(ApprovalDecision.Rejected("Stopped")) }
        pendingDecisions.clear()
    }

    /**
     * Runs a complete multi-step Antigravity agent turn on the user's configured Custom API,
     * streaming live tool calls, thoughts, and assistant text into [store] (`HomeAgentFeed`)
     * and returning the final assistant reply text.
     */
    suspend fun executeTurn(
        sessionId: String,
        profile: AgyCustomApiProfile,
        displayUserText: String,
        expandedAgentPrompt: String,
        inlineAttachments: List<ChatAttachment>,
        temperature: Float,
        onStreamingText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        sessionAllowAll = false
        ensureWorkspaceDirectories()

        val priorLoaded = if (store.messages.value.isNotEmpty() &&
            store.messages.value.firstOrNull()?.conversationId == sessionId
        ) {
            store.messages.value
        } else {
            loadSessionMessages(sessionId)
            store.messages.value.filter { it.conversationId == sessionId }
        }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = sessionId,
            role = MessageRole.USER,
            content = displayUserText,
            attachments = inlineAttachments
        )
        val assistantMsgId = UUID.randomUUID().toString()
        var assistantMsg = ChatMessage(
            id = assistantMsgId,
            conversationId = sessionId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelId = "${profile.name} · ${profile.selectedModel}"
        )

        store.messages.value = priorLoaded + userMsg + assistantMsg

        fun updateAssistant(transform: (ChatMessage) -> ChatMessage) {
            assistantMsg = transform(assistantMsg)
            store.messages.value = store.messages.value.map {
                if (it.id == assistantMsgId) assistantMsg else it
            }
        }

        val conversationHistory = buildInitialHistoryJson(priorLoaded, expandedAgentPrompt, inlineAttachments)
        val startMs = System.currentTimeMillis()
        var totalPromptTokens = 0
        var totalOutputTokens = 0
        val accumulatedNarrative = StringBuilder()
        val collectedThoughts = mutableListOf<String>()
        var stepCounter = 0
        var forceXmlToolFallback = false

        val maxAgentSteps = 14
        while (stepCounter < maxAgentSteps) {
            currentCoroutineContext().ensureActive()
            stepCounter++

            val turnStart = System.currentTimeMillis()
            val parsedTurn = callCustomProvider(
                profile = profile,
                conversationHistory = conversationHistory,
                temperature = temperature,
                usePromptToolFallback = forceXmlToolFallback,
                onFallbackTriggered = { forceXmlToolFallback = true }
            )
            val turnElapsed = System.currentTimeMillis() - turnStart
            totalPromptTokens += parsedTurn.promptTokens
            totalOutputTokens += parsedTurn.outputTokens

            if (!parsedTurn.thought.isNullOrBlank()) {
                collectedThoughts.add(parsedTurn.thought.trim())
            }

            if (parsedTurn.toolInvocations.isEmpty()) {
                val finalPiece = parsedTurn.text.trim()
                if (finalPiece.isNotBlank()) {
                    if (accumulatedNarrative.isNotEmpty() && !accumulatedNarrative.endsWith("\n\n")) {
                        accumulatedNarrative.append("\n\n")
                    }
                    accumulatedNarrative.append(finalPiece)
                }
                val visible = accumulatedNarrative.toString().trim().ifBlank { "Done." }
                onStreamingText(stripToolMarkers(visible))
                updateAssistant { cur ->
                    cur.copy(
                        content = visible,
                        thoughtText = collectedThoughts.joinToString("\n\n").takeIf { it.isNotBlank() },
                        thoughtDurationMs = turnElapsed.takeIf { collectedThoughts.isNotEmpty() },
                        isStreaming = false,
                        tokenUsage = TokenUsage(
                            promptTokens = totalPromptTokens,
                            outputTokens = totalOutputTokens,
                            totalTokens = totalPromptTokens + totalOutputTokens,
                            durationMs = System.currentTimeMillis() - startMs,
                            model = profile.selectedModel
                        )
                    )
                }
                persistSessionMessages(sessionId)
                return@withContext stripToolMarkers(visible).ifBlank { visible }
            }

            // Append any pre-tool narration + record assistant tool_calls in conversationHistory
            if (parsedTurn.text.isNotBlank()) {
                if (accumulatedNarrative.isNotEmpty() && !accumulatedNarrative.endsWith("\n\n")) {
                    accumulatedNarrative.append("\n\n")
                }
                accumulatedNarrative.append(parsedTurn.text.trim()).append("\n\n")
                onStreamingText(stripToolMarkers(accumulatedNarrative.toString()))
            }

            val assistantHistoryEntry = JSONObject().apply {
                put("role", "assistant")
                put("content", parsedTurn.text)
                val tcArr = JSONArray()
                parsedTurn.toolInvocations.forEach { inv ->
                    tcArr.put(
                        JSONObject()
                            .put("id", inv.callId)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", inv.name)
                                    .put("arguments", inv.arguments.toString())
                            )
                    )
                }
                put("tool_calls", tcArr)
            }
            conversationHistory.put(assistantHistoryEntry)

            // Execute each tool call sequentially with live UI cards in HomeAgentFeed
            for ((idx, inv) in parsedTurn.toolInvocations.withIndex()) {
                currentCoroutineContext().ensureActive()
                val toolId = "tool_${inv.name}_${stepCounter}_$idx"
                val uiToolType = mapToolType(inv.name)
                val displayCmd = formatToolDisplayLabel(inv.name, inv.arguments)
                val needsApproval = requiresUserApproval(inv.name) && !isAutoApproveProvider() && !sessionAllowAll

                var liveTool = ToolCall(
                    id = toolId,
                    name = inv.name,
                    toolType = uiToolType,
                    command = displayCmd,
                    status = if (needsApproval) "PENDING_APPROVAL" else "RUNNING",
                    stepIndex = stepCounter * 10 + idx,
                    interactionType = if (needsApproval) "permission" else null
                )

                accumulatedNarrative.append("\n<!-- tool_call:$toolId -->\n")
                updateAssistant { cur ->
                    cur.copy(
                        content = accumulatedNarrative.toString(),
                        thoughtText = collectedThoughts.joinToString("\n\n").takeIf { it.isNotBlank() },
                        toolCalls = cur.toolCalls + liveTool,
                        isStreaming = true
                    )
                }

                if (needsApproval) {
                    val deferred = CompletableDeferred<ApprovalDecision>()
                    pendingDecisions[toolId] = deferred
                    val decision = try {
                        deferred.await()
                    } finally {
                        pendingDecisions.remove(toolId)
                    }
                    if (decision is ApprovalDecision.Rejected) {
                        val rejectMsg = decision.reason?.takeIf { it.isNotBlank() } ?: "User rejected tool execution."
                        liveTool = liveTool.copy(status = "FAILED", output = rejectMsg, exitCode = 1)
                        updateToolInAssistant(::updateAssistant, liveTool)
                        conversationHistory.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", inv.callId)
                                .put("name", inv.name)
                                .put("content", "REJECTED_BY_USER: $rejectMsg")
                        )
                        continue
                    }
                    liveTool = liveTool.copy(status = "RUNNING", interactionType = null)
                    updateToolInAssistant(::updateAssistant, liveTool)
                }

                val execStart = System.currentTimeMillis()
                val (ok, toolResultText, exitCode) = executeSingleTool(
                    toolId = toolId,
                    name = inv.name,
                    args = inv.arguments,
                    onLiveOutput = { partialOut ->
                        liveTool = liveTool.copy(output = partialOut.takeLast(8_000))
                        updateToolInAssistant(::updateAssistant, liveTool)
                    }
                )
                val execDuration = System.currentTimeMillis() - execStart
                liveTool = liveTool.copy(
                    status = if (ok) "SUCCESS" else "FAILED",
                    output = toolResultText.take(16_000),
                    exitCode = exitCode,
                    durationMs = execDuration
                )
                updateToolInAssistant(::updateAssistant, liveTool)

                conversationHistory.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", inv.callId)
                        .put("name", inv.name)
                        .put("content", toolResultText.take(24_000))
                )
            }
        }

        val fallbackText = stripToolMarkers(accumulatedNarrative.toString()).ifBlank {
            "Completed maximum agent tool steps ($maxAgentSteps). Let me know if you'd like me to continue."
        }
        updateAssistant { cur ->
            cur.copy(
                content = accumulatedNarrative.toString().ifBlank { fallbackText },
                isStreaming = false
            )
        }
        persistSessionMessages(sessionId)
        fallbackText
    }

    private fun updateToolInAssistant(
        updateAssistant: ((ChatMessage) -> ChatMessage) -> Unit,
        updatedTool: ToolCall
    ) {
        updateAssistant { cur ->
            cur.copy(
                toolCalls = cur.toolCalls.map { if (it.id == updatedTool.id) updatedTool else it }
            )
        }
    }

    private fun ensureWorkspaceDirectories() {
        File(appContext.filesDir, "home").mkdirs()
        File(appContext.filesDir, "ubuntu/root/uploads").mkdirs()
    }

    private fun requiresUserApproval(toolName: String): Boolean {
        return when (toolName.lowercase()) {
            "run_command", "write_file", "edit_file", "build_android_apk" -> true
            else -> false
        }
    }

    private fun mapToolType(toolName: String): ToolType {
        return when (toolName.lowercase()) {
            "run_command" -> ToolType.BASH
            "view_file" -> ToolType.VIEW_FILE
            "write_file", "edit_file" -> ToolType.EDIT_FILE
            "list_dir" -> ToolType.LIST_DIR
            "grep_search" -> ToolType.GREP_SEARCH
            "read_url" -> ToolType.READ_URL
            "build_android_apk" -> ToolType.BASH
            else -> ToolType.BASH
        }
    }

    private fun formatToolDisplayLabel(toolName: String, args: JSONObject): String {
        return when (toolName.lowercase()) {
            "run_command" -> args.optString("command").ifBlank { "bash" }
            "view_file" -> "view ${args.optString("path")}"
            "write_file" -> "write ${args.optString("path")}"
            "edit_file" -> "edit ${args.optString("path")}"
            "list_dir" -> "ls ${args.optString("path", "/root/workspace")}"
            "grep_search" -> "grep \"${args.optString("pattern")}\" ${args.optString("path", "/root/workspace")}"
            "read_url" -> "fetch ${args.optString("url")}"
            "build_android_apk" -> "saif-build-apk ${args.optString("project_dir")} --name \"${args.optString("app_name")}\""
            else -> "$toolName ${args.toString().take(120)}"
        }
    }

    private suspend fun executeSingleTool(
        toolId: String,
        name: String,
        args: JSONObject,
        onLiveOutput: (String) -> Unit
    ): Triple<Boolean, String, Int?> {
        return try {
            when (name.lowercase()) {
                "run_command" -> runWorkspaceCommand(toolId, args, onLiveOutput)
                "view_file" -> viewWorkspaceFile(args)
                "write_file" -> writeWorkspaceFile(args)
                "edit_file" -> editWorkspaceFile(args)
                "list_dir" -> listWorkspaceDir(args)
                "grep_search" -> grepWorkspace(args)
                "read_url" -> fetchWebUrl(args)
                "build_android_apk" -> buildAndroidApkTool(args)
                else -> Triple(false, "Unknown tool: $name", 1)
            }
        } catch (err: Exception) {
            Triple(false, "Tool error: ${e.localizedMessage ?: e.javaClass.simpleName}", 1)
        }
    }

    private suspend fun runWorkspaceCommand(
        toolId: String,
        args: JSONObject,
        onLiveOutput: (String) -> Unit
    ): Triple<Boolean, String, Int?> {
        val rawCommand = args.optString("command").trim()
        if (rawCommand.isBlank()) return Triple(false, "Missing 'command' argument.", 1)
        val timeoutSec = args.optLong("timeout_seconds", 180L).coerceIn(5L, 600L)

        if (LocalEnvironmentManager.isUbuntuInstalled(appContext)) {
            val sb = StringBuilder()
            val res = LocalEnvironmentManager.executeCommand(
                command = rawCommand,
                context = appContext,
                timeoutSeconds = timeoutSec,
                onOutputLine = { line ->
                    sb.appendLine(line)
                    onLiveOutput(sb.toString())
                }
            )
            val out = res.output.ifBlank { "(exit code ${res.exitCode})" }
            return Triple(res.exitCode == 0, out, res.exitCode)
        }

        // Fallback host shell inside filesDir/home when Ubuntu rootfs has not been downloaded yet
        val hostWorkspace = File(appContext.filesDir, "home").apply { mkdirs() }
        val hostUploads = File(appContext.filesDir, "ubuntu/root/uploads").apply { mkdirs() }
        val rewritten = rawCommand
            .replace("/root/workspace", hostWorkspace.absolutePath)
            .replace("/root/uploads", hostUploads.absolutePath)
        val pb = ProcessBuilder("/system/bin/sh", "-c", rewritten)
            .directory(hostWorkspace)
            .redirectErrorStream(true)
        pb.environment()["HOME"] = hostWorkspace.absolutePath
        val proc = pb.start()
        runningProcesses[toolId] = proc
        return try {
            val out = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    out.appendLine(line)
                    onLiveOutput(out.toString())
                }
            }
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                Triple(false, "${out}\n[Timed out after ${timeoutSec}s]".trim(), 124)
            } else {
                val code = proc.exitValue()
                Triple(code == 0, out.toString().trim().ifBlank { "(exit code $code)" }, code)
            }
        } finally {
            runningProcesses.remove(toolId)
        }
    }

    private fun resolveTargetFile(rawPath: String): File {
        val cleaned = rawPath.trim().ifBlank { "/root/workspace" }
        LinuxApkBuildBridge.resolveLinuxPathOnHost(appContext, cleaned)?.let { return it }
        val workspace = File(appContext.filesDir, "home").apply { mkdirs() }
        val safeRel = cleaned.trimStart('/').split('/').filter { it.isNotBlank() && it != ".." }.joinToString("/")
        return if (safeRel.isEmpty()) workspace else File(workspace, safeRel)
    }

    private fun viewWorkspaceFile(args: JSONObject): Triple<Boolean, String, Int?> {
        val path = args.optString("path").trim()
        if (path.isBlank()) return Triple(false, "Missing 'path' parameter.", 1)
        val file = resolveTargetFile(path)
        if (!file.exists()) return Triple(false, "File not found: $path", 1)
        if (file.isDirectory) return listWorkspaceDir(args)
        val lines = file.readLines()
        val start = (args.optInt("start_line", 1) - 1).coerceAtLeast(0)
        val end = args.optInt("end_line", minOf(lines.size, start + 300)).coerceAtMost(lines.size)
        if (start >= lines.size) return Triple(true, "(File has ${lines.size} lines)", 0)
        val numbered = lines.subList(start, end).mapIndexed { idx, line ->
            "${start + idx + 1}: $line"
        }.joinToString("\n")
        return Triple(true, numbered.ifBlank { "(Empty file)" }, 0)
    }

    private fun writeWorkspaceFile(args: JSONObject): Triple<Boolean, String, Int?> {
        val path = args.optString("path").trim()
        if (path.isBlank()) return Triple(false, "Missing 'path' parameter.", 1)
        val content = args.optString("content", "")
        val file = resolveTargetFile(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return Triple(true, "Wrote ${content.length} chars (${content.lines().size} lines) to $path", 0)
    }

    private fun editWorkspaceFile(args: JSONObject): Triple<Boolean, String, Int?> {
        val path = args.optString("path").trim()
        val oldText = args.optString("old_text", "")
        val newText = args.optString("new_text", "")
        if (path.isBlank() || oldText.isEmpty()) {
            return Triple(false, "Provide 'path' and non-empty 'old_text'.", 1)
        }
        val file = resolveTargetFile(path)
        if (!file.isFile) return Triple(false, "File not found: $path", 1)
        val current = file.readText()
        if (!current.contains(oldText)) {
            return Triple(false, "'old_text' not found in $path. Call view_file first to verify exact contents.", 1)
        }
        val updated = current.replaceFirst(oldText, newText)
        file.writeText(updated)
        return Triple(true, "Updated $path successfully.", 0)
    }

    private fun listWorkspaceDir(args: JSONObject): Triple<Boolean, String, Int?> {
        val raw = args.optString("path", "/root/workspace")
        val dir = resolveTargetFile(raw)
        if (!dir.exists()) return Triple(false, "Directory does not exist: $raw", 1)
        if (!dir.isDirectory) return Triple(true, "${dir.name} (${dir.length()} bytes)", 0)
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.take(200).orEmpty()
        if (entries.isEmpty()) return Triple(true, "(Empty directory: $raw)", 0)
        val formatted = entries.joinToString("\n") { f ->
            if (f.isDirectory) "[DIR]  ${f.name}/" else "[FILE] ${f.name} (${f.length()} bytes)"
        }
        return Triple(true, formatted, 0)
    }

    private fun grepWorkspace(args: JSONObject): Triple<Boolean, String, Int?> {
        val patternStr = args.optString("pattern").trim()
        if (patternStr.isBlank()) return Triple(false, "Missing 'pattern'.", 1)
        val root = resolveTargetFile(args.optString("path", "/root/workspace"))
        if (!root.exists()) return Triple(false, "Search path not found.", 1)
        val regex = runCatching { Regex(patternStr, RegexOption.IGNORE_CASE) }
            .getOrElse { Regex(Regex.escape(patternStr), RegexOption.IGNORE_CASE) }
        val hits = mutableListOf<String>()
        root.walkTopDown()
            .onEnter { it.name != ".git" && it.name != "node_modules" && it.name != "build" }
            .filter { it.isFile && it.length() <= 1_500_000L }
            .forEach { file ->
                if (hits.size >= 80) return@forEach
                runCatching {
                    file.useLines { seq ->
                        seq.forEachIndexed { idx, line ->
                            if (hits.size < 80 && regex.containsMatchIn(line)) {
                                hits.add("${file.name}:${idx + 1}: ${line.take(180)}")
                            }
                        }
                    }
                }
            }
        return Triple(true, if (hits.isEmpty()) "No matches for '$patternStr'." else hits.joinToString("\n"), 0)
    }

    private fun fetchWebUrl(args: JSONObject): Triple<Boolean, String, Int?> {
        val url = args.optString("url").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Triple(false, "URL must start with http:// or https://", 1)
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SAIF-AI-Antigravity/1.0")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return Triple(false, "HTTP ${resp.code}: ${raw.take(400)}", 1)
            val cleaned = raw
                .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return Triple(true, cleaned.take(14_000), 0)
        }
    }

    private suspend fun buildAndroidApkTool(args: JSONObject): Triple<Boolean, String, Int?> {
        val projectDir = args.optString("project_dir").trim().ifBlank { "/root/workspace/app" }
        val appName = args.optString("app_name").trim().ifBlank { "SAIF App" }
        val packageName = args.optString("package_name").trim()
        AndroidLocalBridgeServer.instance.start(appContext)
        val res = LinuxApkBuildBridge.compileLinuxDirectoryToApk(
            appContext,
            JSONObject()
                .put("projectDir", projectDir)
                .put("appName", appName)
                .put("packageName", packageName)
        )
        if (!res.optBoolean("success", false)) {
            return Triple(false, "APK build failed: ${res.optString("error", "Unknown build error")}", 1)
        }
        val hostApkPath = res.optString("hostApkPath")
        var downloadUrl = res.optString("downloadUrl")
        if (downloadUrl.isBlank() && hostApkPath.isNotBlank()) {
            val f = File(hostApkPath)
            if (f.isFile) {
                downloadUrl = AndroidLocalBridgeServer.instance.registerDownloadFile(appContext, f, f.name)
            }
        }
        val summary = buildString {
            appendLine("APK_BUILT_SUCCESSFULLY=true")
            appendLine("APP_NAME=$appName")
            appendLine("PACKAGE_NAME=${res.optString("packageName")}")
            appendLine("LINUX_APK_PATH=${res.optString("linuxApkPath")}")
            appendLine("DOWNLOAD_URL=$downloadUrl")
            appendLine("Include [Download $appName APK]($downloadUrl) in your final answer so the user can download or install it with one tap.")
        }
        return Triple(true, summary.trim(), 0)
    }

    private fun buildInitialHistoryJson(
        priorMessages: List<ChatMessage>,
        expandedPrompt: String,
        inlineAttachments: List<ChatAttachment>
    ): JSONArray {
        val arr = JSONArray()
        for (m in priorMessages.takeLast(14)) {
            val cleanText = stripToolMarkers(m.content).trim()
            if (cleanText.isBlank()) continue
            when (m.role) {
                MessageRole.USER -> arr.put(JSONObject().put("role", "user").put("content", cleanText.take(6_000)))
                MessageRole.ASSISTANT -> arr.put(JSONObject().put("role", "assistant").put("content", cleanText.take(6_000)))
                else -> {}
            }
        }
        val userEntry = JSONObject().put("role", "user").put("content", expandedPrompt)
        val images = inlineAttachments.filter { it.isImage && !it.base64.isNullOrBlank() }
        if (images.isNotEmpty()) {
            val imgArr = JSONArray()
            images.forEach { img ->
                imgArr.put(
                    JSONObject()
                        .put("mimeType", img.mimeType ?: "image/jpeg")
                        .put("base64", img.base64)
                )
            }
            userEntry.put("_images", imgArr)
        }
        arr.put(userEntry)
        return arr
    }

    private fun buildSystemPrompt(useXmlToolMode: Boolean): String {
        val base = """
You are SAIF AI running on the Antigravity Workspace Engine.
Workspace paths:
- `/root/workspace` is your persistent project & code workspace.
- `/root/uploads` contains any files, videos, APKs, ZIPs, PDFs, or media attached by the user.
- For greetings, casual chat, and general-knowledge questions, answer directly and concisely in plain text without calling tools.
- For coding, inspecting attached files in `/root/uploads`, Linux commands, git/GitHub tasks, web lookups, or building Android APKs, use your workspace tools (`view_file`, `write_file`, `edit_file`, `list_dir`, `grep_search`, `run_command`, `read_url`, `build_android_apk`).
- When asked to build an Android app or APK: create clean project files inside `/root/workspace/<app-slug>` (either a mobile HTML5/CSS/JS app with `index.html` or a Java Android layout under `app/src/main/{AndroidManifest.xml,java,res}`), then call `build_android_apk` (or `saif-build-apk` via `run_command`) and include the returned `DOWNLOAD_URL` as a clickable Markdown link `[Download App APK](DOWNLOAD_URL)`.
""".trimIndent()
        if (!useXmlToolMode) return base
        return base + "\n\n" + """
TOOL CALL FORMAT (when you need a tool, output one or more `<agy_tool>` blocks and wait for the result):
<agy_tool>{"name":"write_file","arguments":{"path":"/root/workspace/demo/index.html","content":"<!doctype html>..."}}</agy_tool>
Available tools:
- `run_command`: `{"command": "...", "timeout_seconds": 120}`
- `view_file`: `{"path": "/root/...", "start_line": 1, "end_line": 200}`
- `write_file`: `{"path": "/root/...", "content": "..."}`
- `edit_file`: `{"path": "/root/...", "old_text": "...", "new_text": "..."}`
- `list_dir`: `{"path": "/root/workspace"}`
- `grep_search`: `{"pattern": "...", "path": "/root/workspace"}`
- `read_url`: `{"url": "https://..."}`
- `build_android_apk`: `{"project_dir": "/root/workspace/app", "app_name": "My App", "package_name": "com.saifai.myapp"}`
""".trimIndent()
    }

    private fun openAiToolDefinitions(): JSONArray {
        fun prop(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)
        fun fn(name: String, desc: String, props: JSONObject, req: List<String>): JSONObject {
            val reqArr = JSONArray().apply { req.forEach { put(it) } }
            return JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", name)
                        .put("description", desc)
                        .put(
                            "parameters",
                            JSONObject()
                                .put("type", "object")
                                .put("properties", props)
                                .put("required", reqArr)
                        )
                )
        }
        return JSONArray()
            .put(
                fn(
                    "run_command",
                    "Execute a bash command inside the Antigravity Linux workspace.",
                    JSONObject()
                        .put("command", prop("string", "Shell command to run"))
                        .put("timeout_seconds", prop("integer", "Optional timeout in seconds")),
                    listOf("command")
                )
            )
            .put(
                fn(
                    "view_file",
                    "Read text lines from a file in /root/workspace or /root/uploads.",
                    JSONObject()
                        .put("path", prop("string", "Absolute path such as /root/workspace/index.html"))
                        .put("start_line", prop("integer", "1-based start line"))
                        .put("end_line", prop("integer", "1-based end line")),
                    listOf("path")
                )
            )
            .put(
                fn(
                    "write_file",
                    "Create or overwrite a file in /root/workspace.",
                    JSONObject()
                        .put("path", prop("string", "Target path in /root/workspace/..."))
                        .put("content", prop("string", "Full file content")),
                    listOf("path", "content")
                )
            )
            .put(
                fn(
                    "edit_file",
                    "Replace old_text with new_text inside an existing file.",
                    JSONObject()
                        .put("path", prop("string", "File path"))
                        .put("old_text", prop("string", "Exact text to find"))
                        .put("new_text", prop("string", "Replacement text")),
                    listOf("path", "old_text", "new_text")
                )
            )
            .put(
                fn(
                    "list_dir",
                    "List files and directories at the given path.",
                    JSONObject().put("path", prop("string", "Directory path (default /root/workspace)")),
                    emptyList()
                )
            )
            .put(
                fn(
                    "grep_search",
                    "Search files for a regex or text pattern.",
                    JSONObject()
                        .put("pattern", prop("string", "Regex or text pattern"))
                        .put("path", prop("string", "Directory to search")),
                    listOf("pattern")
                )
            )
            .put(
                fn(
                    "read_url",
                    "Fetch text content from an HTTP/HTTPS web URL.",
                    JSONObject().put("url", prop("string", "Full https:// URL")),
                    listOf("url")
                )
            )
            .put(
                fn(
                    "build_android_apk",
                    "Compile and sign an installable Android APK from a project folder in /root/workspace/<slug> and mint a browser download link.",
                    JSONObject()
                        .put("project_dir", prop("string", "Project directory, e.g. /root/workspace/calculator"))
                        .put("app_name", prop("string", "Display name of the Android app"))
                        .put("package_name", prop("string", "Android package name, e.g. com.saifai.calculator")),
                    listOf("project_dir", "app_name")
                )
            )
    }

    private fun callCustomProvider(
        profile: AgyCustomApiProfile,
        conversationHistory: JSONArray,
        temperature: Float,
        usePromptToolFallback: Boolean,
        onFallbackTriggered: () -> Unit
    ): ParsedModelTurn {
        val protocol = AntigravityCustomApiManager.resolveProtocol(profile)
        return when (protocol) {
            AgyApiProtocol.ANTHROPIC -> callAnthropic(profile, conversationHistory, temperature, usePromptToolFallback, onFallbackTriggered)
            AgyApiProtocol.GEMINI_NATIVE -> callGeminiNative(profile, conversationHistory, temperature, usePromptToolFallback, onFallbackTriggered)
            else -> callOpenAiCompatible(profile, conversationHistory, temperature, usePromptToolFallback, onFallbackTriggered)
        }
    }

    private fun callOpenAiCompatible(
        profile: AgyCustomApiProfile,
        history: JSONArray,
        temperature: Float,
        useXmlFallback: Boolean,
        onFallbackTriggered: () -> Unit
    ): ParsedModelTurn {
        val url = AntigravityCustomApiManager.buildOpenAiChatCompletionsUrl(profile.baseUrl)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val messagesArr = JSONArray()
        messagesArr.put(JSONObject().put("role", "system").put("content", buildSystemPrompt(useXmlFallback)))

        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val role = item.optString("role")
            if (useXmlFallback) {
                when (role) {
                    "assistant" -> {
                        val tcs = item.optJSONArray("tool_calls")
                        val rebuilt = buildString {
                            append(item.optString("content"))
                            if (tcs != null) {
                                for (j in 0 until tcs.length()) {
                                    val fn = tcs.optJSONObject(j)?.optJSONObject("function") ?: continue
                                    append("\n<agy_tool>")
                                    append(JSONObject().put("name", fn.optString("name")).put("arguments", JSONObject(fn.optString("arguments", "{}"))).toString())
                                    append("</agy_tool>")
                                }
                            }
                        }
                        messagesArr.put(JSONObject().put("role", "assistant").put("content", rebuilt))
                    }
                    "tool" -> {
                        messagesArr.put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", "Tool result (${item.optString("name")}):\n${item.optString("content")}")
                        )
                    }
                    else -> messagesArr.put(toOpenAiUserOrAssistantMessage(item))
                }
            } else {
                messagesArr.put(toOpenAiUserOrAssistantMessage(item))
            }
        }

        val payload = JSONObject()
            .put("model", profile.selectedModel)
            .put("messages", messagesArr)
            .put("temperature", temperature.toDouble())
        if (!useXmlFallback) {
            payload.put("tools", openAiToolDefinitions())
        }

        val reqBuilder = Request.Builder().url(url)
        if (profile.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${profile.apiKey}")
        }
        if (profile.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
            reqBuilder.header("HTTP-Referer", "https://saif.ai")
            reqBuilder.header("X-Title", "SAIF AI Antigravity")
        }
        profile.extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        val req = reqBuilder.post(payload.toString().toRequestBody(mediaType)).build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val lower = body.lowercase()
                if (!useXmlFallback && (resp.code == 400 || resp.code == 404 || resp.code == 422) &&
                    (lower.contains("tool") || lower.contains("function") || lower.contains("unsupported"))
                ) {
                    onFallbackTriggered()
                    return callOpenAiCompatible(profile, history, temperature, true, onFallbackTriggered)
                }
                error("${profile.name} API error (HTTP ${resp.code}): ${AntigravityCustomApiManager.extractApiError(body)}")
            }
            return parseOpenAiResponse(body)
        }
    }

    private fun toOpenAiUserOrAssistantMessage(item: JSONObject): JSONObject {
        val imgs = item.optJSONArray("_images")
        if (imgs == null || imgs.length() == 0) {
            val copy = JSONObject(item.toString())
            copy.remove("_images")
            return copy
        }
        val contentArr = JSONArray()
        contentArr.put(JSONObject().put("type", "text").put("text", item.optString("content")))
        for (i in 0 until imgs.length()) {
            val img = imgs.optJSONObject(i) ?: continue
            val dataUri = "data:${img.optString("mimeType", "image/jpeg")};base64,${img.optString("base64")}"
            contentArr.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUri))
            )
        }
        return JSONObject().put("role", "user").put("content", contentArr)
    }

    internal fun parseOpenAiResponse(rawBody: String): ParsedModelTurn {
        val root = JSONObject(rawBody)
        val usage = root.optJSONObject("usage")
        val promptTok = usage?.optInt("prompt_tokens", 0) ?: 0
        val completionTok = usage?.optInt("completion_tokens", 0) ?: 0
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: error("API returned no choices: ${rawBody.take(220)}")
        val message = choice.optJSONObject("message") ?: JSONObject()

        val rawContent = when (val c = message.opt("content")) {
            is String -> c
            is JSONArray -> buildString {
                for (i in 0 until c.length()) {
                    val part = c.optJSONObject(i)
                    if (part != null && part.optString("type") == "text") {
                        append(part.optString("text"))
                    }
                }
            }
            else -> ""
        }
        val explicitReasoning = message.optString("reasoning_content").ifBlank {
            message.optString("reasoning")
        }
        val (cleanText, inlineThink, xmlTools) = extractThinkAndXmlTools(rawContent)
        val combinedThought = listOf(explicitReasoning, inlineThink)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }

        val invocations = mutableListOf<AgentToolInvocation>()
        val tcArray = message.optJSONArray("tool_calls")
        if (tcArray != null) {
            for (i in 0 until tcArray.length()) {
                val tc = tcArray.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name").trim()
                if (name.isBlank()) continue
                val argsObj = parseJsonArgsSafely(fn.optString("arguments", "{}"))
                invocations.add(
                    AgentToolInvocation(
                        callId = tc.optString("id").ifBlank { "call_${UUID.randomUUID()}" },
                        name = name,
                        arguments = argsObj
                    )
                )
            }
        }
        if (invocations.isEmpty() && xmlTools.isNotEmpty()) {
            invocations.addAll(xmlTools)
        }
        return ParsedModelTurn(
            text = cleanText,
            thought = combinedThought,
            toolInvocations = invocations,
            promptTokens = promptTok,
            outputTokens = completionTok
        )
    }

    private fun callAnthropic(
        profile: AgyCustomApiProfile,
        history: JSONArray,
        temperature: Float,
        useXmlFallback: Boolean,
        onFallbackTriggered: () -> Unit
    ): ParsedModelTurn {
        val url = AntigravityCustomApiManager.buildAnthropicMessagesUrl(profile.baseUrl)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val anthroMessages = JSONArray()

        var i = 0
        while (i < history.length()) {
            val item = history.optJSONObject(i) ?: run { i++; continue }
            when (item.optString("role")) {
                "user" -> {
                    val imgs = item.optJSONArray("_images")
                    if (imgs != null && imgs.length() > 0) {
                        val blocks = JSONArray()
                        for (j in 0 until imgs.length()) {
                            val img = imgs.optJSONObject(j) ?: continue
                            blocks.put(
                                JSONObject()
                                    .put("type", "image")
                                    .put(
                                        "source",
                                        JSONObject()
                                            .put("type", "base64")
                                            .put("media_type", img.optString("mimeType", "image/jpeg"))
                                            .put("data", img.optString("base64"))
                                    )
                            )
                        }
                        blocks.put(JSONObject().put("type", "text").put("text", item.optString("content")))
                        anthroMessages.put(JSONObject().put("role", "user").put("content", blocks))
                    } else {
                        anthroMessages.put(JSONObject().put("role", "user").put("content", item.optString("content")))
                    }
                    i++
                }
                "assistant" -> {
                    val blocks = JSONArray()
                    val text = item.optString("content")
                    if (text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", text))
                    val tcs = item.optJSONArray("tool_calls")
                    if (!useXmlFallback && tcs != null) {
                        for (j in 0 until tcs.length()) {
                            val tc = tcs.optJSONObject(j) ?: continue
                            val fn = tc.optJSONObject("function") ?: continue
                            blocks.put(
                                JSONObject()
                                    .put("type", "tool_use")
                                    .put("id", tc.optString("id"))
                                    .put("name", fn.optString("name"))
                                    .put("input", parseJsonArgsSafely(fn.optString("arguments", "{}")))
                            )
                        }
                    }
                    anthroMessages.put(JSONObject().put("role", "assistant").put("content", blocks))
                    i++
                }
                "tool" -> {
                    val resultBlocks = JSONArray()
                    while (i < history.length() && history.optJSONObject(i)?.optString("role") == "tool") {
                        val tItem = history.getJSONObject(i)
                        if (useXmlFallback) {
                            resultBlocks.put(
                                JSONObject().put("type", "text")
                                    .put("text", "Tool result (${tItem.optString("name")}):\n${tItem.optString("content")}")
                            )
                        } else {
                            resultBlocks.put(
                                JSONObject()
                                    .put("type", "tool_result")
                                    .put("tool_use_id", tItem.optString("tool_call_id"))
                                    .put("content", tItem.optString("content"))
                            )
                        }
                        i++
                    }
                    anthroMessages.put(JSONObject().put("role", "user").put("content", resultBlocks))
                }
                else -> i++
            }
        }

        val payload = JSONObject()
            .put("model", profile.selectedModel)
            .put("max_tokens", 8192)
            .put("system", buildSystemPrompt(useXmlFallback))
            .put("temperature", temperature.toDouble().coerceIn(0.0, 1.0))
            .put("messages", anthroMessages)

        if (!useXmlFallback) {
            val anthroTools = JSONArray()
            val oaTools = openAiToolDefinitions()
            for (idx in 0 until oaTools.length()) {
                val fn = oaTools.getJSONObject(idx).getJSONObject("function")
                anthroTools.put(
                    JSONObject()
                        .put("name", fn.getString("name"))
                        .put("description", fn.getString("description"))
                        .put("input_schema", fn.getJSONObject("parameters"))
                )
            }
            payload.put("tools", anthroTools)
        }

        val req = Request.Builder()
            .url(url)
            .header("x-api-key", profile.apiKey)
            .header("anthropic-version", "2023-06-01")
            .apply { profile.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (!useXmlFallback && resp.code == 400 && body.contains("tool", ignoreCase = true)) {
                    onFallbackTriggered()
                    return callAnthropic(profile, history, temperature, true, onFallbackTriggered)
                }
                error("${profile.name} API error (HTTP ${resp.code}): ${AntigravityCustomApiManager.extractApiError(body)}")
            }
            val root = JSONObject(body)
            val usage = root.optJSONObject("usage")
            val contentArr = root.optJSONArray("content") ?: JSONArray()
            val textParts = mutableListOf<String>()
            val thoughtParts = mutableListOf<String>()
            val invocations = mutableListOf<AgentToolInvocation>()

            for (idx in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(idx) ?: continue
                when (block.optString("type")) {
                    "text" -> textParts.add(block.optString("text"))
                    "thinking" -> thoughtParts.add(block.optString("thinking"))
                    "tool_use" -> invocations.add(
                        AgentToolInvocation(
                            callId = block.optString("id").ifBlank { "toolu_${UUID.randomUUID()}" },
                            name = block.optString("name"),
                            arguments = block.optJSONObject("input") ?: JSONObject()
                        )
                    )
                }
            }
            val (cleanText, inlineThink, xmlTools) = extractThinkAndXmlTools(textParts.joinToString("\n"))
            if (inlineThink.isNotBlank()) thoughtParts.add(inlineThink)
            if (invocations.isEmpty() && xmlTools.isNotEmpty()) invocations.addAll(xmlTools)

            return ParsedModelTurn(
                text = cleanText,
                thought = thoughtParts.joinToString("\n\n").takeIf { it.isNotBlank() },
                toolInvocations = invocations,
                promptTokens = usage?.optInt("input_tokens", 0) ?: 0,
                outputTokens = usage?.optInt("output_tokens", 0) ?: 0
            )
        }
    }

    private fun callGeminiNative(
        profile: AgyCustomApiProfile,
        history: JSONArray,
        temperature: Float,
        useXmlFallback: Boolean,
        onFallbackTriggered: () -> Unit
    ): ParsedModelTurn {
        val url = AntigravityCustomApiManager.buildGeminiGenerateContentUrl(
            profile.baseUrl,
            profile.selectedModel,
            profile.apiKey
        )
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val contents = JSONArray()

        var i = 0
        while (i < history.length()) {
            val item = history.optJSONObject(i) ?: run { i++; continue }
            when (item.optString("role")) {
                "user" -> {
                    val parts = JSONArray()
                    val imgs = item.optJSONArray("_images")
                    if (imgs != null) {
                        for (j in 0 until imgs.length()) {
                            val img = imgs.optJSONObject(j) ?: continue
                            parts.put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", img.optString("mimeType", "image/jpeg"))
                                        .put("data", img.optString("base64"))
                                )
                            )
                        }
                    }
                    parts.put(JSONObject().put("text", item.optString("content")))
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                    i++
                }
                "assistant" -> {
                    val parts = JSONArray()
                    val txt = item.optString("content")
                    if (txt.isNotBlank()) parts.put(JSONObject().put("text", txt))
                    val tcs = item.optJSONArray("tool_calls")
                    if (!useXmlFallback && tcs != null) {
                        for (j in 0 until tcs.length()) {
                            val fn = tcs.optJSONObject(j)?.optJSONObject("function") ?: continue
                            parts.put(
                                JSONObject().put(
                                    "functionCall",
                                    JSONObject()
                                        .put("name", fn.optString("name"))
                                        .put("args", parseJsonArgsSafely(fn.optString("arguments", "{}")))
                                )
                            )
                        }
                    }
                    if (parts.length() > 0) {
                        contents.put(JSONObject().put("role", "model").put("parts", parts))
                    }
                    i++
                }
                "tool" -> {
                    val parts = JSONArray()
                    while (i < history.length() && history.optJSONObject(i)?.optString("role") == "tool") {
                        val tItem = history.getJSONObject(i)
                        if (useXmlFallback) {
                            parts.put(
                                JSONObject().put(
                                    "text",
                                    "Tool result (${tItem.optString("name")}):\n${tItem.optString("content")}"
                                )
                            )
                        } else {
                            parts.put(
                                JSONObject().put(
                                    "functionResponse",
                                    JSONObject()
                                        .put("name", tItem.optString("name"))
                                        .put("response", JSONObject().put("result", tItem.optString("content")))
                                )
                            )
                        }
                        i++
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
                else -> i++
            }
        }

        val payload = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildSystemPrompt(useXmlFallback))))
            )
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))

        if (!useXmlFallback) {
            val decls = JSONArray()
            val oaTools = openAiToolDefinitions()
            for (idx in 0 until oaTools.length()) {
                val fn = oaTools.getJSONObject(idx).getJSONObject("function")
                decls.put(
                    JSONObject()
                        .put("name", fn.getString("name"))
                        .put("description", fn.getString("description"))
                        .put("parameters", fn.getJSONObject("parameters"))
                )
            }
            payload.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }

        val req = Request.Builder()
            .url(url)
            .apply { profile.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (!useXmlFallback && resp.code == 400 && body.contains("tool", ignoreCase = true)) {
                    onFallbackTriggered()
                    return callGeminiNative(profile, history, temperature, true, onFallbackTriggered)
                }
                error("${profile.name} API error (HTTP ${resp.code}): ${AntigravityCustomApiManager.extractApiError(body)}")
            }
            val root = JSONObject(body)
            val usage = root.optJSONObject("usageMetadata")
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: JSONArray()

            val texts = mutableListOf<String>()
            val thoughts = mutableListOf<String>()
            val invocations = mutableListOf<AgentToolInvocation>()
            for (idx in 0 until parts.length()) {
                val p = parts.optJSONObject(idx) ?: continue
                val fc = p.optJSONObject("functionCall")
                if (fc != null) {
                    invocations.add(
                        AgentToolInvocation(
                            callId = "gem_${UUID.randomUUID()}",
                            name = fc.optString("name"),
                            arguments = fc.optJSONObject("args") ?: JSONObject()
                        )
                    )
                } else if (p.optBoolean("thought", false)) {
                    thoughts.add(p.optString("text"))
                } else if (p.has("text")) {
                    texts.add(p.optString("text"))
                }
            }
            val (cleanText, inlineThink, xmlTools) = extractThinkAndXmlTools(texts.joinToString("\n"))
            if (inlineThink.isNotBlank()) thoughts.add(inlineThink)
            if (invocations.isEmpty() && xmlTools.isNotEmpty()) invocations.addAll(xmlTools)

            return ParsedModelTurn(
                text = cleanText,
                thought = thoughts.joinToString("\n\n").takeIf { it.isNotBlank() },
                toolInvocations = invocations,
                promptTokens = usage?.optInt("promptTokenCount", 0) ?: 0,
                outputTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0
            )
        }
    }

    companion object {
        private val THINK_BLOCK_REGEX = Regex("""<think>([\s\S]*?)</think>""", RegexOption.IGNORE_CASE)
        private val XML_TOOL_REGEX = Regex("""<agy_tool>([\s\S]*?)</agy_tool>""", RegexOption.IGNORE_CASE)
        private val TOOL_COMMENT_MARKER_REGEX = Regex("""<!--\s*tool_call:[^>]+-->""")

        internal fun stripToolMarkers(text: String): String {
            return text.replace(TOOL_COMMENT_MARKER_REGEX, "").replace(Regex("\n{3,}"), "\n\n").trim()
        }

        internal fun parseJsonArgsSafely(raw: String): JSONObject {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return JSONObject()
            return runCatching { JSONObject(trimmed) }.getOrElse {
                val start = trimmed.indexOf('{')
                val end = trimmed.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    runCatching { JSONObject(trimmed.substring(start, end + 1)) }.getOrDefault(JSONObject())
                } else JSONObject()
            }
        }

        internal fun extractThinkAndXmlTools(raw: String): Triple<String, String, List<AgentToolInvocation>> {
            val thoughts = mutableListOf<String>()
            val tools = mutableListOf<AgentToolInvocation>()

            var cleaned = THINK_BLOCK_REGEX.replace(raw) { m ->
                val inner = m.groupValues[1].trim()
                if (inner.isNotBlank()) thoughts.add(inner)
                ""
            }
            cleaned = XML_TOOL_REGEX.replace(cleaned) { m ->
                val payload = m.groupValues[1].trim()
                runCatching {
                    val obj = parseJsonArgsSafely(payload)
                    val name = obj.optString("name").trim()
                    if (name.isNotBlank()) {
                        tools.add(
                            AgentToolInvocation(
                                callId = "xml_${UUID.randomUUID()}",
                                name = name,
                                arguments = obj.optJSONObject("arguments") ?: JSONObject()
                            )
                        )
                    }
                }
                ""
            }
            return Triple(cleaned.trim(), thoughts.joinToString("\n\n"), tools)
        }
    }
}
