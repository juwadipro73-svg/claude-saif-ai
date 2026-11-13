/*
 * SAIF AI - live voice: the orb and the live conversation screen.
 * Copyright (C) 2026 SAIF AI contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.LiveMode
import com.example.live.LiveOrbState
import com.example.live.SaifLive
import kotlin.math.cos
import kotlin.math.sin

/**
 * SAIF AI LIVE ORB - the animated orb of SAIF AI's live conversation.
 *
 * It is SAIF AI's own drawing, made with Compose: a glowing core, three orbit rings and a circle of sound bars.
 * The colour follows what the conversation is doing (listening, thinking, speaking) and the bars and the core
 * follow the loudness of the voice. It reads [SaifLive]'s state, so it works with every AI provider.
 *
 * A tap and a long press are passed on; the screen around the orb decides what they do.
 */
@Composable
fun SaifLiveOrb(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") isLightMode: Boolean = false,
    compact: Boolean = false,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val state by SaifLive.orbState.collectAsState()
    val level by SaifLive.audioLevel.collectAsState()
    val status by SaifLive.connectionStatus.collectAsState()

    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)

    val accent by animateColorAsState(targetValue = orbColour(state), animationSpec = tween(450), label = "orb_colour")
    val loudness by animateFloatAsState(targetValue = level.coerceIn(0f, 1f), animationSpec = tween(140), label = "orb_level")
    val motion = rememberInfiniteTransition(label = "saif_orb")
    val spin by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "orb_spin"
    )
    val pulse by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "orb_pulse"
    )
    val breath by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb_breath"
    )

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { tap() }, onLongPress = { longPress() })
        }
    ) {
        drawSaifOrb(state, accent, loudness, spin, pulse, breath)
    }

    if (!compact) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            StatusPill(status)
        }
    }
}

/** The colour of the orb for each state, in SAIF AI's palette. */
private fun orbColour(state: LiveOrbState): Color = when (state) {
    LiveOrbState.SPEAKING -> Color(0xFF22C55E)
    LiveOrbState.LISTENING -> Color(0xFF7C5CE0)
    LiveOrbState.THINKING -> Color(0xFF6B31CF)
    LiveOrbState.IDLE -> Color(0xFF9B82EC)
}

private const val FULL_TURN = 6.2831855f

/**
 * Draws the orb into the available square. Every length is a share of the smaller side, so the same drawing serves
 * the large orb of the live screen and the small one of the floating bubble.
 *
 * @param loudness 0..1, the voice level
 * @param spin 0..1, one slow turn of the rings
 * @param pulse 0..1, a faster repeating beat
 * @param breath 0..1 and back, a slow swelling
 */
private fun DrawScope.drawSaifOrb(
    state: LiveOrbState,
    accent: Color,
    loudness: Float,
    spin: Float,
    pulse: Float,
    breath: Float
) {
    val unit = size.minDimension
    if (unit <= 0f) return
    val centre = center
    val speaking = state == LiveOrbState.SPEAKING
    val listening = state == LiveOrbState.LISTENING
    val thinking = state == LiveOrbState.THINKING
    val light = lerp(accent, Color.White, 0.55f)
    val deep = lerp(accent, Color.Black, 0.55f)
    val core = unit * 0.21f * (1f + 0.05f * breath + 0.20f * loudness)

    // 1. A soft glow behind everything; it grows brighter with the voice.
    val glowRadius = unit * 0.49f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = (0.30f + 0.25f * loudness + 0.08f * breath).coerceIn(0f, 1f)),
                accent.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = centre,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = centre
    )

    // 2. While SAIF AI speaks, rings travel outwards like sound.
    if (speaking) {
        for (i in 0 until 3) {
            val travel = (pulse + i / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - travel) * 0.45f),
                radius = core * (1.05f + travel * 0.80f),
                center = centre,
                style = Stroke(width = unit * 0.006f)
            )
        }
    }

    // 3. Three tilted orbit rings, each with a bright stretch that runs around it (faster while thinking).
    val ringWidth = unit * 0.0075f
    val ringSize = Size(unit * 0.80f, unit * 0.30f)
    val ringTopLeft = Offset(centre.x - ringSize.width / 2f, centre.y - ringSize.height / 2f)
    val tilts = floatArrayOf(-28f, 34f, 82f)
    val speed = if (thinking) 2.2f else 1f
    for (i in tilts.indices) {
        val direction = if (i % 2 == 0) 1f else -1f
        rotate(degrees = tilts[i], pivot = centre) {
            drawOval(
                color = accent.copy(alpha = 0.16f),
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringWidth)
            )
            drawArc(
                color = light.copy(alpha = 0.85f),
                startAngle = (spin * 360f * direction * speed + i * 120f) % 360f,
                sweepAngle = 70f + 60f * loudness,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringWidth * 1.6f, cap = StrokeCap.Round)
            )
        }
    }

    // 4. A circle of sound bars around the core: tall while the user speaks, quiet otherwise.
    val bars = 56
    val barAlpha = if (listening) 0.9f else if (speaking) 0.55f else 0.22f
    val barStart = core * 1.22f
    for (i in 0 until bars) {
        val angle = (i.toFloat() / bars) * FULL_TURN
        val beat = 0.5f + 0.5f * sin(i * 0.9f + pulse * FULL_TURN)
        val drift = 0.5f + 0.5f * sin(i * 0.37f - spin * FULL_TURN * 3f)
        val length = unit * (0.012f + 0.085f * loudness * beat + 0.012f * drift)
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = light.copy(alpha = barAlpha * (0.45f + 0.55f * beat)),
            start = Offset(centre.x + dx * barStart, centre.y + dy * barStart),
            end = Offset(centre.x + dx * (barStart + length), centre.y + dy * (barStart + length)),
            strokeWidth = unit * 0.006f,
            cap = StrokeCap.Round
        )
    }

    // 5. The core: a lit ball with its light coming from the upper left.
    val lightSpot = Offset(centre.x - core * 0.34f, centre.y - core * 0.40f)
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(light, accent, deep), center = lightSpot, radius = core * 1.45f),
        radius = core,
        center = centre
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            center = lightSpot,
            radius = core * 0.62f
        ),
        radius = core * 0.62f,
        center = lightSpot
    )
    drawCircle(
        color = light.copy(alpha = 0.35f),
        radius = core,
        center = centre,
        style = Stroke(width = unit * 0.004f)
    )

    // 6. While thinking, two arcs chase each other closely around the core.
    if (thinking) {
        val arcRadius = core * 1.12f
        val arcTopLeft = Offset(centre.x - arcRadius, centre.y - arcRadius)
        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
        val arcStroke = Stroke(width = unit * 0.009f, cap = StrokeCap.Round)
        drawArc(
            color = Color.White.copy(alpha = 0.9f),
            startAngle = pulse * 360f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = arcStroke
        )
        drawArc(
            color = light.copy(alpha = 0.7f),
            startAngle = pulse * 360f + 180f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = arcStroke
        )
    }
}

/** Small glass pill showing the engine's connection state, in SAIF AI's colours. */
@Composable
private fun StatusPill(status: String) {
    val live = status.equals("LIVE", true)
    val bad = status.contains("ERR", true) || status.contains("FAIL", true)
    val tint = when {
        bad -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        live -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        else -> androidx.compose.ui.graphics.Color(0xFF7C5CE0)
    }
    val pulse = rememberInfiniteTransition(label = "orb_status_pulse")
    val a by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0x33000000), androidx.compose.ui.graphics.Color(0x55000000))
                )
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tint.copy(alpha = a)))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (live) "SAIF AI LIVE" else status.uppercase(),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * The full-screen live conversation panel opened from the purple mic: the orb in the middle, live captions under
 * it, and the controls (mute, screen share, stop) around it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SaifLiveConversationPanel(
    isLightMode: Boolean,
    onClose: () -> Unit,
    onRequestScreenShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userText by SaifLive.userTranscript.collectAsState()
    val aiText by SaifLive.assistantTranscript.collectAsState()
    val liveError by SaifLive.liveError.collectAsState()
    val state by SaifLive.orbState.collectAsState()
    val sharing by SaifLive.isScreenSharing.collectAsState()
    val mode by SaifLive.liveMode.collectAsState()
    var muted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { SaifLive.setMicMuted(false) } }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF22211E),
                        androidx.compose.ui.graphics.Color(0xFF150C2E),
                        androidx.compose.ui.graphics.Color(0xFF22211E)
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SAIF AI LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                    Text(
                        text = if (mode == LiveMode.SCREEN_SHARE) "Helping with your screen" else "Voice conversation",
                        color = androidx.compose.ui.graphics.Color(0xFFB3A0F2), fontSize = 11.sp
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
                        .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClose(); true } else ev.action == MotionEvent.ACTION_DOWN },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, "Close", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
            }

            // The orb. A tap mutes the microphone (or cuts SAIF AI off while it speaks); a long press ends the session.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                SaifLiveOrb(
                    modifier = Modifier.size(if (sharing) 300.dp else 340.dp),
                    isLightMode = isLightMode,
                    onTap = {
                        if (state == LiveOrbState.SPEAKING) SaifLive.interrupt()
                        else { muted = !muted; SaifLive.setMicMuted(muted) }
                    },
                    onLongPress = { SaifLive.stopLive(context); onClose() }
                )
            }

            // Live captions.
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = userText.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(userText, color = androidx.compose.ui.graphics.Color(0xFFD6D3C7), fontSize = 14.sp,
                        textAlign = TextAlign.Center, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                AnimatedVisibility(visible = aiText.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(aiText, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 4)
                }
                // STT/provider failures can be recoverable while the session is still running.
                // Keep the reason visible rather than leaving the orb saying LIVE with no answer.
                liveError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text("${error.title}: ${error.message}",
                        color = androidx.compose.ui.graphics.Color(0xFFFCA5A5), fontSize = 12.sp,
                        textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
            }

            // Controls.
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(bottom = 34.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.Default.Mic, Icons.Default.MicOff, "Mic", !muted) {
                    muted = !muted
                    SaifLive.setMicMuted(muted)
                }
                OrbButton(
                    if (sharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                    if (sharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                    if (sharing) "Screen on" else "Screen", !sharing
                ) {
                    if (sharing) SaifLive.stopScreenShare() else onRequestScreenShare()
                }
                OrbButton(Icons.Default.Close, Icons.Default.Close, "Stop", false) {
                    SaifLive.stopLive(context)
                    onClose()
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun OrbButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    offIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7C5CE0), androidx.compose.ui.graphics.Color(0xFF5E43BD)))
                    else Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0x33FFFFFF), androidx.compose.ui.graphics.Color(0x22FFFFFF)))
                )
                .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClick(); true } else ev.action == MotionEvent.ACTION_DOWN },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (active) icon else offIcon, label, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = androidx.compose.ui.graphics.Color(0xFFD6D3C7), fontSize = 11.sp)
    }
}

/**
 * A small round version of the orb with a close button, for places where the live session is shown as a bubble.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SaifLiveOrbBubble(
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SaifLive.orbState.collectAsState()
    val level by SaifLive.audioLevel.collectAsState()
    val pulse = rememberInfiniteTransition(label = "orb_bubble_pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "bubbleGlow"
    )
    val ring = when (state) {
        LiveOrbState.SPEAKING -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        LiveOrbState.LISTENING -> androidx.compose.ui.graphics.Color(0xFF7C5CE0)
        LiveOrbState.THINKING -> androidx.compose.ui.graphics.Color(0xFF6B31CF)
        else -> androidx.compose.ui.graphics.Color(0xFF9B82EC)
    }
    Box(
        modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(ring.copy(alpha = 0.35f + glow * 0.3f * (0.4f + level)), androidx.compose.ui.graphics.Color(0xCC100F0E))))
            .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onOpen(); true } else ev.action == MotionEvent.ACTION_DOWN },
        contentAlignment = Alignment.Center
    ) {
        SaifLiveOrb(modifier = Modifier.size(66.dp), compact = true, onTap = onOpen)
        Box(
            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFFEF4444))
                .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClose(); true } else ev.action == MotionEvent.ACTION_DOWN },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, "Close", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(13.dp)) }
    }
}
