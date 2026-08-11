package com.g1.sketchbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Coral = Color(0xFFFF6B6B)
private val CoralDark = Color(0xFFE85555)
private val Teal = Color(0xFF4ECDC4)
private val Ink = Color(0xFF1A1A2E)

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color(0xFFFDF7F4),
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Ink,
    background = Color(0xFF14141F),
    surface = Color(0xFF1E1E2E),
)

@Composable
fun G1Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
