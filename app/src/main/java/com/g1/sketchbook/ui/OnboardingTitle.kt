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
 * 온보딩(Splash/Login) 공통 "Daymory" 타이틀.
 *
 * [preferredFontSize]를 먼저 그대로 적용하고, 화면 폭에 들어가지 않을 때만 줄인다.
 * 화면이 넓어도 설정값보다 키우지 않으며 한 단어를 항상 한 줄로 표시한다.
 */
@Composable
fun OnboardingTitle(
    preferredFontSize: TextUnit, color: Color, modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val measurer = rememberTextMeasurer()
    val minFontSize = preferredFontSize * 0.55f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        var resolvedFontSize = preferredFontSize
        while (resolvedFontSize > minFontSize &&
            measurer.measure(AnnotatedString("Daymory"), TextStyle(fontFamily = Cavorting, fontSize = resolvedFontSize)).size.width > maxWidthPx
        ) {
            resolvedFontSize = (resolvedFontSize.value - 4f).sp
        }
        Text(
            "Daymory", fontFamily = Cavorting, fontSize = resolvedFontSize, color = color, textAlign = textAlign,
            lineHeight = resolvedFontSize * 1.15f, modifier = Modifier.fillMaxWidth(),
        )
    }
}
