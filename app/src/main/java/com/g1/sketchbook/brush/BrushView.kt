package com.g1.sketchbook.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
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
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class BrushType { PEN, PENCIL, CRAYON, WATER }

/**
 * Fixed-resolution canvas: strokes are drawn into bitmaps sized to the sketchbook's own pixel
 * dimensions (independent of screen). The content is then fit into the view (with an optional 90°
 * rotation) via a display matrix, and touches are mapped back through its inverse — so every visible
 * part of the paper is drawable, at any screen size, and saves come out at full resolution.
 *
 * One finger draws; two fingers pinch to zoom and pan. (Multi-finger undo/redo was removed.)
 */
class BrushView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var brush = BrushType.PEN
    var color = 0xFF2B4C9B.toInt()
    var strokeSize = 20f      // diameter in screen px
    var opacity = 1f
    var drawEnabled = true
    var erasing = false
    var paper: Bitmap? = null
    var onStrokeEnd: (() -> Unit)? = null

    private var cw = 0; private var ch = 0
    private var paperBmp: Bitmap? = null
    private var contentBmp: Bitmap? = null
    private var content: Canvas? = null
    private var strokeBmp: Bitmap? = null
    private var strokeLayer: Canvas? = null
    private var base: Bitmap? = null
    private var pendingContent: Bitmap? = null
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()

    private var rotationQ = 0                 // 0..3 quarter turns
    private val disp = Matrix()
    private val inv = Matrix()
    private var fitScale = 1f                 // screen px per canvas px

    private val userM = Matrix()              // view-space pinch zoom/pan on top of the fit
    private var userScale = 1f                // total user zoom (1 = fit, capped at 5)
    private var pinching = false
    private var prevDist = 0f
    private var prevMidX = 0f; private var prevMidY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val eraseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val eraseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val compositeP = Paint()
    private val path = Path()
    private val rnd = Random(7)

    private var strokeStarted = false
    private var acc = 0f
    private var lx = 0f; private var ly = 0f; private var lt = 0L

    /** Creates the canvas bitmaps at [w]x[h] px (capped for memory). Call once when opening a page-set. */
    fun initCanvas(w: Int, h: Int) {
        val cap = 1280
        val s = min(1f, cap.toFloat() / max(w, h))
        val nw = max(1, (w * s).toInt()); val nh = max(1, (h * s).toInt())
        if (nw == cw && nh == ch && contentBmp != null) return
        cw = nw; ch = nh
        paperBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.RGB_565).also { paintPaper(Canvas(it), cw, ch) }
        contentBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        content = Canvas(contentBmp!!)
        pendingContent?.let { content!!.drawBitmap(it, null, RectF(0f, 0f, cw.toFloat(), ch.toFloat()), null); pendingContent = null }
        strokeBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); strokeLayer = Canvas(strokeBmp!!)
        undo.clear(); redo.clear()
        resetZoom(); computeDisplay(); invalidate()
    }

    private fun resetZoom() { userM.reset(); userScale = 1f; pinching = false; prevDist = 0f }

    fun rotate() { rotationQ = (rotationQ + 1) % 4; resetZoom(); computeDisplay(); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        // A resize (fold/rotate) invalidates the old view-space zoom, so drop back to fit.
        if (cw == 0 && w > 0 && h > 0) initCanvas(w, h) else { resetZoom(); computeDisplay(); invalidate() }
    }

    private fun computeDisplay() {
        if (cw <= 0 || ch <= 0 || width <= 0 || height <= 0) return
        // Auto-turn the page a quarter so its long side follows the screen's long side
        // (a portrait page fills a landscape screen and vice-versa). Manual rotate adds on top.
        val autoQ = if ((width > height) != (cw > ch)) 1 else 0
        val q = (rotationQ + autoQ) % 4
        val rw = if (q % 2 == 0) cw else ch
        val rh = if (q % 2 == 0) ch else cw
        fitScale = min(width.toFloat() / rw, height.toFloat() / rh)
        disp.reset()
        disp.postTranslate(-cw / 2f, -ch / 2f)
        disp.postRotate(q * 90f)
        disp.postScale(fitScale, fitScale)
        disp.postTranslate(width / 2f, height / 2f)
        disp.postConcat(userM)          // apply pinch zoom/pan last, in view space
        disp.invert(inv)
    }

    /** Keeps the zoomed content from drifting fully off-screen; recentres when not zoomed. */
    private fun clampAndRefresh() {
        computeDisplay()
        val r = RectF(0f, 0f, cw.toFloat(), ch.toFloat()); disp.mapRect(r)
        var ax = 0f; var ay = 0f
        if (r.width() >= width) { if (r.left > 0) ax = -r.left else if (r.right < width) ax = width - r.right }
        else ax = (width - r.width()) / 2f - r.left
        if (r.height() >= height) { if (r.top > 0) ay = -r.top else if (r.bottom < height) ay = height - r.bottom }
        else ay = (height - r.height()) / 2f - r.top
        if (ax != 0f || ay != 0f) { userM.postTranslate(ax, ay); computeDisplay() }
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
        val cb = contentBmp ?: return
        c.save(); c.concat(disp)
        paperBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        c.drawBitmap(cb, 0f, 0f, null)
        c.restore()
    }

    fun clearCanvas() { pushUndo(); content?.drawColor(0, PorterDuff.Mode.CLEAR); invalidate() }
    fun undo() { val b = undo.removeLastOrNull() ?: return; snapshotTo(redo); restore(b); invalidate() }
    fun redo() { val b = redo.removeLastOrNull() ?: return; snapshotTo(undo); restore(b); invalidate() }
    private fun restore(b: Bitmap) { content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) } }
    private fun pushUndo() { snapshotTo(undo); redo.clear() }
    private fun snapshotTo(stack: ArrayDeque<Bitmap>) {
        val b = contentBmp ?: return
        stack.addLast(b.copy(Bitmap.Config.ARGB_8888, false)); if (stack.size > 6) stack.removeFirst()
    }

    fun loadContent(saved: Bitmap?) {
        undo.clear(); redo.clear()
        val c = content
        if (c != null && cw > 0) {
            c.drawColor(0, PorterDuff.Mode.CLEAR)
            saved?.let { c.drawBitmap(it, null, RectF(0f, 0f, cw.toFloat(), ch.toFloat()), null) }
            invalidate()
        } else pendingContent = saved
    }

    fun exportBitmap(): Bitmap? {
        val p = paperBmp ?: return contentBmp?.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawBitmap(p, 0f, 0f, null); contentBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        return out
    }

    // ---- touch (one finger draws, two fingers pinch-zoom/pan) ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawEnabled) return false
        val now = System.currentTimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                val p = mapPoint(e.x, e.y); acc = 0f; lx = p[0]; ly = p[1]; lt = now
                beginStroke(lx, ly)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    if (strokeStarted) discardStroke()   // don't leave a stray dot when zoom begins
                    pinching = true
                    prevDist = spacing(e); prevMidX = midX(e); prevMidY = midY(e)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && e.pointerCount >= 2) {
                    val d = spacing(e); val mx = midX(e); val my = midY(e)
                    if (prevDist > 0f) {
                        var ds = d / prevDist
                        val ns = (userScale * ds).coerceIn(1f, 5f); ds = ns / userScale; userScale = ns
                        userM.postScale(ds, ds, mx, my)
                        userM.postTranslate(mx - prevMidX, my - prevMidY)
                        if (userScale <= 1.001f) resetZoom()
                        clampAndRefresh()
                    }
                    prevDist = d; prevMidX = mx; prevMidY = my
                } else if (strokeStarted && !pinching) {
                    val p = mapPoint(e.x, e.y); val x = p[0]; val y = p[1]
                    val dd = hypot(x - lx, y - ly); val v = dd / max(1L, now - lt)
                    strokeMove(lx, ly, x, y, v); lx = x; ly = y; lt = now; invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Dropping back to one finger ends the pinch; the leftover finger must not start
                // a stray stroke, so we just clear pinch state (a fresh DOWN will draw next).
                if (e.pointerCount <= 2) { pinching = false; prevDist = 0f }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (strokeStarted) endStroke()
                pinching = false; prevDist = 0f
            }
        }
        return true
    }

    private fun spacing(e: MotionEvent): Float = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun midX(e: MotionEvent): Float = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent): Float = (e.getY(0) + e.getY(1)) / 2f

    /** Undo the in-progress stroke without recording it (used when a pinch takes over). */
    private fun discardStroke() {
        base?.let { b -> content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) } }
        undo.removeLastOrNull()
        strokeStarted = false; base = null; invalidate()
    }

    private val tmp = FloatArray(2)
    private fun mapPoint(x: Float, y: Float): FloatArray { tmp[0] = x; tmp[1] = y; inv.mapPoints(tmp); return tmp }

    private fun beginStroke(x: Float, y: Float) { pushUndo(); strokePrep(); strokeStart(x, y); strokeStarted = true; invalidate() }
    private fun endStroke() { strokeStarted = false; base = null; onStrokeEnd?.invoke(); invalidate() }
    private fun strokePrep() { strokeLayer?.drawColor(0, PorterDuff.Mode.CLEAR); base = contentBmp?.copy(Bitmap.Config.ARGB_8888, false) }
    private fun composite() {
        val c = content ?: return; val b = base ?: return; val sb = strokeBmp ?: return
        c.drawColor(0, PorterDuff.Mode.CLEAR); c.drawBitmap(b, 0f, 0f, null)
        compositeP.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt(); c.drawBitmap(sb, 0f, 0f, compositeP)
    }

    private fun r0() = (strokeSize / 2f) / fitScale   // base radius in canvas px

    private fun strokeStart(x: Float, y: Float) {
        when {
            erasing -> content?.drawCircle(x, y, max(1f, strokeSize / fitScale / 2f), eraseFill)
            brush == BrushType.PEN -> { penDot(x, y); composite() }
            brush == BrushType.WATER -> { stampWater(x, y, r0() * scaleFor()); composite() }
            else -> stampDispatch(x, y, r0() * scaleFor())
        }
    }
    private fun strokeMove(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        when {
            erasing -> { eraseStroke.strokeWidth = max(1f, strokeSize / fitScale); content?.drawLine(x0, y0, x1, y1, eraseStroke) }
            brush == BrushType.PEN -> { penSeg(x0, y0, x1, y1, speed); composite() }
            brush == BrushType.WATER -> { seg(x0, y0, x1, y1, speed); composite() }
            else -> seg(x0, y0, x1, y1, speed)
        }
    }

    private fun penDot(x: Float, y: Float) { fill.color = color or (0xFF shl 24); strokeLayer?.drawCircle(x, y, max(1f, r0()), fill) }
    private fun penSeg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        val r = max(1f, r0() * (1 - minOf(0.45f, speed * 0.06f)))
        pen.color = color or (0xFF shl 24); pen.strokeWidth = r * 2; strokeLayer?.drawLine(x0, y0, x1, y1, pen)
    }

    private fun scaleFor(): Float = when (brush) { BrushType.PEN -> 1f; BrushType.PENCIL -> 1.5f; BrushType.CRAYON -> 3f; BrushType.WATER -> 6f }

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        var r = r0() * scaleFor()
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
        when (brush) { BrushType.PENCIL -> stampPencil(x, y, r); BrushType.CRAYON -> stampCrayon(x, y, r); BrushType.WATER -> stampWater(x, y, r); else -> {} }
    }
    /** Canvas px per screen px — grains scale by this so they stay visible when the canvas (e.g. A4)
     *  is much larger than the view it's shown in (like a small split pane). */
    private fun grainPx() = (1f / fitScale).coerceIn(1f, 5f)

    private fun stampPencil(x: Float, y: Float, r: Float) {
        val c = content ?: return
        fill.style = Paint.Style.FILL; fill.strokeWidth = 0f   // fill is shared with water/pen; keep it FILL
        val g = grainPx()
        val n = min(900, max(5f, r * r * 0.7f).toInt())
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f; val rr = Math.pow(rnd.nextDouble(), 0.7).toFloat() * r * 1.15f
            val sx = x + cos(a) * rr; val sy = y + sin(a) * rr
            val al = (0.06f + rnd.nextFloat() * 0.5f) * opacity; val ss = (if (rnd.nextFloat() < 0.2f) 1.6f else 1.0f) * g
            fill.color = withAlpha(color, al); c.drawRect(sx, sy, sx + ss, sy + ss, fill)
        }
    }
    private fun stampCrayon(x: Float, y: Float, r: Float) {
        val c = content ?: return
        fill.style = Paint.Style.FILL; fill.strokeWidth = 0f
        val g = grainPx()
        val m = min(800, max(10f, r * r * 0.55f).toInt())
        for (j in 0 until m) {
            val a = rnd.nextFloat() * 6.2832f; val rr = rnd.nextFloat() * r * 1.15f; val edge = rr / (r * 1.15f)
            if (rnd.nextFloat() > (0.15f + 0.85f * edge)) continue
            val cxp = x + cos(a) * rr; val cyp = y + sin(a) * rr
            fill.color = withAlpha(color, (0.18f + rnd.nextFloat() * 0.6f) * opacity)
            val s = (1.5f + rnd.nextFloat() * 3f) * g; c.drawRect(cxp, cyp, cxp + s, cyp + s, fill)
        }
    }
    private fun stampWater(x: Float, y: Float, r: Float) {
        val c = strokeLayer ?: return
        val R = r * 1.3f; fill.style = Paint.Style.FILL
        for (L in 0 until 3) { buildBlob(x, y, R * (1 + L * 0.06f)); fill.color = withAlpha(color, 0.08f); c.drawPath(path, fill) }
        buildBlob(x, y, R); fill.style = Paint.Style.STROKE; fill.strokeWidth = 1.5f; fill.color = withAlpha(color, 0.16f); c.drawPath(path, fill)
        if (rnd.nextFloat() < 0.25f) {
            val a = rnd.nextFloat() * 6.2832f; val dd = R * (0.6f + rnd.nextFloat() * 0.7f)
            buildBlob(x + cos(a) * dd, y + sin(a) * dd, R * 0.5f); fill.style = Paint.Style.FILL; fill.color = withAlpha(color, 0.06f); c.drawPath(path, fill)
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

    private fun withAlpha(c: Int, a: Float): Int { val aa = (a.coerceIn(0f, 1f) * 255).toInt(); return (aa shl 24) or (c and 0x00FFFFFF) }
}
