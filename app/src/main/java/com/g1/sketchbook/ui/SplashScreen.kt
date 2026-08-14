package com.g1.sketchbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.Dimens

private val SageBg = Color(0xFFACBDAA)
private val Ink = Color(0xFF20201C)

/** First screen the app shows — stays put until the user taps "enter" (no auto-advance timer). */
@Composable
fun SplashScreen(onEnter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(SageBg).systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        OnboardingTitle(fontSize = Dimens.Onboarding.titleSp, color = Ink)
        DuckWalk(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentDescription = null,
        )
        Text("Draw together, keep the little days", fontFamily = Cavorting, fontSize = Dimens.Onboarding.subtitleSp,
            color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Ink).bounceClick(onClick = onEnter)
                .padding(horizontal = 46.dp, vertical = 14.dp),
        ) {
            Text("enter", color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
