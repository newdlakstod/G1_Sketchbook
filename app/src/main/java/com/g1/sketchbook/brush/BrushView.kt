package com.g1.sketchbook.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class BrushType { PEN, PENCIL, CRAYON, WATER }

/**
 * Two-layer brush engine: a static [paperBmp] base plus a transparent [contentBmp] for strokes.
 * Splitting them enables a real eraser (clears content -> paper shows through) and clean save.
 *
 * Gestures: one finger draws; two fingers pinch-zoom & pan the view; a two-finger tap = undo and a
 * three-finger tap = redo. Zoom can be locked. Drawing maps touch points back through the view
 * transform, so strokes land correctly at any zoom/pan.
 */
class BrushView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var brush = BrushType.PEN
    var color = 0xFF2B4C9B.toInt()
    var strokeSize = 20f
    var opacity = 1f
    var drawEnabled = true
    var erasing = false
    var zoomLocked = false
    var paper: Bitmap? = null
    var onStrokeEnd: (() -> Unit)? = null

    private var paperBmp: Bitmap? = null
    private var contentBmp: Bitmap? = null
    private var content: Canvas? = null
    private var strokeBmp: Bitmap? = null
    private var strokeLayer: Canvas? = null
    private var base: Bitmap? = null            // content snapshot at stroke start
    private var pendingContent: Bitmap? = null
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val eraseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val eraseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val compositeP = Paint()
    private val path = Path()
    private val rnd = Random(7)

    // view transform
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    // gesture state
    private var drawing = false
    private var gesture = false
    private var moved = false
    private var maxPointers = 1
    private var prevDist = 0f
    private var prevFocusX = 0f
    private var prevFocusY = 0f

    private var acc = 0f
    private var lx = 0f; private var ly = 0f; private var lt = 0L

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        paperBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { paintPaper(Canvas(it), w, h) }
        contentBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        content = Canvas(contentBmp!!)
        pendingContent?.let { content!!.drawBitmap(it, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null); pendingContent = null }
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

    override fun onDraw(c: Canvas) {
        c.save()
        c.translate(offX, offY); c.scale(scale, scale)
        paperBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        contentBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        c.restore()
    }

    fun clearCanvas() { pushUndo(); content?.drawColor(0, PorterDuff.Mode.CLEAR); invalidate() }
    fun undo() { val b = undo.removeLastOrNull() ?: return; snapshotTo(redo); content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) }; invalidate() }
    fun redo() { val b = redo.removeLastOrNull() ?: return; snapshotTo(undo); content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) }; invalidate() }
    fun resetZoom() { scale = 1f; offX = 0f; offY = 0f; invalidate() }

    private fun pushUndo() { snapshotTo(undo); redo.clear() }
    private fun snapshotTo(stack: ArrayDeque<Bitmap>) {
        val b = contentBmp ?: return
        stack.addLast(b.copy(Bitmap.Config.ARGB_8888, false))
        if (stack.size > 12) stack.removeFirst()
    }

    fun loadContent(saved: Bitmap?) {
        undo.clear(); redo.clear()
        val c = content
        if (c != null && width > 0 && height > 0) {
            c.drawColor(0, PorterDuff.Mode.CLEAR)
            saved?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
            invalidate()
        } else pendingContent = saved
    }

    /** Paper + strokes flattened, for saving. */
    fun exportBitmap(): Bitmap? {
        val p = paperBmp ?: return contentBmp?.copy(Bitmap.Config.ARGB_8888, false)
        val out = p.copy(Bitmap.Config.ARGB_8888, true)
        contentBmp?.let { Canvas(out).drawBitmap(it, 0f, 0f, null) }
        return out
    }

    // touch -> content coordinate (inverse of the view transform)
    private fun cx(x: Float) = (x - offX) / scale
    private fun cy(y: Float) = (y - offY) / scale

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moved = false; maxPointers = 1; gesture = false
                drawing = true; acc = 0f; lx = cx(e.x); ly = cy(e.y); lt = System.currentTimeMillis()
                pushUndo(); strokePrep()
                strokeStart(lx, ly)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, e.pointerCount)
                if (e.pointerCount >= 2) {
                    if (drawing) { restoreBase(); drawing = false } // cancel accidental stroke
                    gesture = true
                    prevDist = spacingOf(e); val f = focusOf(e); prevFocusX = f.first; prevFocusY = f.second
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesture) {
                    if (!zoomLocked && e.pointerCount >= 2) {
                        val d = spacingOf(e); val f = focusOf(e)
                        if (prevDist > 0f) {
                            val ns = (scale * (d / prevDist)).coerceIn(1f, 5f)
                            // keep focus point stable, then pan by focus delta
                            offX = f.first - (f.first - offX) * (ns / scale)
                            offY = f.second - (f.second - offY) * (ns / scale)
                            offX += f.first - prevFocusX; offY += f.second - prevFocusY
                            scale = ns
                        }
                        if (hypot(d - prevDist, f.first - prevFocusX + f.second - prevFocusY) > 8f) moved = true
                        prevDist = d; prevFocusX = f.first; prevFocusY = f.second
                        clampPan(); invalidate()
                    }
                } else if (drawing) {
                    val x = cx(e.x); val y = cy(e.y)
                    val now = System.currentTimeMillis(); val dd = hypot(x - lx, y - ly); val v = dd / max(1L, now - lt)
                    if (dd > 1.5f) moved = true
                    strokeMove(lx, ly, x, y, v); lx = x; ly = y; lt = now; invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> { /* keep gesture until all up */ }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gesture) {
                    if (!moved) { if (maxPointers >= 3) redo() else if (maxPointers == 2) undo() }
                    gesture = false
                } else if (drawing) {
                    drawing = false; base = null; onStrokeEnd?.invoke()
                }
            }
        }
        return true
    }

    private fun spacingOf(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    }
    private fun focusOf(e: MotionEvent): Pair<Float, Float> {
        if (e.pointerCount < 2) return e.x to e.y
        return ((e.getX(0) + e.getX(1)) / 2) to ((e.getY(0) + e.getY(1)) / 2)
    }
    private fun clampPan() {
        val w = width.toFloat(); val h = height.toFloat()
        offX = offX.coerceIn(-(scale - 1f) * w, 0f)
        offY = offY.coerceIn(-(scale - 1f) * h, 0f)
    }

    private fun withAlpha(c: Int, a: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255).toInt(); return (aa shl 24) or (c and 0x00FFFFFF)
    }

    // ---- stroke lifecycle ----
    private fun strokePrep() { strokeLayer?.drawColor(0, PorterDuff.Mode.CLEAR); base = contentBmp?.copy(Bitmap.Config.ARGB_8888, false) }
    private fun restoreBase() { val b = base ?: return; content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) } }
    private fun composite() {
        val c = content ?: return; val b = base ?: return; val sb = strokeBmp ?: return
        c.drawColor(0, PorterDuff.Mode.CLEAR); c.drawBitmap(b, 0f, 0f, null)
        compositeP.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt(); c.drawBitmap(sb, 0f, 0f, compositeP)
    }

    private fun strokeStart(x: Float, y: Float) {
        when {
            erasing -> eraseDot(x, y)
            brush == BrushType.PEN -> { penDot(x, y); composite() }
            brush == BrushType.WATER -> { stampWater(x, y, strokeSize); composite() }
            else -> stampDispatch(x, y, strokeSize / 2f)
        }
    }
    private fun strokeMove(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        when {
            erasing -> eraseSeg(x0, y0, x1, y1)
            brush == BrushType.PEN -> { penSeg(x0, y0, x1, y1, speed); composite() }
            brush == BrushType.WATER -> { seg(x0, y0, x1, y1, speed); composite() }
            else -> seg(x0, y0, x1, y1, speed)
        }
    }

    private fun eraseDot(x: Float, y: Float) { content?.drawCircle(x, y, max(1f, strokeSize / 2f), eraseFill) }
    private fun eraseSeg(x0: Float, y0: Float, x1: Float, y1: Float) { eraseStroke.strokeWidth = max(1f, strokeSize); content?.drawLine(x0, y0, x1, y1, eraseStroke) }

    private fun penDot(x: Float, y: Float) { fill.color = color or (0xFF shl 24); strokeLayer?.drawCircle(x, y, max(1f, strokeSize / 2f), fill) }
    private fun penSeg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        val r = max(1f, (strokeSize / 2f) * (1 - minOf(0.45f, speed * 0.06f)))
        pen.color = color or (0xFF shl 24); pen.strokeWidth = r * 2; strokeLayer?.drawLine(x0, y0, x1, y1, pen)
    }

    private fun scaleFor(): Float = when (brush) {
        BrushType.PEN -> 1f; BrushType.PENCIL -> 1.5f; BrushType.CRAYON -> 3f; BrushType.WATER -> 6f
    }

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        var r = strokeSize / 2f * scaleFor()
        if (brush == BrushType.PENCIL) r *= (1 - minOf(0.45f, speed * 0.06f))
        r = max(1f, r)
        val spacing = when (brush) { BrushType.WATER -> r * 0.6f; BrushType.CRAYON -> r * 0.30f; else -> r * 0.20f }
        val dx = x1 - x0; val dy = y1 - y0; val d = hypot(dx, dy); if (d == 0f) return
        val nx = dx / d; val ny = dy / d
        var dist = spacing - acc; if (dist < 0) dist = 0f
        while (dist <= d) { stampDispatch(x0 + nx * dist, y0 + ny * dist, r); dist += spacing }
        acc = d - (dist - spacing)
    }

    private fun stampDispatch(x: Float, y: Float, r: Float) {
        when (brush) {
            BrushType.PENCIL -> stampPencil(x, y, r)
            BrushType.CRAYON -> stampCrayon(x, y, r)
            BrushType.WATER -> stampWater(x, y, r)
            else -> {}
        }
    }

    private fun stampPencil(x: Float, y: Float, r: Float) {
        val c = content ?: return
        val n = max(5f, r * r * 0.7f).toInt()
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val rr = Math.pow(rnd.nextDouble(), 0.7).toFloat() * r * 1.15f
            val sx = x + cos(a) * rr; val sy = y + sin(a) * rr
            val al = (0.06f + rnd.nextFloat() * 0.5f) * opacity
            val ss = if (rnd.nextFloat() < 0.2f) 1.6f else 1.0f
            fill.color = withAlpha(color, al); c.drawRect(sx, sy, sx + ss, sy + ss, fill)
        }
    }
    private fun stampCrayon(x: Float, y: Float, r: Float) {
        val c = content ?: return
        val m = max(10f, r * r * 0.55f).toInt()
        for (j in 0 until m) {
            val a = rnd.nextFloat() * 6.2832f
            val rr = rnd.nextFloat() * r * 1.15f; val edge = rr / (r * 1.15f)
            if (rnd.nextFloat() > (0.15f + 0.85f * edge)) continue
            val cxp = x + cos(a) * rr; val cyp = y + sin(a) * rr
            fill.color = withAlpha(color, (0.18f + rnd.nextFloat() * 0.6f) * opacity)
            val s = 1.5f + rnd.nextFloat() * 3f; c.drawRect(cxp, cyp, cxp + s, cyp + s, fill)
        }
    }
    private fun stampWater(x: Float, y: Float, r: Float) {
        val c = strokeLayer ?: return
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

    private fun buildBlob(cx: Float, cy: Float, r: Float) {
        var pts = ArrayList<FloatArray>(7)
        for (i in 0 until 7) { val a = i.toFloat() / 7 * 6.2832f; val rr = r * (0.8f + rnd.nextFloat() * 0.4f); pts.add(floatArrayOf(cx + cos(a) * rr, cy + sin(a) * rr)) }
        repeat(3) { d ->
            val out = ArrayList<FloatArray>(pts.size * 2); val v = r * 0.55f * Math.pow(0.55, d.toDouble()).toFloat()
            for (j in pts.indices) { val A = pts[j]; val B = pts[(j + 1) % pts.size]; out.add(A); out.add(floatArrayOf((A[0] + B[0]) / 2 + (rnd.nextFloat() - 0.5f) * v, (A[1] + B[1]) / 2 + (rnd.nextFloat() - 0.5f) * v)) }
            pts = out
        }
        path.reset(); path.moveTo(pts[0][0], pts[0][1]); for (i in 1 until pts.size) path.lineTo(pts[i][0], pts[i][1]); path.close()
    }
}
