package com.g1.sketchbook.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.DaymoryTeal
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.Ivory

internal fun onboardingColors(isDark: Boolean): Pair<Color, Color> =
    if (isDark) DaymoryTeal to Ivory else Ivory to DaymoryTeal

@Composable
internal fun OnboardingLayout(
    contentDescription: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    // 온보딩 색상 조절: 라이트는 아이보리 배경/틸 요소, 다크는 두 색을 서로 반전한다.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (backgroundColor, foregroundColor) = onboardingColors(isDark)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        OnboardingLayoutLandscape(contentDescription, ctaLabel, onCta, busy, error, backgroundColor, foregroundColor)
        return
    }
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
                    // 오리 위치 조절: x는 좌우(+ 오른쪽), y는 상하(+ 아래쪽) 이동값이다.
                    .offset(x = 0.dp, y = 0.dp)
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
                Text(error, color = foregroundColor, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 가로모드 전용 배치 — 시안처럼 텍스트(타이틀/부제/버튼)는 왼쪽에 좌측정렬로, 오리는 오른쪽에 크게. */
@Composable
private fun OnboardingLayoutLandscape(
    contentDescription: String?, ctaLabel: String, onCta: () -> Unit, busy: Boolean, error: String?,
    backgroundColor: Color, foregroundColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxSize().background(backgroundColor).systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            OnboardingTitle(preferredFontSize = Dimens.Onboarding.titleSp, color = foregroundColor, textAlign = TextAlign.Start)
            Spacer(Modifier.height(8.dp))
            // 시안처럼 2줄로 — 이 칼럼이 이미 화면 폭의 절반이라, "칼럼 안에서 가운데" 정렬이면
            // 자동으로 화면 기준 1/4 지점이 된다(버튼도 같은 원리로 아래에서 가운데 정렬).
            Text(
                "Draw together,\nkeep the little days",
                fontFamily = Cavorting,
                fontSize = Dimens.Onboarding.subtitleSp,
                color = foregroundColor,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(20.dp))
            // 버튼은 화면 좌측에서 1/4 지점(= 이 칼럼 폭의 절반, "반의 반") — 칼럼 안에서 가운데
            // 정렬하면 칼럼 자체가 화면의 왼쪽 절반이라 자동으로 화면 1/4 지점에 온다.
            if (busy) {
                CircularProgressIndicator(color = foregroundColor, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50)).background(foregroundColor)
                        .bounceClick(onClick = onCta)
                        .padding(horizontal = 46.dp, vertical = 14.dp),
                ) {
                    Text(ctaLabel, color = backgroundColor, fontSize = Dimens.Onboarding.ctaSp)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = foregroundColor, fontSize = 13.sp, textAlign = TextAlign.Start,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.onboarding_duck2),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(foregroundColor),
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
            )
        }
    }
}
