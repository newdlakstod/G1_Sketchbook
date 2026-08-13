package com.g1.sketchbook.dev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.core.content.res.ResourcesCompat
import com.g1.sketchbook.R

/**
 * TEMPORARY dev-only spec overlay. Draws leader lines + Pretendard labels calling out the
 * dp/sp values we chose on a screen, so the design values are visible while iterating.
 *
 * To remove ALL of it in one shot: set [SHOW] to false (annotations vanish, zero layout impact),
 * or delete this file plus the `DevAnno.*` / `Modifier.devBounds` call-sites it flags.
 */
object DevAnno {
    /** Master switch — flip to false to hide every dev annotation app-wide. */
    const val SHOW = true
}

/** One callout: which tagged element ([key]) it points at, the [text], and where the label sits. */
data class DevNote(
    val key: String,
    val text: String,
    /** Anchor point on the element (0..1 within its bounds) that the leader line touches. */
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    /** Label offset from that anchor, in px. Negative dx puts the label to the left. */
    val dx: Float,
    val dy: Float,
)

/** Records an element's on-screen bounds into [sink] so an overlay can point a leader line at it. */
fun Modifier.devBounds(key: String, sink: MutableMap<String, Rect>): Modifier =
    if (DevAnno.SHOW) this.onGloballyPositioned { sink[key] = it.boundsInRoot() } else this

/**
 * Full-screen overlay that draws a leader line from each [notes] anchor to its label.
 * Place as the last child of a `Box(Modifier.fillMaxSize())` wrapping the screen so its
 * coordinate space matches [boundsInRoot].
 */
@Composable
fun DevAnnoOverlay(marks: Map<String, Rect>, notes: List<DevNote>) {
    if (!DevAnno.SHOW) return
    val ctx = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(ctx, R.font.pretendard_bold) }
    Canvas(Modifier.fillMaxSize()) {
        val nc = drawContext.canvas.nativeCanvas
        val red = 0xFFD3352B.toInt()
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true; color = red; textSize = 30f; typeface?.let { setTypeface(it) }
        }
        val linePaint = android.graphics.Paint().apply {
            isAntiAlias = true; color = red; strokeWidth = 2.5f; style = android.graphics.Paint.Style.STROKE
        }
        val dotPaint = android.graphics.Paint().apply { isAntiAlias = true; color = red }
        val bgPaint = android.graphics.Paint().apply { isAntiAlias = true; color = 0xF2FFFFFF.toInt() }
        notes.forEach { n ->
            val r = marks[n.key] ?: return@forEach
            val ax = r.left + r.width * n.anchorX
            val ay = r.top + r.height * n.anchorY
            val lx = ax + n.dx
            val ly = ay + n.dy
            nc.drawLine(ax, ay, lx, ly, linePaint)
            nc.drawCircle(ax, ay, 4f, dotPaint)
            val tw = textPaint.measureText(n.text)
            val pad = 6f
            val tx = if (n.dx < 0) lx - tw - pad else lx + pad
            val ty = ly + 10f
            nc.drawRect(tx - pad, ty - 26f, tx + tw + pad, ty + 8f, bgPaint)
            nc.drawText(n.text, tx, ty, textPaint)
        }
    }
}
