/*
 * SAIF AI - storage cleanup for the Linux workspace.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.storage

/** Text shared between the cleaner, the settings screen and the agent prompts. */
object ModuleCleanupRules {
    /** Where the agent (inside Linux) can read what was removed and how to bring it back. */
    const val LOG_PATH_IN_LINUX = "/root/workspace/.config/saif-cleanup.md"

    /** Idle times offered in Settings, in minutes. */
    val IDLE_CHOICES_MINUTES: List<Int> = listOf(10, 30, 120, 720)
    const val DEFAULT_IDLE_MINUTES = 30

    fun idleLabel(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 && minutes / 60 == 1 -> "1 hour"
        minutes % 60 == 0 -> "${minutes / 60} hours"
        else -> "$minutes min"
    }

    fun idleMinutesFromLabel(label: String): Int =
        IDLE_CHOICES_MINUTES.firstOrNull { idleLabel(it) == label } ?: DEFAULT_IDLE_MINUTES

    /** Sent with every chat request, so a missing dependency folder is reinstalled instead of reported as a fault. */
    const val AGENT_RULE: String =
        "Storage rule: SAIF AI frees phone storage by itself. When a project has not been touched for a while, its " +
            "re-downloadable folders (node_modules, Python virtual environments, Gradle, npm, pip and apt caches, " +
            "build intermediates) are deleted. Project source files, .git and finished build outputs are never " +
            "deleted. If a project you return to is missing its dependencies, reinstall them first (for example " +
            "`npm install`, `pip install -r requirements.txt`, or simply run the build again) and carry on - this " +
            "is expected, not an error, and needs no question to the user. What was removed and the command that " +
            "restores it is listed in `$LOG_PATH_IN_LINUX`."

    /** "/data/.../files/home/app" -> "/root/workspace/app", so the log shows paths as the agent sees them. */
    fun linuxPath(hostPath: String, filesDir: String): String {
        val base = filesDir.trimEnd('/')
        val workspace = "$base/home"
        val rootfs = "$base/ubuntu"
        val temp = "$base/tmp"
        return when {
            hostPath == workspace -> "/root/workspace"
            hostPath.startsWith("$workspace/") -> "/root/workspace/" + hostPath.removePrefix("$workspace/")
            hostPath == temp -> "/tmp"
            hostPath.startsWith("$temp/") -> "/tmp/" + hostPath.removePrefix("$temp/")
            hostPath == rootfs -> "/"
            hostPath.startsWith("$rootfs/") -> "/" + hostPath.removePrefix("$rootfs/")
            else -> hostPath
        }
    }

    /** The Markdown log the agent reads. Newest entries first; [previous] is the existing file, if any. */
    fun renderLog(result: CleanupResult, filesDir: String, timestamp: String, previous: String = "", maxEntries: Int = 60): String {
        // Leftovers of an interrupted run need no restore command, so the agent is not told about them.
        val newLines = result.removed.filter { it.item.kind != WorkspaceModuleCleaner.KIND_LEFTOVER }.map { removed ->
            val item = removed.item
            "- `${linuxPath(item.path.absolutePath, filesDir)}` (${item.kind}, " +
                "${WorkspaceModuleCleaner.formatBytes(removed.freedBytes)}) removed $timestamp - restore: ${item.restoreCommand}"
        }
        val oldLines = previous.lineSequence().filter { it.startsWith("- `") }.toList()
        val lines = (newLines + oldLines).take(maxEntries)
        return buildString {
            append("# SAIF AI storage cleanup\n\n")
            append("SAIF AI removed these re-downloadable folders to free phone storage. No project source file was deleted.\n")
            append("To continue a project, run the restore command inside the project folder.\n\n")
            lines.forEach { append(it).append('\n') }
        }
    }
}
