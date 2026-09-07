package com.gdo.pagecurl

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PageCurl(
    source: PageCurlBitmapSource,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    require(source.pageCount >= 1) { "PageCurl requires at least one page" }
    requireSupportedPageAspectRatio(source.pageAspectRatio)
    require(pageIndex in 0 until source.pageCount)

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPageChanged = rememberUpdatedState(onPageChanged)
    val currentOnError = rememberUpdatedState(onError)
    val supportsGles30 = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
    }
    val layoutMode = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        PageLayoutMode.TwoPageSpread
    } else {
        PageLayoutMode.SinglePage
    }

    if (!supportsGles30) {
        LaunchedEffect(source) {
            currentOnError.value(IllegalStateException("OpenGL ES 3.0 is required for PageCurl"))
        }
        Box(modifier)
        return
    }

    var surface by remember { mutableStateOf<PageCurlSurface?>(null) }
    DisposableEffect(lifecycleOwner, surface) {
        val currentSurface = surface
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentSurface?.onResume()
                Lifecycle.Event.ON_PAUSE -> currentSurface?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            currentSurface?.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentSurface?.onPause()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PageCurlSurface(
                context = viewContext,
                initialSource = source,
                initialFocusedPageIndex = pageIndex,
                initialLayoutMode = layoutMode,
                onFocusedPageCommitted = { currentOnPageChanged.value(it) },
                onError = { currentOnError.value(it) },
            ).also { surface = it }
        },
        update = { view -> view.setDocument(source, pageIndex, layoutMode) },
        onRelease = { view -> view.release() },
        modifier = modifier,
    )
}
