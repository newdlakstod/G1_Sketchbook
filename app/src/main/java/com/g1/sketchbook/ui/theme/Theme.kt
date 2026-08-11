package com.g1.sketchbook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm "paper notebook" palette: cream backgrounds, deep navy ink, and soft storybook accents.
 * The whole app commits to this single light look (the design is warm/paper-based), so there is
 * no dark scheme to fight it.
 */
val Navy = Color(0xFF2B4C9B)
val NavyDeep = Color(0xFF223C7A)
val Cream = Color(0xFFF6EFDF)
val CardCream = Color(0xFFFFFBF2)
val PaperCanvas = Color(0xFFFBF6EA)
val Ink = Color(0xFF223150)
val Olive = Color(0xFF7E9A52)
val Clay = Color(0xFFDE7F3C)
val SoftLine = Color(0xFFD9CDB2)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F7),
    onPrimaryContainer = NavyDeep,
    secondary = Olive,
    onSecondary = Color.White,
    tertiary = Clay,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = CardCream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE4D0),
    onSurfaceVariant = Color(0xFF6B6552),
    outline = SoftLine,
    outlineVariant = Color(0xFFE4DAC3),
    error = Color(0xFFC0553B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    )
}

@Composable
fun G1Theme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
