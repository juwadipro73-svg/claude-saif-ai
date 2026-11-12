/*
 * SAIF AI - which API keys can do speech-to-text.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.stt

/**
 * Picks, from the keys the user saved under "Use my own API key", the ones that can also turn speech into text.
 *
 * The app has no on-device speech model. Only OpenAI, Groq and Gemini offer a speech-to-text endpoint it can
 * call, so only their keys are returned. Keys of every other provider (OpenRouter, Anthropic, DeepSeek, xAI, a
 * custom server ...) are left out: sending audio with them could only fail.
 */
object SttKeys {
    /**
     * @param keysByEndpoint the provider's base URL and the API key of each saved profile
     * @return the usable keys, trimmed and without duplicates, in the order given
     */
    fun speechCapable(keysByEndpoint: List<Pair<String, String>>): List<String> =
        keysByEndpoint.mapNotNull { (baseUrl, rawKey) ->
            val key = rawKey.trim()
            val url = baseUrl.trim().lowercase()
            when {
                key.isEmpty() -> null
                // Groq and Gemini keys are recognisable by themselves.
                key.startsWith("gsk_") -> key
                key.startsWith("AIza") || key.startsWith("AQ.") -> key
                // Many providers hand out "sk-..." keys; only the one for OpenAI's own server can transcribe.
                key.startsWith("sk-") && !key.startsWith("sk-or-") && !key.startsWith("sk-ant-") &&
                    url.contains("api.openai.com") -> key
                else -> null
            }
        }.distinct()
}
