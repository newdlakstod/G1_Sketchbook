package com.g1.sketchbook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.DaymoryTeal
import com.g1.sketchbook.ui.theme.Dimens

/** First screen the app shows — stays put until the user taps "enter" (no auto-advance timer). */
@Composable
fun SplashScreen(onEnter: () -> Unit) {
    val (SageBg, _) = onboardingPalette()
    Column(
        modifier = Modifier.fillMaxSize().background(SageBg).systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        OnboardingTitle(maxFontSize = Dimens.Onboarding.titleSp, color = DaymoryTeal)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 시안 제공 정적 이미지(image/source/ONBOARDING2.png) — 애니메이션 GIF는 색상이
            // 프레임에 고정 인코딩돼 있어 테마에 맞춰 재염색이 어려워서 고정 틸 색상 정적 그림으로 교체.
            Image(
                painterResource(R.drawable.onboarding_duck2), null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().widthIn(max = Dimens.Onboarding.duckW)
                    .aspectRatio(Dimens.Onboarding.duckW / Dimens.Onboarding.duckH)
                    .padding(vertical = 12.dp),
            )
        }
        Text("Draw together, keep the little days", fontFamily = Cavorting, fontSize = Dimens.Onboarding.subtitleSp,
            color = DaymoryTeal, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(DaymoryTeal).bounceClick(onClick = onEnter)
                .padding(horizontal = 46.dp, vertical = 14.dp),
        ) {
            Text("enter", color = Color.White, fontSize = Dimens.Onboarding.ctaSp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
