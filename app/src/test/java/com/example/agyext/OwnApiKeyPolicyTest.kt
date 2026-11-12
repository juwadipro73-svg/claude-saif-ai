package com.example.agyext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnApiKeyPolicyTest {
    private val unbound = "This Antigravity session belongs to another app account. Sign in again inside SAIF AI."

    @Test fun ownApiKeyNeverAsksForAnAntigravityAccount() {
        // The exact situation of the bug: a key is saved, no Antigravity account was ever connected.
        var checked = false
        val issue = OwnApiKeyPolicy.antigravityAccountIssue(usingOwnApiKey = true, hubReady = true) { checked = true; unbound }
        assertNull(issue)
        assertFalse("the Antigravity check must not even run", checked)
        assertNull(OwnApiKeyPolicy.antigravityAccountIssue(usingOwnApiKey = true, hubReady = false) { unbound })
    }

    @Test fun antigravityAccountIsStillCheckedWhenAntigravityIsTheEngine() {
        assertEquals(unbound, OwnApiKeyPolicy.antigravityAccountIssue(usingOwnApiKey = false, hubReady = true) { unbound })
        assertNull(OwnApiKeyPolicy.antigravityAccountIssue(usingOwnApiKey = false, hubReady = true) { null })
        // Not connected at all: the home screen offers the choices, there is no blocking screen.
        assertNull(OwnApiKeyPolicy.antigravityAccountIssue(usingOwnApiKey = false, hubReady = false) { unbound })
    }

    @Test fun voiceCodingTasksRunOnTheUsersKey() {
        assertTrue(OwnApiKeyPolicy.canRunCodingTasks(usingOwnApiKey = true, hubReady = false) { unbound })
        assertTrue(OwnApiKeyPolicy.canRunCodingTasks(usingOwnApiKey = false, hubReady = true) { null })
        assertFalse(OwnApiKeyPolicy.canRunCodingTasks(usingOwnApiKey = false, hubReady = true) { unbound })
        assertFalse(OwnApiKeyPolicy.canRunCodingTasks(usingOwnApiKey = false, hubReady = false) { null })
    }

    @Test fun aSavedButPausedKeyIsFoundWhenAntigravityIsNotConnected() {
        val incomplete = AgyCustomApiProfile(id = "a", apiKey = "")
        val ready = AgyCustomApiProfile(id = "b", apiKey = "sk-or-v1-test")
        assertNull(OwnApiKeyPolicy.savedProfileToUse(AgyCustomApiState()))
        assertNull(OwnApiKeyPolicy.savedProfileToUse(AgyCustomApiState(profiles = listOf(incomplete))))
        assertEquals("b", OwnApiKeyPolicy.savedProfileToUse(AgyCustomApiState(enabled = false, activeProfileId = "b", profiles = listOf(incomplete, ready)))?.id)
        // The selected profile is incomplete: another complete one is used instead.
        assertEquals("b", OwnApiKeyPolicy.savedProfileToUse(AgyCustomApiState(enabled = false, activeProfileId = "a", profiles = listOf(incomplete, ready)))?.id)
    }
}
