package com.g1.sketchbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App palette (from the reference): sage green, deep navy, neutral grey, soft taupe.
 * Navy is the ink/primary, sage & taupe are the calm brand accents.
 */
val Sage = Color(0xFFACBDAA)
val Navy = Color(0xFF1E2D4C)
val Grey = Color(0xFF858585)
val Taupe = Color(0xFFCEC0BB)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE0E8),
    onPrimaryContainer = Navy,
    secondary = Sage,
    onSecondary = Navy,
    secondaryContainer = Color(0xFFD7E0D0),
    onSecondaryContainer = Navy,
    tertiary = Taupe,
    onTertiary = Navy,
    tertiaryContainer = Color(0xFFE8E0DA),
    onTertiaryContainer = Navy,
    background = Color(0xFFEAEDE6),
    onBackground = Navy,
    surface = Color(0xFFFBFCFA),
    onSurface = Navy,
    surfaceVariant = Color(0xFFDEE3D8),
    onSurfaceVariant = Grey,
    outline = Color(0xFFB4BCAF),
    outlineVariant = Color(0xFFCDD3C7),
    error = Color(0xFFB4553F),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Sage,
    onPrimary = Navy,
    primaryContainer = Color(0xFF2E3A46),
    onPrimaryContainer = Sage,
    secondary = Color(0xFFB7C7B4),
    onSecondary = Color(0xFF17202E),
    secondaryContainer = Color(0xFF34413A),
    onSecondaryContainer = Color(0xFFD7E0D0),
    tertiary = Taupe,
    onTertiary = Color(0xFF2A2420),
    tertiaryContainer = Color(0xFF433B36),
    onTertiaryContainer = Color(0xFFE8E0DA),
    background = Color(0xFF14171B),
    onBackground = Color(0xFFE7E9E4),
    surface = Color(0xFF1C2129),
    onSurface = Color(0xFFE7E9E4),
    surfaceVariant = Color(0xFF2A2F35),
    onSurfaceVariant = Color(0xFFAAB0A6),
    outline = Color(0xFF454B50),
    outlineVariant = Color(0xFF333940),
    error = Color(0xFFD98A73),
    onError = Color(0xFF2A1410),
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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun G1Theme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
