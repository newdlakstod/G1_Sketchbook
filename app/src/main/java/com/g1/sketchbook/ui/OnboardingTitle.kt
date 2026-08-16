package com.g1.sketchbook.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.ui.theme.Cavorting

private val OnboardingBgLight = Color(0xFFACBDAA)   // sage green — original brand look
private val OnboardingInkLight = Color(0xFF20201C)
private val OnboardingBgDark = Color(0xFF232A22)    // deep muted olive, coherent with the dark theme's near-black surfaces
private val OnboardingInkDark = Color(0xFFE7E9E4)   // matches DarkColors.onBackground

/** Sage-brand background/ink for Splash & Login — adapted per theme so dark-mode users don't get
 *  jolted by a bright screen before landing on their preferred dark theme (the brand tint is kept,
 *  just deepened, rather than switching to the generic surface colour). */
data class OnboardingPalette(val bg: Color, val ink: Color)

@Composable
fun onboardingPalette(): OnboardingPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) OnboardingPalette(OnboardingBgDark, OnboardingInkDark) else OnboardingPalette(OnboardingBgLight, OnboardingInkLight)
}

/**
 * 온보딩(Splash/Login) 공통 "daymory" 타이틀.
 *
 * 글자 크기를 [maxFontSize]에 고정하지 않는다 — 화면 폭에 맞는 한도 안에서 가능한 한 크게 잡되,
 * 한 단어라 줄바꿈할 지점이 없으므로 안 들어가면 최소 크기까지 계속 줄여서라도 한 줄로 표시한다.
 */
@Composable
fun OnboardingTitle(maxFontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val minFontSize = maxFontSize * 0.55f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        var fs = maxFontSize
        while (fs > minFontSize &&
            measurer.measure(AnnotatedString("daymory"), TextStyle(fontFamily = Cavorting, fontSize = fs)).size.width > maxWidthPx
        ) {
            fs = (fs.value - 4f).sp
        }
        Text(
            "daymory", fontFamily = Cavorting, fontSize = fs, color = color, textAlign = TextAlign.Center,
            lineHeight = fs * 1.15f, modifier = Modifier.fillMaxWidth(),
        )
    }
}
