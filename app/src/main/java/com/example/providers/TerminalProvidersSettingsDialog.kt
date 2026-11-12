package com.example.providers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agyext.AgyApiProtocol
import com.example.agyext.AntigravityCustomApiManager
import com.example.ui.theme.SaifTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact background setup, retry, cancel & status banner displayed at the top of SaifAiApp
 * while a terminal provider environment is preparing or needs attention.
 */
@Composable
fun MainScreenProviderProgressBanner(
    onOpenProvidersDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ecosystem by TerminalProviderManager.state.collectAsState()
    val active = ecosystem.activeProvider ?: return

    val showBanner = ecosystem.isPreparingInBackground ||
        ecosystem.readiness == TerminalProviderReadiness.FAILED ||
        ecosystem.readiness == TerminalProviderReadiness.DEGRADED ||
        ecosystem.readiness == TerminalProviderReadiness.NEEDS_CONFIGURATION

    if (!showBanner) return

    val colors = SaifTheme.colors
    val accent = when (ecosystem.readiness) {
        TerminalProviderReadiness.FAILED -> Color(0xFFEF4444)
        TerminalProviderReadiness.DEGRADED, TerminalProviderReadiness.NEEDS_CONFIGURATION -> Color(0xFFF59E0B)
        else -> Color(0xFF8B6CEF)
    }
    val cardBg = if (colors.isLight) Color(0xFFF6F4EE) else Color(0xFF201F1D)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    // The silently selected default is simply "the workspace" to the user, not a provider to choose.
                    val bannerTitle = if (active.id == TerminalProviderManager.DEFAULT_PROVIDER_ID) "Workspace" else active.name
                    Text(
                        text = "$bannerTitle: ${ecosystem.readiness.displayLabel}",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ecosystem.isPreparingInBackground) {
                        TextButton(
                            onClick = { TerminalProviderManager.cancelPreparation(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp, color = Color(0xFFEF4444))
                        }
                    } else if (ecosystem.canRetry) {
                        TextButton(
                            onClick = { TerminalProviderManager.retryPreparation(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Retry", fontSize = 11.sp, color = accent)
                        }
                    }
                    // No provider list on the main screen during the normal first-run set-up. The way into the
                    // provider settings is offered only when something needs the user's attention.
                    if (!ecosystem.isPreparingInBackground) {
                        TextButton(
                            onClick = onOpenProvidersDialog,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Fix", fontSize = 11.sp, color = accent)
                        }
                    }
                }
            }

            if (ecosystem.statusMessage.isNotBlank()) {
                Text(
                    text = ecosystem.statusMessage,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (ecosystem.isPreparingInBackground) {
                LinearProgressIndicator(
                    progress = { ecosystem.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = colors.textPrimary.copy(alpha = 0.10f)
                )
            }
        }
    }
}

/**
 * Dedicated Settings → Providers Management Hub (Sections 6, 7 & 9).
 *
 * Supports full Custom Provider CRUD, Android KeyStore encrypted credentials, protocol adapters,
 * connection testing, model fetching, default/active switching, multi-session terminal management,
 * AI terminal command assistance, and setup/health diagnostics.
 */
@Composable
fun TerminalProvidersSettingsDialog(
    onDismiss: () -> Unit,
    onOpenTerminalSession: ((PersistentTerminalSession) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TerminalProviderManager.ensureLoaded(context) }
    val ecosystem by TerminalProviderManager.state.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Providers, 1: Sessions & AI Helper, 2: Diagnostics
    var editingSpec by remember { mutableStateOf<TerminalProviderSpec?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }

    // AI Terminal Command Assistant state
    var commandGoal by remember { mutableStateOf("") }
    var suggestedCommand by remember { mutableStateOf("") }
    var isSuggestingCommand by remember { mutableStateOf(false) }

    val colors = SaifTheme.colors
    val accent = Color(0xFF8B6CEF)
    val green = Color(0xFF10B981)
    val panelBg = if (colors.isLight) Color(0xFFFDFCF9) else Color(0xFF161514)
    val cardBg = if (colors.isLight) Color(0xFFF2F0E8) else Color(0xFF21201E)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = panelBg,
            border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Terminal & AI Providers",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active: ${ecosystem.activeProvider?.name ?: "None"} · ${ecosystem.readiness.displayLabel}",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                editingSpec = null
                                showEditor = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Provider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                    }
                }

                // Sub-navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = accent
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Providers (${ecosystem.providers.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Sessions & AI CLI", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Health & Logs", fontSize = 12.sp) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Intelligent Auto-Failover toggle
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Intelligent Provider Failover & Reconnect",
                                            color = colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Automatically falls back to a healthy configured provider on outage",
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = ecosystem.autoFailoverEnabled,
                                        onCheckedChange = { TerminalProviderManager.setAutoFailover(context, it) }
                                    )
                                }
                            }

                            ecosystem.providers.forEach { prov ->
                                val isActive = ecosystem.activeProviderId == prov.id
                                val isDefault = ecosystem.defaultProviderId == prov.id
                                val isTesting = testingProviderId == prov.id
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) green else colors.textPrimary.copy(alpha = 0.10f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = prov.name,
                                                        color = colors.textPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (isActive) {
                                                        ProviderBadge("ACTIVE", green)
                                                    }
                                                    if (isDefault) {
                                                        ProviderBadge("DEFAULT", accent)
                                                    }
                                                }
                                                Text(
                                                    text = "${prov.mode.badgeLabel} · ${prov.protocol.label}",
                                                    color = colors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text = prov.description,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )

                                        if (prov.endpointUrl.isNotBlank()) {
                                            Text(
                                                text = "Endpoint: ${prov.endpointUrl} · Key: ${prov.maskedKeyPreview()} · Model: ${prov.selectedModel.ifBlank { "auto" }}",
                                                color = colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (prov.lastHealthMessage.isNotBlank()) {
                                            val healthColor = when (prov.lastHealthOk) {
                                                true -> green
                                                false -> Color(0xFFF59E0B)
                                                null -> colors.textSecondary
                                            }
                                            Text(
                                                text = "Status: ${prov.lastHealthMessage}",
                                                color = healthColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (!isActive) {
                                                Button(
                                                    onClick = {
                                                        TerminalProviderManager.selectAndPrepareProvider(
                                                            context = context,
                                                            providerId = prov.id,
                                                            setAsDefault = false
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                                ) {
                                                    Text("Switch Active", fontSize = 11.sp, color = Color.White)
                                                }
                                            }
                                            if (!isDefault) {
                                                OutlinedButton(
                                                    onClick = { TerminalProviderManager.setDefaultProvider(context, prov.id) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Set Default", fontSize = 11.sp)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    testingProviderId = prov.id
                                                    scope.launch {
                                                        TerminalProviderManager.testProviderConnection(context, prov)
                                                        testingProviderId = null
                                                    }
                                                },
                                                enabled = !isTesting,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isTesting) "Testing..." else "Test Connection", fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    editingSpec = prov
                                                    showEditor = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Edit / Configure", fontSize = 11.sp)
                                            }
                                            if (!prov.isBuiltIn) {
                                                TextButton(
                                                    onClick = { TerminalProviderManager.deleteProvider(context, prov.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Delete", fontSize = 11.sp, color = Color(0xFFEF4444))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Multi-Session Terminal Persistence & AI Command Assistant (Section 9)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "AI TERMINAL COMMAND ASSISTANT",
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Describe any Linux, Git, Python, or APK task and generate a ready-to-run shell command via ${ecosystem.activeProvider?.name ?: "active provider"}.",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                    OutlinedTextField(
                                        value = commandGoal,
                                        onValueChange = { commandGoal = it },
                                        label = { Text("e.g. Find largest files in workspace or start Python HTTP server") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            if (commandGoal.isBlank()) return@Button
                                            isSuggestingCommand = true
                                            scope.launch {
                                                val res = TerminalProviderManager.suggestTerminalCommand(context, commandGoal)
                                                suggestedCommand = res.getOrElse { "Error: ${it.message}" }
                                                isSuggestingCommand = false
                                            }
                                        },
                                        enabled = commandGoal.isNotBlank() && !isSuggestingCommand,
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                                    ) {
                                        Text(
                                            text = if (isSuggestingCommand) "Generating Command..." else "Generate Command",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (suggestedCommand.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF101012),
                                            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = suggestedCommand,
                                                    color = Color(0xFF10B981),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = {
                                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        cm?.setPrimaryClip(ClipData.newPlainText("SAIF Command", suggestedCommand))
                                                        Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text("Copy", fontSize = 11.sp, color = accent)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Persistent Multi-Session Terminal List
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PERSISTENT TERMINAL SESSIONS",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                FilledTonalButton(
                                    onClick = { TerminalProviderManager.createTerminalSession(context) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New Session", fontSize = 11.sp)
                                }
                            }

                            ecosystem.terminalSessions.forEach { sess ->
                                val isCurrent = ecosystem.activeTerminalSessionId == sess.id
                                val provName = ecosystem.providers.firstOrNull { it.id == sess.providerId }?.name ?: sess.providerId
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) green.copy(alpha = 0.7f) else colors.textPrimary.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sess.title,
                                                color = colors.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Provider: $provName · Dir: ${sess.workingDir}",
                                                color = colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    TerminalProviderManager.switchTerminalSession(context, sess.id)
                                                    onOpenTerminalSession?.invoke(sess)
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isCurrent) "Open" else "Switch", fontSize = 11.sp)
                                            }
                                            if (ecosystem.terminalSessions.size > 1) {
                                                TextButton(
                                                    onClick = { TerminalProviderManager.closeTerminalSession(context, sess.id) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Close", fontSize = 11.sp, color = Color(0xFFEF4444))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Resource Diagnostics & Setup History
                            val freeMb = TerminalProviderManager.checkAvailableStorageMb(context)
                            val netUp = TerminalProviderManager.isNetworkAvailable(context)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ENVIRONMENT DIAGNOSTICS",
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text("Readiness: ${ecosystem.readiness.displayLabel}", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Free Disk Storage: $freeMb MB", color = colors.textSecondary, fontSize = 12.sp)
                                    Text("Network Connectivity: ${if (netUp) "Online" else "Offline"}", color = colors.textSecondary, fontSize = 12.sp)
                                    Text("Credential Encryption: Android KeyStore AES-256-GCM", color = green, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { TerminalProviderManager.retryPreparation(context) }) {
                                            Text("Re-Verify Environment", fontSize = 12.sp)
                                        }
                                        if (ecosystem.isPreparingInBackground) {
                                            OutlinedButton(onClick = { TerminalProviderManager.cancelPreparation(context) }) {
                                                Text("Cancel Active Setup", fontSize = 12.sp, color = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "DOWNLOAD & HEALTH HISTORY",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            if (ecosystem.installHistory.isEmpty()) {
                                Text("No installation or health events logged yet.", color = colors.textSecondary, fontSize = 12.sp)
                            } else {
                                val fmt = remember { SimpleDateFormat("MMM dd HH:mm:ss", Locale.US) }
                                ecosystem.installHistory.reversed().forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.06f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.providerName, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                Text(fmt.format(Date(item.timestampMs)), color = colors.textSecondary, fontSize = 10.sp)
                                            }
                                            Text("${item.status} · ${item.detail}", color = colors.textSecondary, fontSize = 11.sp)
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

    if (showEditor) {
        CustomProviderEditorDialog(
            initial = editingSpec,
            onDismiss = { showEditor = false },
            onSave = { updated, setAsDefault ->
                TerminalProviderManager.upsertProvider(
                    context = context,
                    spec = updated,
                    setAsDefault = setAsDefault,
                    activateNow = true
                )
                showEditor = false
            }
        )
    }
}

@Composable
private fun ProviderBadge(text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CustomProviderEditorDialog(
    initial: TerminalProviderSpec?,
    onDismiss: () -> Unit,
    onSave: (TerminalProviderSpec, setAsDefault: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SaifTheme.colors
    val accent = Color(0xFF8B6CEF)
    val panelBg = if (colors.isLight) Color(0xFFFDFCF9) else Color(0xFF1B1A18)

    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var mode by remember(initial) { mutableStateOf(initial?.mode ?: TerminalProviderMode.HYBRID_API_WORKSPACE) }
    var protocol by remember(initial) { mutableStateOf(initial?.protocol ?: AgyApiProtocol.OPENAI_COMPATIBLE) }
    var endpointUrl by remember(initial) { mutableStateOf(initial?.endpointUrl ?: "https://openrouter.ai/api/v1") }
    var apiKey by remember(initial) { mutableStateOf(initial?.apiKey ?: "") }
    var authScheme by remember(initial) { mutableStateOf(initial?.authScheme ?: TerminalAuthScheme.BEARER_TOKEN) }
    var customHeaderName by remember(initial) { mutableStateOf(initial?.customAuthHeaderName ?: "X-Custom-Auth") }
    var selectedModel by remember(initial) { mutableStateOf(initial?.selectedModel ?: "anthropic/claude-sonnet-4") }
    var availableModels by remember(initial) { mutableStateOf(initial?.availableModels ?: emptyList()) }
    var requiresLinuxRootfs by remember(initial) { mutableStateOf(initial?.requiresLinuxRootfs ?: false) }
    var setAsDefault by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultOk by remember { mutableStateOf<Boolean?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    fun currentDraft(): TerminalProviderSpec = (initial ?: TerminalProviderSpec(name = name, description = description)).copy(
        name = name.trim().ifBlank { "Custom Provider" },
        description = description.trim().ifBlank { "Custom compatible AI & terminal provider" },
        mode = mode,
        protocol = protocol,
        endpointUrl = endpointUrl.trim(),
        apiKey = apiKey.trim(),
        authScheme = authScheme,
        customAuthHeaderName = customHeaderName.trim(),
        selectedModel = selectedModel.trim(),
        availableModels = availableModels,
        requiresLinuxRootfs = requiresLinuxRootfs
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(22.dp),
            color = panelBg,
            border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initial == null) "Add Custom Provider" else "Configure ${initial.name}",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // Quick Preset Fillers
                if (initial == null || !initial.isBuiltIn) {
                    Text("Quick Template", color = colors.textSecondary, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AntigravityCustomApiManager.PRESETS.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    if (name.isBlank()) name = preset.label
                                    endpointUrl = preset.defaultBaseUrl
                                    protocol = preset.protocol
                                    availableModels = preset.defaultModels
                                    if (preset.defaultModels.isNotEmpty()) {
                                        selectedModel = preset.defaultModels.first()
                                    }
                                    authScheme = when (preset.protocol) {
                                        AgyApiProtocol.ANTHROPIC -> TerminalAuthScheme.X_API_KEY
                                        AgyApiProtocol.GEMINI_NATIVE -> TerminalAuthScheme.QUERY_PARAM_KEY
                                        else -> TerminalAuthScheme.BEARER_TOKEN
                                    }
                                },
                                label = { Text(preset.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = initial?.isBuiltIn != true,
                    label = { Text("Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Protocol Adapter", color = colors.textSecondary, fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AgyApiProtocol.entries.forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = { protocol = proto },
                            label = { Text(proto.label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("API Endpoint Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Authentication Scheme", color = colors.textSecondary, fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalAuthScheme.entries.forEach { scheme ->
                        FilterChip(
                            selected = authScheme == scheme,
                            onClick = { authScheme = scheme },
                            label = { Text(scheme.label, fontSize = 11.sp) }
                        )
                    }
                }

                if (authScheme == TerminalAuthScheme.CUSTOM_HEADER) {
                    OutlinedTextField(
                        value = customHeaderName,
                        onValueChange = { customHeaderName = it },
                        label = { Text("Custom Header Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (authScheme != TerminalAuthScheme.NONE_LOCAL) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key / Token (Encrypted with Android KeyStore)") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("Default Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (availableModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableModels.take(12).forEach { m ->
                            FilterChip(
                                selected = selectedModel == m,
                                onClick = { selectedModel = m },
                                label = { Text(m, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isBusy = true
                            scope.launch {
                                val res = TerminalProviderManager.fetchProviderModels(currentDraft())
                                res.onSuccess { list ->
                                    availableModels = list
                                    if (selectedModel.isBlank() && list.isNotEmpty()) {
                                        selectedModel = list.first()
                                    }
                                    testResultOk = true
                                    testResultText = "Loaded ${list.size} models from endpoint."
                                }.onFailure { err ->
                                    testResultOk = false
                                    testResultText = err.message ?: "Could not fetch models"
                                }
                                isBusy = false
                            }
                        },
                        enabled = !isBusy && endpointUrl.isNotBlank()
                    ) {
                        Text("Fetch Models", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isBusy = true
                            scope.launch {
                                val res = TerminalProviderManager.testProviderConnection(context, currentDraft())
                                testResultOk = res.ok
                                testResultText = res.message
                                isBusy = false
                            }
                        },
                        enabled = !isBusy
                    ) {
                        Text(if (isBusy) "Testing..." else "Test Connection", fontSize = 12.sp)
                    }
                }

                if (testResultText != null) {
                    Text(
                        text = testResultText!!,
                        color = if (testResultOk == true) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Require Local Ubuntu PRoot Shell", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(checked = requiresLinuxRootfs, onCheckedChange = { requiresLinuxRootfs = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Set as Default Provider", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(checked = setAsDefault, onCheckedChange = { setAsDefault = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(currentDraft(), setAsDefault) },
                        enabled = name.isNotBlank() || initial != null,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Save & Activate", color = Color.White)
                    }
                }
            }
        }
    }
}
