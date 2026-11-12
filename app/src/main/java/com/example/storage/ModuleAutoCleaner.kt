/*
 * SAIF AI - storage cleanup for the Linux workspace.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.storage

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class ModuleCleanupStatus(
    val enabled: Boolean = true,
    val idleMinutes: Int = ModuleCleanupRules.DEFAULT_IDLE_MINUTES,
    val lastRunMs: Long = 0L,
    val lastFreedBytes: Long = 0L,
    val totalFreedBytes: Long = 0L,
    val running: Boolean = false
)

/**
 * Frees phone storage after a project is finished.
 *
 * Work in the Linux environment downloads a lot (node_modules, Python environments, Gradle and npm caches, .deb
 * files), which makes the app grow by gigabytes. When no agent turn has run for the chosen idle time, a background
 * job removes those re-downloadable folders. Project files are never removed: see [WorkspaceModuleCleaner] for the
 * exact rules. Returning to a project later only costs a fresh `npm install` / build, which the agent is told to do
 * ([ModuleCleanupRules.AGENT_RULE]).
 */
object ModuleAutoCleaner {
    private const val PREFS = "saif_module_cleanup"
    private const val WORK_NAME = "saif_module_cleanup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_IDLE_MINUTES = "idle_minutes"
    private const val KEY_LAST_RUN = "last_run_ms"
    private const val KEY_LAST_FREED = "last_freed_bytes"
    private const val KEY_TOTAL_FREED = "total_freed_bytes"
    internal const val KEY_ATTEMPT = "attempt"

    /** A skipped (busy or just-changed) project is looked at again this many times before the job rests. */
    internal const val MAX_ATTEMPTS = 8

    private val activeWork = AtomicInteger(0)
    private val runLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(ModuleCleanupStatus())
    val status: StateFlow<ModuleCleanupStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved settings and the last result. */
    fun load(context: Context): ModuleCleanupStatus {
        val saved = prefs(context)
        val loaded = ModuleCleanupStatus(
            enabled = saved.getBoolean(KEY_ENABLED, true),
            idleMinutes = saved.getInt(KEY_IDLE_MINUTES, ModuleCleanupRules.DEFAULT_IDLE_MINUTES)
                .takeIf { it in ModuleCleanupRules.IDLE_CHOICES_MINUTES } ?: ModuleCleanupRules.DEFAULT_IDLE_MINUTES,
            lastRunMs = saved.getLong(KEY_LAST_RUN, 0L),
            lastFreedBytes = saved.getLong(KEY_LAST_FREED, 0L),
            totalFreedBytes = saved.getLong(KEY_TOTAL_FREED, 0L),
            running = _status.value.running
        )
        _status.value = loaded
        return loaded
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        load(context)
        if (enabled) schedule(context) else cancel(context)
    }

    fun setIdleMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_IDLE_MINUTES, minutes).apply()
        load(context)
        schedule(context)
    }

    /** An agent turn started: nothing may be removed under it. */
    fun onWorkStarted(context: Context) {
        activeWork.incrementAndGet()
        cancel(context)
    }

    /** An agent turn ended: when nothing else happens for the idle time, the modules are cleaned. */
    fun onWorkFinished(context: Context) {
        if (activeWork.decrementAndGet() < 0) activeWork.set(0)
        schedule(context)
    }

    /** The user left the app. A clean-up that is already waiting keeps its time. */
    fun onAppBackgrounded(context: Context) {
        if (!isWorking()) schedule(context, ExistingWorkPolicy.KEEP)
    }

    fun isWorking(): Boolean = activeWork.get() > 0

    internal fun schedule(
        context: Context,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        attempt: Int = 0
    ) {
        val current = load(context)
        if (!current.enabled) return
        try {
            val request = OneTimeWorkRequestBuilder<ModuleCleanupWorker>()
                .setInitialDelay(current.idleMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(Data.Builder().putInt(KEY_ATTEMPT, attempt).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, policy, request)
        } catch (error: Exception) {
            android.util.Log.w("ModuleAutoCleaner", "Could not schedule the storage clean-up", error)
        }
    }

    private fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        } catch (error: Exception) {
            android.util.Log.w("ModuleAutoCleaner", "Could not cancel the storage clean-up", error)
        }
    }

    /**
     * Removes the re-downloadable folders now.
     *
     * @param manual true when the user pressed the button: the idle time is ignored. Projects with a running
     * program and caches of a running package manager are skipped in both cases.
     */
    suspend fun cleanNow(context: Context, manual: Boolean): CleanupResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runLock.withLock {
            val settings = load(app)
            _status.value = settings.copy(running = true)
            try {
                val filesDir = app.filesDir
                val workspace = File(filesDir, "home")        // /root/workspace inside Linux
                val linuxRoot = File(filesDir, "ubuntu")
                val linuxHome = File(linuxRoot, "root")       // /root inside Linux
                val cleaner = WorkspaceModuleCleaner(
                    projectParents = listOf(workspace, linuxHome),
                    homeDirs = listOf(linuxHome, workspace),
                    linuxRoot = linuxRoot,
                    tempDir = File(filesDir, "tmp")           // /tmp inside Linux
                )
                // An automatic run ends as soon as the user starts a new chat turn; the turn's end reschedules it.
                val result = cleaner.clean(
                    CleanupOptions(minIdleMs = settings.idleMinutes * 60_000L, ignoreIdle = manual),
                    shouldStop = { !manual && isWorking() }
                )
                record(app, result)
                result
            } finally {
                _status.value = load(app).copy(running = false)
            }
        }
    }

    /** The settings button: cleans in the background and tells the user what was freed. */
    fun startManualClean(context: Context) {
        val app = context.applicationContext
        if (_status.value.running) return
        scope.launch {
            val message = try {
                summary(cleanNow(app, manual = true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "Clean-up failed: ${error.message ?: "unknown error"}"
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun record(app: Context, result: CleanupResult) {
        val saved = prefs(app)
        saved.edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putLong(KEY_LAST_FREED, result.freedBytes)
            .putLong(KEY_TOTAL_FREED, saved.getLong(KEY_TOTAL_FREED, 0L) + result.freedBytes)
            .apply()
        if (result.removed.none { it.item.kind != WorkspaceModuleCleaner.KIND_LEFTOVER }) return
        // The agent reads this file inside Linux to learn what to reinstall.
        try {
            val log = File(app.filesDir, "home/.config/saif-cleanup.md")
            log.parentFile?.mkdirs()
            val previous = if (log.isFile) log.readText() else ""
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            log.writeText(ModuleCleanupRules.renderLog(result, app.filesDir.absolutePath, stamp, previous))
        } catch (error: Exception) {
            android.util.Log.w("ModuleAutoCleaner", "Could not write the clean-up log", error)
        }
    }

    /** One line for the toast after a manual clean. */
    fun summary(result: CleanupResult): String {
        val folders = result.removed.size
        val base = if (folders == 0) "Nothing to clean right now."
        else "Freed ${WorkspaceModuleCleaner.formatBytes(result.freedBytes)} from $folders folder${if (folders == 1) "" else "s"}. Your project files were not touched."
        val inUse = result.skipped.size
        val skippedNote = if (inUse > 0) " $inUse project${if (inUse == 1) " is" else "s are"} in use and ${if (inUse == 1) "was" else "were"} skipped." else ""
        val cacheNote = result.sharedCachesSkippedReason?.let { " Shared caches were skipped: $it." }.orEmpty()
        return base + skippedNote + cacheNote
    }

    /** One line under the settings button. */
    fun describe(status: ModuleCleanupStatus): String = when {
        status.running -> "Removing re-downloadable folders…"
        status.lastRunMs == 0L -> "Nothing cleaned yet"
        else -> "Last clean freed ${WorkspaceModuleCleaner.formatBytes(status.lastFreedBytes)} · " +
            "${WorkspaceModuleCleaner.formatBytes(status.totalFreedBytes)} in total"
    }
}

/** Runs once the idle time has passed. WorkManager keeps the job across app restarts. */
class ModuleCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!ModuleAutoCleaner.load(app).enabled) return Result.success()
        val attempt = inputData.getInt(ModuleAutoCleaner.KEY_ATTEMPT, 0)
        val lookAgain = if (ModuleAutoCleaner.isWorking()) {
            true // an agent turn is running: its end schedules the next clean-up anyway
        } else {
            val result = try {
                ModuleAutoCleaner.cleanNow(app, manual = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("ModuleCleanupWorker", "Storage clean-up failed", error)
                null
            }
            // Projects that were busy or changed a moment ago are cleaned on a later pass.
            result != null && (result.stopped || result.skipped.isNotEmpty() || result.sharedCachesSkippedReason != null)
        }
        if (lookAgain && attempt < ModuleAutoCleaner.MAX_ATTEMPTS) {
            ModuleAutoCleaner.schedule(app, ExistingWorkPolicy.APPEND_OR_REPLACE, attempt + 1)
        }
        return Result.success()
    }
}
