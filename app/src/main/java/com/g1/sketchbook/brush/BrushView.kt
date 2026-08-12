package com.g1.sketchbook.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class BrushType { PEN, PENCIL, CRAYON, WATER }

/**
 * Bitmap-backed brush engine (Phase 0).
 *
 * Unified architecture: every stroke is drawn onto its own transparent layer at full strength,
 * then composited onto the canvas exactly once at [opacity]. This makes the opacity slider behave
 * correctly for all brushes and keeps color visible.
 *
 * Texture is deposited perpendicular to the travel direction (a brush "nib" cross-section) so the
 * grain spreads evenly across the stroke width instead of piling up on the centre line.
 */
class BrushView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var brush = BrushType.PEN
    var color = 0xFF2B4C9B.toInt()
    var strokeSize = 20f   // diameter, px
    var opacity = 1f       // 0..1
    var paper: Bitmap? = null

    private var bmp: Bitmap? = null
    private var layer: Canvas? = null
    private var strokeBmp: Bitmap? = null
    private var strokeLayer: Canvas? = null
    private var base: Bitmap? = null            // pre-stroke copy, for compositing this stroke
    private val undo = ArrayDeque<Bitmap>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val compositeP = Paint()
    private val path = Path()
    private val rnd = Random(7)

    private var acc = 0f
    private var lx = 0f; private var ly = 0f; private var lt = 0L

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); paintPaper(c, w, h)
        bmp = b; layer = c
        strokeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); strokeLayer = Canvas(strokeBmp!!)
        invalidate()
    }

    private fun paintPaper(c: Canvas, w: Int, h: Int) {
        val p = paper
        if (p != null) {
            val s = max(w.toFloat() / p.width, h.toFloat() / p.height)
            val dw = p.width * s; val dh = p.height * s
            c.drawBitmap(p, null, RectF((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh), null)
        } else c.drawColor(0xFFFBF6EA.toInt())
    }

    override fun onDraw(c: Canvas) { bmp?.let { c.drawBitmap(it, 0f, 0f, null) } }

    fun clearCanvas() { val c = layer ?: return; pushUndo(); paintPaper(c, width, height); invalidate() }
    fun undo() { val b = undo.removeLastOrNull() ?: return; layer?.drawBitmap(b, 0f, 0f, null); invalidate() }
    private fun pushUndo() {
        val b = bmp ?: return
        undo.addLast(b.copy(Bitmap.Config.ARGB_8888, false))
        if (undo.size > 10) undo.removeFirst()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pushUndo(); acc = 0f; lx = x; ly = y; lt = System.currentTimeMillis()
                strokePrep()
                if (brush == BrushType.PEN) penDot(x, y)
                else { val a = rnd.nextFloat() * 6.2832f; stampLayer(x, y, strokeSize / 2f, cos(a), sin(a)) }
                composite(); invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val now = System.currentTimeMillis()
                val d = hypot(x - lx, y - ly); val v = d / max(1L, now - lt)
                if (brush == BrushType.PEN) penSeg(lx, ly, x, y, v) else seg(lx, ly, x, y, v)
                composite(); lx = x; ly = y; lt = now; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> base = null
        }
        return true
    }

    private fun withAlpha(c: Int, a: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255).toInt()
        return (aa shl 24) or (c and 0x00FFFFFF)
    }

    // ---- stroke layer compositing (all brushes) ----
    private fun strokePrep() {
        strokeLayer?.drawColor(0, PorterDuff.Mode.CLEAR)
        base = bmp?.copy(Bitmap.Config.ARGB_8888, false)
    }
    private fun composite() {
        val c = layer ?: return; val b = base ?: return; val sb = strokeBmp ?: return
        c.drawBitmap(b, 0f, 0f, null)
        compositeP.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        c.drawBitmap(sb, 0f, 0f, compositeP)
    }

    private fun penDot(x: Float, y: Float) {
        fill.color = color or (0xFF shl 24)
        strokeLayer?.drawCircle(x, y, max(1f, strokeSize / 2f), fill)
    }
    private fun penSeg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        val r = max(1f, (strokeSize / 2f) * (1 - minOf(0.45f, speed * 0.06f)))
        pen.color = color or (0xFF shl 24); pen.strokeWidth = r * 2
        strokeLayer?.drawLine(x0, y0, x1, y1, pen)
    }

    // ---- textured brushes: distance-accumulated, perpendicular deposit ----
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        var r = strokeSize / 2f
        if (brush == BrushType.PENCIL) r *= (1 - minOf(0.45f, speed * 0.06f)) // crayon/water: no speed
        r = max(1f, r)
        val spacing = when (brush) { BrushType.WATER -> r * 0.6f; BrushType.CRAYON -> r * 0.18f; else -> r * 0.15f }
        val dx = x1 - x0; val dy = y1 - y0; val d = hypot(dx, dy); if (d == 0f) return
        val nx = dx / d; val ny = dy / d
        var dist = spacing - acc; if (dist < 0) dist = 0f
        while (dist <= d) { stampLayer(x0 + nx * dist, y0 + ny * dist, r, nx, ny); dist += spacing }
        acc = d - (dist - spacing)
    }

    /** Deposits texture at (x,y). Grain spreads along the perpendicular (px,py) of travel (nx,ny). */
    private fun stampLayer(x: Float, y: Float, r: Float, nx: Float, ny: Float) {
        val c = strokeLayer ?: return
        val px = -ny; val py = nx
        when (brush) {
            BrushType.PENCIL -> {
                val n = max(8, (r * 2.0f).toInt())
                for (i in 0 until n) {
                    val t = rnd.nextFloat() * 2 - 1
                    val off = t * r * 1.05f
                    val j = (rnd.nextFloat() - 0.5f) * r * 0.3f
                    val sx = x + px * off + nx * j; val sy = y + py * off + ny * j
                    val al = 0.15f + rnd.nextFloat() * 0.55f
                    val ss = if (rnd.nextFloat() < 0.25f) 1.7f else 1.1f
                    fill.color = withAlpha(color, al); c.drawRect(sx, sy, sx + ss, sy + ss, fill)
                }
            }
            BrushType.CRAYON -> {
                val n = max(10, (r * 2.4f).toInt())
                for (i in 0 until n) {
                    val t = rnd.nextFloat() * 2 - 1; val edge = abs(t)
                    if (rnd.nextFloat() > (0.4f + 0.6f * edge)) continue // slightly sparser centre
                    val off = t * r * 1.15f
                    val j = (rnd.nextFloat() - 0.5f) * r * 0.4f
                    val sx = x + px * off + nx * j; val sy = y + py * off + ny * j
                    fill.color = withAlpha(color, 0.30f + rnd.nextFloat() * 0.6f)
                    val s = 1.2f + rnd.nextFloat() * 2.2f; c.drawRect(sx, sy, sx + s, sy + s, fill)
                }
            }
            BrushType.WATER -> {
                val R = r * 1.3f
                fill.style = Paint.Style.FILL
                for (L in 0 until 3) { buildBlob(x, y, R * (1 + L * 0.06f)); fill.color = withAlpha(color, 0.08f); c.drawPath(path, fill) }
                buildBlob(x, y, R); fill.style = Paint.Style.STROKE; fill.strokeWidth = 1.5f
                fill.color = withAlpha(color, 0.16f); c.drawPath(path, fill)
                if (rnd.nextFloat() < 0.25f) {
                    val a = rnd.nextFloat() * 6.2832f; val dd = R * (0.6f + rnd.nextFloat() * 0.7f)
                    buildBlob(x + cos(a) * dd, y + sin(a) * dd, R * 0.5f)
                    fill.style = Paint.Style.FILL; fill.color = withAlpha(color, 0.06f); c.drawPath(path, fill)
                }
                fill.style = Paint.Style.FILL
            }
            else -> {}
        }
    }

    // Tyler-Hobbs style irregular blob into [path]
    private fun buildBlob(cx: Float, cy: Float, r: Float) {
        var pts = ArrayList<FloatArray>(7)
        val n = 7
        for (i in 0 until n) {
            val a = i.toFloat() / n * 6.2832f; val rr = r * (0.8f + rnd.nextFloat() * 0.4f)
            pts.add(floatArrayOf(cx + cos(a) * rr, cy + sin(a) * rr))
        }
        repeat(3) { d ->
            val out = ArrayList<FloatArray>(pts.size * 2)
            val v = r * 0.55f * Math.pow(0.55, d.toDouble()).toFloat()
            for (j in pts.indices) {
                val A = pts[j]; val B = pts[(j + 1) % pts.size]
                out.add(A)
                out.add(floatArrayOf((A[0] + B[0]) / 2 + (rnd.nextFloat() - 0.5f) * v, (A[1] + B[1]) / 2 + (rnd.nextFloat() - 0.5f) * v))
            }
            pts = out
        }
        path.reset(); path.moveTo(pts[0][0], pts[0][1])
        for (i in 1 until pts.size) path.lineTo(pts[i][0], pts[i][1])
        path.close()
    }
}
