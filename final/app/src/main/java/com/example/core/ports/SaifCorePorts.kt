/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.core.ports

import com.example.core.model.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 100% Original SAIF AI Core Service Ports.
 * Authored strictly from docs/spec/ports.md.
 */

interface SaifAgentBackend {
    val messageHistory: StateFlow<List<SaifChatMessage>>
    val isStreaming: StateFlow<Boolean>
    val pendingToolApprovals: StateFlow<List<SaifPendingApproval>>

    suspend fun dispatchTurn(prompt: String, attachments: List<SaifChatAttachment> = emptyList())
    fun authorizeTool(callId: String, scope: SaifApprovalScope)
    fun refuseTool(callId: String, feedbackMessage: String? = null)
    fun abortTurn()
}

interface SaifWorkspaceEnvironment {
    val isReady: StateFlow<Boolean>
    val projectRootDirectory: File

    fun verifyEnvironment(): Boolean
    fun mapVirtualPath(virtualPath: String): File?
    suspend fun runShell(command: String, onLineStream: (String) -> Unit = {}): Int
}

interface SaifAccountBinding {
    val isAuthenticated: StateFlow<Boolean>
    val accountEmail: StateFlow<String?>
    val accountDisplayName: StateFlow<String?>

    fun refreshAuth(): Boolean
}

data class SaifSkillRecord(
    val skillSlug: String,
    val title: String,
    val description: String,
    val promptDirectives: String,
    val sourcePath: String = ""
)

interface SaifSkillStore {
    fun queryAllSkills(): List<SaifSkillRecord>
    fun getSkill(slug: String): SaifSkillRecord?
    fun storeSkill(record: SaifSkillRecord)
    fun removeSkill(slug: String)
}
