package com.example.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModuleCleanupRulesTest {
    private val filesDir = "/data/user/0/com.saifai.app/files"

    @Test fun hostPathsAreShownAsTheAgentSeesThem() {
        assertEquals("/root/workspace/shop/node_modules", ModuleCleanupRules.linuxPath("$filesDir/home/shop/node_modules", filesDir))
        assertEquals("/root/workspace", ModuleCleanupRules.linuxPath("$filesDir/home", "$filesDir/"))
        assertEquals("/root/.npm/_cacache", ModuleCleanupRules.linuxPath("$filesDir/ubuntu/root/.npm/_cacache", filesDir))
        assertEquals("/var/cache/apt/archives", ModuleCleanupRules.linuxPath("$filesDir/ubuntu/var/cache/apt/archives", filesDir))
        assertEquals("/tmp", ModuleCleanupRules.linuxPath("$filesDir/tmp", filesDir))
        assertEquals("/somewhere/else", ModuleCleanupRules.linuxPath("/somewhere/else", filesDir))
        // "homework" is not below "home".
        assertEquals("$filesDir/homework/x", ModuleCleanupRules.linuxPath("$filesDir/homework/x", filesDir))
    }

    @Test fun logListsWhatWasRemovedWithItsRestoreCommand() {
        val item = CleanableItem(File("$filesDir/home/shop/node_modules"), "node_modules", File("$filesDir/home/shop"), "npm install")
        val first = ModuleCleanupRules.renderLog(
            CleanupResult(listOf(RemovedItem(item, 3L * 1024 * 1024)), emptyList(), emptyList()), filesDir, "2026-11-12 18:00"
        )
        assertTrue(first.contains("- `/root/workspace/shop/node_modules` (node_modules, 3.0 MB) removed 2026-11-12 18:00 - restore: npm install"))
        assertTrue(first.contains("No project source file was deleted."))

        val venv = CleanableItem(File("$filesDir/home/bot/venv"), "python-venv", File("$filesDir/home/bot"), "python3 -m venv venv")
        val second = ModuleCleanupRules.renderLog(
            CleanupResult(listOf(RemovedItem(venv, 10L)), emptyList(), emptyList()), filesDir, "2026-11-13 09:00", previous = first
        )
        val entries = second.lines().filter { it.startsWith("- `") }
        assertEquals(2, entries.size)
        assertTrue("newest first", entries[0].contains("/root/workspace/bot/venv"))
        assertTrue(entries[1].contains("/root/workspace/shop/node_modules"))
        assertEquals(1, second.lines().count { it.startsWith("# ") })
    }

    @Test fun logKeepsOnlyTheNewestEntries() {
        val previous = (1..80).joinToString("\n") { "- `/root/workspace/p$it/node_modules` old" }
        val log = ModuleCleanupRules.renderLog(CleanupResult(emptyList(), emptyList(), emptyList()), filesDir, "now", previous, maxEntries = 60)
        assertEquals(60, log.lines().count { it.startsWith("- `") })
        assertFalse(log.contains("/p61/"))
    }

    @Test fun idleChoicesRoundTripThroughTheirLabels() {
        assertEquals(listOf("10 min", "30 min", "2 hours", "12 hours"), ModuleCleanupRules.IDLE_CHOICES_MINUTES.map(ModuleCleanupRules::idleLabel))
        ModuleCleanupRules.IDLE_CHOICES_MINUTES.forEach { minutes ->
            assertEquals(minutes, ModuleCleanupRules.idleMinutesFromLabel(ModuleCleanupRules.idleLabel(minutes)))
        }
        assertEquals("1 hour", ModuleCleanupRules.idleLabel(60))
        assertEquals(ModuleCleanupRules.DEFAULT_IDLE_MINUTES, ModuleCleanupRules.idleMinutesFromLabel("nonsense"))
        assertTrue(ModuleCleanupRules.DEFAULT_IDLE_MINUTES in ModuleCleanupRules.IDLE_CHOICES_MINUTES)
    }

    @Test fun agentIsToldToReinstallInsteadOfReportingAFault() {
        val rule = ModuleCleanupRules.AGENT_RULE
        assertTrue(rule.contains("npm install"))
        assertTrue(rule.contains("never deleted") || rule.contains("are never"))
        assertTrue(rule.contains(ModuleCleanupRules.LOG_PATH_IN_LINUX))
    }
}
