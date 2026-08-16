package com.g1.sketchbook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.DaymoryTeal
import com.g1.sketchbook.ui.theme.Dimens

@Composable
internal fun OnboardingLayout(
    contentDescription: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    val (sageBg, _) = onboardingPalette()
    Column(
        modifier = Modifier.fillMaxSize().background(sageBg).systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        OnboardingTitle(preferredFontSize = Dimens.Onboarding.titleSp, color = DaymoryTeal)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.onboarding_duck2),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(Dimens.Onboarding.duckMaxWidth)
                    .aspectRatio(Dimens.Onboarding.duckAspectRatio)
                    .padding(vertical = 12.dp),
            )
        }
        Text(
            "Draw together, keep the little days",
            fontFamily = Cavorting,
            fontSize = Dimens.Onboarding.subtitleSp,
            color = DaymoryTeal,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = DaymoryTeal)
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(DaymoryTeal)
                        .bounceClick(onClick = onCta)
                        .padding(horizontal = 46.dp, vertical = 14.dp),
                ) {
                    Text(ctaLabel, color = Color.White, fontSize = Dimens.Onboarding.ctaSp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(error, color = Color(0xFF6E2A1E), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
