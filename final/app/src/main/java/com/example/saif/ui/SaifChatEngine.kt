/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saif.backend.SaifUniversalAgentBackend
import com.example.saif.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 100% Original SAIF AI Chat ViewModel.
 * Drives chat feed, tool approvals, and backend streaming.
 */
class SaifChatViewModel(
    private val backend: AgentBackend = SaifUniversalAgentBackend()
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = backend.messages
    val isStreaming: StateFlow<Boolean> = backend.isStreaming
    val pendingApprovals: StateFlow<List<PendingToolApproval>> = backend.pendingApprovals

    fun sendMessage(prompt: String, attachments: List<ChatAttachment> = emptyList()) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            backend.sendMessage(prompt, attachments)
        }
    }

    fun approveTool(toolCall: ToolCall, messageId: String, scope: String? = null) {
        backend.approveTool(toolCall, messageId, scope)
    }

    fun rejectTool(toolCall: ToolCall, messageId: String, reason: String? = null) {
        backend.rejectTool(toolCall, messageId, reason)
    }

    fun stop() {
        backend.stop()
    }
}

/**
 * SAIF AI Markdown Renderer.
 * Supports headings, bold/italic, code blocks with copy, quotes, and lists.
 */
@Composable
fun SaifMarkdown(
    content: String,
    modifier: Modifier =
    Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context =
    LocalContext.current
    val lines = remember(content) { content.lines() }

    Column(modifier =
    modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var inCodeBlock = false
        var codeLang = ""
        val codeBuffer = mutableListOf<String>()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    val codeContent = codeBuffer.joinToString("\n")
                    SaifCodeBlock(codeContent = codeContent, language = codeLang)
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.add(line)
                continue
            }

            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight =
    FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight =
    FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight =
    FontWeight.ExtraBold,
                        color = textColor
                    )
                }
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier =
    Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Box(
                            modifier =
    Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier =
    Modifier.padding(start = 6.dp)) {
                        Text("• ", fontWeight =
    FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(2)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                trimmed.isNotBlank() -> {
                    SelectionContainer(modifier = Modifier) {
                        Text(
                            text = parseInlineMarkdown(line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            SaifCodeBlock(codeContent = codeBuffer.joinToString("\n"), language = codeLang)
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight =
    FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily =
    FontFamily.Monospace,
                                background = Color(0x22888888),
                                fontSize =
    13.sp
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun SaifCodeBlock(codeContent: String, language: String) {
    val context =
    LocalContext.current
    Surface(
        shape =
    RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E2E),
        modifier =
    Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column {
            Row(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF28283E))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                horizontalArrangement =
    Arrangement.SpaceBetween,
                verticalAlignment =
    Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCCCCCC),
                    fontFamily =
    FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("code", codeContent)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier =
    Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = Color(0xFFCCCCCC),
                        modifier =
    Modifier.size(16.dp)
                    )
                }
            }
            SelectionContainer(modifier = Modifier) {
                Text(
                    text = codeContent,
                    color = Color(0xFFE2E8F0),
                    fontFamily =
    FontFamily.Monospace,
                    fontSize =
    12.5.sp,
                    modifier =
    Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Tool Call Inspection Card.
 */
@Composable
fun SaifToolCallCard(toolCall: ToolCall, modifier: Modifier =
    Modifier) {
    var isBlockExpanded by remember { mutableStateOf(value = false) }

    Surface(
        shape =
    RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier =
    modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(modifier =
    Modifier.padding(10.dp)) {
            Row(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .clickable { isBlockExpanded = !isBlockExpanded },
                verticalAlignment =
    Alignment.CenterVertically,
                horizontalArrangement =
    Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment =
    Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (toolCall.toolType) {
                            ToolType.BASH -> Icons.Default.Terminal
                            ToolType.VIEW_FILE, ToolType.EDIT_FILE -> Icons.Default.Code
                            ToolType.SEARCH_WEB, ToolType.READ_URL -> Icons.Default.Language
                            else -> Icons.Default.Build
                        },
                        contentDescription =
    null,
                        modifier =
    Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "${toolCall.name} ${toolCall.command.take(30)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily =
    FontFamily.Monospace,
                        maxLines = 1,
                        overflow =
    TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (isBlockExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription =
    null,
                    modifier =
    Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isBlockExpanded) {
                Column(modifier =
    Modifier.padding(top = 8.dp)) {
                    if (toolCall.command.isNotBlank()) {
                        Text("Input / Command:", style = MaterialTheme.typography.labelSmall, fontWeight =
    FontWeight.Bold)
                        Surface(
                            shape =
    RoundedCornerShape(4.dp),
                            color = Color(0xFF1E1E2E),
                            modifier =
    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = toolCall.command,
                                color =
    Color.White,
                                fontFamily =
    FontFamily.Monospace,
                                fontSize =
    11.5.sp,
                                modifier =
    Modifier.padding(8.dp)
                            )
                        }
                    }
                    if (toolCall.output.isNotBlank()) {
                        Text("Output:", style = MaterialTheme.typography.labelSmall, fontWeight =
    FontWeight.Bold)
                        Surface(
                            shape =
    RoundedCornerShape(4.dp),
                            color = Color(0xFF181825),
                            modifier =
    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = toolCall.output,
                                color = Color(0xFFA6E3A1),
                                fontFamily =
    FontFamily.Monospace,
                                fontSize =
    11.5.sp,
                                modifier =
    Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tool Approval Panel docked above Chat Input.
 */
@Composable
fun SaifAgentApprovalPanel(
    pendingApprovals: List<PendingToolApproval>,
    onApprove: (ToolCall, String, String?) -> Unit,
    onReject: (ToolCall, String, String?) -> Unit,
    modifier: Modifier =
    Modifier
) {
    val approval = pendingApprovals.firstOrNull() ?: return

    Surface(
        modifier =
    modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape =
    RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier =
    Modifier.padding(14.dp)) {
            Row(verticalAlignment =
    Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription =
    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
    Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "Tool Permission Request",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight =
    FontWeight.Bold
                )
                if (pendingApprovals.size > 1) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "(1 of ${pendingApprovals.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SAIF Agent is requesting to execute:",
                style = MaterialTheme.typography.bodySmall
            )
            Surface(
                shape =
    RoundedCornerShape(6.dp),
                color = Color(0xFF1E1E2E),
                modifier =
    Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = approval.toolCall.command.ifBlank { approval.toolCall.name },
                    color =
    Color.White,
                    fontFamily =
    FontFamily.Monospace,
                    fontSize =
    12.sp,
                    modifier =
    Modifier.padding(8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier =
    Modifier.fillMaxWidth(),
                horizontalArrangement =
    Arrangement.End,
                verticalAlignment =
    Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onReject(approval.toolCall, approval.messageId, null) },
                    shape =
    RoundedCornerShape(8.dp)
                ) {
                    Text("Deny")
                }
                Spacer(modifier = Modifier.width(9.dp))
                Button(
                    onClick = { onApprove(approval.toolCall, approval.messageId, null) },
                    shape =
    RoundedCornerShape(8.dp)
                ) {
                    Text("Allow Once")
                }
            }
        }
    }
}

/**
 * Message Feed Composable.
 */
@Composable
fun SaifMessageFeed(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    modifier: Modifier =
    Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        state = listState,
        modifier =
    modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                MessageRole.USER -> SaifUserMessageBubble(message)
                MessageRole.ASSISTANT -> SaifAssistantMessageBubble(message)
                else -> {}
            }
        }
        if (isStreaming) {
            item {
                Row(
                    verticalAlignment =
    Alignment.CenterVertically,
                    modifier =
    Modifier.padding(horizontal = 8.dp)
                ) {
                    CircularProgressIndicator(modifier =
    Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(9.dp))
                    Text("Thinking & responding...", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SaifUserMessageBubble(message: ChatMessage) {
    Column(
        modifier =
    Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape =
    RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier =
    Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.content,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
    Modifier.padding(14.dp)
            )
        }
        if (message.attachments.isNotEmpty()) {
            Row(
                modifier =
    Modifier.padding(top = 4.dp),
                horizontalArrangement =
    Arrangement.spacedBy(4.dp)
            ) {
                for (att in message.attachments) {
                    Surface(
                        shape =
    RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = att.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier =
    Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SaifAssistantMessageBubble(message: ChatMessage) {
    Column(
        modifier =
    Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (!message.thoughtText.isNullOrBlank()) {
            var thoughtExpanded by remember { mutableStateOf(false) }
            Surface(
                shape =
    RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier =
    Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clickable { thoughtExpanded = !thoughtExpanded }
            ) {
                Column(modifier =
    Modifier.padding(10.dp)) {
                    Row(
                        modifier =
    Modifier.fillMaxWidth(),
                        horizontalArrangement =
    Arrangement.SpaceBetween,
                        verticalAlignment =
    Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thinking Process",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight =
    FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (thoughtExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription =
    null,
                            modifier =
    Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = thoughtExpanded) {
                        Text(
                            text = message.thoughtText,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            modifier =
    Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        SaifMarkdown(content = message.content)

        if (message.toolCalls.isNotEmpty()) {
            Column(modifier =
    Modifier.padding(top = 8.dp)) {
                for (tool in message.toolCalls) {
                    SaifToolCallCard(toolCall = tool)
                }
            }
        }
    }
}
