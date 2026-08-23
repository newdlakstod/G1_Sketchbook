package com.g1.sketchbook.ui

import androidx.compose.runtime.Composable

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    OnboardingLayout(
        contentDescription = "Daymory 오리",
        ctaLabel = "Google 계정으로 로그인",
        onCta = onSignIn,
        busy = busy,
        error = error,
    )
}
