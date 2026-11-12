/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Records a short voice clip to the app cache and publishes the live input level (0..1)
 * so the UI can draw a waveform while the user is speaking.
 */
class AudioRecordingEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val level = MutableStateFlow(0f)
    val maxAmplitude: StateFlow<Float> = level

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var poller: Job? = null

    fun startRecording() {
        if (recorder != null) return
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(96_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        } catch (t: Throwable) {
            runCatching { rec.release() }
            return
        }
        recorder = rec
        target = file
        poller = scope.launch {
            while (isActive) {
                val raw = runCatching { rec.maxAmplitude }.getOrDefault(0)
                level.value = (raw / 32767f).coerceIn(0f, 1f)
                delay(80)
            }
        }
    }

    fun stopRecording(): File? {
        poller?.cancel()
        poller = null
        val rec = recorder ?: return null
        val file = target
        recorder = null
        target = null
        level.value = 0f
        val finished = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        return if (finished && file != null && file.exists() && file.length() > 0) file else null
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }
}
