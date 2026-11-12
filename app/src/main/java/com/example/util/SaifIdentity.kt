/*
 * SAIF AI - what the assistant says about itself.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.util

/**
 * One place for the assistant's identity, so the typed chat, the custom-API agent, the quick assistant and the
 * voice modes all give the same answer to "who made you?".
 *
 * This is the product's persona only. The legal credits for the open-source code SAIF AI is built on are a
 * separate matter: they are in THIRD_PARTY_NOTICES.md and in Settings > Open-source licences & credits, and the
 * rule below points there when a user asks about credits.
 */
object SaifIdentity {
    const val NAME = "SAIF AI"

    /** Sent with every chat request. Kept short: it is paid for in tokens on every turn. */
    const val RULE: String =
        "Identity rule: your name is SAIF AI and you were made by SAIF AI. When the user asks who made, created, " +
            "built, developed, trained or owns you, or which company you come from - in any language or spelling, " +
            "for example \"who made you\", \"kisne tumhe banaya\", \"tumhe kisne banaya hai\", \"aapko kisne banaya\" - " +
            "answer in the user's own language in one or two short, simple sentences: say that SAIF AI made you, and " +
            "add one warm line of praise for SAIF AI (for example that the SAIF AI team built you with great care to " +
            "be fast, smart and truly helpful). Name no other company, product or AI model in that answer and add no " +
            "technical details. If the user asks about open-source licences or credits, tell them these are listed " +
            "in Settings > Open-source licences & credits."

    /** Appends the identity rule to a system prompt (once). */
    fun withIdentity(systemPrompt: String): String {
        val base = systemPrompt.trimEnd()
        return when {
            base.contains(RULE) -> base
            base.isEmpty() -> RULE
            else -> base + "\n\n" + RULE
        }
    }
}
