/*
 * SAIF AI - licence and credits screen.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shows the bundled licence texts and credits. Used by SAIF AI Settings and by the workspace "About" screen. */
@Composable
fun SaifLegalNoticesDialog(
    onDismiss: () -> Unit,
    initialPage: String = SaifLegalNotices.CREDITS_PAGE
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(initialPage) }
    val content = remember(page) {
        runCatching { context.assets.open("legal/$page").bufferedReader().use { it.readText() } }
            .getOrElse { "This text could not be loaded. It is in the source code at ${SaifLegalNotices.SOURCE_URL}" }
    }
    // A new page starts at its top.
    val textScroll = remember(page) { ScrollState(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences & credits") },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    SaifLegalNotices.PAGES.forEach { (label, file) ->
                        TextButton(onClick = { page = file }) {
                            Text(label, fontWeight = if (page == file) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Text(
                    content,
                    modifier = Modifier.verticalScroll(textScroll),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
