package com.gdo.pagecurl

import android.graphics.Bitmap

internal const val MIN_PAGE_ASPECT_RATIO = 0.25f
internal const val MAX_PAGE_ASPECT_RATIO = 4f

internal fun requireSupportedPageAspectRatio(value: Float): Float = value.also {
    require(it.isFinite() && it in MIN_PAGE_ASPECT_RATIO..MAX_PAGE_ASPECT_RATIO) {
        "pageAspectRatio must be finite and between $MIN_PAGE_ASPECT_RATIO and $MAX_PAGE_ASPECT_RATIO"
    }
}

interface PageCurlBitmapSource {
    /** Immutable metadata safe to read from UI or GL threads. */
    val pageCount: Int

    /** Page width divided by page height, in the supported 0.25..4 range. */
    val pageAspectRatio: Float get() = 3f / 4f

    /** Called only on the GL thread. The returned Bitmap remains caller-owned. */
    fun getPageBitmap(
        pageIndex: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap
}
