package com.g1.sketchbook.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Press feedback that springs the element's own shape (a gentle scale-down + bounce back) instead
 * of the default rectangular ripple — so a rounded button animates as a rounded button.
 */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bounce",
    )
    // pointerInput(enabled) only restarts its coroutine when `enabled` changes — since that's almost
    // always a fixed `true`, the SAME onTap closure keeps running for the modifier's whole lifetime.
    // Capturing `onClick` directly would freeze it at whatever it was on first composition (stale —
    // e.g. `{ if (selected) open() else onClick() }` where `selected` is a plain per-recomposition
    // value keeps re-evaluating that first, frozen `selected`). rememberUpdatedState keeps the
    // callback current without needing pointerInput to restart.
    val currentOnClick = rememberUpdatedState(onClick)
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { currentOnClick.value() },
            )
        }
}
