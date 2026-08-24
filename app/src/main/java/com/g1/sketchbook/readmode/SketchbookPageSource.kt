package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.gdo.pagecurl.PageCurlBitmapSource
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlin.math.max

internal enum class PageBitmapLayer { PAPER, CONTENT }

internal fun pageBitmapLayers(hasContent: Boolean): List<PageBitmapLayer> =
    if (hasContent) {
        listOf(PageBitmapLayer.PAPER, PageBitmapLayer.CONTENT)
    } else {
        listOf(PageBitmapLayer.PAPER)
    }

/** Flattens the editor's transparent strokes onto the selected paper for OpenGL upload. */
internal fun composePageBitmap(
    content: Bitmap?,
    paper: Bitmap,
    width: Int,
    height: Int,
): Bitmap {
    require(width > 0 && height > 0)
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    pageBitmapLayers(content != null).forEach { layer ->
        when (layer) {
            PageBitmapLayer.PAPER -> {
                canvas.drawColor(0xFFFBF6EA.toInt())
                val rotate = (paper.width > paper.height) != (width > height)
                val paperWidth = if (rotate) paper.height else paper.width
                val paperHeight = if (rotate) paper.width else paper.height
                val scale = max(width.toFloat() / paperWidth, height.toFloat() / paperHeight)
                val matrix = Matrix().apply {
                    postTranslate(-paper.width / 2f, -paper.height / 2f)
                    if (rotate) postRotate(90f)
                    postScale(scale, scale)
                    postTranslate(width / 2f, height / 2f)
                }
                canvas.drawBitmap(paper, matrix, paint)
            }

            PageBitmapLayer.CONTENT -> content?.let {
                canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint)
            }
        }
    }
    result.setHasAlpha(false)
    return result
}

internal class SketchbookPageSource(
    private val repo: SketchbookRepository,
    private val bookId: String,
    override val pageCount: Int,
    override val pageAspectRatio: Float,
    private val paper: Bitmap,
) : PageCurlBitmapSource {
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
        return composePageBitmap(
            content = decoded,
            paper = paper,
            width = requestedWidth,
            height = requestedHeight,
        ).also {
            decoded?.recycle()
        }
    }
}
