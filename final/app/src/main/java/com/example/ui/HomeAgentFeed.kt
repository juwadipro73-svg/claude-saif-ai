/* Copyright (c) SAIF AI. All rights reserved. */
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saif.domain.model.ChatMessage
import com.example.saif.domain.model.MessageRole
import com.example.saif.ui.SaifMarkdown

/** Native feed: persisted messages and tool results share the same domain contract. */
@Composable
internal fun HomeAgentFeed(messages: List<ChatMessage>, modelId: String, generating: Boolean,
    listState: LazyListState, viewModel: ChatViewModel, onRetry: () -> Unit) {
    LaunchedEffect(messages.lastOrNull(), generating) {
        val layout = listState.layoutInfo
        val nearBottom = (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
        if (nearBottom && layout.totalItemsCount > 0) listState.scrollToItem(layout.totalItemsCount - 1)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(messages, key = { it.id }) { message ->
            if (message.role == MessageRole.USER) {
                com.example.saif.ui.components.UserMessageBubble(message)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.thoughtText?.takeIf { it.isNotBlank() }?.let {
                        com.example.saif.ui.components.ThinkingAccordion(it, message.thoughtDurationMs, message.isStreaming)
                    }
                    if (message.content.isNotBlank()) SelectionContainer { SaifMarkdown(message.content) }
                    message.toolCalls.forEach { tool ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${tool.name} · ${tool.status}", style = MaterialTheme.typography.labelLarge)
                                SelectionContainer { Text(tool.command, style = MaterialTheme.typography.bodySmall) }
                                if (tool.output.isNotEmpty()) SelectionContainer {
                                    Text(tool.output.takeLast(32768), style = MaterialTheme.typography.bodySmall)
                                }
                                if (tool.status == "PENDING_APPROVAL") Row {
                                    TextButton(onClick = { viewModel.approveMainTool(tool, message.id, null) }) { Text("Allow once") }
                                    TextButton(onClick = { viewModel.rejectMainTool(tool, message.id, "Declined by user") }) { Text("Deny") }
                                }
                                if (tool.status == "RUNNING") TextButton(onClick = { viewModel.terminateMainTool(tool, message.id) }) { Text("Stop command") }
                            }
                        }
                    }
                }
            }
            message.attachments.forEach { Text(it.name, style = MaterialTheme.typography.labelSmall) }
        }
        if (generating) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (messages.isNotEmpty()) item { TextButton(onClick = onRetry) { Text("Retry last response") } }
    }
}
