/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.agyext

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.ProviderSettingsManager
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch
import java.util.UUID

private val EmeraldReady = Color(0xFF22C55E)

@Composable
fun AntigravityCustomApiCompactRow(
    onOpenDialog: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    val context =
    LocalContext.current
    LaunchedEffect(context) { AntigravityCustomApiManager.init(context) }
    val state by AntigravityCustomApiManager.stateFlow.collectAsState()
    val active = state.activeProfile
    val isLive = state.isActiveAndReady

    Row(
        modifier =
    modifier
            .fillMaxWidth()
            .clickable { onOpenDialog() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment =
    Alignment.CenterVertically
    ) {
        Box(
            modifier =
    Modifier
                .size(34.dp)
                .clip(shape = RoundedCornerShape(10.dp))
                .background(
                    if (isLive) EmeraldReady.copy(alpha = 0.16f)
                    else PrimaryAccent.copy(alpha = 0.14f)
                ),
            contentAlignment =
    Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = "Antigravity Custom API",
                tint = if (isLive) EmeraldReady else PrimaryAccent,
                modifier =
    Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment =
    Alignment.CenterVertically) {
                Text(
                    text = "Antigravity Custom API",
                    color = SaifTheme.colors.textPrimary,
                    fontSize =
    15.sp,
                    fontWeight =
    FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
    TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape =
    RoundedCornerShape(6.dp),
                    color = if (isLive) EmeraldReady.copy(alpha = 0.18f) else PrimaryAccent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "EXT",
                        color = if (isLive) EmeraldReady else PrimaryAccent,
                        fontSize =
    10.sp,
                        fontWeight =
    FontWeight.Bold,
                        modifier =
    Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = when {
                    isLive && active != null -> "${active.name} · ${active.selectedModel}"
                    active != null && active.isReady() -> "Paused · ${active.name} (${active.selectedModel})"
                    else -> "Add any API key & URL to run Antigravity"
                },
                color = if (isLive) EmeraldReady else SaifTheme.colors.textSecondary,
                fontSize =
    12.sp,
                maxLines = 1,
                overflow =
    TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = isLive,
            onCheckedChange = { wantEnabled ->
                if (wantEnabled && (active == null || !active.isReady())) {
                    onOpenDialog()
                } else {
                    AntigravityCustomApiManager.setEnabled(context, wantEnabled)
                }
            }
        )

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription =
    null,
            tint = SaifTheme.colors.textSecondary,
            modifier =
    Modifier.padding(start = 2.dp).size(18.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityCustomApiDialog(
    onDismiss: () -> Unit
) {
    val context =
    LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope =
    rememberCoroutineScope()

    LaunchedEffect(context) { AntigravityCustomApiManager.init(context) }
    val savedState by AntigravityCustomApiManager.stateFlow.collectAsState()

    val initialProfile = remember {
        savedState.activeProfile ?: AntigravityCustomApiManager.createProfileFromPreset("openrouter")
    }

    var editingId by remember { mutableStateOf(initialProfile.id) }
    var presetId by remember { mutableStateOf(initialProfile.presetId) }
    var profileName by remember { mutableStateOf(initialProfile.name) }
    var baseUrl by remember { mutableStateOf(initialProfile.baseUrl) }
    var apiKey by remember { mutableStateOf(initialProfile.apiKey) }
    var protocol by remember { mutableStateOf(initialProfile.protocol) }
    var selectedModel by remember { mutableStateOf(initialProfile.selectedModel) }
    var modelList by remember { mutableStateOf(initialProfile.effectiveModels()) }
    var extensionEnabled by remember {
        mutableStateOf(if (savedState.profiles.isEmpty()) true else savedState.enabled)
    }

    var showApiKey by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun loadProfileIntoEditor(p: AgyCustomApiProfile) {
        editingId = p.id
        presetId = p.presetId
        profileName = p.name
        baseUrl = p.baseUrl
        apiKey = p.apiKey
        protocol = p.protocol
        selectedModel = p.selectedModel
        modelList = p.effectiveModels()
        statusBanner = null
    }

    fun applyPreset(preset: AgyApiPreset) {
        presetId = preset.id
        profileName = preset.label
        baseUrl = preset.defaultBaseUrl
        protocol = preset.protocol
        if (preset.defaultModels.isNotEmpty()) {
            selectedModel = preset.defaultModels.first()
            modelList = preset.defaultModels
        }
        statusBanner = null
    }

    fun buildCurrentProfile(): AgyCustomApiProfile {
        val cleanModel = selectedModel.trim()
        val combinedModels = (listOf(cleanModel) + modelList).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return AgyCustomApiProfile(
            id = editingId,
            name = profileName.trim().ifBlank { "Custom API" },
            presetId = presetId,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            protocol = protocol,
            selectedModel = cleanModel,
            models = combinedModels
        )
    }

    val hasLegacyKey = remember {
        runCatching { ProviderSettingsManager.loadState().apiKeys.any { it.isNotBlank() } }.getOrDefault(false)
    }

    Dialog(
        onDismissRequest =
    onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier =
    Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
            shape =
    RoundedCornerShape(22.dp),
            color = SaifTheme.colors.surfaceCard,
            border = BorderStroke(1.dp, SaifTheme.colors.cardBorder),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier =
    Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Compact Top Bar
                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    verticalAlignment =
    Alignment.CenterVertically
                ) {
                    Box(
                        modifier =
    Modifier
                            .size(32.dp)
                            .clip(shape = RoundedCornerShape(9.dp))
                            .background(PrimaryAccent.copy(alpha = 0.16f)),
                        contentAlignment =
    Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription =
    null,
                            tint = PrimaryAccent,
                            modifier =
    Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Antigravity API Extension",
                            color = SaifTheme.colors.textPrimary,
                            fontSize =
    16.sp,
                            fontWeight =
    FontWeight.Bold
                        )
                        Text(
                            text = "Run Antigravity agent & Linux tools on any API",
                            color = SaifTheme.colors.textSecondary,
                            fontSize =
    11.sp
                        )
                    }
                    Switch(
                        checked = extensionEnabled,
                        onCheckedChange = {
                            extensionEnabled = it
                            if (savedState.profiles.isNotEmpty()) {
                                AntigravityCustomApiManager.setEnabled(context, it)
                            }
                        }
                    )
                    IconButton(onClick = onDismiss, modifier =
    Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SaifTheme.colors.textSecondary,
                            modifier =
    Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = SaifTheme.colors.cardBorder,
                    modifier =
    Modifier.padding(vertical = 10.dp)
                )

                // Scrollable Compact Body
                Column(
                    modifier =
    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(state = rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Saved API Profiles Strip (multi-API switching + "+ New")
                    if (savedState.profiles.isNotEmpty() || hasLegacyKey) {
                        Row(
                            modifier =
    Modifier
                                .fillMaxWidth()
                                .horizontalScroll(state = rememberScrollState()),
                            horizontalArrangement =
    Arrangement.spacedBy(6.dp),
                            verticalAlignment =
    Alignment.CenterVertically
                        ) {
                            savedState.profiles.forEach { prof ->
                                val selected = prof.id == editingId
                                Surface(
                                    onClick = {
                                        loadProfileIntoEditor(prof)
                                        AntigravityCustomApiManager.selectActiveProfile(context, prof.id)
                                    },
                                    shape =
    RoundedCornerShape(10.dp),
                                    color = if (selected) PrimaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) PrimaryAccent else SaifTheme.colors.cardBorder
                                    )
                                ) {
                                    Row(
                                        modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment =
    Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier =
    Modifier
                                                .size(6.dp)
                                                .clip(shape =
    CircleShape)
                                                .background(if (prof.isReady()) EmeraldReady else SaifTheme.colors.textSecondary)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = prof.name,
                                            color = if (selected) PrimaryAccent else SaifTheme.colors.textPrimary,
                                            fontSize =
    12.sp,
                                            fontWeight =
    FontWeight.SemiBold
                                        )
                                        if (savedState.profiles.size > 1 && selected) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete profile",
                                                tint = StatusError,
                                                modifier =
    Modifier
                                                    .size(13.dp)
                                                    .clickable {
                                                        AntigravityCustomApiManager.deleteProfile(context, prof.id)
                                                        val next = AntigravityCustomApiManager.getState(context).activeProfile
                                                            ?: AntigravityCustomApiManager.createProfileFromPreset("openrouter")
                                                        loadProfileIntoEditor(next)
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                onClick = {
                                    val fresh = AntigravityCustomApiManager.createProfileFromPreset("custom")
                                        .copy(id = UUID.randomUUID().toString(), name = "Custom API")
                                    loadProfileIntoEditor(fresh)
                                },
                                shape =
    RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, SaifTheme.colors.cardBorder)
                            ) {
                                Row(
                                    modifier =
    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment =
    Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription =
    null, tint = PrimaryAccent, modifier =
    Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Add API", color = PrimaryAccent, fontSize =
    12.sp, fontWeight =
    FontWeight.Medium)
                                }
                            }

                            if (hasLegacyKey) {
                                Surface(
                                    onClick = {
                                        val imported = AntigravityCustomApiManager.importFromLegacyProviderSettings(context)
                                        if (imported != null) {
                                            loadProfileIntoEditor(imported)
                                            extensionEnabled = true
                                            statusBanner = true to "Imported saved key (${imported.name})"
                                        }
                                    },
                                    shape =
    RoundedCornerShape(10.dp),
                                    color = EmeraldReady.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "Import Saved Key",
                                        color = EmeraldReady,
                                        fontSize =
    11.sp,
                                        fontWeight =
    FontWeight.SemiBold,
                                        modifier =
    Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1-Tap Provider Presets
                    Text(
                        text = "PROVIDER PRESET",
                        color = SaifTheme.colors.textSecondary,
                        fontSize =
    10.sp,
                        fontWeight =
    FontWeight.Bold
                    )
                    Row(
                        modifier =
    Modifier
                            .fillMaxWidth()
                            .horizontalScroll(state = rememberScrollState()),
                        horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                    ) {
                        AntigravityCustomApiManager.PRESETS.forEach { preset ->
                            val isSelected = presetId.equals(preset.id, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { applyPreset(preset) },
                                label = { Text(preset.label, fontSize =
    12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryAccent
                                )
                            )
                        }
                    }

                    // Name + Wire Protocol Row
                    Row(
                        modifier =
    Modifier.fillMaxWidth(),
                        horizontalArrangement =
    Arrangement.spacedBy(8.dp),
                        verticalAlignment =
    Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Label", fontSize =
    11.sp) },
                            singleLine =
    true,
                            modifier =
    Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize =
    13.sp)
                        )
                        var protoMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { protoMenu = true },
                                shape =
    RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = when (protocol) {
                                        AgyApiProtocol.AUTO -> "Auto"
                                        AgyApiProtocol.OPENAI_COMPATIBLE -> "OpenAI"
                                        AgyApiProtocol.ANTHROPIC -> "Anthropic"
                                        AgyApiProtocol.GEMINI_NATIVE -> "Gemini"
                                    },
                                    fontSize =
    12.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription =
    null, modifier =
    Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = protoMenu, onDismissRequest = { protoMenu = false }) {
                                AgyApiProtocol.values().forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.label, fontSize =
    13.sp) },
                                        onClick = {
                                            protocol = entry
                                            protoMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // API Key Field (with Paste + Get Key + Auto-Detect)
                    val activePresetObj = AntigravityCustomApiManager.presetById(presetId)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { typed ->
                            apiKey = typed
                            val detected = AntigravityCustomApiManager.detectPresetFromApiKey(typed)
                            if (detected != null && presetId == "custom" && baseUrl == "https://api.openai.com/v1") {
                                applyPreset(detected)
                                apiKey = typed
                            }
                        },
                        label = { Text("API Key", fontSize =
    11.sp) },
                        placeholder = { Text(activePresetObj.keyPlaceholder, fontSize =
    12.sp) },
                        singleLine =
    true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment =
    Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                        if (pasted.isNotEmpty()) {
                                            apiKey = pasted
                                            val detected = AntigravityCustomApiManager.detectPresetFromApiKey(pasted)
                                            if (detected != null && (baseUrl.isBlank() || presetId != detected.id)) {
                                                applyPreset(detected)
                                                apiKey = pasted
                                            }
                                        }
                                    },
                                    modifier =
    Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste API Key", modifier =
    Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { showApiKey = !showApiKey },
                                    modifier =
    Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key visibility",
                                        modifier =
    Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier =
    Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize =
    13.sp)
                    )

                    if (activePresetObj.docsUrl.isNotBlank()) {
                        Text(
                            text = "Get ${activePresetObj.label} API Key ↗",
                            color = PrimaryAccent,
                            fontSize =
    11.sp,
                            fontWeight =
    FontWeight.Medium,
                            modifier =
    Modifier.clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activePresetObj.docsUrl)))
                                }
                            }
                        )
                    }

                    // Base URL Field
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API Base URL", fontSize =
    11.sp) },
                        placeholder = { Text("https://openrouter.ai/api/v1", fontSize =
    12.sp) },
                        singleLine =
    true,
                        modifier =
    Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize =
    13.sp)
                    )

                    // Model ID + Live Fetch Models Button
                    Row(
                        modifier =
    Modifier.fillMaxWidth(),
                        horizontalArrangement =
    Arrangement.spacedBy(8.dp),
                        verticalAlignment =
    Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it },
                            label = { Text("Model ID", fontSize =
    11.sp) },
                            placeholder = { Text("e.g. anthropic/claude-sonnet-4", fontSize =
    12.sp) },
                            singleLine =
    true,
                            modifier =
    Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize =
    13.sp)
                        )
                        OutlinedButton(
                            enabled = !isFetchingModels,
                            onClick = {
                                isFetchingModels = true
                                statusBanner = null
                                scope.launch {
                                    val res = AntigravityCustomApiManager.fetchModels(buildCurrentProfile())
                                    isFetchingModels = false
                                    res.onSuccess { fetched ->
                                        modelList = fetched.take(40)
                                        if (selectedModel.isBlank() || selectedModel !in fetched) {
                                            selectedModel = fetched.first()
                                        }
                                        statusBanner = true to "Loaded ${fetched.size} models from endpoint"
                                    }.onFailure { err ->
                                        statusBanner = false to (err.localizedMessage ?: "Failed to fetch models")
                                    }
                                }
                            },
                            shape =
    RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier =
    Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription =
    null, modifier =
    Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Models", fontSize =
    12.sp)
                            }
                        }
                    }

                    // Quick Model Chips
                    if (modelList.isNotEmpty()) {
                        Row(
                            modifier =
    Modifier
                                .fillMaxWidth()
                                .horizontalScroll(state = rememberScrollState()),
                            horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                        ) {
                            modelList.take(25).forEach { m ->
                                val picked = m == selectedModel.trim()
                                Surface(
                                    onClick = { selectedModel = m },
                                    shape =
    RoundedCornerShape(8.dp),
                                    color = if (picked) PrimaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        if (picked) PrimaryAccent else SaifTheme.colors.cardBorder
                                    )
                                ) {
                                    Text(
                                        text = m,
                                        color = if (picked) PrimaryAccent else SaifTheme.colors.textSecondary,
                                        fontSize =
    11.sp,
                                        maxLines = 1,
                                        modifier =
    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Live Test / Status Banner
                    statusBanner?.let { (ok, msg) ->
                        Surface(
                            shape =
    RoundedCornerShape(10.dp),
                            color = (if (ok) EmeraldReady else StatusError).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (ok) EmeraldReady else StatusError).copy(alpha = 0.35f)),
                            modifier =
    Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = if (ok) EmeraldReady else StatusError,
                                fontSize =
    12.sp,
                                modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Compact Action Footer
                Row(
                    modifier =
    Modifier.fillMaxWidth(),
                    horizontalArrangement =
    Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !isTesting,
                        onClick = {
                            val candidate = buildCurrentProfile()
                            isTesting = true
                            statusBanner = null
                            scope.launch {
                                val res = AntigravityCustomApiManager.testConnection(candidate)
                                isTesting = false
                                statusBanner = res.ok to res.message
                            }
                        },
                        shape =
    RoundedCornerShape(12.dp),
                        modifier =
    Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier =
    Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Testing…", fontSize =
    13.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription =
    null, modifier =
    Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test API", fontSize =
    13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val candidate = buildCurrentProfile()
                            if (!candidate.isReady()) {
                                statusBanner = false to "Enter API Key, Base URL, and Model ID first."
                                return@Button
                            }
                            AntigravityCustomApiManager.upsertProfile(
                                context =
    context,
                                profile = candidate,
                                activate = true,
                                enableExtension = extensionEnabled
                            )
                            Toast.makeText(
                                context,
                                if (extensionEnabled) "Antigravity now running on ${candidate.name} (${candidate.selectedModel})"
                                else "Saved ${candidate.name} API profile",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        shape =
    RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        modifier =
    Modifier.weight(1.35f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription =
    null, modifier =
    Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (extensionEnabled) "Save & Run" else "Save API",
                            fontSize =
    13.sp,
                            fontWeight =
    FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
