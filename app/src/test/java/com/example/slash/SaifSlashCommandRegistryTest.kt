package com.example.slash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaifSlashCommandRegistryTest {
    @Test
    fun resolvesAllMainWorkspaceActions() {
        assertEquals(SaifAppSlashAction.OPEN_TERMINAL, SaifSlashCommandRegistry.resolveAppAction(" /terminal "))
        assertEquals(SaifAppSlashAction.OPEN_CODE_IDE, SaifSlashCommandRegistry.resolveAppAction("/ide"))
        assertEquals(SaifAppSlashAction.OPEN_BROWSER, SaifSlashCommandRegistry.resolveAppAction("/browser"))
        assertEquals(SaifAppSlashAction.OPEN_VIDEO_STUDIO, SaifSlashCommandRegistry.resolveAppAction("/video"))
        assertEquals(SaifAppSlashAction.OPEN_NOTES, SaifSlashCommandRegistry.resolveAppAction("/notes"))
        assertEquals(SaifAppSlashAction.NEW_CHAT, SaifSlashCommandRegistry.resolveAppAction("/new"))
        assertEquals(SaifAppSlashAction.CLEAR_CHAT, SaifSlashCommandRegistry.resolveAppAction("/clear"))
        assertEquals(SaifAppSlashAction.OPEN_AGENTS, SaifSlashCommandRegistry.resolveAppAction("/agents"))
        assertEquals(SaifAppSlashAction.OPEN_SKILLS, SaifSlashCommandRegistry.resolveAppAction("/skills"))
        assertEquals(SaifAppSlashAction.OPEN_PROVIDERS, SaifSlashCommandRegistry.resolveAppAction("/providers"))
        assertEquals(SaifAppSlashAction.OPEN_GITHUB, SaifSlashCommandRegistry.resolveAppAction("/github"))
        assertEquals(SaifAppSlashAction.OPEN_SETTINGS, SaifSlashCommandRegistry.resolveAppAction("/settings"))
    }

    @Test
    fun onlyWholeInputAppCommandsResolveImmediately() {
        assertNull(SaifSlashCommandRegistry.resolveAppAction("Please open /terminal"))
        assertNull(SaifSlashCommandRegistry.resolveAppAction("/terminal run ls"))
        assertNull(SaifSlashCommandRegistry.resolveAppAction("/plu:"))
    }

    @Test
    fun findsSlashTokensAtStartAndAfterWhitespace() {
        assertEquals(
            ActiveSlashToken(0, 4, "/pla", "pla"),
            SaifSlashCommandRegistry.extractActiveSlashToken("/pla", 4)
        )
        assertEquals(
            ActiveSlashToken(8, 12, "/ide", "ide"),
            SaifSlashCommandRegistry.extractActiveSlashToken("open it /ide", 12)
        )
        assertNull(SaifSlashCommandRegistry.extractActiveSlashToken("path/to/file", 12))
    }

    @Test
    fun builtInWorkflowExpansionsCarryTheUserTask() {
        val expanded = SaifSlashCommandRegistry.expandBuiltInCommandIfMatched("/plan Build a secure login")
        assertTrue(expanded.orEmpty().contains("Create a structured, concrete implementation plan"))
        assertTrue(expanded.orEmpty().contains("Build a secure login"))
        assertTrue(expanded.orEmpty().startsWith("<antigem-expansion"))
    }
}
