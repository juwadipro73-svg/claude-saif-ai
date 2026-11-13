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
        assertTrue("licence texts", texts.size >= 8)
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
            "termux/termux-app", "Jack Palevich",
            "PRoot", "STMicroelectronics", "talloc", "GNU C Library",
            "sora-editor", "Rosemoe", "JLaTeXMath", "JSch", "jsoup", "Jonathan Hedley",
            "OkHttp", "Coil", "Markwon", "Apache Commons Compress",
            // on-device speech recognition of SAIF AI's own voice code
            "Vosk", "Alpha Cephei", "JNA", "vosk-model-small-en-us",
            // found in the list of libraries that the build really packages (build #7)
            "AndroidSVG", "Accompanist", "gRPC-Java", "Protocol Buffers", "PerfMark", "Tink", "Guava",
            "Commons IO", "Commons Codec", "Commons Lang", "Checker Framework", "Firebase Authentication",
            "gorilla/websocket", "google/uuid", "go-humanize", "go-isatty", "go-strftime", "modernc.org/sqlite",
            "The Go Authors", "The Android Open Source Project", "JetBrains"
        ).forEach { name -> assertTrue("credit missing: $name", notices.contains(name)) }
    }

    @Test fun theRemovedVoiceModuleIsCompletelyGone() {
        // The owner had the third-party voice-automation module (Gradle module "automation", Java package
        // com.jarvis.assistant) taken out, with its licence and credits, and replaced by SAIF AI's own live voice
        // code (com.example.live). Its licence adds conditions that do not fit the GPL, so neither the module nor
        // code copied from it may come back.
        assertFalse(file("automation").exists())
        assertFalse(file("licenses/TAMCL-1.0.txt").exists())
        assertFalse(file("$bundled/licenses/TAMCL-1.0.txt").exists())
        assertFalse(file("settings.gradle.kts").readText().contains("automation"))
        assertFalse(file("app/build.gradle.kts").readText().contains(":automation"))
        assertFalse(SaifLegalNotices.PAGES.any { it.second.contains("TAMCL") })

        val leftovers = listOf("app/src/main", "app/src/standard", "app/src/termux", "apprunner/src")
            .map { file(it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml", "html") }.toList() }
            .filter { source ->
                val text = source.readText()
                text.contains("com.jarvis.assistant") || text.contains("SaifAutomation") || text.contains("JarvisApp")
            }
            .map { it.path }
        assertEquals("files that still use the removed module", emptyList<String>(), leftovers)

        // Nothing of it is left to credit, so the notices and the app's licence screen no longer name it.
        listOf("THIRD_PARTY_NOTICES.md", "README.md").forEach { name ->
            val text = file(name).readText()
            assertFalse(name, text.contains("TAMCL"))
            assertFalse(name, text.contains("techgptx", ignoreCase = true))
            assertFalse(name, text.contains("Tech GPTx", ignoreCase = true))
        }
        assertFalse(SaifLegalNotices.SHORT_NOTICE.contains("Tech GPTx", ignoreCase = true))
    }

    @Test fun theLiveVoiceIsSaifAisOwnCode() {
        // The replacement lives in the app itself and carries SAIF AI's own licence header.
        val live = "app/src/main/java/com/example"
        listOf(
            "$live/live/SaifLive.kt", "$live/live/SaifLiveService.kt", "$live/live/LiveScreenCapture.kt",
            "$live/live/LivePrompt.kt", "$live/live/LiveModels.kt",
            "$live/stt/OfflineSpeech.kt", "$live/stt/OfflineSpeechFiles.kt"
        ).forEach { path ->
            val text = file(path).readText()
            assertTrue(path, text.contains("SPDX-License-Identifier: GPL-3.0-or-later"))
            assertTrue(path, text.contains("Copyright (C) 2026 SAIF AI contributors"))
        }
        // The service that owns the microphone is declared, and may capture the screen only as a second type.
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("com.example.live.SaifLiveService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|mediaProjection\""))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"))
    }
}
