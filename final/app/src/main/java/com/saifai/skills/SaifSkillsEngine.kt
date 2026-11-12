/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.skills

import java.nio.charset.StandardCharsets

/**
 * 100% Original Cleanroom Skills Engine for SAIF AI.
 * Handles bundling, validation, and execution of workspace AI skills.
 */
data class SaifSkillDocument(
    val skillName: String,
    val originUrl: String,
    val textContent: String,
    val registrationTimestamp: Long = System.currentTimeMillis()
)

object SaifSkillsEngine {
    const val MAX_SKILL_PAYLOAD_BYTES = 4 * 1024 * 1024 // 4 MB support

    fun composeSkillPackage(
        name: String,
        originUrl: String,
        files: Map<String, String>
    ): String {
        require(files.isNotEmpty()) { "Skills package requires at least one markdown/instruction file." }
        val sb = StringBuilder("# SAIF AI Proprietary Skill: /").append(name)
            .append("
Source: ").append(originUrl)
            .append("

")

        for ((relPath, content) in files) {
            sb.append("
--- BEGIN FILE: ").append(relPath).append(" ---
")
            sb.append(content.trim())
            sb.append("
--- END FILE: ").append(relPath).append(" ---
")
        }

        val bytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_SKILL_PAYLOAD_BYTES) {
            "Skill document exceeds 4MB capacity cap."
        }
        return sb.toString()
    }
}
