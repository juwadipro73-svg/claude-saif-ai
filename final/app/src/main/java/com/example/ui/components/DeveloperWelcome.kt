package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** In-app identity changes with the clock; launcher icons cannot interpolate on Android. */
@Composable
fun rememberIdentityColor(): Color {
    var slot by remember { mutableStateOf(System.currentTimeMillis() / 7_200_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(7_200_000L - System.currentTimeMillis() % 7_200_000L)
            slot = System.currentTimeMillis() / 7_200_000L
        }
    }
    val palette = listOf(Color(0xFF9B8AFF), Color(0xFF56DCC5), Color(0xFF77B7FF))
    return animateColorAsState(palette[(slot % palette.size).toInt()], tween(2400), label = "identity").value
}

@Composable
fun MorphingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateFloatAsState(if (pressed) 12f else 24f, spring(), label = "corner")
    val scale by animateFloatAsState(if (pressed) .92f else 1f, spring(), label = "press")
    IconButton(onClick = onClick, enabled = enabled, colors = colors, interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) .18f else .06f)),
        content = content)
}

@Composable
fun DeveloperWelcomeCards() {
    val accent = rememberIdentityColor()
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(650)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SAIF AI", color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Your next idea starts here.", color = MaterialTheme.colorScheme.onSurface)
        Text("PLAN / BUILD / SHIP", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = accent)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("{ }", "Architect", "Think through systems"),
                Triple(">_", "Builder", "Code with your tools"),
                Triple("◇", "Reviewer", "Refine every detail")).forEach { (face, name, description) ->
                Surface(shape = RoundedCornerShape(24.dp), color = accent.copy(alpha = .09f),
                    modifier = Modifier.width(160.dp).graphicsLayer {
                        alpha = entrance.value
                        translationY = (1f - entrance.value) * 32.dp.toPx()
                    }) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(face, color = accent, fontFamily = FontFamily.Monospace, fontSize = 30.sp)
                        Text(name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var notices by remember { mutableStateOf("Loading notices…") }
    LaunchedEffect(Unit) {
        notices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.assets.open("legal/THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
                .replace(Regex("\\s+"), " ")
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Column { Text("SAIF AI", fontWeight = FontWeight.Bold); Text("Credits", fontSize = 16.sp) } },
        text = { Text(notices, fontSize = 11.sp, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
