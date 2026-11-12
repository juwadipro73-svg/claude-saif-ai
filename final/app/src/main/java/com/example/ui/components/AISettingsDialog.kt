/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Cleanroom dialog for adjusting SAIF AI provider preferences and runtime parameters.
 */
@Composable
fun AISettingsDialog(
    isDarkTheme: Boolean = true,
    onDismiss: () -> Unit = {},
    onConfigSaved: () -> Unit = {}
) {
    val modalShape = RoundedCornerShape(all = 22.dp)
    val modalSurfaceColor = if (isDarkTheme) Color(0xFF242321) else Color(0xFFFCFCFB)
    val modalStrokeColor = if (isDarkTheme) Color(0xFF3E3B36) else Color(0xFFE2DFD4)
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { onConfigSaved.invoke(); onDismiss.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(fraction = 0.94f)
                    .fillMaxHeight(fraction = 0.86f),
            shape = modalShape,
            color = modalSurfaceColor,
            tonalElevation = 5.dp,
            border = BorderStroke(width = 1.dp, color = modalStrokeColor)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(all = 19.dp)
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
    Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAIF AI Provider Configuration",
                        fontWeight = FontWeight(600),
                        fontSize =
    17.sp,
                        color = if (isDarkTheme) Color(0xFFF0EFEA) else Color(0xFF191816),
                        modifier =
    Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onConfigSaved.invoke(); onDismiss.invoke() },
                        modifier =
                            Modifier
                                .size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss SAIF Settings Dialog",
                            tint = if (isDarkTheme) Color(0xFFD4D0C5) else Color(0xFF4A4740)
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier
                            .height(13.dp)
                )

                Box(
                    modifier =
                        Modifier
                            .weight(weight = 1f, fill = true)
                            .verticalScroll(state = scrollState)
                ) {
                    AiProviderSettingsInline()
                }
            }
        }
    }
}
