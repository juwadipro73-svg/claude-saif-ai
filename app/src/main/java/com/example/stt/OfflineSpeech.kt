/*
 * SAIF AI - on-device speech recognition.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.stt

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Speech recognition that runs on the phone, for AI providers that have no speech-to-text service of their own
 * (OpenRouter, Anthropic, DeepSeek and others). Voice typing and the live conversation use it through [SttRouter]
 * when no key of the user can transcribe.
 *
 * It is a thin layer over the Vosk library (Apache-2.0). The English model is downloaded once, on first use, into
 * the app's private storage; see [OfflineSpeechFiles].
 */
object OfflineSpeech {
    private const val TAG = "OfflineSpeech"
    private const val SAMPLE_RATE = 16000.0f
    private const val FEED_BYTES = 8000
    private const val RETRY_AFTER_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preparing = AtomicBoolean(false)
    private val recogniserLock = Any()

    @Volatile private var model: Model? = null
    @Volatile private var lastFailureAt = 0L

    private val _isModelReady = MutableStateFlow(false)

    /** True once the model is loaded and [transcribePcm] can be used. */
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    /**
     * Loads the model in the background, downloading it first when it is not on the phone yet. Safe to call often:
     * it does nothing while a load is running, after the model is ready, or shortly after a failed attempt.
     */
    fun prepare(context: Context) {
        if (_isModelReady.value) return
        val failedAt = lastFailureAt
        if (failedAt != 0L && SystemClock.elapsedRealtime() - failedAt < RETRY_AFTER_MS) return
        if (!preparing.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val folder = File(app.filesDir, OfflineSpeechFiles.MODEL_DIR)
                if (!OfflineSpeechFiles.looksComplete(folder)) fetchModel(app, folder)
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                model = Model(folder.absolutePath)
                lastFailureAt = 0L
                _isModelReady.value = true
            } catch (t: Throwable) {
                // No network, no space, or a phone the recogniser has no native library for.
                lastFailureAt = SystemClock.elapsedRealtime()
                Log.w(TAG, "The on-device speech model is not available: ${t.message}")
            } finally {
                preparing.set(false)
            }
        }
    }

    /**
     * Turns one utterance into text.
     *
     * @param pcm 16 kHz, mono, 16-bit little-endian samples without a header
     * @return the recognised words, or null when the model is not ready or nothing was understood
     */
    fun transcribePcm(pcm: ByteArray): String? {
        val loaded = model ?: return null
        if (pcm.isEmpty()) return null
        synchronized(recogniserLock) {
            var recogniser: Recognizer? = null
            return try {
                recogniser = Recognizer(loaded, SAMPLE_RATE)
                val piece = ByteArray(FEED_BYTES)
                var offset = 0
                while (offset < pcm.size) {
                    val count = minOf(FEED_BYTES, pcm.size - offset)
                    System.arraycopy(pcm, offset, piece, 0, count)
                    recogniser.acceptWaveForm(piece, count)
                    offset += count
                }
                OfflineSpeechFiles.textOf(recogniser.finalResult)
            } catch (t: Throwable) {
                Log.w(TAG, "On-device recognition failed: ${t.message}")
                null
            } finally {
                try { recogniser?.close() } catch (_: Throwable) {}
            }
        }
    }

    /** Downloads the model archive and unpacks it next to [target]; [target] is replaced only by a complete model. */
    private fun fetchModel(app: Context, target: File) {
        val archive = File(app.cacheDir, "speech-model.zip.part")
        val staging = File(target.parentFile, target.name + ".new")
        try {
            download(OfflineSpeechFiles.MODEL_URL, archive)
            staging.deleteRecursively()
            unpack(archive, staging)
            if (!OfflineSpeechFiles.looksComplete(staging)) throw IOException("the downloaded speech model is incomplete")
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("the speech model could not be moved into place")
        } finally {
            archive.delete()
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun download(address: String, to: File) {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("speech model download answered HTTP $status")
            connection.inputStream.use { input ->
                FileOutputStream(to).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > OfflineSpeechFiles.MAX_DOWNLOAD_BYTES) throw IOException("speech model download is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unpack(archive: File, folder: File) {
        if (!folder.mkdirs() && !folder.isDirectory) throw IOException("cannot create ${folder.name}")
        var entries = 0
        var total = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > OfflineSpeechFiles.MAX_ENTRIES) throw IOException("speech model archive has too many files")
                val relative = OfflineSpeechFiles.entryPath(entry.name)
                if (relative != null) {
                    val out = File(folder, relative)
                    if (!OfflineSpeechFiles.isInside(folder, out)) throw IOException("speech model archive has an unsafe path")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > OfflineSpeechFiles.MAX_UNPACKED_BYTES) throw IOException("speech model archive is too large")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
