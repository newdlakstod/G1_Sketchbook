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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.Dimens

@Composable
internal fun OnboardingLayout(
    contentDescription: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val foregroundColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxSize().background(backgroundColor).systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        OnboardingTitle(preferredFontSize = Dimens.Onboarding.titleSp, color = foregroundColor)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.onboarding_duck2),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(foregroundColor),
                modifier = Modifier.width(Dimens.Onboarding.duckMaxWidth)
                    .aspectRatio(Dimens.Onboarding.duckAspectRatio)
                    .padding(vertical = 12.dp),
            )
        }
        Text(
            "Draw together, keep the little days",
            fontFamily = Cavorting,
            fontSize = Dimens.Onboarding.subtitleSp,
            color = foregroundColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = foregroundColor)
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(foregroundColor)
                        .bounceClick(onClick = onCta)
                        .padding(horizontal = 46.dp, vertical = 14.dp),
                ) {
                    Text(ctaLabel, color = backgroundColor, fontSize = Dimens.Onboarding.ctaSp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
