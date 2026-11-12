package com.example

import com.example.ui.components.SaifLegalNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Credits and licence texts must stay complete: once before, a credit was removed from this project and its
 * licence was broken. These checks fail the build when a name disappears or the copy inside the APK is stale.
 */
class LegalNoticesBundleTest {
    private val repoRoot: File = listOf(File("."), File("..")).first { File(it, "settings.gradle.kts").isFile }
    private fun file(path: String) = File(repoRoot, path)
    private val bundled = "app/src/main/assets/legal"

    @Test fun theApkCarriesExactCopiesOfTheLicenceFiles() {
        assertEquals(file("LICENSE").readText(), file("$bundled/LICENSE").readText())
        assertEquals(file("THIRD_PARTY_NOTICES.md").readText(), file("$bundled/THIRD_PARTY_NOTICES.md").readText())
        val texts = file("licenses").listFiles { f -> f.isFile }!!.sortedBy { it.name }
        assertTrue("licence texts", texts.size >= 9)
        texts.forEach { text ->
            assertEquals(text.name, text.readText(), file("$bundled/licenses/${text.name}").readText())
        }
        assertEquals(texts.map { it.name }, file("$bundled/licenses").listFiles { f -> f.isFile }!!.map { it.name }.sorted())
    }

    @Test fun everyPageOfTheLicenceScreenExists() {
        SaifLegalNotices.PAGES.forEach { (label, path) ->
            val page = file("$bundled/$path")
            assertTrue("$label -> $path", page.isFile && page.length() > 500)
        }
        assertEquals(SaifLegalNotices.CREDITS_PAGE, SaifLegalNotices.PAGES.first().second)
        assertTrue(SaifLegalNotices.SHORT_NOTICE.contains("Santosh Kurmi"))
        assertTrue(SaifLegalNotices.SHORT_NOTICE.contains("Tech GPTx"))
        assertTrue(SaifLegalNotices.SHORT_NOTICE.contains("NO WARRANTY"))
    }

    @Test fun saifAiIsUnderTheGplLikeTheCodeItIsBuiltOn() {
        val license = file("LICENSE").readText()
        assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(license.contains("Version 3, 29 June 2007"))
        val notices = file("THIRD_PARTY_NOTICES.md").readText()
        assertTrue(notices.contains("GPL-3.0-or-later"))
        assertTrue(notices.contains(SaifLegalNotices.SOURCE_URL))
        // The GPL text itself uses the word "proprietary", so look for the title of a closed-source licence.
        assertFalse("no proprietary licence on GPL code", license.contains("PROPRIETARY SOFTWARE LICENSE", ignoreCase = true))
        assertFalse(license.contains("All rights reserved", ignoreCase = true))
    }

    @Test fun everyoneWhoseCodeIsUsedIsCredited() {
        val notices = file("THIRD_PARTY_NOTICES.md").readText()
        listOf(
            "Santosh Kurmi", "https://github.com/Santoshkurmi/antigem",
            "Tech GPTx", "https://github.com/techgptx/Playlist", "TAMCL v1.0",
            "termux/termux-app", "Jack Palevich",
            "PRoot", "STMicroelectronics", "talloc", "GNU C Library",
            "sora-editor", "Rosemoe", "JLaTeXMath", "JSch", "jsoup", "Jonathan Hedley",
            "Vosk", "Alpha Cephei", "OkHttp", "Coil", "Markwon", "Apache Commons Compress", "JNA",
            "gorilla/websocket", "google/uuid", "go-humanize", "go-isatty", "go-strftime", "modernc.org/sqlite",
            "The Go Authors", "The Android Open Source Project", "JetBrains"
        ).forEach { name -> assertTrue("credit missing: $name", notices.contains(name)) }
    }

    @Test fun theVoiceModuleKeepsItsAuthorsCredit() {
        // TAMCL v1.0: the Tech GPTx attribution may not be removed from code, interface or documentation.
        assertEquals(file("automation/LICENSE").readText(), file("licenses/TAMCL-1.0.txt").readText())
        assertTrue(file("automation/LICENSE").readText().contains("Copyright (c) 2026 Tech GPTx"))
        val info = file("automation/src/main/java/com/jarvis/assistant/security/DeveloperInfo.kt").readText()
        assertTrue(info.contains("const val DEVELOPER_NAME      = \"Tech GPTx\""))
        assertTrue(info.contains("https://github.com/techgptx/Playlist"))
        assertTrue(info.contains("TAMCL v1.0"))
        assertFalse(info.contains("No external credits"))
        val readme = file("automation/README.md").readText()
        assertTrue(readme.contains("Tech GPTx"))
        assertFalse(readme.contains("Owned and credited 100% to SAIF AI"))
        val footer = file("automation/src/main/res/layout/fragment_settings.xml").readText()
        assertTrue(footer.contains("Built on JARVIS Android Assistant by Tech GPTx"))
        val prompt = file("automation/src/main/java/com/jarvis/assistant/util/PromptGenerator.kt").readText()
        assertTrue(prompt.contains("JARVIS AI Android assistant by Tech GPTx"))
    }
}
