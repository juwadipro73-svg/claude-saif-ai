/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.feature.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% Original SAIF AI Workspace Manager.
 * Implements docs/spec/workspace.md.
 */
data class SaifWorkspaceNode(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val children: List<SaifWorkspaceNode> = emptyList()
)

data class SaifGitStatus(
    val activeBranch: String = "main",
    val modifiedFiles: List<String> = emptyList(),
    val untrackedFiles: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList()
)

class SaifWorkspaceManager(val rootDir: File) {

    private val _treeState = MutableStateFlow<SaifWorkspaceNode?>(null)
    val treeState: StateFlow<SaifWorkspaceNode?> = _treeState.asStateFlow()

    private val _gitStatus = MutableStateFlow(SaifGitStatus())
    val gitStatus: StateFlow<SaifGitStatus> = _gitStatus.asStateFlow()

    suspend fun refreshTree() = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) rootDir.mkdirs()
        _treeState.value = buildNode(rootDir)
    }

    private fun buildNode(f: File): SaifWorkspaceNode {
        val children = if (f.isDirectory) {
            f.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { !it.isDirectory }?.map { buildNode(it) } ?: emptyList()
        } else emptyList()
        return SaifWorkspaceNode(
            file = f,
            name = f.name,
            isDirectory = f.isDirectory,
            sizeBytes = if (f.isFile) f.length() else 0L,
            children = children
        )
    }

    suspend fun createFile(parentDir: File, fileName: String, initialContent: String = ""): File = withContext(Dispatchers.IO) {
        val target = File(parentDir, fileName)
        // Guard against path traversal
        check(target.canonicalPath.startsWith(rootDir.canonicalPath)) { "Path traversal escape rejected" }
        target.writeText(initialContent)
        refreshTree()
        target
    }

    suspend fun deleteNode(target: File): Boolean = withContext(Dispatchers.IO) {
        check(target.canonicalPath.startsWith(rootDir.canonicalPath)) { "Path traversal escape rejected" }
        val ok = target.deleteRecursively()
        refreshTree()
        ok
    }
}
