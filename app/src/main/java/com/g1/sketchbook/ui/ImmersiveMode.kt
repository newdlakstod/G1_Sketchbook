package com.g1.sketchbook.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides (or restores) the system status/navigation bars for the canvas's "전체화면" toggle — the
 * bars stay swipe-revealable (transient) rather than fully locked out, so the user can still pull
 * them back briefly without leaving fullscreen. Always restores the bars when this leaves
 * composition, so backing out of the canvas never strands the app with system bars hidden.
 */
@Composable
fun ImmersiveModeEffect(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context as? Activity ?: return
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, view) }
    LaunchedEffect(hidden) {
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) { onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) } }
}
