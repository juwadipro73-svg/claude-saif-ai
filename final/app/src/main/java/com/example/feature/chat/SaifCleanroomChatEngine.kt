/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.*
import com.example.core.ports.SaifAgentBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cleanroom Unidirectional Chat Architecture for SAIF AI.
 * Distinct state machine and Composable hierarchy authored from docs/spec/chat_engine.md.
 */

data class SaifConversationFeedState(
    val transcript: List<SaifChatMessage> = emptyList(),
    val isEngineStreaming: Boolean = false,
    val securityQueue: List<SaifPendingApproval> = emptyList()
)

class SaifChatPresenter(
    private val agentBackend: SaifAgentBackend
) : ViewModel() {

    private val _feedState = MutableStateFlow(SaifConversationFeedState())
    val feedState: StateFlow<SaifConversationFeedState> = _feedState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            agentBackend.messageHistory.collect { entries ->
                _feedState.value = _feedState.value.copy(transcript = entries)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            agentBackend.isStreaming.collect { active ->
                _feedState.value = _feedState.value.copy(isEngineStreaming = active)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            agentBackend.pendingToolApprovals.collect { queue ->
                _feedState.value = _feedState.value.copy(securityQueue = queue)
            }
        }
    }

    fun postUserPrompt(text: String, stagedFiles: List<SaifChatAttachment> = emptyList()) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            agentBackend.dispatchTurn(text, stagedFiles)
        }
    }

    fun permitToolCall(callId: String, scope: SaifApprovalScope) {
        agentBackend.authorizeTool(callId, scope)
    }

    fun denyToolCall(callId: String, explanation: String? = null) {
        agentBackend.refuseTool(callId, explanation)
    }

    fun cancelTurn() {
        agentBackend.abortTurn()
    }
}

@Composable
fun SaifConversationScreen(
    state: SaifConversationFeedState,
    onPermit: (String, SaifApprovalScope) -> Unit,
    onDeny: (String, String?) -> Unit,
    viewModifier: Modifier = Modifier
) {
    val scrollPosition = rememberLazyListState()

    LaunchedEffect(state.transcript.size, state.isEngineStreaming) {
        if (state.transcript.isNotEmpty()) {
            scrollPosition.animateScrollToItem(state.transcript.lastIndex)
        }
    }

    Box(modifier = viewModifier.fillMaxSize(1.0f)) {
        LazyColumn(
            state = scrollPosition,
            modifier =
    Modifier.fillMaxSize(1.0f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 14.dp)
        ) {
            items(state.transcript, key = { it.messageId }) { item ->
                when (item.role) {
                    SaifMessageRole.USER -> SaifPromptRow(item)
                    SaifMessageRole.ASSISTANT -> SaifResponseRow(item)
                    else -> {}
                }
            }

            if (state.isEngineStreaming) {
                item {
                    Row(
                        verticalAlignment =
    Alignment.CenterVertically,
                        modifier =
    Modifier.padding(start = 6.dp, end = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier =
    Modifier.size(size = 14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(width = 8.5.dp))
                        Text("SAIF AI generation in progress...", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.securityQueue.isNotEmpty()) {
            val pendingItem = state.securityQueue.first()
            SaifSecurityGuardCard(
                pending = pendingItem,
                onGrant = { onPermit(pendingItem.toolCall.callId, it) },
                onRefuse = { onDeny(pendingItem.toolCall.callId, it) },
                cardModifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SaifPromptRow(entry: SaifChatMessage) {
    Column(modifier =
    Modifier.fillMaxWidth(1.0f), horizontalAlignment = Alignment.End) {
        Card(
            shape =
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier =
    Modifier.widthIn(min = 40.dp, max = 310.dp)
        ) {
            Text(
                text = entry.textContent,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                modifier =
    Modifier.padding(all = 12.dp)
            )
        }
    }
}

@Composable
private fun SaifResponseRow(entry: SaifChatMessage) {
    Column(modifier =
    Modifier.fillMaxWidth(1.0f), horizontalAlignment = Alignment.Start) {
        if (!entry.reasoningText.isNullOrBlank()) {
            var thoughtsExpanded by remember { mutableStateOf(false) }
            Card(
                shape =
    RoundedCornerShape(percent = 20),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier =
    Modifier.fillMaxWidth(1.0f).padding(bottom = 6.dp).clickable { thoughtsExpanded = !thoughtsExpanded }
            ) {
                Column(Modifier.padding(all = 8.dp)) {
                    Text("Model Deliberation", style = MaterialTheme.typography.labelMedium, fontWeight =
    FontWeight.SemiBold)
                    AnimatedVisibility(visible = thoughtsExpanded) {
                        Text(entry.reasoningText, style = MaterialTheme.typography.bodySmall, modifier =
    Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        SelectionContainer {
            Text(text = entry.textContent, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp))
        }

        if (entry.toolInvocations.isNotEmpty()) {
            Column(modifier =
    Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                for (invocation in entry.toolInvocations) {
                    SaifExecutionBadge(tool = invocation)
                }
            }
        }
    }
}

@Composable
private fun SaifExecutionBadge(tool: SaifToolCall) {
    val ctx = LocalContext.current
    Surface(
        shape =
    RoundedCornerShape(percent = 16),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier =
    Modifier.fillMaxWidth(1.0f)
    ) {
        Row(
            modifier =
    Modifier.padding(all = 8.dp),
            verticalAlignment =
    Alignment.CenterVertically,
            horizontalArrangement =
    Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment =
    Alignment.CenterVertically, modifier =
    Modifier.weight(1.0f, fill = false)) {
                Icon(Icons.Default.Terminal, contentDescription =
    null, modifier =
    Modifier.size(16.dp))
                Spacer(Modifier.width(width = 6.5.dp))
                Text(
                    text = "${tool.operationName}: ${tool.commandText.take(28)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize =
    11.5.sp),
                    fontFamily =
    FontFamily.Monospace
                )
            }
            if (tool.standardOutput.isNotBlank()) {
                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("output", tool.standardOutput)
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(ctx, "Output copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier =
    Modifier.size(size = 20.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription =
    null, modifier =
    Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SaifSecurityGuardCard(
    pending: SaifPendingApproval,
    onGrant: (SaifApprovalScope) -> Unit,
    onRefuse: (String?) -> Unit,
    cardModifier: Modifier = Modifier
) {
    Card(
        modifier = cardModifier.fillMaxWidth(1.0f).padding(all = 10.dp),
        shape =
    RoundedCornerShape(percent = 28),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(all = 12.dp)) {
            Row(verticalAlignment =
    Alignment.CenterVertically, modifier =
    Modifier.wrapContentHeight()) {
                Icon(Icons.Default.Security, contentDescription =
    null, tint = MaterialTheme.colorScheme.primary, modifier =
    Modifier.size(18.dp))
                Spacer(Modifier.width(width = 6.5.dp))
                Text("Authorisation Request", style = MaterialTheme.typography.titleSmall, fontWeight =
    FontWeight.Bold)
            }
            Spacer(Modifier.height(height = 4.5.dp))
            Text(pending.toolCall.commandText.ifBlank { pending.toolCall.operationName }, fontFamily =
    FontFamily.Monospace, fontSize =
    11.5.sp)
            Spacer(Modifier.height(height = 8.5.dp))
            Row(Modifier.fillMaxWidth(1.0f), horizontalArrangement =
    Arrangement.End) {
                TextButton(onClick = { onRefuse(null) }) { Text("Refuse") }
                Spacer(Modifier.width(width = 6.5.dp))
                Button(onClick = { onGrant(SaifApprovalScope.ONCE) }) { Text("Authorize") }
            }
        }
    }
}
