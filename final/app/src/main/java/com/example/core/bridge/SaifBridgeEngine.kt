/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.core.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% Cleanroom Local Bridge Server for SAIF AI.
 * Implements docs/spec/browser_bridge.md directly over pure Java ServerSocket.
 */
class SaifBridgeEngine private constructor() {

    companion object {
        private const val LOG_TAG = "SaifBridgeEngine"
        val shared: SaifBridgeEngine by lazy { SaifBridgeEngine() }
    }

    private val random = SecureRandom()
    private val artifactRegistry = ConcurrentHashMap<String, File>()

    @Volatile var activePort: Int = 8767
        private set

    @Volatile var isDaemonRunning: Boolean = false
        private set

    private var serverSocket: ServerSocket? = null
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun launchServer(portPreference: Int = 8767) {
        if (isDaemonRunning) return
        try {
            val sock = ServerSocket(portPreference, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = sock
            activePort = sock.localPort
            isDaemonRunning = true
            Log.i(LOG_TAG, "Cleanroom Bridge Daemon active on 127.0.0.1:$activePort")

            engineScope.launch {
                while (isDaemonRunning && !sock.isClosed) {
                    try {
                        val client = sock.accept()
                        launch { dispatchClient(client) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (err: Exception) {
            Log.e(LOG_TAG, "Bridge Daemon launch failed", e)
        }
    }

    fun registerArtifact(token: String, file: File) {
        artifactRegistry[token] = file
    }

    private fun dispatchClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val path = parts[1]

                if (method == "GET" && path.startsWith("/downloads/")) {
                    val subParts = path.removePrefix("/downloads/").split("/")
                    val token = subParts.getOrNull(0) ?: ""
                    val file = artifactRegistry[token]
                    if (file != null && file.exists()) {
                        val length = file.length()
                        val mime = if (file.name.endsWith(".apk")) "application/vnd.android.package-archive" else "application/zip"
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: $length\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray())
                        file.inputStream().use { it.copyTo(out) }
                        out.flush()
                        return
                    }
                }

                // Default 200 Health Check response
                val body = "{\"status\":\"ok\",\"service\":\"SAIF AI Cleanroom Engine\"}"
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                out.write(resp.toByteArray())
                out.flush()
            }
        } catch (_: Exception) {}
    }
}
