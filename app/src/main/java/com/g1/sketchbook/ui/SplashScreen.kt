package com.g1.sketchbook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.Dimens

private val SageBg = Color(0xFFACBDAA)
private val Ink = Color(0xFF20201C)

@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(SageBg).systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Daily sketch", fontFamily = Cavorting, fontSize = Dimens.Onboarding.titleSp, color = Ink, textAlign = TextAlign.Center)
        DuckWalk(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentDescription = null,
        )
        Text("Draw together, keep the little days", fontFamily = Cavorting, fontSize = Dimens.Onboarding.subtitleSp,
            color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}
