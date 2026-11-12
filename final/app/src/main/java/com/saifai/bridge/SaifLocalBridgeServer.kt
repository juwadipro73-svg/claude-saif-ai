/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 100% Original Cleanroom Local Download & Execution Bridge Server for SAIF AI.
 * Completely replaces legacy AndroidLocalBridgeServer.
 */
class SaifLocalBridgeServer private constructor() {

    companion object /* SAIF Local Bridge Server */ {
        private const val TAG = "SaifLocalBridgeServer"
        val instance: SaifLocalBridgeServer by lazy { SaifLocalBridgeServer() }
    }

    data class DownloadEntry(
        val token:
        String,
        val file: File,
        val fileName:
        String,
        val mimeType:
        String,
        val createdAt:
        Long = System.currentTimeMillis()
    )

    private val secureRandom = SecureRandom()
    private val registeredDownloads = ConcurrentHashMap<String, DownloadEntry>()

    @Volatile
    var isRunning:
        Boolean = false
        private set

    @Volatile
    var port: Int = 8767
        private set

    private var serverSocket:
        ServerSocket? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start(appContext: Context) {
        if (isRunning) { return }
        try {
            val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = s
            port = s.localPort
            isRunning = true
            Log.i(TAG, "SAIF AI Local Bridge Server listening on 127.0.0.1:$port")

            serverScope.launch(Dispatchers.IO) {
                while (isRunning && !s.isClosed) {
                    try {
                        val client = s.accept()
                        launch { handleClientSocket(client) }
                    } catch (err: Exception) {
                        break
                    }
                }
            }
        } catch (err: Exception) {
            Log.e(TAG, "Failed starting SAIF AI Bridge Server", e)
        }
    }

    fun registerDownloadFile(context: Context, file: File, desiredFileName: String? = null): String {
        start(context)
        val rawName = (desiredFileName?.takeIf { it.isNotBlank() } ?: file.name)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "saif_app.apk" }

        val ext = rawName.substringAfterLast('.', file.extension).lowercase(java.util.Locale.ROOT)
        val mime = when (ext) {
            "apk" ->
            "application/vnd.android.package-archive"
            "zip" ->
            "application/zip"
            "mp4" ->
            "video/mp4"
            else ->
            "application/octet-stream"
        }

        val tokenBytes = ByteArray(16)
        secureRandom.nextBytes(tokenBytes)
        val token = tokenBytes.joinToString("") { "%02x".format(it) }

        val entry = DownloadEntry(token, file, rawName, mime)
        registeredDownloads[token] = entry

        return "http://127.0.0.1:$port/download/$token/$rawName"
    }

    private fun handleClientSocket(socket: Socket) {
        try {
            socket.use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())
                val requestLine = input.readLine() ?: return

                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val uri = parts[1]

                if (uri.startsWith("/download/")) {
                    val segments = uri.removePrefix("/download/").split("/")
                    val token = segments.firstOrNull().orEmpty()
                    val entry = registeredDownloads[token]

                    if (entry != null && entry.file.exists()) {
                        val length = entry.file.length()
                        val header = "HTTP/1.1 200 OK
" +
                                "Content-Type: ${entry.mimeType}
" +
                                "Content-Length: $length
" +
                                "Content-Disposition: attachment; filename="${entry.fileName}"
" +
                                "Connection: close

"
                        output.write(header.toByteArray(Charsets.UTF_8))

                        FileInputStream(entry.file).use { fis ->
                            val buffer = ByteArray(32768)
                            var read = fis.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                read = fis.read(buffer)
                            }
                        }
                        output.flush()
                        return
                    }
                }

                // Default 404
                val notFound = "HTTP/1.1 404 Not Found
Content-Length: 0
Connection: close

"
                output.write(notFound.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        } catch (err: Exception) {}
    }
}
