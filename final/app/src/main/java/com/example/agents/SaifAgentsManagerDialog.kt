/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.saif.data.agent.AgentPreferences
import com.example.saif.domain.model.AgentKind
import com.example.ui.theme.SaifTheme

@Composable
fun SaifAgentsManagerDialog(
    onDismiss: () -> Unit
) {
    val context =
    LocalContext.current
    LaunchedEffect(key1 = Unit) { SaifAgentRegistry.ensureLoaded(context) }
    val registryState by SaifAgentRegistry.state.collectAsState()
    val agentPrefs = remember(context) { AgentPreferences(context) }
    val enabledCoreAgents by agentPrefs.enabled.collectAsState()

    var editingAgent by remember { mutableStateOf<SaifSpecialistAgent?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    val colors = SaifTheme.colors
    val accent = Color(0xFF9B82EC)
    val green = Color(0xFF10B981)
    val panelBg = if (colors.isLight) Color(0xFFFDFCF9) else Color(0xFF171615)
    val cardBg = if (colors.isLight) Color(0xFFF3F1EA) else Color(0xFF22201E)

    Dialog(
        onDismissRequest =
    onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier =
    Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape =
    RoundedCornerShape(24.dp),
            color = panelBg,
            border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.12f))
        ) {
            Column(modifier =
    Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier =
    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment =
    Alignment.CenterVertically,
                    horizontalArrangement =
    Arrangement.SpaceBetween
                ) {
                    Column(modifier =
    Modifier.weight(1f)) {
                        Text(
                            text = "AI Agents & Orchestration",
                            color = colors.textPrimary,
                            fontSize =
    18.sp,
                            fontWeight =
    FontWeight.Bold
                        )
                        Text(
                            text = "${registryState.enabledAgents.size} of ${registryState.agents.size} specialist agents enabled",
                            color = colors.textSecondary,
                            fontSize =
    12.sp
                        )
                    }
                    Row(horizontalArrangement =
    Arrangement.spacedBy(6.dp), verticalAlignment =
    Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                creatingNew = true
                                editingAgent = SaifSpecialistAgent(
                                    id = "",
                                    slug = "",
                                    name = "",
                                    category = SaifAgentCategory.CUSTOM,
                                    description = "",
                                    systemPrompt = "",
                                    enabled = true,
                                    isBuiltIn = false
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription =
    null, modifier =
    Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Agent", fontSize =
    12.sp, fontWeight =
    FontWeight.SemiBold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                    }
                }

                HorizontalDivider(color = colors.textPrimary.copy(alpha = 0.08f))

                Column(
                    modifier =
    Modifier
                        .weight(1f)
                        .verticalScroll(state = rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Core Execution Runtime Selector (Antigravity vs Claude Code)
                    Surface(
                        shape =
    RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                    ) {
                        Column(modifier =
    Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "CORE EXECUTION ENGINE",
                                color = accent,
                                fontSize =
    11.sp,
                                fontWeight =
    FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Row(
                                modifier =
    Modifier.fillMaxWidth(),
                                horizontalArrangement =
    Arrangement.spacedBy(8.dp)
                            ) {
                                CoreRuntimeChip(
                                    title = "Antigravity Agent",
                                    subtitle = "Workspace + Multi-Model",
                                    selected = AgentKind.AGY in enabledCoreAgents,
                                    onClick = { agentPrefs.setEnabled(enabledCoreAgents + AgentKind.AGY) },
                                    modifier =
    Modifier.weight(1f)
                                )
                                CoreRuntimeChip(
                                    title = "Claude Code CLI",
                                    subtitle = "Terminal Coding Agent",
                                    selected = AgentKind.CLAUDE in enabledCoreAgents,
                                    onClick = { agentPrefs.setEnabled(enabledCoreAgents + AgentKind.CLAUDE) },
                                    modifier =
    Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = colors.textPrimary.copy(alpha = 0.08f))

                            Row(
                                modifier =
    Modifier.fillMaxWidth(),
                                verticalAlignment =
    Alignment.CenterVertically,
                                horizontalArrangement =
    Arrangement.SpaceBetween
                            ) {
                                Column(modifier =
    Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Specialist Auto-Routing",
                                        color = colors.textPrimary,
                                        fontSize =
    14.sp,
                                        fontWeight =
    FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Automatically dispatches Coding, Research, Automation, Analysis, or Content agents when no specialist is pinned",
                                        color = colors.textSecondary,
                                        fontSize =
    11.sp
                                    )
                                }
                                Switch(
                                    checked = registryState.autoRouteByIntent,
                                    onCheckedChange = { SaifAgentRegistry.setAutoRouteByIntent(context, it) }
                                )
                            }

                            if (registryState.activeAgent != null) {
                                Surface(
                                    shape =
    RoundedCornerShape(12.dp),
                                    color = green.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, green.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier =
    Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment =
    Alignment.CenterVertically,
                                        horizontalArrangement =
    Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Pinned Active Agent: ${registryState.activeAgent!!.name}",
                                            color = green,
                                            fontSize =
    12.sp,
                                            fontWeight =
    FontWeight.SemiBold,
                                            modifier =
    Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { SaifAgentRegistry.setActiveAgent(context, null) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Unpin", fontSize =
    12.sp, color = green)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Specialist Agents List
                    Row(
                        modifier =
    Modifier.fillMaxWidth(),
                        verticalAlignment =
    Alignment.CenterVertically,
                        horizontalArrangement =
    Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SPECIALIST AI AGENTS",
                            color = accent,
                            fontSize =
    11.sp,
                            fontWeight =
    FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        TextButton(
                            onClick = { SaifAgentRegistry.resetBuiltInDefaults(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Reset Built-ins", fontSize =
    11.sp, color = colors.textSecondary)
                        }
                    }

                    registryState.agents.forEach { agent ->
                        val isPinned = registryState.activeAgentId == agent.id
                        Surface(
                            shape =
    RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isPinned -> green.copy(alpha = 0.7f)
                                    agent.enabled -> accent.copy(alpha = 0.25f)
                                    else -> colors.textPrimary.copy(alpha = 0.08f)
                                }
                            )
                        ) {
                            Column(
                                modifier =
    Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier =
    Modifier.fillMaxWidth(),
                                    verticalAlignment =
    Alignment.CenterVertically,
                                    horizontalArrangement =
    Arrangement.SpaceBetween
                                ) {
                                    Column(modifier =
    Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment =
    Alignment.CenterVertically,
                                            horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = agent.name,
                                                color = colors.textPrimary,
                                                fontSize =
    15.sp,
                                                fontWeight =
    FontWeight.Bold
                                            )
                                            Surface(
                                                shape =
    RoundedCornerShape(8.dp),
                                                color = accent.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = agent.category.displayName,
                                                    color = accent,
                                                    fontSize =
    10.sp,
                                                    fontWeight =
    FontWeight.SemiBold,
                                                    modifier =
    Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = agent.slashTrigger,
                                            color = colors.textSecondary,
                                            fontSize =
    11.sp
                                        )
                                    }
                                    Switch(
                                        checked = agent.enabled,
                                        onCheckedChange = { SaifAgentRegistry.setAgentEnabled(context, agent.id, it) }
                                    )
                                }

                                Text(
                                    text = agent.description,
                                    color = colors.textSecondary,
                                    fontSize =
    12.sp,
                                    lineHeight = 16.sp
                                )

                                // Capability pills
                                Row(
                                    modifier =
    Modifier.horizontalScroll(state = rememberScrollState()),
                                    horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                                ) {
                                    AgentCapBadge("Terminal", agent.allowTerminal)
                                    AgentCapBadge("File Edits", agent.allowFileEdits)
                                    AgentCapBadge("Web Fetch", agent.allowWebFetch)
                                    AgentCapBadge("Temp ${String.format("%.2f", agent.temperature)}", true)
                                }

                                Row(
                                    modifier =
    Modifier.fillMaxWidth(),
                                    horizontalArrangement =
    Arrangement.spacedBy(8.dp),
                                    verticalAlignment =
    Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isPinned) {
                                                SaifAgentRegistry.setActiveAgent(context, null)
                                            } else {
                                                if (!agent.enabled) {
                                                    SaifAgentRegistry.setAgentEnabled(context, agent.id, true)
                                                }
                                                SaifAgentRegistry.setActiveAgent(context, agent.id)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isPinned) "Pinned (Tap to Unpin)" else "Set Active",
                                            fontSize =
    12.sp,
                                            color = if (isPinned) green else accent
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            creatingNew = false
                                            editingAgent = agent
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Outlined.Tune, contentDescription =
    null, modifier =
    Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Configure", fontSize =
    12.sp)
                                    }
                                    if (!agent.isBuiltIn) {
                                        TextButton(
                                            onClick = { SaifAgentRegistry.deleteCustomAgent(context, agent.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Delete", fontSize =
    12.sp, color = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingAgent != null) {
        AgentEditorModal(
            initial = editingAgent!!,
            isNew = creatingNew,
            onDismiss = { editingAgent = null },
            onSave = { updated ->
                SaifAgentRegistry.saveAgent(context, updated)
                editingAgent = null
            }
        )
    }
}

@Composable
private fun CoreRuntimeChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    val colors = SaifTheme.colors
    val accent = Color(0xFF9B82EC)
    Surface(
        modifier =
    modifier
            .clip(shape = RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape =
    RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else colors.textPrimary.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, if (selected) accent else colors.textPrimary.copy(alpha = 0.10f))
    ) {
        Column(modifier =
    Modifier.padding(10.dp)) {
            Row(verticalAlignment =
    Alignment.CenterVertically, horizontalArrangement =
    Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier =
    Modifier
                        .size(8.dp)
                        .background(if (selected) Color(0xFF10B981) else colors.textSecondary, CircleShape)
                )
                Text(title, color = colors.textPrimary, fontSize =
    13.sp, fontWeight =
    FontWeight.SemiBold)
            }
            Text(subtitle, color = colors.textSecondary, fontSize =
    11.sp)
        }
    }
}

@Composable
private fun AgentCapBadge(label: String, enabled: Boolean) {
    val colors = SaifTheme.colors
    val tint = if (enabled) Color(0xFF10B981) else colors.textSecondary.copy(alpha = 0.55f)
    Surface(
        shape =
    RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f))
    ) {
        Text(
            text = if (enabled) "✓ $label" else "✕ $label",
            color = tint,
            fontSize =
    10.sp,
            fontWeight =
    FontWeight.Medium,
            modifier =
    Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AgentEditorModal(
    initial: SaifSpecialistAgent,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SaifSpecialistAgent) -> Unit
) {
    val colors = SaifTheme.colors
    val panelBg = if (colors.isLight) Color(0xFFFDFCF9) else Color(0xFF1B1A18)

    var name by remember(initial) { mutableStateOf(initial.name) }
    var slug by remember(initial) { mutableStateOf(initial.slug) }
    var category by remember(initial) { mutableStateOf(initial.category) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var systemPrompt by remember(initial) { mutableStateOf(initial.systemPrompt) }
    var allowTerminal by remember(initial) { mutableStateOf(initial.allowTerminal) }
    var allowFileEdits by remember(initial) { mutableStateOf(initial.allowFileEdits) }
    var allowWebFetch by remember(initial) { mutableStateOf(initial.allowWebFetch) }
    var temperature by remember(initial) { mutableStateOf(initial.temperature) }

    Dialog(onDismissRequest =
    onDismiss) {
        Surface(
            shape =
    RoundedCornerShape(20.dp),
            color = panelBg,
            border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.12f)),
            modifier =
    Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
    Modifier
                    .padding(18.dp)
                    .verticalScroll(state = rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isNew) "Create Custom Agent" else "Configure ${initial.name}",
                    color = colors.textPrimary,
                    fontSize =
    17.sp,
                    fontWeight =
    FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (isNew && slug.isBlank()) {
                            slug = it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        }
                    },
                    label = { Text("Agent Name") },
                    singleLine =
    true,
                    modifier =
    Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it },
                    enabled = !initial.isBuiltIn,
                    label = { Text("Slash Trigger Slug (/agent-<slug>)") },
                    singleLine =
    true,
                    modifier =
    Modifier.fillMaxWidth()
                )

                if (!initial.isBuiltIn) {
                    Text("Category", color = colors.textSecondary, fontSize =
    12.sp)
                    Row(
                        modifier =
    Modifier.horizontalScroll(state = rememberScrollState()),
                        horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                    ) {
                        SaifAgentCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.displayName, fontSize =
    11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    maxLines = 2,
                    modifier =
    Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instructions & Persona") },
                    minLines = 4,
                    maxLines = 8,
                    modifier =
    Modifier.fillMaxWidth()
                )

                Text(
                    text = "Sampling Temperature: ${String.format("%.2f", temperature)}",
                    color = colors.textPrimary,
                    fontSize =
    12.sp,
                    fontWeight =
    FontWeight.Medium
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1.2f
                )

                Row(verticalAlignment =
    Alignment.CenterVertically, horizontalArrangement =
    Arrangement.SpaceBetween, modifier =
    Modifier.fillMaxWidth()) {
                    Text("Allow Terminal Command Execution", color = colors.textPrimary, fontSize =
    13.sp)
                    Switch(checked = allowTerminal, onCheckedChange = { allowTerminal = it })
                }
                Row(verticalAlignment =
    Alignment.CenterVertically, horizontalArrangement =
    Arrangement.SpaceBetween, modifier =
    Modifier.fillMaxWidth()) {
                    Text("Allow Workspace File Edits", color = colors.textPrimary, fontSize =
    13.sp)
                    Switch(checked = allowFileEdits, onCheckedChange = { allowFileEdits = it })
                }
                Row(verticalAlignment =
    Alignment.CenterVertically, horizontalArrangement =
    Arrangement.SpaceBetween, modifier =
    Modifier.fillMaxWidth()) {
                    Text("Allow Web & URL Research", color = colors.textPrimary, fontSize =
    13.sp)
                    Switch(checked = allowWebFetch, onCheckedChange = { allowWebFetch = it })
                }

                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    horizontalArrangement =
    Arrangement.End,
                    verticalAlignment =
    Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                initial.copy(
                                    name = name,
                                    slug = slug,
                                    category = category,
                                    description = description,
                                    systemPrompt = systemPrompt,
                                    allowTerminal = allowTerminal,
                                    allowFileEdits = allowFileEdits,
                                    allowWebFetch = allowWebFetch,
                                    temperature = temperature
                                )
                            )
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save Agent")
                    }
                }
            }
        }
    }
}
