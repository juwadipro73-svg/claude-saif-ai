/*
 * SAIF AI - on-device speech recognition: the file rules of the speech model.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.stt

import java.io.File

/**
 * Where the on-device speech model lives and how its archive is unpacked. No Android class is used here, so the
 * rules can be tested on any machine.
 *
 * The model is "vosk-model-small-en-us" of the Vosk project (Apache-2.0, Alpha Cephei Inc.). It is not part of the
 * APK: it is downloaded once from the Vosk project's own server and kept in the app's private storage.
 */
object OfflineSpeechFiles {
    /** Folder below the app's files directory. A model that an earlier version of the app left there is reused. */
    const val MODEL_DIR = "vosk-model"
    const val MODEL_NAME = "vosk-model-small-en-us"
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    /** The archive is about 40 MB and unpacks to about 70 MB; anything far beyond that is not this model. */
    const val MAX_DOWNLOAD_BYTES: Long = 300L * 1024L * 1024L
    const val MAX_UNPACKED_BYTES: Long = 400L * 1024L * 1024L
    const val MAX_ENTRIES: Int = 5000

    /** True when [dir] holds the files without which the recogniser cannot start. */
    fun looksComplete(dir: File): Boolean {
        val acoustic = File(dir, "am/final.mdl")
        return acoustic.isFile && acoustic.length() > 0L && File(dir, "conf/model.conf").isFile
    }

    /**
     * Where an archive entry goes below the model folder. The archive wraps everything in one top folder
     * ("vosk-model-small-en-us-0.15/am/final.mdl"); that folder is dropped ("am/final.mdl").
     *
     * @return null for the top folder itself and for names that try to leave the model folder
     */
    fun entryPath(zipEntryName: String): String? {
        val parts = zipEntryName.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        if (parts.any { it == "." || it == ".." }) return null
        return parts.drop(1).joinToString("/")
    }

    /** True when [file] really is inside [folder] (after symbolic links and ".." are resolved). */
    fun isInside(folder: File, file: File): Boolean {
        val root = folder.canonicalFile.path
        val path = file.canonicalFile.path
        return path.startsWith(root + File.separator)
    }

    /**
     * The recognised words out of the recogniser's answer, which is a small JSON text such as `{"text" : "hello"}`.
     *
     * @return the words, or null when nothing was recognised
     */
    fun textOf(resultJson: String?): String? {
        if (resultJson.isNullOrBlank()) return null
        val match = TEXT_FIELD.find(resultJson) ?: return null
        val raw = match.groupValues[1]
        val text = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n', 'r', 't' -> text.append(' ')
                    'u' -> {
                        val hex = raw.substring(minOf(i + 2, raw.length), minOf(i + 6, raw.length))
                        val code = hex.toIntOrNull(16)
                        if (hex.length == 4 && code != null) {
                            text.append(code.toChar())
                            i += 4
                        }
                    }
                    else -> text.append(next)
                }
                i += 2
            } else {
                text.append(c)
                i++
            }
        }
        return text.toString().trim().replace(MANY_SPACES, " ").takeIf { it.isNotEmpty() }
    }

    private val TEXT_FIELD = Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val MANY_SPACES = Regex("\\s+")
}
