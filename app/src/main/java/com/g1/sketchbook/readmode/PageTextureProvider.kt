package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlin.math.roundToInt

/** Computes the (width, height) a page bitmap should be downsampled to for GL texture upload —
 *  pure function, no Bitmap/Android dependency, so it's unit-testable on its own. Caps the longest
 *  edge at [maxEdge]; returns the size unchanged if it's already within budget. */
fun downsampleTargetSize(width: Int, height: Int, maxEdge: Int = 1600): Pair<Int, Int> {
    require(width > 0 && height > 0) { "width and height must be positive" }
    require(maxEdge > 0) { "maxEdge must be positive" }
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toFloat() / longest
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return scaledWidth to scaledHeight
}

/** The `reqPx` to hand `SketchbookRepository.loadPageThumb` so its power-of-two `inSampleSize`
 *  decode lands just *above* a [maxEdge] longest-edge budget (never below it — [downsampleTargetSize]
 *  plus one exact rescale finishes the job).
 *
 *  `loadPageThumb` picks the largest sample with `outWidth / sample >= reqPx`, so its `reqPx` is a
 *  lower bound on the decoded **width**, not on the longest edge. For a portrait page (the common
 *  case here) height is the longest edge, so asking for `reqPx = maxEdge` outright would keep the
 *  width at/above 1600 and leave the height far above it — i.e. sample would stay 1 and nothing
 *  would be downsampled at all. Scaling the request by the page's aspect converts the width bound
 *  into the longest-edge bound we actually want. */
fun decodeRequestWidth(maxEdge: Int, pageAspect: Float): Int {
    require(maxEdge > 0) { "maxEdge must be positive" }
    require(pageAspect > 0f) { "pageAspect must be positive" }
    return (maxEdge * minOf(1f, pageAspect)).roundToInt().coerceAtLeast(1)
}

/** Loads sketchbook page bitmaps for read mode, downsampled so GL texture uploads on page turns
 *  stay cheap even though the editor stores pages at full canvas resolution (up to 3308px, see
 *  `SketchbookRepository.loadPage`). Decoding goes through `loadPageThumb`, which downsamples
 *  *while* decoding, so a page turn never has to materialise a full-resolution bitmap (an A3 page
 *  is ~31MB) just to shrink it a moment later. Does no threading of its own — callers load off the
 *  main thread (see `ReadModeScreen`'s `LaunchedEffect` + `Dispatchers.IO`).
 *
 *  [pageAspect] is one page's real width/height ratio (`book.size.pxW() / pxH()`); it only tunes the
 *  decode budget, so a stored page whose aspect differs from the book's still comes out correctly
 *  sized — just decoded a power-of-two step larger or smaller than ideal. */
class PageTextureProvider(
    private val repo: SketchbookRepository,
    private val bookId: String,
    private val pageAspect: Float,
    private val maxEdge: Int = 1600,
) {
    private val requestWidth = decodeRequestWidth(maxEdge, pageAspect)

    /** Returns the downsampled bitmap for [pageIndex], or null if that page has no drawing yet. */
    fun pageBitmap(pageIndex: Int): Bitmap? {
        val decoded = repo.loadPageThumb(bookId, pageIndex, reqPx = requestWidth) ?: return null
        val (w, h) = downsampleTargetSize(decoded.width, decoded.height, maxEdge)
        if (w == decoded.width && h == decoded.height) return decoded
        return Bitmap.createScaledBitmap(decoded, w, h, true).also { if (it !== decoded) decoded.recycle() }
    }
}
