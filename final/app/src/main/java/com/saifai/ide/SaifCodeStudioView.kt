/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.ide

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SaifCodeStudioView(
    onClose: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    modifier: Modifier =
    Modifier
) {
    val context =
    LocalContext.current
    val engine = remember { SaifCodeStudioEngine.instance }
    val tabs by engine.tabsFlow.collectAsState()
    val activePath by engine.activeFilePath.collectAsState()

    LaunchedEffect(key1 = Unit) {
        if (tabs.isEmpty()) {
            engine.openFile(context, "app/build.gradle.kts")
        }
    }

    val activeTab = tabs.firstOrNull { it.relativePath == activePath }
    var currentText by remember(activePath, activeTab?.content) {
        mutableStateOf(activeTab?.content ?: "// SAIF AI Code Editor\n// Open or create files in /root/workspace\n")
    }

    val ext = remember(activePath) { activePath?.substringAfterLast('.', "") ?: "kt" }
    val highlightedText = remember(currentText, ext) { engine.highlightCode(currentText, ext) }

    Surface(
        color = Color(0xFF141312),
        modifier =
    modifier.fillMaxSize()
    ) {
        Column(modifier =
    Modifier.fillMaxSize()) {
            // Action & Tab Strip
            Row(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1E1D))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment =
    Alignment.CenterVertically
            ) {
                Row(
                    modifier =
    Modifier
                        .weight(1f)
                        .horizontalScroll(state = rememberScrollState()),
                    horizontalArrangement =
    Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab.relativePath == activePath
                        Surface(
                            shape =
    RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF383533) else Color(0xFF262524),
                            modifier =
    Modifier.clickable { engine.openFile(context, tab.relativePath) }
                        ) {
                            Row(
                                modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment =
    Alignment.CenterVertically
                            ) {
                                Text(
                                    tab.displayName + if (tab.isModified) " •" else "",
                                    fontSize =
    11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFEDE9FE) else Color(0xFF9E9B97)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Editor Tab",
                                    tint = Color(0xFF9E9B97),
                                    modifier =
    Modifier
                                        .size(12.dp)
                                        .clickable { engine.closeTab(tab.relativePath) }
                                )
                            }
                        }
                    }
                }

                // Action icons: Save, Terminal, Close
                IconButton(
                    onClick = {
                        activePath?.let { path ->
                            engine.updateContent(path, currentText)
                            val ok = engine.saveFile(context, path)
                            Toast.makeText(context, if (ok) "Saved: $path" else "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier =
    Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = "Save", tint = Color(0xFF10B981), modifier =
    Modifier.size(18.dp))
                }

                IconButton(onClick = onNavigateToTerminal, modifier =
    Modifier.size(28.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Terminal", tint = Color(0xFF9B82EC), modifier =
    Modifier.size(18.dp))
                }

                IconButton(onClick = onClose, modifier =
    Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Exit IDE", tint =
    Color.White, modifier =
    Modifier.size(18.dp))
                }
            }

            // Editor Area (Line Numbers + Text Field)
            val lines = remember(currentText) { currentText.lines() }
            Row(
                modifier =
    Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(state = rememberScrollState())
            ) {
                // Line numbers
                Column(
                    modifier =
    Modifier
                        .background(Color(0xFF191817))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = "$i",
                            color = Color(0xFF6B7280),
                            fontSize =
    12.sp,
                            fontFamily =
    FontFamily.Monospace,
                            lineHeight = 18.2.sp
                        )
                    }
                }

                // Code input
                Box(
                    modifier =
    Modifier
                        .weight(1f)
                        .horizontalScroll(state = rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = currentText,
                        onValueChange = {
                            currentText = it
                            activePath?.let { path -> engine.updateContent(path, it) }
                        },
                        textStyle = TextStyle(
                            color = Color(0xFFE5E7EB),
                            fontSize =
    12.sp,
                            fontFamily =
    FontFamily.Monospace,
                            lineHeight = 18.2.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF9B82EC)),
                        modifier =
    Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
