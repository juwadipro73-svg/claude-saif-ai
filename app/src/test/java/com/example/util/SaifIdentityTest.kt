package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaifIdentityTest {
    @Test fun ruleNamesSaifAiAsTheMakerAndAsksForPraise() {
        val rule = SaifIdentity.RULE
        assertTrue(rule.contains("you were made by SAIF AI"))
        assertTrue(rule.contains("praise for SAIF AI"))
        assertTrue(rule.contains("one or two short, simple sentences"))
        assertTrue(rule.contains("Name no other company, product or AI model"))
    }

    @Test fun ruleCoversHinglishWaysOfAsking() {
        listOf("who made you", "kisne tumhe banaya", "tumhe kisne banaya hai", "aapko kisne banaya").forEach { phrase ->
            assertTrue(phrase, SaifIdentity.RULE.contains(phrase))
        }
    }

    @Test fun creditsQuestionsPointToTheLicenceScreen() {
        assertTrue(SaifIdentity.RULE.contains("Settings > Open-source licences & credits"))
    }

    @Test fun ruleIsAddedToAPromptExactlyOnce() {
        val once = SaifIdentity.withIdentity("You are SAIF AI, a voice assistant.\n")
        assertEquals("You are SAIF AI, a voice assistant.\n\n" + SaifIdentity.RULE, once)
        assertEquals(once, SaifIdentity.withIdentity(once))
        assertEquals(SaifIdentity.RULE, SaifIdentity.withIdentity("  "))
    }
}
