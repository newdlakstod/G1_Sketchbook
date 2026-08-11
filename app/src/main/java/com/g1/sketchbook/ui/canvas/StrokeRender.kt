package com.g1.sketchbook.ui.canvas

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import com.g1.sketchbook.data.model.Stroke

/**
 * Renders finished strokes into a bitmap for the permanent gallery snapshot, using the same crayon
 * grain as the live canvas: a repeating grain shader supplies uneven alpha and a SrcIn color filter
 * paints it in the stroke color. Eraser strokes are drawn solid so they fully cover.
 */
fun renderStrokesToBitmap(
    strokes: List<Stroke>,
    width: Int,
    height: Int,
    backgroundColor: Int = 0xFFFBF6EA.toInt(), // matches PaperCanvas so eraser strokes blend
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(backgroundColor)

    val grainShader = BitmapShader(
        CrayonGrain.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        val pts = stroke.points
        if (pts.size < 2) continue
        paint.strokeWidth = (stroke.width * width).coerceAtLeast(1f)
        if (stroke.erase) {
            paint.shader = null
            paint.colorFilter = null
            paint.color = stroke.color.toInt()
        } else {
            paint.shader = grainShader
            paint.colorFilter = PorterDuffColorFilter(stroke.color.toInt(), PorterDuff.Mode.SRC_IN)
        }

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
