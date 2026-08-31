package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

fun drawVectorPage(canvas: Canvas, page: VectorPage, stampBrushes: Map<String, StampBrushProfile> = emptyMap()) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            fillPaint.color = stroke.color.toInt()
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val path = Path()
                shape.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        if (stroke.fillEnabled) {
            fillPaint.color = stroke.color.toInt()
            canvas.drawPath(path, fillPaint)
        }
        stroke.strokeColor?.let { sc ->
            strokePaint.color = sc.toInt()
            strokePaint.strokeWidth = stroke.strokeWidthPx
            canvas.drawPath(path, strokePaint)
        }
    }
}

private const val PREVIEW_PADDING_RATIO = 0.08f

fun renderVectorPage(page: VectorPage, sizePx: Int, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val bounds = contentBounds(page.strokes) ?: return bmp
    val padX = bounds.width * PREVIEW_PADDING_RATIO
    val padY = bounds.height * PREVIEW_PADDING_RATIO
    val left = bounds.minX - padX; val top = bounds.minY - padY
    val w = bounds.width + padX * 2f; val h = bounds.height + padY * 2f
    val scale = min(sizePx / w, sizePx / h)
    canvas.save()
    canvas.translate((sizePx - w * scale) / 2f, (sizePx - h * scale) / 2f)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    drawVectorPage(canvas, page, stampBrushes)
    canvas.restore()
    return bmp
}
