package com.example.live

import com.example.util.SaifIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of SAIF AI's own live voice engine that need no phone to check. */
class LiveRulesTest {
    @Test fun aSessionNeedsTheMicrophoneFirst() {
        val problem = LiveStartCheck.problem(hasMicPermission = false, hasProviderKey = true, agentConnected = true)!!
        assertEquals(LiveError.CODE_NO_MICROPHONE, problem.errorCode)
        assertEquals("ALLOW MICROPHONE", problem.actionLabel)
    }

    @Test fun aSessionNeedsSomethingThatCanAnswer() {
        val problem = LiveStartCheck.problem(hasMicPermission = true, hasProviderKey = false, agentConnected = false)!!
        assertEquals(LiveError.CODE_NO_AI, problem.errorCode)
        // The user's own key comes first: the message must not send them to an Antigravity sign-in only.
        assertTrue(problem.message.contains("API key"))
    }

    @Test fun aProviderKeyOrAConnectedAgentIsEnough() {
        assertNull(LiveStartCheck.problem(hasMicPermission = true, hasProviderKey = true, agentConnected = false))
        assertNull(LiveStartCheck.problem(hasMicPermission = true, hasProviderKey = false, agentConnected = true))
    }

    @Test fun anUnusedSessionEndsAfterHalfAnHour() {
        val start = 1_000_000L
        assertFalse(LiveSessionRules.isIdleTooLong(start, start + 29 * 60_000L))
        assertTrue(LiveSessionRules.isIdleTooLong(start, start + 30 * 60_000L))
        assertTrue(LiveSessionRules.isIdleTooLong(start, start + 5_000L, limitMs = 5_000L))
        assertFalse(LiveSessionRules.isIdleTooLong(start, start + 4_999L, limitMs = 5_000L))
    }

    @Test fun aSharedScreenPictureIsMadeSmallerButKeepsItsShape() {
        // A tall phone screen: the long side becomes 1024, the short side follows.
        assertEquals(460 to 1024, LiveSessionRules.frameSize(1080, 2400))
        // Landscape.
        assertEquals(1024 to 460, LiveSessionRules.frameSize(2400, 1080))
        // A screen a little over the limit is reduced, a small one is not enlarged.
        assertEquals(576 to 1024, LiveSessionRules.frameSize(720, 1280))
        assertEquals(540 to 960, LiveSessionRules.frameSize(540, 960))
        // Both sides are even and never zero, whatever comes in.
        assertEquals(2 to 2, LiveSessionRules.frameSize(0, 0))
        assertEquals(1024 to 2, LiveSessionRules.frameSize(100_000, 3))
        listOf(1081 to 2399, 1439 to 3199, 601 to 1023, 3 to 5).forEach { (w, h) ->
            val (fw, fh) = LiveSessionRules.frameSize(w, h)
            assertTrue("$w x $h -> $fw x $fh", fw % 2 == 0 && fh % 2 == 0 && fw >= 2 && fh >= 2)
            assertTrue(maxOf(fw, fh) <= LiveSessionRules.SCREEN_FRAME_MAX_SIDE)
        }
    }

    @Test fun theStatusLineSaysWhatTheSessionIsDoing() {
        assertEquals("Ready", LiveSessionRules.statusLine(running = false, sharingScreen = true))
        assertEquals("Voice conversation", LiveSessionRules.statusLine(running = true, sharingScreen = false))
        assertEquals("Helping with your screen", LiveSessionRules.statusLine(running = true, sharingScreen = true))
    }

    @Test fun thePromptAsksForSpokenAnswersAndCarriesTheIdentityRule() {
        val prompt = LivePrompt.build(memory = "", autoLanguage = true)
        assertTrue(prompt.startsWith("You are SAIF AI"))
        assertTrue(prompt.contains("spoken aloud"))
        assertTrue(prompt.contains("No lists"))
        assertTrue(prompt.contains("Hinglish"))
        assertTrue(prompt.endsWith(SaifIdentity.RULE))
        // The engine is SAIF AI's own: the prompt names no other assistant or maker.
        listOf("JARVIS", "Jarvis", "TechGPT", "Tech GPTx", "Rehaan").forEach { name ->
            assertFalse(name, prompt.contains(name))
        }
    }

    @Test fun thePromptFollowsTheLanguageSetting() {
        val auto = LivePrompt.build(memory = "", autoLanguage = true)
        val fixed = LivePrompt.build(memory = "", autoLanguage = false)
        assertTrue(auto.contains("the language and style the user just spoke"))
        assertFalse(fixed.contains("the language and style the user just spoke"))
        assertTrue(fixed.contains("Change language only when the user asks"))
    }

    @Test fun thePromptIncludesWhatTheAppRemembersButNotWithoutLimit() {
        val memory = "WHAT SAIF AI ALREADY KNOWS:\n- name: Saif\n- city: Varanasi"
        val prompt = LivePrompt.build(memory = "  $memory  ", autoLanguage = true)
        assertTrue(prompt.contains(memory))
        assertTrue(prompt.indexOf(memory) < prompt.indexOf(SaifIdentity.RULE))

        val huge = "x".repeat(50_000)
        val limited = LivePrompt.build(memory = huge, autoLanguage = true)
        assertTrue(limited.length < 6_000)
        assertTrue(limited.endsWith(SaifIdentity.RULE))
    }
}
