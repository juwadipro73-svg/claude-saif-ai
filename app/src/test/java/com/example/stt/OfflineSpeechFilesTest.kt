package com.example.stt

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The on-device speech model: which files make it usable and how its archive may be unpacked. */
class OfflineSpeechFilesTest {
    private lateinit var base: File

    @Before fun setUp() { base = Files.createTempDirectory("saif-speech").toFile() }
    @After fun tearDown() { base.deleteRecursively() }

    private fun file(dir: File, path: String, text: String = "x"): File =
        File(dir, path).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun aModelIsCompleteOnlyWithItsAcousticModelAndItsSettings() {
        val model = File(base, "vosk-model").apply { mkdirs() }
        assertFalse(OfflineSpeechFiles.looksComplete(model))
        file(model, "conf/model.conf")
        assertFalse(OfflineSpeechFiles.looksComplete(model))
        // An empty file is what an interrupted download leaves behind.
        file(model, "am/final.mdl", "")
        assertFalse(OfflineSpeechFiles.looksComplete(model))
        file(model, "am/final.mdl", "model")
        assertTrue(OfflineSpeechFiles.looksComplete(model))
        assertFalse(OfflineSpeechFiles.looksComplete(File(base, "missing")))
    }

    @Test fun theTopFolderOfTheArchiveIsDropped() {
        assertEquals("am/final.mdl", OfflineSpeechFiles.entryPath("vosk-model-small-en-us-0.15/am/final.mdl"))
        assertEquals("conf/model.conf", OfflineSpeechFiles.entryPath("vosk-model-small-en-us-0.15/conf/model.conf"))
        assertEquals("graph/phones/word_boundary.int", OfflineSpeechFiles.entryPath("m/graph/phones/word_boundary.int"))
        assertEquals("ivector", OfflineSpeechFiles.entryPath("vosk-model-small-en-us-0.15/ivector/"))
        assertEquals("am/final.mdl", OfflineSpeechFiles.entryPath("top\\am\\final.mdl"))
        // The top folder itself, and a file next to it, have no place in the model folder.
        assertNull(OfflineSpeechFiles.entryPath("vosk-model-small-en-us-0.15/"))
        assertNull(OfflineSpeechFiles.entryPath("README"))
        assertNull(OfflineSpeechFiles.entryPath(""))
    }

    @Test fun anArchiveEntryCannotLeaveTheModelFolder() {
        assertNull(OfflineSpeechFiles.entryPath("model/../../databases/app.db"))
        assertNull(OfflineSpeechFiles.entryPath("../outside/file"))
        assertNull(OfflineSpeechFiles.entryPath("model/./am/final.mdl"))
        assertNull(OfflineSpeechFiles.entryPath("model\\..\\..\\shared_prefs\\keys.xml"))

        val model = File(base, "vosk-model").apply { mkdirs() }
        assertTrue(OfflineSpeechFiles.isInside(model, File(model, "am/final.mdl")))
        assertFalse(OfflineSpeechFiles.isInside(model, File(model, "../other/file")))
        assertFalse(OfflineSpeechFiles.isInside(model, File(base, "vosk-model-evil/file")))
        assertFalse(OfflineSpeechFiles.isInside(model, model))
    }

    @Test fun theRecognisedWordsAreReadFromTheAnswer() {
        assertEquals("hello world", OfflineSpeechFiles.textOf("{\n  \"text\" : \"hello world\"\n}"))
        assertEquals("open youtube", OfflineSpeechFiles.textOf("{\"text\":\"open youtube\"}"))
        assertEquals("say \"hi\" now", OfflineSpeechFiles.textOf("{\"text\" : \"say \\\"hi\\\" now\"}"))
        assertEquals("caf\u00e9", OfflineSpeechFiles.textOf("{\"text\" : \"caf\\u00e9\"}"))
        assertEquals("two lines", OfflineSpeechFiles.textOf("{\"text\" : \"two\\nlines\"}"))
        assertEquals("spaced out", OfflineSpeechFiles.textOf("{\"text\" : \"  spaced    out \"}"))
        // Silence: the recogniser answers with an empty text.
        assertNull(OfflineSpeechFiles.textOf("{\"text\" : \"\"}"))
        assertNull(OfflineSpeechFiles.textOf("{\"partial\" : \"hel\"}"))
        assertNull(OfflineSpeechFiles.textOf(""))
        assertNull(OfflineSpeechFiles.textOf(null))
    }
}
