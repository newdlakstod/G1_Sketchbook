package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import android.graphics.Color
import com.gdo.pagecurl.PageCurlBitmapSource
import com.g1.sketchbook.sketchbook.SketchbookRepository

internal class SketchbookPageSource(
    private val repo: SketchbookRepository,
    private val bookId: String,
    override val pageCount: Int,
    override val pageAspectRatio: Float,
) : PageCurlBitmapSource {
    private val blankPage = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
    }

    init {
        require(pageCount > 0) { "pageCount must be positive" }
        require(pageAspectRatio.isFinite() && pageAspectRatio > 0f) {
            "pageAspectRatio must be positive and finite"
        }
    }

    override fun getPageBitmap(
        pageIndex: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap {
        require(pageIndex in 0 until pageCount)
        require(requestedWidth > 0 && requestedHeight > 0)
        val decoded = repo.loadPageThumb(bookId, pageIndex, reqPx = requestedWidth)
            ?: return blankPage
        if (decoded.width <= requestedWidth && decoded.height <= requestedHeight) return decoded

        val scale = minOf(
            requestedWidth.toFloat() / decoded.width,
            requestedHeight.toFloat() / decoded.height,
        )
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, width, height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
