/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SaifResearchBrowserView(
    initialUrl: String = "https://www.google.com",
    onClose: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    var urlInput by remember { mutableStateOf(initialUrl) }
    var currentWebUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var progressVal by remember { mutableFloatStateOf(0f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Surface(
        color = Color(0xFF141312),
        modifier =
    modifier.fillMaxSize()
    ) {
        Column(modifier =
    Modifier.fillMaxSize()) {
            // URL & Nav bar
            Row(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1E1D))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment =
    Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    enabled = webViewInstance?.canGoBack() == true,
                    modifier =
    Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint =
    Color.White, modifier =
    Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    enabled = webViewInstance?.canGoForward() == true,
                    modifier =
    Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint =
    Color.White, modifier =
    Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier =
    Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint =
    Color.White, modifier =
    Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(5.dp))

                BasicTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    textStyle = TextStyle(color =
    Color.White, fontSize =
    12.sp),
                    singleLine =
    true,
                    cursorBrush = SolidColor(Color(0xFF9B82EC)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val target = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                            urlInput
                        } else {
                            "https://$urlInput"
                        }
                        currentWebUrl = target
                        webViewInstance?.loadUrl(target)
                    }),
                    modifier =
    Modifier
                        .weight(1f)
                        .background(Color(0xFF262523), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )

                Spacer(modifier = Modifier.width(5.dp))

                IconButton(onClick = onClose, modifier =
    Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Browser", tint =
    Color.White, modifier =
    Modifier.size(18.dp))
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progressVal },
                    color = Color(0xFF9B82EC),
                    trackColor = Color(0xFF262523),
                    modifier =
    Modifier.fillMaxWidth().height(2.dp)
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply { javaScriptEnabled = true }
                        settings.apply { domStorageEnabled = true }
                        settings.apply { loadWithOverviewMode = true }
                        settings.apply { useWideViewPort = true }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(targetView: WebView?, pageUrl: String?, pageFavicon: Bitmap?) {
                                isLoading = true
                                url?.let { urlInput = it }
                            }

                            override fun onPageFinished(targetView: WebView?, pageUrl: String?) {
                                isLoading = false
                                url?.let { urlInput = it }
                            }

                            override fun shouldOverrideUrlLoading(targetView: WebView?, resourceRequest: WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(targetView: WebView?, progressValue: Int) {
                                progressVal = newProgress / 100f
                            }
                        }

                        loadUrl(currentWebUrl)
                        webViewInstance = this
                    }
                },
                modifier =
    Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
