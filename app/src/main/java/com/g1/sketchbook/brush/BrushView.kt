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
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class BrushType { PEN, PENCIL, CRAYON, WATER }

/** Action a gesture can trigger — mapped per-gesture in Settings, off (NONE) by default. */
enum class GestureAction { NONE, UNDO, REDO, EYEDROP }

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
    var color = 0xFF1E2D4C.toInt()
    var strokeSize = 20f      // diameter in screen px
    var opacity = 1f
    var drawEnabled = true
    var erasing = false
    var paper: Bitmap? = null
    var onStrokeEnd: (() -> Unit)? = null

    // Gesture shortcuts (configured in Settings; NONE = off, so behaviour is unchanged until opted in).
    var twoFingerTapAction = GestureAction.NONE
    var threeFingerTapAction = GestureAction.NONE
    var longPressAction = GestureAction.NONE
    /** Fires continuously while an armed eyedropper drag is in progress: sampled colour + screen pos,
     *  for a floating preview bubble the caller can render (no colour change until release). */
    var onEyedropPreview: ((Int, Float, Float) -> Unit)? = null
    /** Final colour pick on release. */
    var onEyedrop: ((Int) -> Unit)? = null
    /** Armed drag was released outside the canvas / cancelled — caller should hide its preview bubble. */
    var onEyedropCancel: (() -> Unit)? = null
    /** Set true to make the next touch sample a colour instead of drawing (toolbar eyedropper). Stays
     *  armed for the whole press-drag-release; disarmed automatically on release. */
    var eyedropArmed = false
    private var eyedropDragging = false

    private var cw = 0; private var ch = 0
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
    private val MIN_SCALE = Dimens.Canvas.minZoom   // zoom OUT past fit (PPT-style); see ui.theme.Dimens
    private val MAX_SCALE = Dimens.Canvas.maxZoom
    private val MAX_UNDO = 19          // kept snapshots; +1 (the live state) = 20 undoable steps
    private val LONG_PRESS_MS = 500L
    private val TAP_WINDOW_MS = 300L

    private val userM = Matrix()              // view-space pinch zoom/pan on top of the fit
    private var userScale = 1f                // total user zoom (1 = fit, capped at 5)
    private var pinching = false
    private var prevDist = 0f
    private var prevMidX = 0f; private var prevMidY = 0f

    // Multi-finger tap detection (2/3-finger tap gestures) — tracked alongside the pinch above,
    // but harmless no-ops while both tap actions are GestureAction.NONE (the default).
    private val tapSlopPx = 24f * resources.displayMetrics.density
    private var multiDownTime = 0L
    private var multiDownCount = 0
    private var multiStartMidX = 0f; private var multiStartMidY = 0f
    private var multiStartDist = 0f
    private var multiMoved = false
    private var multiTapFired = false

    // Long-press detection — a stroke always starts on ACTION_DOWN (so a plain tap still draws a
    // dot); if the finger sits still past LONG_PRESS_MS without lifting, we undo that tentative dot
    // and fire the mapped gesture instead. Also a no-op while longPressAction is NONE.
    private var longPressPending = false
    private var downX = 0f; private var downY = 0f
    private val longPressRunnable = Runnable {
        if (longPressPending && strokeStarted && !pinching) {
            longPressPending = false
            discardStroke()
            runGesture(longPressAction, downX, downY)
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val eraseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val eraseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val compositeP = Paint()
    private val paperPaint = Paint(Paint.FILTER_BITMAP_FLAG)   // smooth, full-quality paper scaling
    private val paperM = Matrix()                              // places/rotates the paper texture to cover the page
    private val pageShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pageRect = RectF()
    private val path = Path()
    private val rnd = Random(7)

    private var strokeStarted = false
    private var acc = 0f
    private var lx = 0f; private var ly = 0f; private var lt = 0L

    /** Creates the canvas bitmaps at [w]x[h] px (capped for memory). Call once when opening a page-set. */
    fun initCanvas(w: Int, h: Int) {
        val cap = 3308   // full 200-dpi A3 (2339×3307) fits; smaller sizes stay at their native px
        val s = min(1f, cap.toFloat() / max(w, h))
        val nw = max(1, (w * s).toInt()); val nh = max(1, (h * s).toInt())
        if (nw == cw && nh == ch && contentBmp != null) return
        cw = nw; ch = nh
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

    /**
     * Keeps the panned/zoomed canvas usable: you may slide the canvas aside to reveal a strip of
     * background on any side (so the edges/corners are easy to draw), but only up to [revealBudget],
     * and never so far that the screen centre stops covering the canvas — that guarantees you're
     * always drawing on the canvas, not on the empty margin. Does NOT change the canvas size.
     * (Fit recentres via resetZoom when the zoom returns to 1.)
     */
    private fun clampAndRefresh() {
        computeDisplay()
        val r = RectF(0f, 0f, cw.toFloat(), ch.toFloat()); disp.mapRect(r)
        val ax = axisAdjust(r.left, r.right, width.toFloat())
        val ay = axisAdjust(r.top, r.bottom, height.toFloat())
        if (ax != 0f || ay != 0f) { userM.postTranslate(ax, ay); computeDisplay() }
        invalidate()
    }

    /** Translation needed to keep the canvas span [lo,hi] usable on one axis. */
    private fun axisAdjust(lo: Float, hi: Float, view: Float): Float {
        val size = hi - lo
        return if (size <= view) {
            // Zoomed out (canvas smaller than the screen on this axis): keep it fully on screen,
            // free to sit anywhere inside the surrounding workspace (PowerPoint-style).
            when { lo < 0f -> -lo; hi > view -> view - hi; else -> 0f }
        } else {
            // Zoomed in (larger than the screen): keep the screen centre over the canvas so you're
            // always drawing on paper, while allowing up to half the screen as room on any side.
            val c = view / 2f
            when { lo > c -> c - lo; hi < c -> c - hi; else -> 0f }
        }
    }

    /**
     * Draws the paper bitmap to cover the canvas (centre-crop, filtered). When the paper's
     * orientation doesn't match the page (e.g. a landscape texture on a portrait mobile page),
     * it's rotated 90° first so its long side follows the page's long side — that way far more of
     * the image is used and much less gets cropped.
     */
    private fun drawPaper(c: Canvas) {
        val p = paper ?: run { c.drawColor(0xFFFBF6EA.toInt()); return }
        val rotate = (p.width > p.height) != (cw > ch)     // orientation mismatch → turn the paper
        val pw = if (rotate) p.height else p.width          // footprint after the optional rotation
        val ph = if (rotate) p.width else p.height
        val s = max(cw.toFloat() / pw, ch.toFloat() / ph)   // cover-fit
        paperM.reset()
        paperM.postTranslate(-p.width / 2f, -p.height / 2f)
        if (rotate) paperM.postRotate(90f)
        paperM.postScale(s, s)
        paperM.postTranslate(cw / 2f, ch / 2f)
        c.drawBitmap(p, paperM, paperPaint)
    }

    override fun onDraw(c: Canvas) {
        val cb = contentBmp ?: return
        // A soft drop shadow behind the page (screen space) makes the drawable paper obvious against
        // the surrounding workspace — otherwise the letterbox margins look drawable but silently
        // ignore touches. Drawn before the paper/content so it only peeks out around the edges.
        pageRect.set(0f, 0f, cw.toFloat(), ch.toFloat()); disp.mapRect(pageRect)
        drawPageShadow(c, pageRect)
        c.save(); c.concat(disp)
        // Cover-fit paper can overshoot the page rect on one axis; clip so it never spills onto the
        // surrounding zoomed-out workspace (only the canvas-sized area should ever show the texture).
        c.clipRect(0f, 0f, cw.toFloat(), ch.toFloat())
        drawPaper(c)
        c.drawBitmap(cb, 0f, 0f, null)
        c.restore()
    }

    /** Cheap layered-rect approximation of a soft drop shadow (no BlurMaskFilter, which needs a
     *  software layer to render under hardware acceleration) — light source from the upper-left.
     *  Tuned to the same weight as the sketchbook cover shadow (Modifier.shadow(12.dp, ...)) so the
     *  canvas reads as "the same kind of shadow", just behind a page instead of a book cover. */
    private val shadowDensity = resources.displayMetrics.density
    private fun drawPageShadow(c: Canvas, r: RectF) {
        val d = shadowDensity
        val dx = 3f * d; val dy = 7f * d
        val spreads = floatArrayOf(12f * d, 8f * d, 4f * d)
        val alphas = intArrayOf(18, 30, 46)
        for (i in spreads.indices) {
            val s = spreads[i]
            pageShadow.color = alphas[i] shl 24
            c.drawRoundRect(r.left - s + dx, r.top - s + dy, r.right + s + dx, r.bottom + s + dy, 14f, 14f, pageShadow)
        }
    }

    fun clearCanvas() { pushUndo(); redo.clear(); content?.drawColor(0, PorterDuff.Mode.CLEAR); invalidate() }
    fun undo() { val b = undo.removeLastOrNull() ?: return; snapshotTo(redo); restore(b); invalidate() }
    fun redo() { val b = redo.removeLastOrNull() ?: return; snapshotTo(undo); restore(b); invalidate() }
    private fun restore(b: Bitmap) { content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) } }
    // NOTE: does NOT clear the redo stack — every touch-down provisionally calls this (via
    // beginStroke) before we know whether it'll become a real stroke or get discarded by a pinch/
    // long-press/multi-tap gesture. Clearing redo here used to wipe it on every touch, silently
    // breaking "redo" whenever it was triggered by a gesture. Redo is invalidated only once a
    // stroke actually commits — see endStroke().
    private fun pushUndo() { snapshotTo(undo) }
    private fun snapshotTo(stack: ArrayDeque<Bitmap>) {
        val b = contentBmp ?: return
        stack.addLast(b.copy(Bitmap.Config.ARGB_8888, false)); if (stack.size > MAX_UNDO) stack.removeFirst()
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
        if (contentBmp == null || cw <= 0) return null
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); drawPaper(c); contentBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        return out
    }

    /** Just the strokes (no paper) — the right thing to persist for a page you'll reload into the editor. */
    fun exportContent(): Bitmap? = contentBmp?.copy(Bitmap.Config.ARGB_8888, false)

    // ---- touch (one finger draws, two fingers pinch-zoom/pan) ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawEnabled) return false
        val now = System.currentTimeMillis()
        // Eyedropper: while armed (toolbar button) or already dragging, every touch is a colour pick,
        // never a stroke — handled entirely separately from drawing/gestures below.
        if (eyedropArmed || eyedropDragging) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    eyedropDragging = true
                    pickColorAt(e.x, e.y)?.let { c -> onEyedropPreview?.invoke(c, e.x, e.y) }
                }
                MotionEvent.ACTION_MOVE -> if (eyedropDragging) {
                    pickColorAt(e.x, e.y)?.let { c -> onEyedropPreview?.invoke(c, e.x, e.y) }
                }
                MotionEvent.ACTION_UP -> if (eyedropDragging) {
                    eyedropDragging = false; eyedropArmed = false
                    val picked = pickColorAt(e.x, e.y)
                    if (picked != null) { color = picked; onEyedrop?.invoke(picked) } else onEyedropCancel?.invoke()
                }
                MotionEvent.ACTION_CANCEL -> {
                    eyedropDragging = false; eyedropArmed = false
                    onEyedropCancel?.invoke()
                }
            }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                val p = mapPoint(e.x, e.y); acc = 0f; lx = p[0]; ly = p[1]; lt = now
                downX = e.x; downY = e.y
                beginStroke(lx, ly)
                if (longPressAction != GestureAction.NONE) {
                    longPressPending = true
                    postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (longPressPending) { longPressPending = false; removeCallbacks(longPressRunnable) }
                if (e.pointerCount == 2) {
                    if (strokeStarted) discardStroke()   // don't leave a stray dot when zoom begins
                    pinching = true
                    prevDist = spacing(e); prevMidX = midX(e); prevMidY = midY(e)
                    multiStartDist = prevDist; multiStartMidX = prevMidX; multiStartMidY = prevMidY
                    multiDownTime = now; multiDownCount = 2; multiMoved = false; multiTapFired = false
                } else if (e.pointerCount == 3) {
                    multiDownTime = now; multiDownCount = 3; multiMoved = false; multiTapFired = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && e.pointerCount >= 2) {
                    val d = spacing(e); val mx = midX(e); val my = midY(e)
                    if (!multiMoved && (hypot(mx - multiStartMidX, my - multiStartMidY) > tapSlopPx ||
                            kotlin.math.abs(d - multiStartDist) > tapSlopPx)) multiMoved = true
                    // Only apply the pinch/pan transform once we're sure this isn't a tap (past slop) —
                    // otherwise the tiny natural hand tremor of placing/lifting 2-3 fingers for a tap
                    // gesture (e.g. redo) nudges the canvas by a stray pixel or two every time.
                    if (multiMoved && prevDist > 0f) {
                        var ds = d / prevDist
                        val ns = (userScale * ds).coerceIn(MIN_SCALE, MAX_SCALE); ds = ns / userScale; userScale = ns
                        userM.postScale(ds, ds, mx, my)
                        userM.postTranslate(mx - prevMidX, my - prevMidY)
                        clampAndRefresh()
                    }
                    prevDist = d; prevMidX = mx; prevMidY = my
                } else if (strokeStarted && !pinching) {
                    val p = mapPoint(e.x, e.y); val x = p[0]; val y = p[1]
                    val dd = hypot(x - lx, y - ly); val v = dd / max(1L, now - lt)
                    strokeMove(lx, ly, x, y, v); lx = x; ly = y; lt = now; invalidate()
                    if (longPressPending && hypot(e.x - downX, e.y - downY) > tapSlopPx) {
                        longPressPending = false; removeCallbacks(longPressRunnable)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!multiMoved && !multiTapFired && multiDownCount > 0 && multiDownCount == e.pointerCount &&
                    (now - multiDownTime) < TAP_WINDOW_MS) {
                    val action = when (multiDownCount) { 2 -> twoFingerTapAction; 3 -> threeFingerTapAction; else -> GestureAction.NONE }
                    if (action != GestureAction.NONE) { runGesture(action, multiStartMidX, multiStartMidY); multiTapFired = true }
                }
                // Dropping back to one finger ends the pinch; the leftover finger must not start
                // a stray stroke, so we just clear pinch state (a fresh DOWN will draw next).
                if (e.pointerCount <= 2) { pinching = false; prevDist = 0f }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (longPressPending) { longPressPending = false; removeCallbacks(longPressRunnable) }
                if (strokeStarted) endStroke()
                pinching = false; prevDist = 0f; multiDownCount = 0
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

    private fun runGesture(action: GestureAction, sx: Float, sy: Float) {
        when (action) {
            GestureAction.UNDO -> undo()
            GestureAction.REDO -> redo()
            GestureAction.EYEDROP -> pickColorAt(sx, sy)?.let { c -> color = c; onEyedrop?.invoke(c) }
            GestureAction.NONE -> {}
        }
    }

    /** Samples the colour actually shown at a screen point: strokes first, then the paper texture underneath. */
    private fun pickColorAt(sx: Float, sy: Float): Int? {
        val p = mapPoint(sx, sy)
        val x = p[0].toInt(); val y = p[1].toInt()
        if (x !in 0 until cw || y !in 0 until ch) return null
        contentBmp?.let { cb -> val px = cb.getPixel(x, y); if (((px ushr 24) and 0xFF) > 10) return px or (0xFF shl 24) }
        val paperBmp = paper ?: return 0xFFFBF6EA.toInt()
        val rotate = (paperBmp.width > paperBmp.height) != (cw > ch)
        val pw = if (rotate) paperBmp.height else paperBmp.width
        val ph = if (rotate) paperBmp.width else paperBmp.height
        val s = max(cw.toFloat() / pw, ch.toFloat() / ph)
        val m = Matrix()
        m.postTranslate(-paperBmp.width / 2f, -paperBmp.height / 2f)
        if (rotate) m.postRotate(90f)
        m.postScale(s, s)
        m.postTranslate(cw / 2f, ch / 2f)
        val im = Matrix(); if (!m.invert(im)) return 0xFFFBF6EA.toInt()
        val pt = floatArrayOf(x.toFloat(), y.toFloat()); im.mapPoints(pt)
        val px = pt[0].toInt().coerceIn(0, paperBmp.width - 1)
        val py = pt[1].toInt().coerceIn(0, paperBmp.height - 1)
        return paperBmp.getPixel(px, py) or (0xFF shl 24)
    }

    private val tmp = FloatArray(2)
    private fun mapPoint(x: Float, y: Float): FloatArray { tmp[0] = x; tmp[1] = y; inv.mapPoints(tmp); return tmp }

    private fun beginStroke(x: Float, y: Float) { pushUndo(); strokePrep(); strokeStart(x, y); strokeStarted = true; invalidate() }
    private fun endStroke() { strokeStarted = false; base = null; redo.clear(); onStrokeEnd?.invoke(); invalidate() }
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
