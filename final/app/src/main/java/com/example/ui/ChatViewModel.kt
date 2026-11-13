/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.ChatSessionEntity
import com.example.data.remote.GeminiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import android.net.Uri
import java.io.File
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val repository: ChatRepository,
    private val application: android.app.Application
) : ViewModel() {
    private val customApiStore = com.example.saif.data.agent.ChatSessionStore()
    private val customApiEngine by lazy {
        com.example.agyext.AntigravityCustomApiAgentEngine(application, viewModelScope, customApiStore,
            isAutoApproveProvider = { com.example.ui.components.SaifEngineSettings.isAutoApprove(application) })
    }
    private val providerState = com.example.agyext.AntigravityCustomApiManager.stateFlow
    val antigravityModels = providerState.map { state ->
        state.profiles.filter { it.isReady() }.flatMap { profile -> profile.effectiveModels().map {
            com.example.saif.domain.model.AiModel("${profile.id}::$it", "${profile.name}: ${it.substringAfterLast('/')}")
        } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val antigravitySelectedModel = providerState.map { state ->
        state.activeProfile?.let { "${it.id}::${it.selectedModel}" }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val antigravityConnection = providerState.map { state ->
        com.example.saif.data.remote.SystemConnectionState(
            if (state.isActiveAndReady) com.example.saif.data.remote.SystemStatus.READY
            else com.example.saif.data.remote.SystemStatus.UNAUTHENTICATED)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.saif.data.remote.SystemConnectionState())
    val antigravityAuthInfo = combine(providerState, com.example.data.local.AuthManager.currentUser) { state, user ->
        com.example.saif.data.remote.AgyHubClient.AgyAuthInfo(
            email = user?.email.orEmpty(), displayName = state.activeProfile?.name.orEmpty(),
            fullName = user?.name.orEmpty(), isLoggedIn = user != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.saif.data.remote.AgyHubClient.AgyAuthInfo())
    // BYOK providers do not expose a common quota API. Never fabricate balances.
    val antigravityQuotas = MutableStateFlow<List<com.example.saif.domain.model.ModelQuota>>(emptyList())
    fun selectAntigravityModel(id: String) = com.example.agyext.AntigravityCustomApiManager.selectModelFromComposer(application, id)
    suspend fun connectGoogleFlow(context: android.content.Context): String =
        "Google Flow MCP is not configured in this native build. Use the browser to open Flow directly."
    val mainAgentMessages: StateFlow<List<com.example.saif.domain.model.ChatMessage>> = customApiStore.messages
    private val _mainAgentSessionId = MutableStateFlow<String?>(null)
    val mainAgentSessionId: StateFlow<String?> = _mainAgentSessionId.asStateFlow()
    private var sessionLoadJob: Job? = null
    val mainAgentApprovals get() = customApiEngine.pendingApprovals
    fun terminateMainTool(tool: com.example.saif.domain.model.ToolCall, id: String) = customApiEngine.terminateRunningTool(tool)
    fun approveMainTool(tool: com.example.saif.domain.model.ToolCall, messageId: String, scope: String?) = customApiEngine.approveTool(tool, scope)
    fun rejectMainTool(tool: com.example.saif.domain.model.ToolCall, messageId: String, reason: String?) = customApiEngine.rejectTool(tool, reason)
    fun approveAllMainTools(items: List<com.example.saif.ui.chat.PendingToolApproval>) = customApiEngine.approveAllPending(items)
    fun rejectAllMainTools(items: List<com.example.saif.ui.chat.PendingToolApproval>) = customApiEngine.rejectAllPending(items)

    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedMessages: StateFlow<List<ChatMessageEntity>> = repository.bookmarkedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _currentMode = MutableStateFlow("general")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _isFilterBookmarked = MutableStateFlow(false)
    val isFilterBookmarked: StateFlow<Boolean> = _isFilterBookmarked.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _pendingOpenVideoProject = MutableStateFlow<com.example.ui.components.VideoProjectData?>(null)
    val pendingOpenVideoProject: StateFlow<com.example.ui.components.VideoProjectData?> = _pendingOpenVideoProject.asStateFlow()

    fun consumePendingVideoProject() {
        _pendingOpenVideoProject.value = null
    }

    fun triggerVideoProjectOpen(project: com.example.ui.components.VideoProjectData) {
        _pendingOpenVideoProject.value = project
    }

    private fun isVideoCreationPrompt(prompt: String): Boolean {
        if (_currentMode.value == "video" || _currentMode.value == "video_project") return true
        val p = prompt.lowercase().trim()
        if (p.contains("grow on youtube") || p.contains("how to grow on youtube") || p.contains("grow channel")) return true
        if (p.contains("promt se video") || p.contains("prompt se video") || p.contains("prompt to video") || p.contains("script to video")) return true
        val videoKeywords = listOf(
            "video", "reel", "short", "clip", "movie", "animat", "timeline", "edl",
            "youtube", "render", "director", "editor", "sfx", "b-roll", "broll", "voiceover"
        )
        val actionKeywords = listOf(
            "bana", "create", "make", "generate", "render", "edit", "chahiye", "ready karo",
            "karo", "do", "produce", "synthesize", "likho", "write", "bna", "dikhao", "play"
        )
        val hasVideo = videoKeywords.any { p.contains(it) }
        val hasAction = actionKeywords.any { p.contains(it) }
        return hasVideo && (hasAction || p.split(Regex("\\s+")).size <= 6)
    }

    private val _selectedAttachments = MutableStateFlow<List<Uri>>(emptyList())
    val selectedAttachments: StateFlow<List<Uri>> = _selectedAttachments.asStateFlow()

    
    fun processAudioToText(context: android.content.Context, file: File) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                val result = com.example.stt.SttRouter.transcribe(bytes, context = context)
                check(result.isSuccess) { result.rejectedReason ?: "Transcription failed" }
                _input.value = listOf(_input.value, result.text).filter { it.isNotBlank() }.joinToString(" ")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) {
                android.widget.Toast.makeText(context, error.message, android.widget.Toast.LENGTH_LONG).show()
            } finally { file.delete() }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        _selectedAttachments.value = _selectedAttachments.value + uris
    }

    fun removeAttachment(uri: Uri) {
        _selectedAttachments.value = _selectedAttachments.value.filter { it != uri }
    }
    
    fun clearAttachments() {
        _selectedAttachments.value = emptyList()
    }

    private val _currentStreamingContent = MutableStateFlow("")
    val currentStreamingContent: StateFlow<String> = _currentStreamingContent.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _customSystemPrompt = MutableStateFlow("")
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    private var activeGenerationJob: Job? = null

    // Observe messages for active session
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        com.example.agyext.AntigravityCustomApiManager.init(application)
        // This ViewModel survives sign-out and another Google sign-in in the same Activity.
        // Do not carry drafts, attachments, an in-flight reply or a selected chat across accounts.
        viewModelScope.launch {
            com.example.data.local.AuthManager.currentUser
                .map { it?.uid }.distinctUntilChanged().collect {
                    activeGenerationJob?.cancel()
                    sessionLoadJob?.cancel()
                    _activeSessionId.value = null
                    _mainAgentSessionId.value = null
                    _input.value = ""
                    _selectedAttachments.value = emptyList()
                    _currentStreamingContent.value = ""
                    _isGenerating.value = false
                    customApiEngine.stopActiveExecution()
                    customApiStore.clear()
                }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        val curId = _activeSessionId.value
        if (curId != null) {
            viewModelScope.launch {
                val session = repository.getSessionById(curId)
                if (session != null) {
                    repository.updateSessionTitle(curId, session.title)
                }
            }
        }
    }

    fun toggleBookmarkFilter() {
        _isFilterBookmarked.value = !_isFilterBookmarked.value
    }

    fun selectSession(sessionId: String) {
        if (_isGenerating.value) return
        _activeSessionId.value = sessionId
        _mainAgentSessionId.value = null
        sessionLoadJob?.cancel()
        sessionLoadJob = viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null && _activeSessionId.value == sessionId) _currentMode.value = session.mode
            if (customApiEngine.loadSessionMessages(sessionId)) {
                if (_activeSessionId.value == sessionId) _mainAgentSessionId.value = sessionId
                return@launch
            }
            customApiStore.clear()
        }
    }

    fun createNewSession(mode: String = _currentMode.value) {
        if (_isGenerating.value) return
        sessionLoadJob?.cancel()
        _mainAgentSessionId.value = null
        viewModelScope.launch {
            val newSession = repository.createSession(
                title = "New Chat",
                mode = mode
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = mode
            _isFilterBookmarked.value = false
        }
    }

    fun createProjectSession(
        appName: String,
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        buildStudio: String,
        language: String,
        iconBitmap: android.graphics.Bitmap?,
        context: android.content.Context
    ) {
        viewModelScope.launch {
            // Save ProjectData for persistent lookup when reopened from history drawer
            val projData = com.example.ui.components.ProjectData(
                appName = appName,
                packageName = packageName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                buildStudio = buildStudio,
                language = language,
                iconBitmap = iconBitmap
            )
            com.example.util.ProjectDataManager.saveProject(context, projData)

            val sessionTitle = appName.trim()
            val newSession = repository.createSession(
                title = sessionTitle,
                mode = "project"
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = "coding"
            _isFilterBookmarked.value = false

            // Do not insert developer initialization message into chatDao so home screen chat stays clean!

            // Seed initial chat history for Build AI studio
            val welcomeBuildMsg = com.example.ui.components.BuildAiMessage(
                sender = "assistant",
                content = "Hello! I am SAIF AI, your coding specialist for **$appName**. Tell me what to build, modify, or fix, and I will write and auto-apply the changes directly to your files.",
                timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            )
            com.example.util.ProjectChatHistoryManager.saveMessages(context, appName, listOf(welcomeBuildMsg))
        }
    }

    fun createVideoProjectSession(
        videoProject: com.example.ui.components.VideoProjectData,
        context: android.content.Context
    ) {
        viewModelScope.launch {
            // Save video project persistently
            com.example.util.ProjectDataManager.saveVideoProject(context, videoProject)

            val sessionTitle = videoProject.projectName.trim().ifBlank { "Video Project" }
            val existing = allSessions.value.firstOrNull { it.title.equals(sessionTitle, ignoreCase = true) && (it.mode == "video_project" || it.mode == "project") }
            if (existing != null) {
                _activeSessionId.value = existing.id
            } else {
                val newSession = repository.createSession(
                    title = sessionTitle,
                    mode = "video_project"
                )
                _activeSessionId.value = newSession.id
            }
            _currentMode.value = "general"
            _isFilterBookmarked.value = false
        }
    }

    private fun safeUriToBase64(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 1024): String? {
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
            val finalBmp = if (scale < 1f) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }

            val out = java.io.ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            finalBmp.recycle()
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            android.util.Log.e("ChatViewModel", "Failed to convert image to Base64: ${t.message}", t)
            null
        }
    }

    fun sendMessage(
        context: android.content.Context,
        userPrompt: String = _input.value,
        onSessionSelected: ((String) -> Unit)? = null
    ): Job? {
        val trimmed = userPrompt.trim()
        val attachments = _selectedAttachments.value.toList()
        if ((trimmed.isEmpty() && attachments.isEmpty()) || _isGenerating.value) return null

        val customApiState = com.example.agyext.AntigravityCustomApiManager.getState(context)
        val activeCustomProfile = customApiState.activeProfile?.takeIf { customApiState.isActiveAndReady }

        if (activeCustomProfile == null) {
            android.widget.Toast.makeText(context, "Configure and enable a Custom API provider in Settings first.", android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        _input.value = ""
        _selectedAttachments.value = emptyList()
        sessionLoadJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true
            var sessionId: String? = null
            val taskId = java.util.UUID.randomUUID().toString()
            val appContext = context.applicationContext
            var taskSuccessful = false
            com.example.util.TaskNotifications.begin(taskId)
            try {
                androidx.core.content.ContextCompat.startForegroundService(appContext,
                    android.content.Intent(appContext, com.example.util.ChatTaskService::class.java))
                com.example.saif.data.remote.AndroidLocalBridgeServer.instance.ensureLinuxCliReady(context)
                com.example.github.GitHubIntegrationManager.init(context)

                sessionId = _activeSessionId.value
                if (sessionId == null) {
                    val title = trimmed.take(28).ifBlank { "Attachment" }
                    sessionId = repository.createSession(title, _currentMode.value).id
                    _activeSessionId.value = sessionId
                } else if (repository.getSessionById(sessionId)?.title == "New Chat") {
                    repository.updateSessionTitle(sessionId, trimmed.take(28).ifBlank { "Attachment" })
                }
                onSessionSelected?.invoke(sessionId)
                val history = repository.getRecentMessages(sessionId, 20)
                    .filter { it.content.isNotBlank() && (it.role == "user" || it.role == "model") }
                val prompt = trimmed.ifBlank { "Please inspect and describe the attached file in detail." }
                val (specialistAgent, routedPrompt) = com.example.agents.SaifAgentRegistry.resolveAgentForTurn(context, prompt)
                val effectivePrompt = routedPrompt
                val specialistDirective = specialistAgent?.let(com.example.agents.SaifAgentRegistry::buildAgentDirective).orEmpty()
                repository.insertMessage(ChatMessageEntity(
                    sessionId = sessionId, role = "user", content = trimmed.ifBlank { "Attachment" },
                    attachmentUris = attachments.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }
                ))

                // Stage all attachments (including large mp4/apk/zip/pdf/images) into Linux /root/uploads/
                // and extract visual keyframes & metadata directly for Antigravity
                val stagedBundle = ChatAttachmentStager.prepareForAntigravity(context, attachments)
                val sessionPrefs = application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE)

                val result: String = if (activeCustomProfile != null) {
                        _mainAgentSessionId.value = sessionId
                    val systemText = _customSystemPrompt.value.trim()
                    val expandedPrompt = buildString {
                        if (systemText.isNotBlank()) append("Instructions: ").append(systemText).append("\n\n")
                        if (specialistDirective.isNotBlank()) append(specialistDirective).append("\n\n")
                        append(DIRECT_ANSWER_RULE).append("\n\n")
                        val memory = com.example.util.SaifMemoryCore.promptBlock(com.example.util.SaifMemoryCore.Features.CHAT)
                        if (memory.isNotBlank()) append(memory).append("\n\n")
                        if (!customApiEngine.loadSessionMessages(sessionId) && history.isNotEmpty()) {
                            customApiStore.messages.value = emptyList()
                            val historyText = history.joinToString("\n\n") {
                                (if (it.role == "user") "User: " else "Assistant: ") + it.content.take(6000)
                            }
                            append("Previous conversation (for context):\n").append(historyText).append("\n\n")
                        }
                        if (stagedBundle.promptContextBlock.isNotBlank()) {
                            append(stagedBundle.promptContextBlock).append("\n\n")
                        }
                        append("Current user message:\n").append(com.example.saif.data.remote.PromptExpansion.expand(effectivePrompt))
                    }
                    customApiEngine.executeTurn(
                        sessionId = sessionId,
                        profile = activeCustomProfile,
                        displayUserText = trimmed.ifBlank { "Attachment" },
                        expandedAgentPrompt = expandedPrompt,
                        inlineAttachments = stagedBundle.inlineAttachments,
                        temperature = _temperature.value,
                        onStreamingText = { partial ->
                            if (partial.isNotBlank()) _currentStreamingContent.value = partial
                        }
                    )
                } else {
                    error("Custom API profile is no longer available")
                }
                val finalDeliveredResult = if (result.contains("<saif-apk", ignoreCase = true)) {
                    val sessionTitle = allSessions.value.firstOrNull { it.id == sessionId }?.title
                    val compiledReply = runCatching {
                        com.example.compiler.OnDeviceApkBuildManager.processAssistantReplyForEmbeddedApk(
                            context =
    context,
                            userPrompt = trimmed,
                            assistantReply = result,
                            sessionTitle = sessionTitle
                        )
                    }.getOrDefault(result)
                    if (compiledReply != result) {
                        sessionPrefs.edit().remove(sessionId).apply()
                        _mainAgentSessionId.value = null
                    }
                    compiledReply
                } else {
                    result
                }
                repository.insertMessage(ChatMessageEntity(sessionId = sessionId, role = "model", content = finalDeliveredResult))
                taskSuccessful = true
                com.example.util.SaifMemoryCore.learn(com.example.util.SaifMemoryCore.Features.CHAT, prompt, finalDeliveredResult)
                val prefs = context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE)
                if (prefs.getBoolean("autoSpeak", false)) com.example.utils.TTSManager.speak(finalDeliveredResult, context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                    customApiEngine.stopActiveExecution()
                    customApiEngine.stopActiveExecution()
                    sessionId?.let { repository.insertMessage(ChatMessageEntity(sessionId = it, role = "model", content = "Antigravity response timed out. Please check the server and try again.")) }
                } else throw cancelled
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.message?.startsWith("Chat attachments must total under 3 MB") == true) {
                    if (_input.value.isBlank()) _input.value = trimmed
                    if (_selectedAttachments.value.isEmpty()) _selectedAttachments.value = attachments
                }
                sessionId?.let { repository.insertMessage(ChatMessageEntity(sessionId = it, role = "model", content = "Antigravity error: ${error.localizedMessage ?: "Unknown error"}")) }
            } finally {
                appContext.stopService(android.content.Intent(appContext, com.example.util.ChatTaskService::class.java))
                com.example.util.TaskNotifications.finish(appContext, taskId, taskSuccessful)
                _currentStreamingContent.value = ""
                _isGenerating.value = false
            }
        }
        return activeGenerationJob
    }

    /** Uses the same verified connection, attachments pipeline and chat history as a typed coding request. */
    suspend fun sendLiveCodingTask(context: android.content.Context, spokenRequest: String): String {
        if (_isGenerating.value) return "Antigravity is still working on the last request. Check the chat before sending another edit."
        val customReady = com.example.agyext.AntigravityCustomApiManager.getState(context).isActiveAndReady
        if (!customReady) return "Enable a configured Custom API provider in Settings first."
        val selectedSession = kotlinx.coroutines.CompletableDeferred<String>()
        val job = sendMessage(context, spokenRequest, onSessionSelected = { selectedSession.complete(it) })
            ?: return "Could not send your coding request. Please check the chat connection."
        val sessionId = kotlinx.coroutines.withTimeoutOrNull(10_000L) { selectedSession.await() }
            ?: return "Could not open the Antigravity chat. Please check the connection."
        val finished = kotlinx.coroutines.withTimeoutOrNull(90_000L) { job.join(); true } ?: false
        if (!finished) return "Your request is running in Antigravity chat. You can review its progress and approve tools there."
        if (job.isCancelled) return "That Antigravity request was stopped. Check the chat for details."
        // Don't speak an older response, or a response from a chat the user switched to meanwhile.
        val turn = repository.getRecentMessages(sessionId, 2)
        val last = turn.lastOrNull()
        if (last?.role != "model" || turn.firstOrNull()?.role != "user" ||
            turn.firstOrNull()?.content != spokenRequest.trim()) {
            return "Your request is in chat. Check for pending tool approval or a reply."
        }
        return if (last.content.startsWith("Antigravity error:") || last.content.startsWith("Antigravity response timed out"))
            last.content else last.content.take(1500)
    }

    fun stopGeneration() {
        customApiEngine.stopActiveExecution()
        customApiEngine.stopActiveExecution()
        activeGenerationJob?.cancel()
        val partial = _currentStreamingContent.value
        val sessionId = _activeSessionId.value
        if (partial.isNotBlank() && sessionId != null) {
            viewModelScope.launch {
                val partialMsg = ChatMessageEntity(
                    sessionId = sessionId,
                    role = "model",
                    content = "$partial [Stopped]"
                )
                repository.insertMessage(partialMsg)
            }
        }
        _currentStreamingContent.value = ""
        _isGenerating.value = false
    }

    fun regenerateLastResponse(context: android.content.Context) {
        if (_isGenerating.value) return
        val messages = activeMessages.value
        val lastUserMsg = messages.lastOrNull { it.role == "user" }
        if (lastUserMsg != null) {
            sendMessage(context, lastUserMsg.content)

        }
    }

    fun toggleBookmark(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(message.id, message.isBookmarked)
        }
    }

    fun renameSession(sessionId: String, newTitle: String, context: android.content.Context? = null) {
        viewModelScope.launch {
            val target = allSessions.value.firstOrNull { it.id == sessionId }
            if (target != null && context != null) {
                if (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title)) {
                    val oldProj = com.example.util.ProjectDataManager.getVideoProject(context, target.title)
                    if (oldProj != null) {
                        com.example.util.ProjectDataManager.deleteVideoProject(context, target.title)
                        com.example.util.ProjectDataManager.saveVideoProject(context, oldProj.copy(projectName = newTitle.trim()))
                    }
                } else if (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title)) {
                    val oldProj = com.example.util.ProjectDataManager.getProject(context, target.title)
                    if (oldProj != null) {
                        com.example.util.ProjectDataManager.renameProject(context, target.title, newTitle.trim(), oldProj.copy(appName = newTitle.trim()))
                    }
                }
            }
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String, context: android.content.Context? = null) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val target = allSessions.value.firstOrNull { it.id == sessionId }
            if (target != null && context != null) {
                if (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title)) {
                    com.example.util.ProjectDataManager.deleteProject(context, target.title)
                    com.example.util.ProjectChatHistoryManager.clearMessages(context, target.title)
                } else if (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title)) {
                    com.example.util.ProjectDataManager.deleteVideoProject(context, target.title)
                }
            }
            repository.deleteSession(sessionId)
            customApiEngine.deleteSessionTranscript(sessionId)
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().remove(sessionId).apply()
            if (_activeSessionId.value == sessionId) {
                val remaining = allSessions.value.filter { it.id != sessionId }
                _activeSessionId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun clearAllSessions() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            repository.deleteAllSessions()
            customApiEngine.clearAllSessionTranscripts()
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            _activeSessionId.value = null
        }
    }

    fun clearCurrentSessionMessages() {
        if (_isGenerating.value) return
        val curId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.clearSessionMessages(curId)
            customApiEngine.deleteSessionTranscript(curId)
            if (_mainAgentSessionId.value == curId) {
                customApiStore.messages.value = emptyList()
                _mainAgentSessionId.value = null
            }
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().remove(curId).apply()
        }
    }

    fun updateSettings(temp: Float, prompt: String) {
        _temperature.value = temp
        _customSystemPrompt.value = prompt
    }

    companion object {
        /** Keeps everyday chat fast while letting Antigravity build & compile real APKs in its Linux workspace. */
        const val DIRECT_ANSWER_RULE =
            "Reply rule: for greetings, small talk and simple daily-life or general-knowledge questions, answer directly " +
                "in plain text straight away and do NOT run any command, tool or subagent. Use commands and tools when " +
                "the task genuinely needs them (building or editing apps and files, code, git, research, media or video). " +
                "When the user attaches any file (video .mp4, .apk, .zip, image, code, etc.) and asks a question about it, " +
                "inspect the inline preview/keyframes/metadata and the staged file in `/root/uploads/` to answer accurately, " +
                "or use `/root/uploads/` directly in your commands if they ask you to process or build with it. " +
                "When the user asks you to create/build an Android app or APK in chat: create the project inside " +
                "`/root/workspace/<app-slug>/` using your normal workspace/terminal commands (either a rich multi-file " +
                "mobile HTML5/CSS/JS app with `index.html` or a native Java Android layout under `app/src/main/{AndroidManifest.xml,java,res}`), " +
                "then run `saif-build-apk /root/workspace/<app-slug> --name \"App Name\" --package com.saifai.app` in the terminal " +
                "to compile & cryptographically sign (v1/v2/v3) the APK, and include the printed `DOWNLOAD_URL` (`http://127.0.0.1:...`) " +
                "as a clickable Markdown link `[Download App APK](DOWNLOAD_URL)` in your reply. You can also publish any built " +
                "file/ZIP/MP4 in Linux into a clickable download link via `saif-apk-link /path/to/file`."
    }

    class Factory(private val repository: ChatRepository, private val application: android.app.Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
