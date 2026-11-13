/*
 * SAIF AI - live voice: pictures of the shared screen.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.live

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns a screen-capture permission the user gave ([projection]) into small JPEG pictures, at most one per second.
 *
 * The screen is mirrored straight into a reduced size (see [LiveSessionRules.frameSize]), so no full-size picture
 * is ever held in memory. Only the newest picture is passed on; nothing is written to storage.
 *
 * @param onFrame called on a background thread with each new picture
 * @param onEnded called once when the system or the user ends the capture from outside (not after [stop])
 */
class LiveScreenCapture(
    private val projection: MediaProjection,
    screenWidth: Int,
    screenHeight: Int,
    private val densityDpi: Int,
    private val onFrame: (ByteArray) -> Unit,
    private val onEnded: () -> Unit
) {
    private val frame = LiveSessionRules.frameSize(screenWidth, screenHeight)
    private val thread = HandlerThread("saif-live-screen")
    private val finished = AtomicBoolean(false)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var lastFrameAt = 0L

    /** Why [start] failed; empty when it worked. */
    @Volatile var lastError: String = ""
        private set

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The user ended sharing from the system bar, or the system ended it (for example on the lock screen).
            finish(endedFromOutside = true)
        }
    }

    /** Starts mirroring. Returns false, with [lastError] set, when the screen cannot be captured. */
    fun start(): Boolean {
        return try {
            thread.start()
            val handler = Handler(thread.looper)
            // Android 14 and newer refuse to mirror before a callback is registered.
            projection.registerCallback(projectionCallback, handler)
            val (width, height) = frame
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source -> deliver(source) }, handler)
            val mirrored = projection.createVirtualDisplay(
                "saif-live-screen", width, height, densityDpi.coerceAtLeast(1),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler
            )
            display = mirrored
            if (mirrored == null) {
                lastError = "The screen could not be mirrored."
                finish(endedFromOutside = false)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "Screen capture did not start: $lastError")
            finish(endedFromOutside = false)
            false
        }
    }

    /** Ends the capture and gives the permission back. */
    fun stop() = finish(endedFromOutside = false)

    private fun deliver(source: ImageReader) {
        val image = try { source.acquireLatestImage() } catch (_: Exception) { null } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (finished.get() || now - lastFrameAt < LiveSessionRules.SCREEN_FRAME_INTERVAL_MS) return
            lastFrameAt = now
            val plane = image.planes[0]
            // A row can be longer than the picture is wide; the extra pixels are cut off again below.
            val rowPixels = (plane.rowStride / plane.pixelStride).coerceAtLeast(image.width)
            val full = Bitmap.createBitmap(rowPixels, image.height, Bitmap.Config.ARGB_8888)
            // On some phones the last row comes without its padding, so the buffer is a little short of what the
            // bitmap asks for. It is then copied into one of the full length.
            val needed = rowPixels * image.height * 4
            val source = plane.buffer
            val pixels = if (source.remaining() >= needed) source else ByteBuffer.allocate(needed).also { it.put(source); it.rewind() }
            full.copyPixelsFromBuffer(pixels)
            val picture = if (rowPixels != image.width) Bitmap.createBitmap(full, 0, 0, image.width, image.height) else full
            val jpeg = ByteArrayOutputStream(96 * 1024)
            picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)
            if (picture !== full) picture.recycle()
            full.recycle()
            onFrame(jpeg.toByteArray())
        } catch (e: Exception) {
            Log.w(TAG, "A screen picture was skipped: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun finish(endedFromOutside: Boolean) {
        if (!finished.compareAndSet(false, true)) return
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { projection.stop() } catch (_: Exception) {}
        try { thread.quitSafely() } catch (_: Exception) {}
        if (endedFromOutside) {
            try { onEnded() } catch (_: Exception) {}
        }
    }

    private companion object {
        const val TAG = "LiveScreenCapture"
        const val JPEG_QUALITY = 60
    }
}
