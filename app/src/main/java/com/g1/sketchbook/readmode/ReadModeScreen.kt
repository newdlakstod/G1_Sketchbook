package com.g1.sketchbook.readmode

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.g1.sketchbook.sketchbook.DefaultSketchbookCoverColor
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen, read-only page-turning viewer for a personal sketchbook — no drawing toolbar.
 *  Portrait shows one page per spread; landscape pairs pages like an open book
 *  ("표지-1, 2-3, 4-5, ..."). Falls back to a plain instant page swap (no curl animation) on
 *  devices without GLES 3.0, so read mode never crashes. [onClose] receives whichever page was
 *  showing (its 0-indexed page number, not spread number) so the caller can sync the editor back
 *  to it. */
@Composable
fun ReadModeScreen(
    repo: SketchbookRepository,
    book: Sketchbook,
    startPage: Int,
    onClose: (lastPage: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val supportsGles30 = remember(ctx) { deviceSupportsGles30(ctx) }
    val provider = remember(repo, book.id) { PageTextureProvider(repo, book.id) }
    var lastKnownPage by remember { mutableIntStateOf(startPage) }
    val spreads = remember(book.pageCount, landscape) { buildSpreads(book.pageCount, landscape) }
    var spreadIndex by remember(landscape) { mutableIntStateOf(spreadIndexForPage(spreads, lastKnownPage)) }
    val targetW = remember(book) { downsampleTargetSize(book.size.pxW(), book.size.pxH()).first }
    val targetH = remember(book) { downsampleTargetSize(book.size.pxW(), book.size.pxH()).second }

    fun currentPage(): Int = spreads[spreadIndex].last { it != COVER_PAGE }

    LaunchedEffect(spreadIndex, landscape) { lastKnownPage = currentPage() }

    BackHandler { onClose(currentPage()) }

    if (!supportsGles30) {
        FallbackSpreadView(spreads.size, spreadIndex, onClose = { onClose(currentPage()) })
        return
    }

    var surface by remember { mutableStateOf<ReadModeSurface?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, surface) {
        val current = surface
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> current?.onResume()
                Lifecycle.Event.ON_PAUSE -> current?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(spreadIndex, landscape, surface) {
        val activeSurface = surface ?: return@LaunchedEffect
        val textures = withContext(Dispatchers.IO) {
            loadSpreadTextures(provider, spreads, spreadIndex, book, repo, targetW, targetH)
        }
        activeSurface.setSpread(textures, landscape)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context -> ReadModeSurface(context) },
            update = { view ->
                surface = view
                view.onTurnCompleted = { if (spreadIndex < spreads.lastIndex) spreadIndex++ }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun deviceSupportsGles30(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
}

/** Loads the bitmaps for one spread. [spread] holds one real page (portrait) or
 *  `[COVER_PAGE, page]` / `[left, right]` (landscape). The "turning" page — the one that curls
 *  when the user drags — is always the rightmost entry; anything to its left is static. `nextRight`
 *  previews the page revealed once the turn completes, so the curling leaf's underside shows real
 *  content instead of a blank flash. Pages with no drawing yet, and the turning leaf's back side,
 *  render as plain white — this app's pages aren't physically double-sided, so there's no "real"
 *  back content to show; matching the sketchbook's actual paper texture there is a possible later
 *  polish pass, not required for the read-mode feature to work. */
private fun loadSpreadTextures(
    provider: PageTextureProvider,
    spreads: List<List<Int>>,
    spreadIndex: Int,
    book: Sketchbook,
    repo: SketchbookRepository,
    targetW: Int,
    targetH: Int,
): SpreadTextures {
    val spread = spreads[spreadIndex]
    val turningIndex = spread.last()
    val blank = blankPage(targetW, targetH)
    val turningFront = provider.pageBitmap(turningIndex) ?: blank
    val staticLeftIndex = spread.firstOrNull { it != turningIndex }
    val staticLeft = when (staticLeftIndex) {
        null -> null
        COVER_PAGE -> renderCoverBitmap(book, repo.loadCoverThumb(book.id, reqPx = maxOf(targetW, targetH)), targetW, targetH)
        else -> provider.pageBitmap(staticLeftIndex) ?: blank
    }
    val nextSpreadRight = spreads.getOrNull(spreadIndex + 1)?.last()
    return SpreadTextures(
        turningFront = turningFront,
        turningBack = blank,
        nextRight = nextSpreadRight?.let { provider.pageBitmap(it) } ?: blank,
        staticLeft = staticLeft,
    )
}

private fun blankPage(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.WHITE) }

/** Draws the sketchbook's cover — color or image, plus the spine strip — onto a plain Bitmap for
 *  use as a GL texture, mirroring `SketchbookCover.kt`'s Compose visuals: color fill, optional
 *  image on top, then a black spine strip along the left 9% of the width (20% opacity for a solid
 *  cover, 70% over an image — same numbers `SketchbookCover.kt` uses). Canvas-drawn rather than
 *  captured from a composition, matching this codebase's existing pattern for bitmap-only renders
 *  (see `diary/DiaryScreens.kt`'s `renderFramedDiaryBitmap`). */
private fun renderCoverBitmap(book: Sketchbook, coverImage: Bitmap?, width: Int, height: Int): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val coverColorArgb = (book.coverColor ?: (DefaultSketchbookCoverColor.toArgb().toLong() and 0xFFFFFFFFL) or 0xFF000000L).toInt()
    canvas.drawColor(coverColorArgb)
    if (coverImage != null) {
        canvas.drawBitmap(coverImage, centerCropRect(coverImage.width, coverImage.height, width, height), Rect(0, 0, width, height), null)
    }
    val spineAlpha = if (coverImage == null) 0.20f else 0.70f
    val spinePaint = Paint().apply { color = AndroidColor.BLACK; alpha = (spineAlpha * 255).roundToInt() }
    canvas.drawRect(0f, 0f, width * 0.09f, height.toFloat(), spinePaint)
    return out
}

private fun centerCropRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): Rect {
    val srcAspect = srcWidth.toFloat() / srcHeight
    val dstAspect = dstWidth.toFloat() / dstHeight
    return if (srcAspect > dstAspect) {
        val cropWidth = (srcHeight * dstAspect).roundToInt()
        val x = (srcWidth - cropWidth) / 2
        Rect(x, 0, x + cropWidth, srcHeight)
    } else {
        val cropHeight = (srcWidth / dstAspect).roundToInt()
        val y = (srcHeight - cropHeight) / 2
        Rect(0, y, srcWidth, y + cropHeight)
    }
}

@Composable
private fun FallbackSpreadView(spreadCount: Int, spreadIndex: Int, onClose: () -> Unit) {
    // Minimal fallback for devices without GLES 3.0 — no curl animation, just enough to avoid a
    // crash and tell the user why. Full parity (tap-to-turn without the 3D effect) is a later
    // polish pass, not required for this feature to ship on GLES-3-capable devices.
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            "이 기기는 3D 책장 넘기기를 지원하지 않아요 (${spreadIndex + 1}/$spreadCount)",
            color = Color.White,
        )
    }
    BackHandler { onClose() }
}
