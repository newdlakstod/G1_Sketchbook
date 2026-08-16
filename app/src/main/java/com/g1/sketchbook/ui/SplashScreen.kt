package com.g1.sketchbook.ui

import androidx.compose.runtime.Composable

/** First screen the app shows — stays put until the user taps "enter" (no auto-advance timer). */
@Composable
fun SplashScreen(onEnter: () -> Unit) {
    OnboardingLayout(contentDescription = null, ctaLabel = "enter", onCta = onEnter)
}
