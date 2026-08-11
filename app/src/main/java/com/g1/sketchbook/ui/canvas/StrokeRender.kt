package com.g1.sketchbook.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.g1.sketchbook.data.model.Stroke

/** Renders finished strokes into a bitmap for the permanent gallery snapshot. */
fun renderStrokesToBitmap(
    strokes: List<Stroke>,
    width: Int,
    height: Int,
    backgroundColor: Int = 0xFFFBF6EA.toInt(), // matches PaperCanvas so eraser strokes blend
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(backgroundColor)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        val pts = stroke.points
        if (pts.size < 2) continue
        paint.color = stroke.color.toInt()
        paint.strokeWidth = (stroke.width * width).coerceAtLeast(1f)

        if (pts.size == 2) {
            canvas.drawPoint(pts[0] * width, pts[1] * height, paint)
            continue
        }
        val path = Path()
        path.moveTo(pts[0] * width, pts[1] * height)
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i] * width, pts[i + 1] * height)
            i += 2
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}
