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
import com.g1.sketchbook.ui.theme.Cavorting

/**
 * 온보딩(Splash/Login) 공통 "Daily sketch" 타이틀.
 * 화면 폭이 좁아 한 줄로 다 안 들어가면(기기 비율 대비 타이틀이 크면) "Daily"/"sketch" 두 줄로 자동 줄바꿈한다.
 */
@Composable
fun OnboardingTitle(fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Cavorting, fontSize = fontSize)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val naturalWidth = measurer.measure(AnnotatedString("Daily sketch"), style).size.width
        val text = if (naturalWidth > constraints.maxWidth) "Daily\nsketch" else "Daily sketch"
        Text(text, fontFamily = Cavorting, fontSize = fontSize, color = color,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
