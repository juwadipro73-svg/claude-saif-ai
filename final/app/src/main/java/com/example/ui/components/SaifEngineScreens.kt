/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saif.data.remote.SystemConnectionState
import com.example.saif.data.remote.SystemStatus
import com.example.saif.ui.chat.ChatViewModel as EngineChatViewModel
import com.example.ui.theme.SaifTheme

private val SaifPurple = Color(0xFF7C5CE0)

/**
 * The centre of an empty SAIF AI chat.
 *  - not signed in  -> "Connect your AI provider" + Sign In button
 *  - connecting     -> a small spinner with the step
 *  - offline/error  -> what is wrong + Reconnect
 *  - signed in      -> just the glowing SAIF AI mark (no greeting text)
 */
@Composable
fun SaifEmptyState(
    status: SystemStatus,
    isNetworkConnected: Boolean,
    isAuthBusy: Boolean,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier =
    Modifier
) {
    val c = SaifTheme.colors
    val customApiState by com.example.agyext.AntigravityCustomApiManager.stateFlow.collectAsState()
    Column(
        modifier =
    modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            customApiState.isActiveAndReady -> DeveloperWelcomeCards()
            !isNetworkConnected -> {
                SaifMarkCircle(icon = Icons.Outlined.CloudOff, tint = Color(0xFFEF4444))
                Spacer(Modifier.height(16.dp))
                Text(
                    "You seem to be offline",
                    color = c.textPrimary, fontSize =
    18.sp, fontWeight =
    FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect to the internet to sign in and chat with SAIF AI.",
                    color = c.textSecondary, fontSize =
    14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
            }

            status == SystemStatus.UNAUTHENTICATED -> {
                SaifMarkCircle(icon = Icons.Default.Person, tint = SaifPurple)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect your AI provider",
                    color = c.textPrimary, fontSize =
    20.sp, fontWeight =
    FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Configure your own API key and model in Settings to start working.",
                    color = c.textSecondary, fontSize =
    14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !isAuthBusy,
                    shape =
    RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    if (isAuthBusy) {
                        CircularProgressIndicator(
                            modifier =
    Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Login, contentDescription =
    null, modifier =
    Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Configure Provider", fontSize =
    15.sp, fontWeight =
    FontWeight.Medium)
                }
            }

            status == SystemStatus.CHECKING_AUTH || status == SystemStatus.ACQUIRING_CSRF || status == SystemStatus.STARTING -> {
                CircularProgressIndicator(modifier =
    Modifier.size(30.dp), strokeWidth = 3.dp, color = SaifPurple)
                Spacer(Modifier.height(16.dp))
                Text(
                    when (status) {
                        SystemStatus.CHECKING_AUTH -> "Checking your sign-in…"
                        SystemStatus.ACQUIRING_CSRF -> "Almost ready…"
                        else -> "Starting SAIF AI…"
                    },
                    color = c.textSecondary, fontSize =
    14.sp, textAlign = TextAlign.Center
                )
            }

            status == SystemStatus.OFFLINE || status == SystemStatus.ERROR -> {
                SaifMarkCircle(icon = Icons.Outlined.CloudOff, tint = Color(0xFF6B31CF))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (status == SystemStatus.ERROR) "SAIF AI hit a connection error" else "SAIF AI is not connected",
                    color = c.textPrimary, fontSize =
    18.sp, fontWeight =
    FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap Reconnect to start it again.",
                    color = c.textSecondary, fontSize =
    14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape =
    RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SaifPurple.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Reconnect", color = SaifPurple, fontSize =
    14.sp, fontWeight =
    FontWeight.Medium)
                }
            }

            else -> {
                DeveloperWelcomeCards()
            }
        }
    }
}

@Composable
private fun SaifMarkCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val transition = rememberInfiniteTransition(label = "saif_mark")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec =
    infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "saif_mark_pulse"
    )
    Box(
        modifier =
    Modifier
            .size(64.dp)
            .scale(pulse)
            .clip(shape =
    CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment =
    Alignment.Center
    ) {
        Icon(icon, contentDescription =
    null, tint = tint, modifier =
    Modifier.size(32.dp))
    }
}

/**
 * Connection / account card, opened by the small status dot in the header and by the account chip in the drawer.
 * Everything the old engine screen offered for this (sign in, sign out, reconnect, restart) lives here.
 */
@Composable
fun SaifConnectionDialog(engineVm: EngineChatViewModel, onDismiss: () -> Unit) {
    com.example.agyext.AntigravityCustomApiDialog(onDismiss = onDismiss)
}

@Composable
fun SaifEngineLoginDialogs(engineVm: EngineChatViewModel) {
    val show by engineVm.showProviderSetup.collectAsState()
    if (show) com.example.agyext.AntigravityCustomApiDialog(onDismiss = { engineVm.showProviderSetup.value = false })
}
