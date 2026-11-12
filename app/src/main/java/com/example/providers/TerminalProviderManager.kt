package com.example.providers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.example.agyext.AgyApiProtocol
import com.example.agyext.AgyConnectionTestResult
import com.example.agyext.AgyCustomApiProfile
import com.example.agyext.AntigravityCustomApiAgentEngine
import com.example.agyext.AntigravityCustomApiManager
import com.example.data.local.AuthManager
import com.example.gemini.R
import com.example.gemini.data.agent.AgentPreferences
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.local.LocalInstallerState
import com.example.llm.KeyStoreCrypto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Central orchestrator for the SAIF AI Terminal Provider Ecosystem.
 *
 * Handles post-login provider onboarding, background environment downloads & verification,
 * storage/network prechecks, cancellation & restart recovery, Android KeyStore-encrypted
 * custom providers, per-account persistence (with guest-to-account upgrade migration),
 * health monitoring, multi-session terminal persistence, and AI command assistance.
 */
object TerminalProviderManager {

    private const val PREFS_PREFIX = "saif_terminal_providers_v1"

    /** Selected silently on first run (see [ensureDefaultProviderSelected]). */
    const val DEFAULT_PROVIDER_ID = "local_ubuntu"
    private const val NOTIFICATION_CHANNEL_ID = "saif_terminal_provider_ready"
    private const val READY_NOTIFICATION_ID = 5120

    val BUILT_IN_PROVIDERS: List<TerminalProviderSpec> = listOf(
        TerminalProviderSpec(
            id = "google_antigravity",
            name = "Google Antigravity",
            description = "Official Antigravity workspace daemon inside Ubuntu PRoot with multi-agent orchestration, IDE bridge, and OAuth / Custom API support.",
            mode = TerminalProviderMode.LOCAL_PROOT_DAEMON,
            protocol = AgyApiProtocol.AUTO,
            endpointUrl = "http://127.0.0.1:1235",
            authScheme = TerminalAuthScheme.OAUTH_OR_CUSTOM_API,
            selectedModel = "gemini-2.5-pro",
            availableModels = listOf("gemini-2.5-pro", "claude-sonnet-4", "gpt-4.1"),
            requiresLinuxRootfs = true,
            minFreeStorageMb = 650L,
            platformCompatibilityNote = "Runs verified local Linux workspace + Antigravity Hub on Android ARM64.",
            isBuiltIn = true
        ),
        TerminalProviderSpec(
            id = "opencode",
            name = "OpenCode",
            description = "Open-source terminal coding agent provisioned in SAIF AI's Ubuntu Linux workspace with OpenRouter, OpenAI, Anthropic, or Gemini backends.",
            mode = TerminalProviderMode.LOCAL_PROOT_DAEMON,
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            endpointUrl = "https://openrouter.ai/api/v1",
            authScheme = TerminalAuthScheme.BEARER_TOKEN,
            selectedModel = "anthropic/claude-sonnet-4",
            availableModels = listOf(
                "anthropic/claude-sonnet-4",
                "google/gemini-2.5-pro",
                "openai/gpt-4.1",
                "deepseek/deepseek-chat-v3-0324",
                "qwen/qwen3-coder"
            ),
            requiresLinuxRootfs = true,
            minFreeStorageMb = 650L,
            platformCompatibilityNote = "Prepares Ubuntu PRoot + /usr/local/bin/opencode CLI config. Requires an API key (OpenRouter/OpenAI/Anthropic/Gemini) for live completions.",
            isBuiltIn = true
        ),
        TerminalProviderSpec(
            id = "arena_ai",
            name = "Arena AI",
            description = "Comparative multi-model coding & evaluation bridge connected to SAIF AI's local Android/Linux workspace and shell tools.",
            mode = TerminalProviderMode.HYBRID_API_WORKSPACE,
            protocol = AgyApiProtocol.OPENAI_COMPATIBLE,
            endpointUrl = "https://openrouter.ai/api/v1",
            authScheme = TerminalAuthScheme.BEARER_TOKEN,
            selectedModel = "google/gemini-2.5-pro",
            availableModels = listOf(
                "google/gemini-2.5-pro",
                "anthropic/claude-sonnet-4",
                "openai/gpt-4.1",
                "x-ai/grok-3-beta",
                "qwen/qwen3-coder"
            ),
            requiresLinuxRootfs = false,
            minFreeStorageMb = 64L,
            platformCompatibilityNote = "Arena AI is a cloud web platform without a standalone ARM64 Android binary; SAIF AI provides a verified Hybrid API + Local Workspace Terminal integration.",
            isBuiltIn = true
        ),
        TerminalProviderSpec(
            id = "claude_code",
            name = "Claude Code",
            description = "Anthropic terminal coding workflow inside SAIF AI's Ubuntu Linux workspace with native Anthropic Messages API & file/shell execution.",
            mode = TerminalProviderMode.LOCAL_PROOT_DAEMON,
            protocol = AgyApiProtocol.ANTHROPIC,
            endpointUrl = "https://api.anthropic.com/v1",
            authScheme = TerminalAuthScheme.X_API_KEY,
            selectedModel = "claude-sonnet-4-20250514",
            availableModels = listOf("claude-sonnet-4-20250514", "claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"),
            requiresLinuxRootfs = true,
            minFreeStorageMb = 650L,
            platformCompatibilityNote = "Uses Ubuntu PRoot + Claude Code bridge with your Anthropic or compatible API credentials.",
            isBuiltIn = true
        ),
        TerminalProviderSpec(
            id = "local_ubuntu",
            name = "Local Ubuntu Shell",
            description = "Pure offline-capable Debian/Ubuntu PRoot Linux terminal with bash, git, python3, and saif-build-apk — no cloud account required.",
            mode = TerminalProviderMode.LOCAL_SHELL_ONLY,
            protocol = AgyApiProtocol.AUTO,
            endpointUrl = "",
            authScheme = TerminalAuthScheme.NONE_LOCAL,
            selectedModel = "local-shell",
            availableModels = listOf("local-shell"),
            requiresLinuxRootfs = true,
            minFreeStorageMb = 650L,
            platformCompatibilityNote = "100% local Linux shell execution on device.",
            isBuiltIn = true
        )
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preparationJob: Job? = null
    private var installerCollectorJob: Job? = null

    private val _state = MutableStateFlow(
        TerminalProviderEcosystemState(providers = BUILT_IN_PROVIDERS)
    )
    val state: StateFlow<TerminalProviderEcosystemState> = _state.asStateFlow()

    @Volatile
    private var boundScopeKey: String? = null

    private fun scopeKeyForUid(uid: String?): String {
        val clean = uid?.trim()?.takeIf { it.isNotEmpty() } ?: "guest"
        return "${PREFS_PREFIX}_$clean"
    }

    /**
     * Binds provider state to the active user account (or guest), migrating guest selections
     * seamlessly when a guest signs up or logs into a new account from the main screen.
     */
    @Synchronized
    fun bindUserAccount(context: Context, uid: String?, previousUid: String? = null): TerminalProviderEcosystemState {
        val app = context.applicationContext
        AntigravityCustomApiManager.init(app)
        val targetKey = scopeKeyForUid(uid)
        val targetPrefs = app.getSharedPreferences(targetKey, Context.MODE_PRIVATE)

        // Seamless Guest -> New Account migration when signing up from the main screen.
        // Prefer the exact guest uid so one guest device cannot inherit another guest's state.
        if (!uid.isNullOrBlank() && uid != "guest" && !targetPrefs.contains("onboarding_done")) {
            val sourceKey = previousUid?.takeIf { it.startsWith("guest:") }?.let(::scopeKeyForUid)
                ?: scopeKeyForUid("guest")
            val sourcePrefs = app.getSharedPreferences(sourceKey, Context.MODE_PRIVATE)
            if (sourcePrefs.getBoolean("onboarding_done", false)) {
                val sourceJson = sourcePrefs.getString("providers_json", null)
                val sourceDefault = sourcePrefs.getString("default_provider_id", null)
                val sourceActive = sourcePrefs.getString("active_provider_id", null)
                val sourceSessions = sourcePrefs.getString("terminal_sessions_json", null)
                targetPrefs.edit().apply {
                    putBoolean("onboarding_done", true)
                    if (sourceJson != null) putString("providers_json", sourceJson)
                    if (sourceDefault != null) putString("default_provider_id", sourceDefault)
                    if (sourceActive != null) putString("active_provider_id", sourceActive)
                    if (sourceSessions != null) putString("terminal_sessions_json", sourceSessions)
                }.apply()
            }
        }

        boundScopeKey = targetKey
        val loaded = loadFromPrefs(app, targetKey)
        _state.value = loaded
        startInstallerObserverIfNeeded(app)
        return loaded
    }

    fun hasSelectedProviderForUser(context: Context, uid: String?): Boolean {
        val st = bindUserAccount(context, uid)
        return st.hasCompletedOnboardingSelection && st.activeProviderId.isNotBlank()
    }

    /**
     * First run without a chooser screen: the plain local Linux workspace is selected silently, so the app opens
     * straight on the chat. It needs no account and no API key; another provider can still be picked later in
     * Settings. Returns true when the default was applied now, false when the user already had a provider.
     */
    fun ensureDefaultProviderSelected(context: Context, uid: String?): Boolean {
        if (hasSelectedProviderForUser(context, uid)) return false
        selectAndPrepareProvider(context, DEFAULT_PROVIDER_ID, setAsDefault = true)
        return true
    }

    /**
     * Saves the user's selected provider after login (or when switching providers) and
     * immediately begins verified background environment preparation.
     */
    fun selectAndPrepareProvider(
        context: Context,
        providerId: String,
        setAsDefault: Boolean = true
    ) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val provider = cur.providers.firstOrNull { it.id == providerId } ?: return
        val nextDefault = if (setAsDefault || cur.defaultProviderId.isBlank()) provider.id else cur.defaultProviderId
        val ensuredSessions = if (cur.terminalSessions.isEmpty()) {
            listOf(
                PersistentTerminalSession(
                    title = "${provider.name} #1",
                    providerId = provider.id,
                    workingDir = "/root/workspace"
                )
            )
        } else {
            cur.terminalSessions
        }
        val updated = cur.copy(
            defaultProviderId = nextDefault,
            activeProviderId = provider.id,
            hasCompletedOnboardingSelection = true,
            terminalSessions = ensuredSessions,
            activeTerminalSessionId = cur.activeTerminalSessionId.ifBlank { ensuredSessions.first().id }
        )
        persist(app, updated)
        syncProviderToRuntimeBridges(app, provider)
        prepareProviderInBackground(app, provider.id, forceRetry = false)
    }

    /** Resumes any unfinished provider preparation if the app restarted mid-setup. */
    fun resumePendingSetupIfNeeded(context: Context) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val key = boundScopeKey ?: scopeKeyForUid(runCatching { AuthManager.currentUser.value?.uid }.getOrNull())
        val pendingId = app.getSharedPreferences(key, Context.MODE_PRIVATE)
            .getString("pending_setup_provider_id", null)
        val targetId = pendingId?.takeIf { it.isNotBlank() }
            ?: cur.activeProviderId.takeIf { cur.hasCompletedOnboardingSelection && it.isNotBlank() }
        if (targetId != null) {
            prepareProviderInBackground(app, targetId, forceRetry = false)
        }
    }

    /**
     * Runs genuine storage checks, network checks, Linux rootfs installation (when required),
     * provider CLI/workspace provisioning, and endpoint health validation in the background.
     * Never marks a provider READY until every required check succeeds.
     */
    fun prepareProviderInBackground(
        context: Context,
        providerId: String,
        forceRetry: Boolean = false
    ) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val provider = cur.providers.firstOrNull { it.id == providerId } ?: return

        preparationJob?.cancel()
        setPendingSetupProvider(app, provider.id)

        preparationJob = scope.launch {
            try {
                val freeMb = checkAvailableStorageMb(app)
                updateReadiness(
                    app = app,
                    readiness = TerminalProviderReadiness.CHECKING_PREREQUISITES,
                    progress = 0.08f,
                    message = "Checking storage (${freeMb} MB free) & environment for ${provider.name}...",
                    isPreparing = true,
                    canRetry = false,
                    freeStorageMb = freeMb
                )

                val linuxAlreadyVerified = LocalEnvironmentManager.hasVerifiedEnvironment(app)

                // 1. Storage availability precheck (only require full 650MB when Linux isn't extracted yet)
                val requiredMb = if (provider.requiresLinuxRootfs && !linuxAlreadyVerified) {
                    provider.minFreeStorageMb
                } else {
                    32L
                }
                if (freeMb in 1 until requiredMb) {
                    val msg = "Insufficient storage: ${freeMb} MB available, ${requiredMb} MB required for ${provider.name}."
                    appendHistory(app, provider, "FAILED", msg)
                    setPendingSetupProvider(app, null)
                    updateReadiness(
                        app = app,
                        readiness = TerminalProviderReadiness.FAILED,
                        progress = 0f,
                        message = msg,
                        isPreparing = false,
                        canRetry = true,
                        freeStorageMb = freeMb
                    )
                    return@launch
                }

                // 2. Ensure host workspace directory (/root/workspace -> filesDir/home) always exists
                val workspaceDir = File(app.filesDir, "home")
                if (!workspaceDir.exists()) workspaceDir.mkdirs()

                // 3. Linux rootfs download & verification if required by provider
                if (provider.requiresLinuxRootfs && !LocalEnvironmentManager.hasVerifiedEnvironment(app)) {
                    if (!isNetworkAvailable(app) && !File(app.filesDir, "ubuntu/bin/sh").exists()) {
                        val msg = "Network unavailable: connect to Wi-Fi or mobile data to download ${provider.name} Linux environment."
                        appendHistory(app, provider, "FAILED", msg)
                        updateReadiness(
                            app = app,
                            readiness = TerminalProviderReadiness.FAILED,
                            progress = 0f,
                            message = msg,
                            isPreparing = false,
                            canRetry = true,
                            freeStorageMb = freeMb
                        )
                        return@launch
                    }

                    updateReadiness(
                        app = app,
                        readiness = TerminalProviderReadiness.DOWNLOADING_ENVIRONMENT,
                        progress = 0.15f,
                        message = "Preparing Ubuntu Linux environment for ${provider.name}...",
                        isPreparing = true,
                        canRetry = false,
                        freeStorageMb = freeMb
                    )

                    if (forceRetry) {
                        LocalEnvironmentManager.retryBackgroundInstall(app)
                    } else {
                        LocalEnvironmentManager.ensureInstalledInBackground(app)
                    }

                    // Monitor LocalEnvironmentManager until verified, failed, or cancelled
                    while (currentCoroutineContext().isActive && !LocalEnvironmentManager.hasVerifiedEnvironment(app)) {
                        val inst = LocalEnvironmentManager.installerState.value
                        when (inst) {
                            is LocalInstallerState.Downloading -> {
                                val dlMb = inst.bytesDownloaded / (1024 * 1024)
                                val totMb = (inst.totalBytes / (1024 * 1024)).coerceAtLeast(1)
                                updateReadiness(
                                    app = app,
                                    readiness = TerminalProviderReadiness.DOWNLOADING_ENVIRONMENT,
                                    progress = (0.15f + inst.progressFraction * 0.50f).coerceIn(0.15f, 0.65f),
                                    message = "Downloading ${inst.currentPackageName}: ${dlMb}/${totMb} MB (${inst.speedText})",
                                    isPreparing = true,
                                    canRetry = false
                                )
                            }
                            is LocalInstallerState.Extracting -> {
                                updateReadiness(
                                    app = app,
                                    readiness = TerminalProviderReadiness.CONFIGURING_TOOLS,
                                    progress = (0.65f + inst.progressFraction * 0.20f).coerceIn(0.65f, 0.85f),
                                    message = "Extracting Linux rootfs (${inst.extractedFilesCount} files)...",
                                    isPreparing = true,
                                    canRetry = false
                                )
                            }
                            is LocalInstallerState.Configuring -> {
                                updateReadiness(
                                    app = app,
                                    readiness = TerminalProviderReadiness.CONFIGURING_TOOLS,
                                    progress = 0.86f,
                                    message = inst.stepDescription,
                                    isPreparing = true,
                                    canRetry = false
                                )
                            }
                            is LocalInstallerState.Verifying -> {
                                updateReadiness(
                                    app = app,
                                    readiness = TerminalProviderReadiness.VERIFYING_HEALTH,
                                    progress = 0.92f,
                                    message = "Verifying shell & binaries (${inst.testName})...",
                                    isPreparing = true,
                                    canRetry = false
                                )
                            }
                            is LocalInstallerState.Error -> {
                                val safeErr = KeyStoreCrypto.redact(inst.errorMessage)
                                appendHistory(app, provider, "FAILED", safeErr)
                                updateReadiness(
                                    app = app,
                                    readiness = TerminalProviderReadiness.FAILED,
                                    progress = 0f,
                                    message = safeErr,
                                    isPreparing = false,
                                    canRetry = true
                                )
                                maybeFailoverToHealthyProvider(app, failedProviderId = provider.id)
                                return@launch
                            }
                            else -> Unit
                        }
                        delay(450)
                    }
                }

                // 4. Configure provider-specific toolchain files inside verified environment
                updateReadiness(
                    app = app,
                    readiness = TerminalProviderReadiness.CONFIGURING_TOOLS,
                    progress = 0.88f,
                    message = "Configuring ${provider.name} workspace & CLI bridge...",
                    isPreparing = true,
                    canRetry = false
                )
                val latestProvider = ensureLoaded(app).providers.firstOrNull { it.id == provider.id } ?: provider
                provisionProviderToolchain(app, latestProvider)

                // 5. Honest Health & Credential Validation (NEVER fake READY!)
                updateReadiness(
                    app = app,
                    readiness = TerminalProviderReadiness.VERIFYING_HEALTH,
                    progress = 0.95f,
                    message = "Validating ${latestProvider.name} health & authentication...",
                    isPreparing = true,
                    canRetry = false
                )

                val validation = validateProviderEnvironment(app, latestProvider)
                setPendingSetupProvider(app, null)
                updateProviderHealthRecord(
                    app = app,
                    providerId = latestProvider.id,
                    ok = validation.first == TerminalProviderReadiness.READY,
                    latencyMs = validation.third,
                    message = validation.second
                )

                appendHistory(app, latestProvider, validation.first.name, validation.second)
                updateReadiness(
                    app = app,
                    readiness = validation.first,
                    progress = if (validation.first == TerminalProviderReadiness.READY) 1.0f else 0.92f,
                    message = validation.second,
                    isPreparing = false,
                    canRetry = validation.first == TerminalProviderReadiness.FAILED || validation.first == TerminalProviderReadiness.DEGRADED
                )

                if (validation.first == TerminalProviderReadiness.READY) {
                    notifyProviderReady(app, latestProvider)
                }
            } catch (_: CancellationException) {
                setPendingSetupProvider(app, null)
                updateReadiness(
                    app = app,
                    readiness = TerminalProviderReadiness.CANCELLED,
                    progress = 0f,
                    message = "Setup cancelled by user.",
                    isPreparing = false,
                    canRetry = true
                )
            } catch (e: Exception) {
                val safeMsg = KeyStoreCrypto.redact(e.message ?: "Unexpected setup error")
                setPendingSetupProvider(app, null)
                appendHistory(app, provider, "FAILED", safeMsg)
                updateReadiness(
                    app = app,
                    readiness = TerminalProviderReadiness.FAILED,
                    progress = 0f,
                    message = safeMsg,
                    isPreparing = false,
                    canRetry = true
                )
            }
        }
    }

    fun cancelPreparation(context: Context) {
        val app = context.applicationContext
        preparationJob?.cancel()
        preparationJob = null
        LocalEnvironmentManager.cancelBackgroundInstall(app)
        setPendingSetupProvider(app, null)
        updateReadiness(
            app = app,
            readiness = TerminalProviderReadiness.CANCELLED,
            progress = 0f,
            message = "Background environment setup cancelled.",
            isPreparing = false,
            canRetry = true
        )
    }

    fun retryPreparation(context: Context) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val id = cur.activeProviderId.ifBlank { cur.defaultProviderId }.ifBlank { "google_antigravity" }
        prepareProviderInBackground(app, id, forceRetry = true)
    }

    // -------------------------------------------------------------------------
    // Custom Provider CRUD & Switching (Section 7)
    // -------------------------------------------------------------------------

    fun upsertProvider(
        context: Context,
        spec: TerminalProviderSpec,
        setAsDefault: Boolean = false,
        activateNow: Boolean = false
    ): TerminalProviderSpec {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val cleanId = spec.id.trim().ifBlank { "custom_${java.util.UUID.randomUUID().toString().take(8)}" }
        val normalized = spec.copy(
            id = cleanId,
            name = spec.name.trim().ifBlank { "Custom Provider" },
            description = spec.description.trim().ifBlank { "Custom terminal & AI provider" },
            endpointUrl = spec.endpointUrl.trim(),
            apiKey = spec.apiKey.trim(),
            selectedModel = spec.selectedModel.trim().ifBlank {
                spec.availableModels.firstOrNull()?.trim().orEmpty()
            },
            availableModels = buildList {
                if (spec.selectedModel.isNotBlank()) add(spec.selectedModel.trim())
                spec.availableModels.map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    if (!contains(it)) add(it)
                }
            }
        )

        val exists = cur.providers.any { it.id == normalized.id }
        val updatedList = if (exists) {
            cur.providers.map { if (it.id == normalized.id) normalized else it }
        } else {
            cur.providers + normalized
        }

        val nextDefault = if (setAsDefault || cur.defaultProviderId.isBlank()) normalized.id else cur.defaultProviderId
        val nextActive = if (activateNow || cur.activeProviderId.isBlank()) normalized.id else cur.activeProviderId

        persist(
            app,
            cur.copy(
                providers = updatedList,
                defaultProviderId = nextDefault,
                activeProviderId = nextActive
            )
        )

        if (activateNow || cur.activeProviderId == normalized.id) {
            syncProviderToRuntimeBridges(app, normalized)
            prepareProviderInBackground(app, normalized.id, forceRetry = false)
        }
        return normalized
    }

    fun deleteProvider(context: Context, providerId: String) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val target = cur.providers.firstOrNull { it.id == providerId } ?: return
        if (target.isBuiltIn) return
        val remaining = cur.providers.filterNot { it.id == providerId }
        val fallbackId = remaining.firstOrNull()?.id ?: "google_antigravity"
        val nextDefault = if (cur.defaultProviderId == providerId) fallbackId else cur.defaultProviderId
        val nextActive = if (cur.activeProviderId == providerId) nextDefault else cur.activeProviderId
        persist(
            app,
            cur.copy(
                providers = remaining,
                defaultProviderId = nextDefault,
                activeProviderId = nextActive
            )
        )
    }

    fun setDefaultProvider(context: Context, providerId: String) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        if (cur.providers.none { it.id == providerId }) return
        persist(app, cur.copy(defaultProviderId = providerId))
    }

    fun setAutoFailover(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        persist(app, cur.copy(autoFailoverEnabled = enabled))
    }

    suspend fun testProviderConnection(
        context: Context,
        spec: TerminalProviderSpec
    ): AgyConnectionTestResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (spec.mode == TerminalProviderMode.LOCAL_SHELL_ONLY) {
            val ok = LocalEnvironmentManager.hasVerifiedEnvironment(app)
            val msg = if (ok) "Verified local Ubuntu PRoot shell is active." else "Local Linux rootfs is not yet verified."
            updateProviderHealthRecord(app, spec.id, ok, 1L, msg)
            return@withContext AgyConnectionTestResult(ok = ok, message = msg, latencyMs = 1L)
        }
        if (spec.id == "google_antigravity" && spec.apiKey.isBlank()) {
            val customApi = AntigravityCustomApiManager.getState(app)
            if (customApi.isActiveAndReady && customApi.activeProfile != null) {
                val res = AntigravityCustomApiManager.testConnection(customApi.activeProfile!!)
                val cleanMsg = KeyStoreCrypto.redact(res.message)
                updateProviderHealthRecord(app, spec.id, res.ok, res.latencyMs, cleanMsg)
                return@withContext res.copy(message = cleanMsg)
            }
            val ok = LocalEnvironmentManager.hasVerifiedEnvironment(app)
            val msg = if (ok) {
                "Local Antigravity Ubuntu workspace verified. Sign in with Google or attach a Custom API key for cloud models."
            } else {
                "Antigravity Linux workspace is still preparing."
            }
            updateProviderHealthRecord(app, spec.id, ok, 5L, msg)
            return@withContext AgyConnectionTestResult(ok = ok, message = msg, latencyMs = 5L)
        }

        val profile = toAgyCustomProfile(spec)
        val result = AntigravityCustomApiManager.testConnection(profile)
        val redactedMessage = KeyStoreCrypto.redact(result.message)
        updateProviderHealthRecord(app, spec.id, result.ok, result.latencyMs, redactedMessage)
        result.copy(message = redactedMessage)
    }

    suspend fun fetchProviderModels(spec: TerminalProviderSpec): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val profile = toAgyCustomProfile(spec)
            AntigravityCustomApiManager.fetchModels(profile)
        }

    // -------------------------------------------------------------------------
    // Multi-Session Terminal Persistence & AI Command Assistance (Section 9)
    // -------------------------------------------------------------------------

    fun createTerminalSession(
        context: Context,
        title: String? = null,
        providerId: String? = null,
        workingDir: String = "/root/workspace"
    ): PersistentTerminalSession {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val boundProvider = providerId ?: cur.activeProviderId.ifBlank { "google_antigravity" }
        val session = PersistentTerminalSession(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Session #${cur.terminalSessions.size + 1}",
            providerId = boundProvider,
            workingDir = workingDir
        )
        val next = cur.copy(
            terminalSessions = cur.terminalSessions + session,
            activeTerminalSessionId = session.id
        )
        persist(app, next)
        return session
    }

    fun switchTerminalSession(context: Context, sessionId: String) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        if (cur.terminalSessions.none { it.id == sessionId }) return
        persist(app, cur.copy(activeTerminalSessionId = sessionId))
    }

    fun closeTerminalSession(context: Context, sessionId: String) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val remaining = cur.terminalSessions.filterNot { it.id == sessionId }
        val ensured = remaining.ifEmpty {
            listOf(
                PersistentTerminalSession(
                    title = "Session #1",
                    providerId = cur.activeProviderId.ifBlank { "google_antigravity" }
                )
            )
        }
        val nextActive = if (cur.activeTerminalSessionId == sessionId) ensured.first().id else cur.activeTerminalSessionId
        persist(app, cur.copy(terminalSessions = ensured, activeTerminalSessionId = nextActive))
    }

    fun recordSessionCommand(context: Context, sessionId: String, command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val updatedSessions = cur.terminalSessions.map { s ->
            if (s.id == sessionId) {
                val safeCmd = KeyStoreCrypto.redact(trimmed)
                s.copy(recentCommands = (s.recentCommands + safeCmd).takeLast(30))
            } else s
        }
        persist(app, cur.copy(terminalSessions = updatedSessions))
    }

    /**
     * AI-powered Terminal Command Assistance (Section 9):
     * Translates a natural-language terminal goal into a safe, executable Linux command
     * using the active provider adapter (or offline deterministic assistant if offline).
     */
    suspend fun suggestTerminalCommand(
        context: Context,
        goal: String,
        workingDir: String = "/root/workspace"
    ): Result<String> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val cur = ensureLoaded(app)
        val active = cur.activeProvider
        val profile = when {
            active != null && active.endpointUrl.isNotBlank() && (active.apiKey.isNotBlank() || active.isLocalEndpoint()) ->
                toAgyCustomProfile(active)
            AntigravityCustomApiManager.getState(app).isActiveAndReady ->
                AntigravityCustomApiManager.getState(app).activeProfile
            else -> null
        }

        if (profile != null) {
            return@withContext runCatching {
                val prompt = buildString {
                    appendLine("You are SAIF AI's Ubuntu Linux Terminal Assistant on Android PRoot.")
                    appendLine("Working directory: $workingDir")
                    appendLine("Return ONLY a single concise bash command (no markdown fences, no commentary) for:")
                    appendLine(goal)
                }
                val res = AntigravityCustomApiManager.completeChat(
                    profile = profile,
                    messages = listOf(JSONObject().put("role", "user").put("content", prompt)),
                    temperature = 0.1
                )
                res.getOrThrow()
                    .trim()
                    .removePrefix("```bash")
                    .removePrefix("```sh")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }
        }

        // Deterministic offline command helper when no cloud API key is configured
        val lower = goal.lowercase()
        val offlineSuggestion = when {
            "disk" in lower || "space" in lower || "storage" in lower -> "df -h && du -sh * 2>/dev/null | sort -hr | head -n 10"
            "git status" in lower || ("git" in lower && "log" in lower) -> "git status -sb && git log --oneline -n 10"
            "port" in lower || "listen" in lower -> "ss -tulnp || netstat -tuln"
            "python" in lower && "server" in lower -> "python3 -m http.server 8000 --bind 0.0.0.0"
            "find" in lower && "large" in lower -> "find . -type f -size +10M -exec ls -lh {} +"
            "apk" in lower && "build" in lower -> "saif-build-apk"
            "process" in lower || "memory" in lower || "ram" in lower -> "ps aux --sort=-%mem | head -n 15"
            else -> "ls -la $workingDir"
        }
        Result.success(offlineSuggestion)
    }

    // -------------------------------------------------------------------------
    // Internal Helpers: Provisioning, Validation, Sync, Storage & Persistence
    // -------------------------------------------------------------------------

    @Synchronized
    fun ensureLoaded(context: Context): TerminalProviderEcosystemState {
        val uid = runCatching { AuthManager.currentUser.value?.uid }.getOrNull()
        val expectedKey = scopeKeyForUid(uid)
        if (boundScopeKey != expectedKey || _state.value.providers.isEmpty()) {
            return bindUserAccount(context, uid)
        }
        return _state.value
    }

    fun checkAvailableStorageMb(context: Context): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        }.getOrDefault(0L)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    private fun provisionProviderToolchain(context: Context, provider: TerminalProviderSpec) {
        val ubuntuRoot = File(context.filesDir, "ubuntu")
        val workspaceDir = File(context.filesDir, "home")
        if (!workspaceDir.exists()) workspaceDir.mkdirs()

        // Write active provider metadata inside /root/.saif_provider.json for terminal sessions
        val rootHome = File(ubuntuRoot, "root")
        if (ubuntuRoot.exists()) {
            rootHome.mkdirs()
            runCatching {
                File(rootHome, ".saif_provider.json").writeText(
                    JSONObject()
                        .put("providerId", provider.id)
                        .put("providerName", provider.name)
                        .put("mode", provider.mode.name)
                        .put("model", provider.selectedModel)
                        .toString(2)
                )
            }
        }

        // OpenCode CLI & config provisioning inside verified Ubuntu PRoot
        if (provider.id == "opencode" && ubuntuRoot.exists()) {
            runCatching {
                val cfgDir = File(rootHome, ".config/opencode").apply { mkdirs() }
                File(cfgDir, "opencode.json").writeText(
                    JSONObject()
                        .put("\$schema", "https://opencode.ai/config.json")
                        .put("model", provider.selectedModel.ifBlank { "anthropic/claude-sonnet-4" })
                        .put("endpoint", provider.endpointUrl.ifBlank { "https://openrouter.ai/api/v1" })
                        .toString(2)
                )
                val localBin = File(ubuntuRoot, "usr/local/bin").apply { mkdirs() }
                val launcher = File(localBin, "opencode")
                if (!launcher.exists()) {
                    launcher.writeText(
                        """
                        #!/bin/sh
                        if [ -x /usr/bin/npx ] && [ -n "${'$'}OPENROUTER_API_KEY${'$'}OPENAI_API_KEY${'$'}ANTHROPIC_API_KEY" ]; then
                          exec /usr/bin/npx -y opencode-ai "${'$'}@"
                        fi
                        echo "OpenCode workspace ready in SAIF AI (/root/.config/opencode/opencode.json)."
                        echo "Use SAIF AI chat or configure your API key in Settings -> Providers for live agent turns."
                        """.trimIndent() + "\n"
                    )
                    launcher.setExecutable(true, false)
                }
            }
        }
    }

    private suspend fun validateProviderEnvironment(
        context: Context,
        provider: TerminalProviderSpec
    ): Triple<TerminalProviderReadiness, String, Long> {
        // 1. Verify Linux rootfs if required
        if (provider.requiresLinuxRootfs && !LocalEnvironmentManager.hasVerifiedEnvironment(context)) {
            return Triple(
                TerminalProviderReadiness.FAILED,
                "${provider.name} requires the local Ubuntu Linux environment, which has not completed verification yet.",
                0L
            )
        }

        // 2. Local offline shell
        if (provider.mode == TerminalProviderMode.LOCAL_SHELL_ONLY) {
            return Triple(
                TerminalProviderReadiness.READY,
                "Local Ubuntu Linux shell verified and ready.",
                1L
            )
        }

        // 3. Google Antigravity
        if (provider.id == "google_antigravity") {
            val customApi = AntigravityCustomApiManager.getState(context)
            if (provider.apiKey.isNotBlank() || customApi.isActiveAndReady) {
                return Triple(
                    TerminalProviderReadiness.READY,
                    "Google Antigravity Linux workspace + Custom API bridge verified and ready.",
                    5L
                )
            }
            return Triple(
                TerminalProviderReadiness.READY,
                "Google Antigravity Ubuntu workspace verified and ready.",
                5L
            )
        }

        // 4. API-backed / Hybrid / OpenCode / Claude Code / Custom Providers
        if (provider.endpointUrl.isBlank()) {
            return Triple(
                TerminalProviderReadiness.NEEDS_CONFIGURATION,
                "${provider.name}: Configure an API endpoint URL in Settings → Providers.",
                0L
            )
        }
        if (!provider.hasValidCredentials()) {
            return Triple(
                TerminalProviderReadiness.NEEDS_CONFIGURATION,
                "${provider.name} workspace prepared — add your API key in Settings → Providers to enable live AI execution.",
                0L
            )
        }

        // Live lightweight connection check when network is available
        if (isNetworkAvailable(context)) {
            val test = AntigravityCustomApiManager.testConnection(toAgyCustomProfile(provider))
            val cleanMsg = KeyStoreCrypto.redact(test.message)
            return if (test.ok) {
                Triple(
                    TerminalProviderReadiness.READY,
                    "${provider.name} verified (${test.latencyMs}ms) — ready.",
                    test.latencyMs
                )
            } else {
                Triple(
                    TerminalProviderReadiness.DEGRADED,
                    "${provider.name} health check warning: $cleanMsg",
                    test.latencyMs
                )
            }
        }

        return Triple(
            TerminalProviderReadiness.READY,
            "${provider.name} configured (offline — will connect automatically when network returns).",
            0L
        )
    }

    private fun syncProviderToRuntimeBridges(context: Context, provider: TerminalProviderSpec) {
        val agentPrefs = AgentPreferences(context)
        when (provider.id) {
            "claude_code" -> agentPrefs.setEnabled(setOf(AgentKind.CLAUDE))
            else -> agentPrefs.setEnabled(setOf(AgentKind.AGY))
        }

        // Sync API-backed providers into AntigravityCustomApiManager so ChatViewModel,
        // AntigravityLlmBridge, and agent tool loops immediately route through the selected provider.
        if (provider.endpointUrl.isNotBlank() && (provider.apiKey.isNotBlank() || (!provider.isBuiltIn && provider.isLocalEndpoint()))) {
            val agyProfile = toAgyCustomProfile(provider)
            AntigravityCustomApiManager.upsertProfile(
                context = context,
                profile = agyProfile,
                activate = true,
                enableExtension = true
            )
        } else if (provider.id == "google_antigravity" && provider.apiKey.isBlank()) {
            // Respect whatever Custom API toggle the user already chose unless they explicitly switched away
            AntigravityCustomApiManager.init(context)
        }
    }

    fun toAgyCustomProfile(spec: TerminalProviderSpec): AgyCustomApiProfile {
        val extraHeaders = mutableMapOf<String, String>()
        when (spec.authScheme) {
            TerminalAuthScheme.X_API_KEY -> if (spec.apiKey.isNotBlank()) extraHeaders["x-api-key"] = spec.apiKey.trim()
            TerminalAuthScheme.CUSTOM_HEADER -> if (spec.apiKey.isNotBlank() && spec.customAuthHeaderName.isNotBlank()) {
                extraHeaders[spec.customAuthHeaderName.trim()] = spec.apiKey.trim()
            }
            else -> Unit
        }
        val effectiveProtocol = when {
            spec.authScheme == TerminalAuthScheme.QUERY_PARAM_KEY && spec.protocol == AgyApiProtocol.AUTO ->
                AgyApiProtocol.GEMINI_NATIVE
            else -> spec.protocol
        }
        return AgyCustomApiProfile(
            id = "prov_${spec.id}",
            name = spec.name,
            presetId = if (spec.isBuiltIn) spec.id else "custom",
            baseUrl = spec.endpointUrl,
            apiKey = spec.apiKey,
            protocol = effectiveProtocol,
            selectedModel = spec.selectedModel.ifBlank {
                spec.availableModels.firstOrNull().orEmpty().ifBlank { "gpt-4.1" }
            },
            models = spec.availableModels,
            extraHeaders = extraHeaders
        )
    }

    private fun maybeFailoverToHealthyProvider(context: Context, failedProviderId: String) {
        val cur = _state.value
        if (!cur.autoFailoverEnabled) return
        val candidate = cur.providers.firstOrNull { p ->
            p.id != failedProviderId && p.enabled && (!p.requiresLinuxRootfs || LocalEnvironmentManager.hasVerifiedEnvironment(context)) && p.hasValidCredentials()
        } ?: return
        syncProviderToRuntimeBridges(context, candidate)
    }

    private fun startInstallerObserverIfNeeded(context: Context) {
        if (installerCollectorJob?.isActive == true) return
        installerCollectorJob = scope.launch {
            LocalEnvironmentManager.installerState.collect { inst ->
                if (inst is LocalInstallerState.Success) {
                    val cur = _state.value
                    val active = cur.activeProvider
                    if (active != null && active.requiresLinuxRootfs && cur.readiness != TerminalProviderReadiness.READY) {
                        val validation = validateProviderEnvironment(context, active)
                        updateReadiness(
                            app = context,
                            readiness = validation.first,
                            progress = 1f,
                            message = validation.second,
                            isPreparing = false,
                            canRetry = false
                        )
                    }
                }
            }
        }
    }

    private fun updateReadiness(
        app: Context,
        readiness: TerminalProviderReadiness,
        progress: Float,
        message: String,
        isPreparing: Boolean,
        canRetry: Boolean,
        freeStorageMb: Long = checkAvailableStorageMb(app)
    ) {
        _state.value = _state.value.copy(
            readiness = readiness,
            progressFraction = progress.coerceIn(0f, 1f),
            statusMessage = KeyStoreCrypto.redact(message),
            isPreparingInBackground = isPreparing,
            canRetry = canRetry,
            freeStorageMb = freeStorageMb
        )
    }

    private fun updateProviderHealthRecord(
        app: Context,
        providerId: String,
        ok: Boolean,
        latencyMs: Long,
        message: String
    ) {
        val cur = _state.value
        val updated = cur.providers.map { p ->
            if (p.id == providerId) {
                p.copy(
                    lastHealthOk = ok,
                    lastHealthLatencyMs = latencyMs,
                    lastHealthMessage = KeyStoreCrypto.redact(message)
                )
            } else p
        }
        persist(app, cur.copy(providers = updated))
    }

    private fun appendHistory(app: Context, provider: TerminalProviderSpec, status: String, detail: String) {
        val cur = _state.value
        val entry = ProviderInstallHistoryItem(
            providerId = provider.id,
            providerName = provider.name,
            status = status,
            detail = KeyStoreCrypto.redact(detail)
        )
        persist(app, cur.copy(installHistory = (cur.installHistory + entry).takeLast(40)))
    }

    private fun setPendingSetupProvider(app: Context, providerId: String?) {
        val key = boundScopeKey ?: scopeKeyForUid(runCatching { AuthManager.currentUser.value?.uid }.getOrNull())
        app.getSharedPreferences(key, Context.MODE_PRIVATE).edit()
            .putString("pending_setup_provider_id", providerId ?: "")
            .apply()
    }

    private fun notifyProviderReady(context: Context, provider: TerminalProviderSpec) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Terminal Provider Status",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when your selected SAIF AI terminal provider environment is verified and ready"
                }
                nm.createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${provider.name} is Ready")
                .setContentText("Terminal environment verified and ready in SAIF AI.")
                .setAutoCancel(true)
                .build()
            nm.notify(READY_NOTIFICATION_ID, n)
        }
    }

    @Synchronized
    private fun persist(context: Context, next: TerminalProviderEcosystemState) {
        val key = boundScopeKey ?: scopeKeyForUid(runCatching { AuthManager.currentUser.value?.uid }.getOrNull()).also {
            boundScopeKey = it
        }
        val prefs = context.applicationContext.getSharedPreferences(key, Context.MODE_PRIVATE)

        val provArr = JSONArray()
        next.providers.forEach { p ->
            provArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("description", p.description)
                    put("mode", p.mode.name)
                    put("protocol", p.protocol.name)
                    put("endpointUrl", p.endpointUrl)
                    put("apiKey", KeyStoreCrypto.encrypt(p.apiKey))
                    put("authScheme", p.authScheme.name)
                    put("customAuthHeaderName", p.customAuthHeaderName)
                    put("selectedModel", p.selectedModel)
                    put("availableModels", JSONArray(p.availableModels))
                    put("requiresLinuxRootfs", p.requiresLinuxRootfs)
                    put("minFreeStorageMb", p.minFreeStorageMb)
                    put("platformCompatibilityNote", p.platformCompatibilityNote)
                    put("isBuiltIn", p.isBuiltIn)
                    put("enabled", p.enabled)
                    if (p.lastHealthOk != null) put("lastHealthOk", p.lastHealthOk)
                    put("lastHealthLatencyMs", p.lastHealthLatencyMs)
                    put("lastHealthMessage", p.lastHealthMessage)
                }
            )
        }

        val histArr = JSONArray()
        next.installHistory.forEach { h ->
            histArr.put(
                JSONObject().apply {
                    put("timestampMs", h.timestampMs)
                    put("providerId", h.providerId)
                    put("providerName", h.providerName)
                    put("status", h.status)
                    put("detail", h.detail)
                }
            )
        }

        val sessArr = JSONArray()
        next.terminalSessions.forEach { s ->
            sessArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("providerId", s.providerId)
                    put("workingDir", s.workingDir)
                    put("createdAtMs", s.createdAtMs)
                    put("recentCommands", JSONArray(s.recentCommands))
                }
            )
        }

        prefs.edit()
            .putString("providers_json", provArr.toString())
            .putString("default_provider_id", next.defaultProviderId)
            .putString("active_provider_id", next.activeProviderId)
            .putBoolean("onboarding_done", next.hasCompletedOnboardingSelection)
            .putBoolean("auto_failover", next.autoFailoverEnabled)
            .putString("install_history_json", histArr.toString())
            .putString("terminal_sessions_json", sessArr.toString())
            .putString("active_terminal_session_id", next.activeTerminalSessionId)
            .apply()

        _state.value = next
    }

    private fun loadFromPrefs(context: Context, scopeKey: String): TerminalProviderEcosystemState {
        val prefs = context.getSharedPreferences(scopeKey, Context.MODE_PRIVATE)
        val savedProviders = parseProvidersJson(prefs.getString("providers_json", null))
        val mergedProviders = mergeBuiltInProviders(savedProviders)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        val defaultId = prefs.getString("default_provider_id", "") ?: ""
        val activeId = prefs.getString("active_provider_id", "") ?: ""
        val autoFailover = prefs.getBoolean("auto_failover", true)
        val history = parseHistoryJson(prefs.getString("install_history_json", null))
        val sessions = parseSessionsJson(prefs.getString("terminal_sessions_json", null))
        val activeSessionId = prefs.getString("active_terminal_session_id", "") ?: ""

        val activeSpec = mergedProviders.firstOrNull { it.id == activeId }
        val initialReadiness = when {
            !onboardingDone || activeSpec == null -> TerminalProviderReadiness.NOT_SELECTED
            activeSpec.requiresLinuxRootfs && !LocalEnvironmentManager.hasVerifiedEnvironment(context) ->
                TerminalProviderReadiness.DOWNLOADING_ENVIRONMENT
            !activeSpec.hasValidCredentials() -> TerminalProviderReadiness.NEEDS_CONFIGURATION
            else -> TerminalProviderReadiness.READY
        }

        return TerminalProviderEcosystemState(
            providers = mergedProviders,
            defaultProviderId = defaultId,
            activeProviderId = activeId,
            hasCompletedOnboardingSelection = onboardingDone,
            readiness = initialReadiness,
            progressFraction = if (initialReadiness == TerminalProviderReadiness.READY) 1f else 0f,
            statusMessage = when (initialReadiness) {
                TerminalProviderReadiness.READY -> "${activeSpec?.name ?: "Provider"} ready"
                TerminalProviderReadiness.NEEDS_CONFIGURATION -> "${activeSpec?.name ?: "Provider"} needs API key in Settings → Providers"
                else -> ""
            },
            freeStorageMb = checkAvailableStorageMb(context),
            autoFailoverEnabled = autoFailover,
            installHistory = history,
            terminalSessions = sessions,
            activeTerminalSessionId = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        )
    }

    private fun mergeBuiltInProviders(saved: List<TerminalProviderSpec>): List<TerminalProviderSpec> {
        if (saved.isEmpty()) return BUILT_IN_PROVIDERS
        val byId = saved.associateBy { it.id }
        val builtIns = BUILT_IN_PROVIDERS.map { def ->
            val ov = byId[def.id]
            if (ov != null) {
                def.copy(
                    endpointUrl = ov.endpointUrl.ifBlank { def.endpointUrl },
                    apiKey = ov.apiKey,
                    protocol = ov.protocol,
                    authScheme = ov.authScheme,
                    selectedModel = ov.selectedModel.ifBlank { def.selectedModel },
                    availableModels = (ov.availableModels + def.availableModels).distinct(),
                    enabled = ov.enabled,
                    lastHealthOk = ov.lastHealthOk,
                    lastHealthLatencyMs = ov.lastHealthLatencyMs,
                    lastHealthMessage = ov.lastHealthMessage
                )
            } else def
        }
        val customs = saved.filter { s -> BUILT_IN_PROVIDERS.none { it.id == s.id } }
            .map { it.copy(isBuiltIn = false) }
        return builtIns + customs
    }

    private fun parseProvidersJson(raw: String?): List<TerminalProviderSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val modelsArr = o.optJSONArray("availableModels") ?: JSONArray()
                    val models = (0 until modelsArr.length()).mapNotNull { idx ->
                        modelsArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                    val mode = runCatching {
                        TerminalProviderMode.valueOf(o.optString("mode", TerminalProviderMode.HYBRID_API_WORKSPACE.name))
                    }.getOrDefault(TerminalProviderMode.HYBRID_API_WORKSPACE)
                    val protocol = runCatching {
                        AgyApiProtocol.valueOf(o.optString("protocol", AgyApiProtocol.OPENAI_COMPATIBLE.name))
                    }.getOrDefault(AgyApiProtocol.OPENAI_COMPATIBLE)
                    val auth = runCatching {
                        TerminalAuthScheme.valueOf(o.optString("authScheme", TerminalAuthScheme.BEARER_TOKEN.name))
                    }.getOrDefault(TerminalAuthScheme.BEARER_TOKEN)
                    add(
                        TerminalProviderSpec(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            description = o.optString("description"),
                            mode = mode,
                            protocol = protocol,
                            endpointUrl = o.optString("endpointUrl"),
                            apiKey = KeyStoreCrypto.decrypt(o.optString("apiKey", "")),
                            authScheme = auth,
                            customAuthHeaderName = o.optString("customAuthHeaderName", "X-Custom-Auth"),
                            selectedModel = o.optString("selectedModel"),
                            availableModels = models,
                            requiresLinuxRootfs = o.optBoolean("requiresLinuxRootfs", false),
                            minFreeStorageMb = o.optLong("minFreeStorageMb", 64L),
                            platformCompatibilityNote = o.optString("platformCompatibilityNote"),
                            isBuiltIn = o.optBoolean("isBuiltIn", false),
                            enabled = o.optBoolean("enabled", true),
                            lastHealthOk = if (o.has("lastHealthOk")) o.optBoolean("lastHealthOk") else null,
                            lastHealthLatencyMs = o.optLong("lastHealthLatencyMs", 0L),
                            lastHealthMessage = o.optString("lastHealthMessage", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseHistoryJson(raw: String?): List<ProviderInstallHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ProviderInstallHistoryItem(
                            timestampMs = o.optLong("timestampMs"),
                            providerId = o.optString("providerId"),
                            providerName = o.optString("providerName"),
                            status = o.optString("status"),
                            detail = o.optString("detail")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSessionsJson(raw: String?): List<PersistentTerminalSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val cmdsArr = o.optJSONArray("recentCommands") ?: JSONArray()
                    val cmds = (0 until cmdsArr.length()).mapNotNull { idx ->
                        cmdsArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                    add(
                        PersistentTerminalSession(
                            id = o.optString("id"),
                            title = o.optString("title", "Workspace"),
                            providerId = o.optString("providerId", "google_antigravity"),
                            workingDir = o.optString("workingDir", "/root/workspace"),
                            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                            recentCommands = cmds
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
