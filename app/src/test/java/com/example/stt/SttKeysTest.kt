package com.example.stt

import org.junit.Assert.assertEquals
import org.junit.Test

/** Voice typing uses the keys saved under "Use my own API key"; only some providers can transcribe. */
class SttKeysTest {
    @Test fun openAiGroqAndGeminiKeysCanTranscribe() {
        assertEquals(
            listOf("sk-proj-abc", "gsk_abc", "AIzaSyAbc", "AQ.abc"),
            SttKeys.speechCapable(
                listOf(
                    "https://api.openai.com/v1" to "sk-proj-abc",
                    "https://api.groq.com/openai/v1" to "gsk_abc",
                    "https://generativelanguage.googleapis.com/v1beta" to "AIzaSyAbc",
                    "https://generativelanguage.googleapis.com/v1beta" to "AQ.abc"
                )
            )
        )
    }

    @Test fun keysOfProvidersWithoutSpeechToTextAreLeftOut() {
        assertEquals(
            emptyList<String>(),
            SttKeys.speechCapable(
                listOf(
                    "https://openrouter.ai/api/v1" to "sk-or-v1-abc",
                    "https://api.anthropic.com/v1" to "sk-ant-api03-abc",
                    // An "sk-" key of another company must not be sent to OpenAI.
                    "https://api.deepseek.com/v1" to "sk-deepseek",
                    "https://api.x.ai/v1" to "xai-abc",
                    "http://192.168.1.5:11434/v1" to "ollama",
                    // Even on OpenAI's address, these are not OpenAI keys.
                    "https://api.openai.com/v1" to "sk-or-v1-abc",
                    "https://api.openai.com/v1" to "sk-ant-abc",
                    "https://api.openai.com/v1" to "",
                    "https://api.openai.com/v1" to "   "
                )
            )
        )
    }

    @Test fun keysAreTrimmedAndListedOnce() {
        assertEquals(
            listOf("gsk_abc", "sk-proj-abc"),
            SttKeys.speechCapable(
                listOf(
                    "https://api.groq.com/openai/v1" to "  gsk_abc ",
                    "https://API.OPENAI.COM/v1/" to "sk-proj-abc",
                    "https://api.groq.com/openai/v1" to "gsk_abc"
                )
            )
        )
    }
}
