/*
 * SAIF AI - live voice: what the assistant is told before a spoken conversation.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.live

import com.example.util.SaifIdentity

/**
 * Builds the system prompt of a live voice conversation.
 *
 * The reply is read aloud by a text-to-speech voice, so the prompt asks for short spoken sentences and forbids
 * everything that sounds wrong when spoken (lists, symbols, code). Phone tasks and coding requests are handled
 * before the model is asked, so the prompt does not describe tools.
 */
object LivePrompt {
    private const val MAX_MEMORY_CHARS = 2400

    /**
     * @param memory what the app already knows about the user (may be empty)
     * @param autoLanguage true: answer in the language the user speaks; false: keep the language of the conversation
     */
    fun build(memory: String, autoLanguage: Boolean): String = buildString {
        append("You are SAIF AI, the voice assistant of the SAIF AI app. ")
        append("You are in a live voice conversation with the user on their Android phone. ")
        append("Every word you write is spoken aloud by a speech voice, so write only what should be heard.\n\n")

        append("How to answer:\n")
        append("- Keep it short: one to three spoken sentences, unless the user asks for more detail.\n")
        append("- Use plain sentences. No lists, no headings, no markdown, no emojis, no code, no web addresses.\n")
        append("- Say numbers, dates and units the way a person would say them.\n")
        append("- Sound warm and natural, like a helpful friend. Do not repeat the question back.\n")
        append("- If you did not understand, ask one short question instead of guessing.\n")
        append("- Never say that you opened, sent, called, played or changed something on the phone ")
        append("unless you are told in this conversation that it really happened.\n\n")

        append("Language:\n")
        if (autoLanguage) {
            append("- Answer in the language and style the user just spoke: Hindi, Hinglish, English or any other. ")
            append("If they mix Hindi and English, mix them the same way.\n\n")
        } else {
            append("- Stay in the language this conversation is already using. ")
            append("Change language only when the user asks you to.\n\n")
        }

        val known = memory.trim()
        if (known.isNotEmpty()) {
            append(known.take(MAX_MEMORY_CHARS)).append("\n\n")
        }

        append(SaifIdentity.RULE)
    }
}
