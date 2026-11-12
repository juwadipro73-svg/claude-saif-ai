/* Copyright (c) SAIF AI. All rights reserved. */

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SAIF AI Sovereign Type Architecture.
 * Custom calibrated typographic scale authored under SAIF cleanroom guidelines.
 */
private val SaifPrimaryTypeface = FontFamily.Default

private fun createSaifStyle(
    sizeSp: Float,
    lineHeightSp: Float,
    weight: FontWeight = FontWeight.Normal,
    spacingSp: Float = 0f
): TextStyle = TextStyle(
    fontFamily = SaifPrimaryTypeface,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = spacingSp.sp
)

val Typography = Typography(
    displaySmall = createSaifStyle(sizeSp = 31f, lineHeightSp = 37f, weight = FontWeight.Bold, spacingSp = -0.4f),
    headlineMedium = createSaifStyle(sizeSp = 27f, lineHeightSp = 33f, weight = FontWeight.SemiBold, spacingSp = -0.25f),
    headlineSmall = createSaifStyle(sizeSp = 23f, lineHeightSp = 29f, weight = FontWeight.SemiBold, spacingSp = -0.15f),
    titleLarge = createSaifStyle(sizeSp = 21f, lineHeightSp = 27f, weight = FontWeight.SemiBold),
    titleMedium = createSaifStyle(sizeSp = 17f, lineHeightSp = 23f, weight = FontWeight.Medium),
    titleSmall = createSaifStyle(sizeSp = 14.5f, lineHeightSp = 20.5f, weight = FontWeight.Medium, spacingSp = 0.1f),
    bodyLarge = createSaifStyle(sizeSp = 16.5f, lineHeightSp = 24.5f, spacingSp = 0.15f),
    bodyMedium = createSaifStyle(sizeSp = 14f, lineHeightSp = 21.5f, spacingSp = 0.15f),
    bodySmall = createSaifStyle(sizeSp = 12.5f, lineHeightSp = 17.5f, spacingSp = 0.2f),
    labelLarge = createSaifStyle(sizeSp = 14f, lineHeightSp = 20f, weight = FontWeight.SemiBold),
    labelMedium = createSaifStyle(sizeSp = 12f, lineHeightSp = 16.5f, weight = FontWeight.Medium, spacingSp = 0.25f),
    labelSmall = createSaifStyle(sizeSp = 10.5f, lineHeightSp = 14.5f, weight = FontWeight.Medium, spacingSp = 0.35f)
)

val SaifShapes = Shapes(
    extraSmall = RoundedCornerShape(percent = 18),
    small = RoundedCornerShape(percent = 26),
    medium = RoundedCornerShape(percent = 32),
    large = RoundedCornerShape(percent = 40),
    extraLarge = RoundedCornerShape(percent = 50)
)
