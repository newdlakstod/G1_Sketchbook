package com.g1.sketchbook.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.ui.theme.Cavorting

/**
 * 온보딩(Splash/Login) 공통 "Daily sketch" 타이틀.
 *
 * 글자 크기를 [maxFontSize]에 고정하지 않는다 — 화면 폭에 맞는 한도 안에서 가능한 한 크게 잡되,
 * 한 줄로 안 들어가면 줄어들어서라도 한 줄을 우선하고, 그래도 안 들어가는 극단적인 비율에서만
 * "Daily"/"sketch" 두 줄로 나눈다(이때도 줄 간격을 넉넉히 둬서 글자가 서로 겹치지 않게 한다).
 */
@Composable
fun OnboardingTitle(maxFontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val minFontSize = maxFontSize * 0.55f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        var fs = maxFontSize
        while (fs > minFontSize &&
            measurer.measure(AnnotatedString("Daily sketch"), TextStyle(fontFamily = Cavorting, fontSize = fs)).size.width > maxWidthPx
        ) {
            fs = (fs.value - 4f).sp
        }
        val fitsOneLine = measurer.measure(AnnotatedString("Daily sketch"), TextStyle(fontFamily = Cavorting, fontSize = fs)).size.width <= maxWidthPx
        val text = if (fitsOneLine) "Daily sketch" else "Daily\nsketch"
        Text(
            text, fontFamily = Cavorting, fontSize = fs, color = color, textAlign = TextAlign.Center,
            lineHeight = fs * 1.15f, modifier = Modifier.fillMaxWidth(),
        )
    }
}
