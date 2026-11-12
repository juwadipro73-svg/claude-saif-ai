/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaifTheme

/**
 * Dedicated Post-Login Terminal Provider Selection Screen (Section 6).
 *
 * Presented after authentication when the user has not yet selected a terminal provider.
 * Saves the chosen provider per account, navigates immediately to the main SAIF AI workspace,
 * and launches verified background environment preparation.
 */
@Composable
fun TerminalProviderSelectionScreen(
    userDisplayName: String?,
    onProviderConfirmed: (TerminalProviderSpec) -> Unit
) {
    val context =
    LocalContext.current
    val ecosystem by TerminalProviderManager.state.collectAsState()
    LaunchedEffect(key1 = Unit) {
        TerminalProviderManager.ensureLoaded(context)
    }

    var selectedId by remember(ecosystem.providers) {
        mutableStateOf(
            ecosystem.activeProviderId.ifBlank {
                ecosystem.providers.firstOrNull()?.id ?: "google_antigravity"
            }
        )
    }
    val selectedSpec = ecosystem.providers.firstOrNull { it.id == selectedId }
        ?: ecosystem.providers.firstOrNull()

    var optionalApiKey by remember(selectedId) {
        mutableStateOf(selectedSpec?.apiKey.orEmpty())
    }
    var optionalEndpoint by remember(selectedId) {
        mutableStateOf(selectedSpec?.endpointUrl.orEmpty())
    }
    var showKeyPlain by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val freeMb = remember { TerminalProviderManager.checkAvailableStorageMb(context) }
    val networkOnline = remember { TerminalProviderManager.isNetworkAvailable(context) }

    val colors = SaifTheme.colors
    val accent = Color(0xFF8B6CEF)
    val green = Color(0xFF10B981)
    val bg = if (colors.isLight) Color(0xFFF8F6F0) else Color(0xFF121110)
    val cardBg = if (colors.isLight) Color(0xFFFFFFFF) else Color(0xFF1D1C1A)

    Surface(modifier =
    Modifier.fillMaxSize(), color = bg) {
        Column(
            modifier =
    Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Top Greeting & Diagnostics Strip
            Row(
                modifier =
    Modifier.fillMaxWidth(),
                verticalAlignment =
    Alignment.CenterVertically,
                horizontalArrangement =
    Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment =
    Alignment.CenterVertically) {
                    Box(
                        modifier =
    Modifier
                            .size(34.dp)
                            .clip(shape = RoundedCornerShape(10.dp))
                            .background(accent),
                        contentAlignment =
    Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Terminal, contentDescription =
    null, tint =
    Color.White, modifier =
    Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Choose Your Terminal Provider",
                            color = colors.textPrimary,
                            fontSize =
    19.sp,
                            fontWeight =
    FontWeight.Bold
                        )
                        Text(
                            text = if (!userDisplayName.isNullOrBlank()) {
                                "Welcome, $userDisplayName · Prepare once, switch anytime in Settings → Providers"
                            } else {
                                "Select your preferred coding & terminal environment"
                            },
                            color = colors.textSecondary,
                            fontSize =
    12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Device Readiness Pills (Storage + Network)
            Row(
                modifier =
    Modifier.fillMaxWidth(),
                horizontalArrangement =
    Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape =
    RoundedCornerShape(10.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment =
    Alignment.CenterVertically,
                        horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Storage, contentDescription =
    null, tint = accent, modifier =
    Modifier.size(14.dp))
                        Text(
                            text = "${freeMb} MB free storage",
                            color = colors.textPrimary,
                            fontSize =
    11.sp,
                            fontWeight =
    FontWeight.Medium
                        )
                    }
                }
                Surface(
                    shape =
    RoundedCornerShape(10.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier =
    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment =
    Alignment.CenterVertically,
                        horizontalArrangement =
    Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudDone,
                            contentDescription =
    null,
                            tint = if (networkOnline) green else Color(0xFFF59E0B),
                            modifier =
    Modifier.size(14.dp)
                        )
                        Text(
                            text = if (networkOnline) "Network Ready" else "Offline Mode",
                            color = colors.textPrimary,
                            fontSize =
    11.sp,
                            fontWeight =
    FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scrollable Provider Cards
            Column(
                modifier =
    Modifier
                    .weight(1f)
                    .verticalScroll(state = rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ecosystem.providers.forEach { provider ->
                    val isSelected = provider.id == selectedId
                    Surface(
                        modifier =
    Modifier
                            .fillMaxWidth()
                            .clip(shape = RoundedCornerShape(16.dp))
                            .clickable { selectedId = provider.id },
                        shape =
    RoundedCornerShape(16.dp),
                        color = if (isSelected) accent.copy(alpha = 0.12f) else cardBg,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accent else colors.textPrimary.copy(alpha = 0.10f)
                        )
                    ) {
                        Column(
                            modifier =
    Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier =
    Modifier.fillMaxWidth(),
                                verticalAlignment =
    Alignment.CenterVertically,
                                horizontalArrangement =
    Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment =
    Alignment.CenterVertically,
                                    horizontalArrangement =
    Arrangement.spacedBy(8.dp),
                                    modifier =
    Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = provider.name,
                                        color = colors.textPrimary,
                                        fontSize =
    16.sp,
                                        fontWeight =
    FontWeight.Bold
                                    )
                                    Surface(
                                        shape =
    RoundedCornerShape(8.dp),
                                        color = accent.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = provider.mode.badgeLabel,
                                            color = accent,
                                            fontSize =
    10.sp,
                                            fontWeight =
    FontWeight.SemiBold,
                                            modifier =
    Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = green,
                                        modifier =
    Modifier.size(20.dp)
                                    )
                                } else {
                                    Box(
                                        modifier =
    Modifier
                                            .size(18.dp)
                                            .clip(shape =
    CircleShape)
                                            .background(colors.textPrimary.copy(alpha = 0.10f))
                                    )
                                }
                            }

                            Text(
                                text = provider.description,
                                color = colors.textSecondary,
                                fontSize =
    12.sp,
                                lineHeight = 17.sp
                            )

                            if (provider.platformCompatibilityNote.isNotBlank()) {
                                Text(
                                    text = "ℹ ${provider.platformCompatibilityNote}",
                                    color = colors.textSecondary.copy(alpha = 0.85f),
                                    fontSize =
    11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            // Optional inline API configuration when an API-backed provider is selected
                            if (isSelected && provider.authScheme != TerminalAuthScheme.NONE_LOCAL && provider.id != "google_antigravity") {
                                HorizontalDivider(
                                    modifier =
    Modifier.padding(vertical = 4.dp),
                                    color = colors.textPrimary.copy(alpha = 0.08f)
                                )
                                OutlinedTextField(
                                    value = optionalEndpoint,
                                    onValueChange = { optionalEndpoint = it },
                                    label = { Text("API Endpoint URL") },
                                    singleLine =
    true,
                                    modifier =
    Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = optionalApiKey,
                                    onValueChange = { optionalApiKey = it },
                                    label = { Text("API Key (optional now — can add later in Settings → Providers)") },
                                    singleLine =
    true,
                                    visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        TextButton(onClick = { showKeyPlain = !showKeyPlain }) {
                                            Text(if (showKeyPlain) "Hide" else "Show", fontSize =
    11.sp)
                                        }
                                    },
                                    modifier =
    Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Add Custom Provider straight from onboarding
                OutlinedButton(
                    onClick = { showAddCustomDialog = true },
                    modifier =
    Modifier.fillMaxWidth(),
                    shape =
    RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription =
    null, modifier =
    Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Custom Compatible Provider")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Primary Continue Action
            Button(
                onClick = {
                    val chosen = selectedSpec ?: return@Button
                    val finalSpec = if (
                        chosen.authScheme != TerminalAuthScheme.NONE_LOCAL &&
                        chosen.id != "google_antigravity" &&
                        (optionalApiKey.trim() != chosen.apiKey || optionalEndpoint.trim() != chosen.endpointUrl)
                    ) {
                        TerminalProviderManager.upsertProvider(
                            context =
    context,
                            spec = chosen.copy(
                                endpointUrl = optionalEndpoint.trim().ifBlank { chosen.endpointUrl },
                                apiKey = optionalApiKey.trim()
                            ),
                            setAsDefault = true,
                            activateNow = true
                        )
                    } else {
                        chosen
                    }
                    TerminalProviderManager.selectAndPrepareProvider(
                        context =
    context,
                        providerId = finalSpec.id,
                        setAsDefault = true
                    )
                    onProviderConfirmed(finalSpec)
                },
                modifier =
    Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape =
    RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    text = "Continue with ${selectedSpec?.name ?: "Provider"} →",
                    color =
    Color.White,
                    fontSize =
    15.sp,
                    fontWeight =
    FontWeight.Bold
                )
            }
        }
    }

    if (showAddCustomDialog) {
        CustomProviderEditorDialog(
            initial = null,
            onDismiss = { showAddCustomDialog = false },
            onSave = { saved, setDefault ->
                val created = TerminalProviderManager.upsertProvider(
                    context =
    context,
                    spec = saved,
                    setAsDefault = setDefault,
                    activateNow = true
                )
                selectedId = created.id
                showAddCustomDialog = false
            }
        )
    }
}
