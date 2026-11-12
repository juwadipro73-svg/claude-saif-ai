/* Copyright (c) SAIF AI. All rights reserved. */

package com.saifai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SaifTerminalView(
    onClose: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    val context =
    LocalContext.current
    val manager = remember { SaifTerminalSessionManager.instance }
    val sessions by manager.activeSessions.collectAsState()
    val currentSessionId by manager.currentSessionId.collectAsState()

    LaunchedEffect(key1 = Unit) {
        if (sessions.isEmpty()) {
            manager.createSession(context)
        }
    }

    val activeSession = sessions.firstOrNull { it.sessionId == currentSessionId }
    val outputFlow = remember(currentSessionId) {
        currentSessionId?.let { manager.getOutputFlow(it) }
    }
    val terminalOutput by outputFlow?.collectAsState() ?: remember { mutableStateOf("Initializing terminal...") }

    var inputCmd by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(terminalOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        color = Color(0xFF141312),
        modifier =
    modifier.fillMaxSize()
    ) {
        Column(modifier =
    Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1E1D))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    sessions.forEach { s ->
                        val selected = s.sessionId == currentSessionId
                        Surface(
                            shape =
    RoundedCornerShape(8.dp),
                            color = if (selected) Color(0xFF383533) else Color(0xFF262524),
                            modifier =
    Modifier.clickable { manager.selectSession(s.sessionId) }
                        ) {
                            Row(
                                modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment =
    Alignment.CenterVertically
                            ) {
                                Text(
                                    s.title,
                                    fontSize =
    11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color(0xFFEDE9FE) else Color(0xFF9E9B97)
                                )
                                if (sessions.size > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = Color(0xFF9E9B97),
                                        modifier =
    Modifier
                                            .size(12.dp)
                                            .clickable { manager.closeSession(s.sessionId) }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { manager.createSession(context) },
                        modifier =
    Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color(0xFF9B82EC), modifier =
    Modifier.size(16.dp))
                    }
                }

                IconButton(onClick = onClose, modifier =
    Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Terminal", tint =
    Color.White, modifier =
    Modifier.size(18.dp))
                }
            }

            // Monospaced Terminal Output
            Box(
                modifier =
    Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = terminalOutput,
                    color = Color(0xFFE2E0D8),
                    fontFamily =
    FontFamily.Monospace,
                    fontSize =
    12.sp,
                    lineHeight = 16.sp
                )
            }

            // Command Prompt Bar & Quick Keys
            Column(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1A19))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Quick Control Keys
                Row(
                    modifier =
    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(state = rememberScrollState()),
                    horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                ) {
                    val quickKeys = listOf(
                        "CTRL+C" to { currentSessionId?.let { manager.sendCtrlC(it) } },
                        "TAB" to { currentSessionId?.let { manager.sendInput(it, "\t") } },
                        "ESC" to { currentSessionId?.let { manager.sendInput(it, "\u001B") } },
                        "CLEAR" to { currentSessionId?.let { manager.sendInput(it, "clear\n") } },
                        "LS" to { currentSessionId?.let { manager.sendInput(it, "ls -la\n") } },
                        "APK" to { currentSessionId?.let { manager.sendInput(it, "saif-build-apk --help\n") } }
                    )
                    quickKeys.forEach { (label, action) ->
                        Surface(
                            shape =
    RoundedCornerShape(6.dp),
                            color = Color(0xFF2B2927),
                            modifier =
    Modifier.clickable { action() }
                        ) {
                            Text(
                                label,
                                fontSize =
    10.sp,
                                fontWeight =
    FontWeight.Bold,
                                color = Color(0xFFD4C9F8),
                                modifier =
    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Input box
                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    verticalAlignment =
    Alignment.CenterVertically
                ) {
                    Text("$ ", color = Color(0xFF9B82EC), fontFamily =
    FontFamily.Monospace, fontWeight =
    FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = inputCmd,
                        onValueChange = { inputCmd = it },
                        textStyle = TextStyle(
                            color =
    Color.White,
                            fontFamily =
    FontFamily.Monospace,
                            fontSize =
    13.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF9B82EC)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputCmd.isNotBlank()) {
                                currentSessionId?.let { manager.sendInput(it, "$inputCmd\n") }
                                inputCmd = ""
                            }
                        }),
                        modifier =
    Modifier
                            .weight(1f)
                            .background(Color(0xFF262523), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
