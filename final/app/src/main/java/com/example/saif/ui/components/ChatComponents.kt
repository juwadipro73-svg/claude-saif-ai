/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.saif.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SaifChatMessage
import com.example.saif.domain.model.ChatMessage

val LocalQuestionPanelActive = compositionLocalOf { false }
val LocalFileLinkHandler = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun UserMessageBubble(message: ChatMessage, modifier: Modifier =
    Modifier) {
    Column(modifier =
    modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape =
    RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.98f),
            modifier =
    Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.textContent,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.15.sp),
                modifier =
    Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, modelId: String, modifier: Modifier =
    Modifier) {
    Surface(
        shape =
    RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.49f),
        modifier =
    modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        SelectionContainer(modifier = Modifier) {
            Text(
                text = message.textContent,
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.15.sp),
                modifier =
    Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ThinkingAccordion
(
    thoughtText:
    String,
    durationMs: Long? = null,
    isStreaming:
    Boolean = false,
    modifier: Modifier =
    Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    Surface(
        shape =
    RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier =
    modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
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
                    text = "Thinking Process" + (durationMs?.let { " (${it / 1000}s)" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight =
    FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription =
    null,
                    modifier =
    Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = thoughtText,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    modifier =
    Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ModelTypingIndicator(modelId: String, modifier: Modifier =
    Modifier) {
    Row(
        verticalAlignment =
    Alignment.CenterVertically,
        modifier =
    modifier.padding(8.dp)
    ) {
        CircularProgressIndicator(modifier =
    Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(9.dp))
        Text("SAIF AI ($modelId) is thinking...", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AssistantMessageFooter(message: ChatMessage, onRetry: () -> Unit, modifier: Modifier =
    Modifier) {
    Row(
        modifier =
    modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement =
    Arrangement.End
    ) {
        TextButton(onClick = onRetry) {
            Text("Retry", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun WaveDivider(label: String, modifier: Modifier =
    Modifier) {
    Row(
        modifier =
    modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment =
    Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier =
    Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier =
    Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(modifier =
    Modifier.weight(1f))
    }
}
