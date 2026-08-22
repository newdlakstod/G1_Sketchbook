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

/** Loads sketchbook page bitmaps for read mode, downsampled so GL texture uploads on page turns
 *  stay cheap even though the editor stores pages at full canvas resolution (up to 3308px, see
 *  `SketchbookRepository.loadPage`). Does no threading of its own — callers load off the main
 *  thread (see `ReadModeScreen`'s `LaunchedEffect` + `Dispatchers.IO`). */
class PageTextureProvider(
    private val repo: SketchbookRepository,
    private val bookId: String,
    private val maxEdge: Int = 1600,
) {
    /** Returns the downsampled bitmap for [pageIndex], or null if that page has no drawing yet. */
    fun pageBitmap(pageIndex: Int): Bitmap? {
        val full = repo.loadPage(bookId, pageIndex) ?: return null
        val (w, h) = downsampleTargetSize(full.width, full.height, maxEdge)
        if (w == full.width && h == full.height) return full
        return Bitmap.createScaledBitmap(full, w, h, true)
    }
}
