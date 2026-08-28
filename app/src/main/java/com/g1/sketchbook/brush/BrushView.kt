package com.g1.sketchbook.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// SMOOTH_TEST: 실험용 임시 브러시(test/smooth-brush 브랜치) — PEN과 렌더링은 동일(penDot/penSeg)하고
// 입력 좌표에만 EMA 스무딩을 얹어서 PEN과 나란히 비교해보기 위한 것. 결과가 좋으면 정식 기능으로
// 정리하고, 아니면 이 브랜치째 버린다.
enum class BrushType { PEN, PENCIL, CRAYON, WATER, SMOOTH_TEST }

/** Action a gesture can trigger — mapped per-gesture in Settings, off (NONE) by default. */
enum class GestureAction { NONE, UNDO, REDO, EYEDROP, TOGGLE_TOOLBARS }

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
    // 캔버스 픽셀 기준 고정 지름 — 화면 밀도/줌(fitScale)과 무관하게 항상 같은 실제(종이 기준 mm) 굵기로
    // 찍힌다. 그래서 같은 단계라도 큰 캔버스(A3 등)에서는 화면에 맞춰 더 축소해서 보여주는 만큼
    // 화면상으로는 더 얇게 보임 — 실제 펜으로 큰 종이에 그리는 것과 같은 체감.
    var strokeSize = 20f      // diameter in canvas px
    var opacity = 1f
    // 지우개 전용 부드러움(경계 블러) 반경, 캔버스 px — 0이면 기존처럼 또렷한 경계.
    // 브러시 획에는 쓰이지 않음(지우개만의 개념이라 strokeSize/opacity처럼 공유 필드로 두지 않음).
    var eraserBlur = 0f
    var drawEnabled = true
    var erasing = false
    /** "화면 잠금" toggle from the toolbar — when true, blocks pinch zoom/pan and the 90° rotate
     *  button, but drawing itself is untouched. Guards the transform paths directly rather than the
     *  caller, so it can't be bypassed by any entry point (button, gesture, future ones). */
    var locked = false
    var paper: Bitmap? = null
    /** "선생님모드" 가이드 — 공유그리기 host의 최신 스냅샷을 [teacherOverlayOpacity] 투명도로 내
     *  캔버스 위에 겹쳐 보여준다(공유그리기 전용, 그 외엔 항상 null). content 위에 그려야 하므로
     *  paper처럼 배경이 아니라 onDraw에서 contentBmp 다음에 합성한다. 외부에서 매번 다시 대입하므로
     *  setter에서 직접 invalidate() — 다른 프레임 트리거(스트로크 등)에 얹혀가지 않는 유일한 값이라
     *  필요하다. */
    var teacherOverlay: Bitmap? = null
        set(value) { field = value; invalidate() }
    /** 0f~1f, 공유받는 쪽(뷰어)이 직접 조절 — host가 아니라 보는 사람 화면마다 다를 수 있다. */
    var teacherOverlayOpacity = 0.5f
        set(value) { field = value; teacherOverlayPaint.alpha = (value * 255f).roundToInt().coerceIn(0, 255); invalidate() }
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
    /** GestureAction.TOGGLE_TOOLBARS fired — caller toggles its own floating toolbars' collapsed
     *  state (BrushView has no notion of them, just relays the gesture). */
    var onToggleToolbars: (() -> Unit)? = null
    /** Fires once per three-finger horizontal swipe past the threshold: +1 = next page, -1 = previous.
     *  Purely a signal (no built-in animation) — the caller just calls goTo(page±dir) instantly. Two
     *  fingers still pinch-zoom/pan as before; a third finger switches that gesture to page-turning
     *  instead, so the two never fight over the same drag. */
    var onThreeFingerSwipe: ((Int) -> Unit)? = null
    /** Set true to make the next touch sample a colour instead of drawing (toolbar eyedropper). Stays
     *  armed for the whole press-drag-release; disarmed automatically on release. */
    var eyedropArmed = false
    private var eyedropDragging = false

    /** S펜(스타일러스) 사이드 버튼을 누르고 있는 동안 true, 떼면 false — 지금 어떤 모드든(브러시/
     *  올가미/채우기/스포이드) 상관없이 순수 버튼 상태 변화만 알려준다(2026-08-27). 호출부가 "누르는
     *  동안만 지우개, 떼면 이전 도구로 복귀" 같은 걸 구현하는 용도. 펜 끝이 화면에 닿아 있을 때만
     *  버튼 상태가 들어오는 게 안드로이드 스타일러스 입력의 일반적인 동작이다. */
    var onStylusButtonChanged: ((Boolean) -> Unit)? = null
    private var stylusButtonDown = false

    /** 올가미(라소) 선택 모드 — 켜져 있으면 손가락으로 자유형 영역을 그려 선택하고, 선택 영역
     *  안쪽을 다시 눌러 드래그하면 그 자리로 옮길 수 있다. 다른 브러시로 바꿔 꺼지면(false로
     *  세팅되면) 남아있던 선택은 자동으로 풀린다. */
    var lassoMode: Boolean = false
        set(value) { if (!value && field) clearSelection(); field = value }
    /** 페인트통(채우기) 모드 — 켜져 있으면 손가락을 대는 즉시 그 지점과 이어진 같은 색 영역
     *  전체를 현재 색·불투명도로 채운다(정확히 같은 색만 — 허용오차 없음). */
    var fillMode: Boolean = false
    /** 라소로 선택 영역이 생기면(true, 화면 px 좌표와 함께) / 없어지면(false) 알려준다 — "선택 지우기"
     *  버튼을 툴바가 아니라 선택 영역 바로 위에 뜨는 팝업으로 보여주는 용도(2026-08-26, 드래그로
     *  옮기는 중엔 위치가 안 맞으니 잠시 false로 숨겼다가 손을 떼면 새 위치로 다시 true). */
    var onLassoSelectionChanged: ((Boolean, Float, Float) -> Unit)? = null

    /** 라소 모드에서 캔버스(페이지) 바깥의 여백(줌아웃했을 때 보이는 워크스페이스 영역)을 탭하면
     *  호출된다 — Compose 쪽에서 라소를 끄고 원래 쓰던 브러시로 돌아가는 용도. */
    var onLassoTapOutside: (() -> Unit)? = null

    private var lassoDrawing = false
    private val lassoPath = Path()
    private var selectionPath: Path? = null
    private var selectionRegion: android.graphics.Region? = null
    private var selectionBmp: Bitmap? = null
    private var movingSelection = false
    // 화면좌표계 델타 변환(이동+크기+회전)을 매 프레임 누적한다 — 손 뗄 때 이 하나의 행렬을
    // 캔버스좌표계로 바꿔(disp/inv 사잇값) 실제 픽셀에 한 번에 합성한다(commitMove 참고).
    private var selectionTransform = Matrix()
    private var prevSelX = 0f; private var prevSelY = 0f       // 손가락 1개일 때(이동만)
    private var selTransforming = false                          // 손가락 2개 이상(크기+회전) 진입 여부
    private var prevSelDist = 0f; private var prevSelAngleDeg = 0f
    private var prevSelMidX = 0f; private var prevSelMidY = 0f
    private val selectionOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dimens.Canvas.lassoStrokeWidthDp * resources.displayMetrics.density
        color = 0xFF444444.toInt()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

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
    // When a finger lifts but 2+ remain (e.g. releasing the 3rd finger after a page-turn swipe),
    // Android reassigns pointer indices 0/1 to whichever fingers are left — spacing()/midX()/midY()
    // always read those indices, so prevDist/prevMidX/prevMidY (last computed against the *old*
    // index assignment) no longer describe the same two fingers. Left as-is, the next MOVE event
    // diffs against that stale point and applies a sudden, spurious pan/zoom jump. This flag tells
    // the next MOVE to just re-baseline instead of diffing.
    private var resyncPinchBaseline = false

    // Multi-finger tap detection (2/3-finger tap gestures) — tracked alongside the pinch above,
    // but harmless no-ops while both tap actions are GestureAction.NONE (the default).
    private val tapSlopPx = 24f * resources.displayMetrics.density
    private var multiDownTime = 0L
    private var multiDownCount = 0
    private var multiStartMidX = 0f; private var multiStartMidY = 0f
    private var multiStartDist = 0f
    private var multiMoved = false
    private var multiTapFired = false
    private var threeFingerSwipeFired = false
    private val pageSwipeThresholdPx = 72f * resources.displayMetrics.density

    // Long-press detection — a stroke always starts on ACTION_DOWN (so a plain tap still draws a
    // dot); if the finger sits still past LONG_PRESS_MS without lifting, we undo that tentative dot
    // and fire the mapped gesture instead. Also a no-op while longPressAction is NONE.
    private var longPressPending = false
    private var downX = 0f; private var downY = 0f
    private val longPressRunnable = Runnable {
        if (longPressPending && strokeStarted && !pinching) {
            longPressPending = false
            discardStroke()
            if (longPressAction == GestureAction.EYEDROP) {
                // Hand off to the same press-drag-release flow the toolbar eyedropper uses, instead
                // of firing blind — the finger is still down, so this shows the floating colour
                // preview immediately and only commits when the finger lifts.
                eyedropDragging = true
                pickColorAt(downX, downY)?.let { c -> onEyedropPreview?.invoke(c, downX, downY) }
            } else {
                runGesture(longPressAction, downX, downY)
            }
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    // DST_OUT(CLEAR 아님)이라 paint.alpha가 실제로 "얼마나 지울지"에 반영됨(불투명도) — CLEAR는 항상
    // 완전히 지워서 알파값을 무시하기 때문에 바꿈. 블러(eraserBlur)는 stroke마다 maskFilter로 적용.
    private val eraseStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val eraseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val compositeP = Paint()
    private val paperPaint = Paint(Paint.FILTER_BITMAP_FLAG)   // smooth, full-quality paper scaling
    private val paperM = Matrix()                              // places/rotates the paper texture to cover the page
    private val teacherOverlayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 128 }
    private val pageEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * resources.displayMetrics.density; color = 0x2E000000
    }
    private val pageRect = RectF()
    private val path = Path()
    private val rnd = Random(7)

    private var strokeStarted = false
    private var acc = 0f
    private var lx = 0f; private var ly = 0f; private var lt = 0L
    // 터치 이벤트 사이 순간속도(dd/dt)는 샘플링 간격이 들쭉날쭉해서 프레임마다 크게 튄다 — 그대로
    // 굵기에 매핑하면 세그먼트마다 굵기가 뚝뚝 끊겨 소세지처럼 보인다(캡슐 모양 선분이 이웃과
    // 부드럽게 안 이어짐). 지수이동평균으로 완만하게 눌러서 굵기 변화가 스트로크를 따라 서서히
    // 일어나게 한다.
    private var smoothedSpeed = 0f
    // SMOOTH_TEST 전용 위치 스무딩(EMA) — 원본 터치 좌표(lx,ly가 매 프레임 갱신되는 값)를 그대로
    // 찍지 않고, 뒤에서 완만하게 따라오는 별도 좌표를 유지해서 그 좌표로 찍는다.
    private var smoothX = 0f; private var smoothY = 0f
    /** 밀리초 단위 시간 상수(RC 로우패스 필터와 같은 개념) — 0이면 원본 그대로, 클수록 더 뭉근하게
     *  (더 오래) 따라와서 지그재그가 더 납작해진다. 툴바의 스무딩 강도(0~100) 슬라이더가 이 값으로
     *  변환해서 넣어준다. SMOOTH_TEST 브러시에만 쓰인다.
     *
     *  ms 단위로 둔 이유: 처음엔 "매 샘플마다 (raw-smooth)*alpha만큼 이동"하는 고정 alpha였는데,
     *  historical point 처리를 추가해서 한 프레임에 여러 샘플이 들어오게 되자 같은 alpha라도 실제
     *  걸린 시간당 훨씬 더 빨리 원본을 따라잡아버려 스무딩 효과가 거의 사라졌다(샘플 개수가
     *  많아질수록 필터가 시간 기준이 아니라 "샘플 개수" 기준으로 동작했기 때문). dt 기반 지수감쇠
     *  alpha = 1 - e^(-dt/시간상수) 를 쓰면 샘플이 몇 개로 쪼개지든 일정 시간당 스무딩 정도가
     *  똑같이 유지된다. */
    var smoothTimeConstantMs = 60f

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
        clearSelection()
        resetZoom(); computeDisplay(); invalidate()
    }

    private fun resetZoom() { userM.reset(); userScale = 1f; pinching = false; prevDist = 0f }

    fun rotate() { if (locked) return; rotationQ = (rotationQ + 1) % 4; resetZoom(); computeDisplay(); invalidate() }

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
        c.save(); c.concat(disp)
        // Cover-fit paper can overshoot the page rect on one axis; clip so it never spills onto the
        // surrounding zoomed-out workspace (only the canvas-sized area should ever show the texture).
        c.clipRect(0f, 0f, cw.toFloat(), ch.toFloat())
        drawPaper(c)
        c.drawBitmap(cb, 0f, 0f, null)
        teacherOverlay?.let { c.drawBitmap(it, null, RectF(0f, 0f, cw.toFloat(), ch.toFloat()), teacherOverlayPaint) }
        if (movingSelection) {
            selectionBmp?.let { sb ->
                // disp → selectionTransform(화면좌표 델타) → inv, 세 개를 이어붙여 원본 비트맵의
                // 캔버스좌표를 "지금 보이는 자리"의 캔버스좌표로 바꾼다(이 c는 이미 disp가 concat된
                // 상태라 drawBitmap(bitmap, matrix, ..)의 matrix는 그 위에 추가로 적용됨).
                val m = Matrix(disp); m.postConcat(selectionTransform); m.postConcat(inv)
                c.drawBitmap(sb, m, null)
            }
        }
        c.restore()
        // A thin, faint outline (no shadow) so the drawable paper edge is obvious against the
        // surrounding workspace — otherwise the letterbox margins look drawable but silently ignore touches.
        pageRect.set(0f, 0f, cw.toFloat(), ch.toFloat()); disp.mapRect(pageRect)
        c.drawRect(pageRect, pageEdge)
        // 라소 선택 테두리(점선, "marching ants") — 화면 좌표로 옮겨서 그려야 캔버스 크기·줌과
        // 무관하게 항상 같은 두께로 보인다. 드래그 중이면 selectionTransform(이미 화면좌표계)을
        // 그대로 적용한다.
        selectionPath?.let { sp ->
            val screenPath = Path(sp); screenPath.transform(disp)
            if (movingSelection) screenPath.transform(selectionTransform)
            c.drawPath(screenPath, selectionOutline)
        }
        if (lassoDrawing) {
            val screenPath = Path(lassoPath); screenPath.transform(disp)
            c.drawPath(screenPath, selectionOutline)
        }
    }

    fun clearCanvas() { pushUndo(); redo.clear(); clearSelection(); content?.drawColor(0, PorterDuff.Mode.CLEAR); invalidate() }

    /** 라소로 선택된 영역의 내용만 지운다(툴바 "선택 지우기" 버튼) — 선택이 없으면 아무 것도 안 한다. */
    fun deleteLassoSelection() {
        val sp = selectionPath ?: return
        pushUndo()
        content?.let { c -> c.save(); c.clipPath(sp); c.drawColor(0, PorterDuff.Mode.CLEAR); c.restore() }
        redo.clear()
        clearSelection()
        invalidate()
        onStrokeEnd?.invoke()
    }

    private fun clearSelection() {
        if (selectionPath == null && !movingSelection) return
        selectionPath = null; selectionRegion = null; selectionBmp = null
        movingSelection = false; selTransforming = false; selectionTransform = Matrix(); lassoDrawing = false
        onLassoSelectionChanged?.invoke(false, 0f, 0f)
        invalidate()
    }

    /** 선택 영역(캔버스 좌표)의 화면 px 상단-중앙 — 삭제 버튼을 그 바로 위에 띄우는 앵커. */
    private fun selectionScreenAnchor(): FloatArray? {
        val bounds = selectionRegion?.bounds ?: return null
        if (bounds.isEmpty) return null
        val rect = RectF(bounds)
        disp.mapRect(rect)
        return floatArrayOf(rect.centerX(), rect.top)
    }
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
        clearSelection()
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
        // 지금 어떤 모드(브러시/올가미/채우기/스포이드)든 상관없이 버튼 눌림/뗌 변화만 먼저 감지 —
        // 아래 모드별 분기와 완전히 독립적이라 어느 도구를 쓰던 중이었어도 정확히 반영된다.
        val stylusButtonNow = e.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
        if (stylusButtonNow != stylusButtonDown) {
            stylusButtonDown = stylusButtonNow
            onStylusButtonChanged?.invoke(stylusButtonNow)
        }
        // SystemClock.uptimeMillis()를 쓴다 — MotionEvent.getEventTime()/getHistoricalEventTime()이
        // 이 시계 기준이라, 아래(스무딩 브러시) historical point 처리에서 실제 이벤트 시각과 맞아떨어지게
        // 하려면 여기 lt/now도 반드시 같은 시계여야 한다(System.currentTimeMillis()와 섞으면 dt가
        // 완전히 틀어짐).
        val now = SystemClock.uptimeMillis()
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
                    performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    eyedropDragging = false; eyedropArmed = false
                    onEyedropCancel?.invoke()
                }
            }
            return true
        }
        // 올가미(라소): 선택 그리기 또는(선택 안쪽을 눌렀으면) 선택 이동/크기조절/회전 — 둘 다 일반
        // 드로잉/핀치줌과 완전히 분리된 별도 제스처(스포이드와 같은 구조). 선택을 옮기는 중 손가락이
        // 하나 더 닿으면 두 손가락 사이 거리·각도 변화로 크기·회전을 함께 조작한다(사진 앱들의 표준
        // 두 손가락 트랜스폼 제스처와 같은 방식 — 손가락 중점을 축으로 매 프레임 회전→확대→이동을
        // 누적 적용).
        if (lassoMode) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = mapPoint(e.x, e.y)
                    if (p[0] < 0f || p[0] > cw || p[1] < 0f || p[1] > ch) {
                        onLassoTapOutside?.invoke()
                        return true
                    }
                    val sr = selectionRegion; val sp = selectionPath
                    if (sr != null && sp != null && sr.contains(p[0].toInt(), p[1].toInt())) {
                        pushUndo()
                        selectionBmp = liftSelection(sp)
                        movingSelection = true
                        selTransforming = false
                        selectionTransform = Matrix()
                        prevSelX = e.x; prevSelY = e.y
                        onLassoSelectionChanged?.invoke(false, 0f, 0f) // 드래그 중엔 옛 위치라 잠시 숨김 — commitMove가 새 위치로 다시 띄운다
                    } else {
                        clearSelection()
                        lassoDrawing = true
                        lassoPath.reset(); lassoPath.moveTo(p[0], p[1])
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (movingSelection && e.pointerCount == 2) {
                        selTransforming = true
                        prevSelDist = spacing(e); prevSelMidX = midX(e); prevSelMidY = midY(e)
                        prevSelAngleDeg = angleDeg(e)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (movingSelection && selTransforming && e.pointerCount >= 2) {
                        val d = spacing(e); val mx = midX(e); val my = midY(e); val a = angleDeg(e)
                        if (prevSelDist > 0f) {
                            val ds = d / prevSelDist
                            selectionTransform.postScale(ds, ds, mx, my)
                            selectionTransform.postRotate(a - prevSelAngleDeg, mx, my)
                            selectionTransform.postTranslate(mx - prevSelMidX, my - prevSelMidY)
                        }
                        prevSelDist = d; prevSelMidX = mx; prevSelMidY = my; prevSelAngleDeg = a
                    } else if (movingSelection) {
                        selectionTransform.postTranslate(e.x - prevSelX, e.y - prevSelY)
                        prevSelX = e.x; prevSelY = e.y
                    } else if (lassoDrawing) {
                        val p = mapPoint(e.x, e.y); lassoPath.lineTo(p[0], p[1])
                    }
                    invalidate()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (movingSelection && selTransforming && e.pointerCount == 2) {
                        // 트랜스폼용 손가락 둘 중 하나가 떨어짐 — 남은 손가락 하나로 이동만 계속하되,
                        // 지금 위치를 새 기준점으로 다시 잡아야 다음 MOVE에서 안 튄다(캔버스 3손가락
                        // 스와이프 뒤 2손가락 팬으로 떨어질 때와 같은 이유).
                        selTransforming = false
                        val survivorIndex = if (e.actionIndex == 0) 1 else 0
                        prevSelX = e.getX(survivorIndex); prevSelY = e.getY(survivorIndex)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (movingSelection) commitMove() else if (lassoDrawing) commitLasso()
                    lassoDrawing = false
                    if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
            }
            return true
        }
        // 페인트통은 여기서 따로 가로채지 않는다 — 일반 드로잉과 같은 흐름(아래 when)을 그대로 타되
        // beginStroke/strokeMove/endStroke 안에서 fillMode일 때만 다르게 동작하도록 분기한다. 그래야
        // 핀치줌(두 손가락)·멀티핑거 탭 제스처가 페인트통 선택 중에도 그대로 작동한다(예전엔 이 위치에서
        // 무조건 true를 반환해 다른 제스처를 전부 막고 있었음, 2026-08-20).
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                val p = mapPoint(e.x, e.y); acc = 0f; lx = p[0]; ly = p[1]; lt = now; smoothedSpeed = 0f
                smoothX = lx; smoothY = ly
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
                    // 3번째 손가락이 닿은 시점을 스와이프 기준점으로 다시 잡는다 — 2손가락 핀치 중
                    // 3번째가 얹히는 경우에도 페이지 넘기기 판정이 그 순간부터 새로 시작되도록.
                    multiStartMidX = midX(e); multiStartMidY = midY(e)
                    multiDownTime = now; multiDownCount = 3; multiMoved = false; multiTapFired = false
                    threeFingerSwipeFired = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching) {
                    // Pinch/pan math always reads the first two pointers (spacing/midX/midY use
                    // index 0/1), regardless of whether a 3rd finger is also down.
                    val d = spacing(e); val mx = midX(e); val my = midY(e)
                    if (resyncPinchBaseline) {
                        resyncPinchBaseline = false
                        prevDist = d; prevMidX = mx; prevMidY = my
                        multiMoved = false
                    }
                    if (!multiMoved && (hypot(mx - multiStartMidX, my - multiStartMidY) > tapSlopPx ||
                            kotlin.math.abs(d - multiStartDist) > tapSlopPx)) multiMoved = true
                    if (e.pointerCount == 3) {
                        // 3손가락 스와이프 = 페이지 넘기기. 핀치/팬과는 완전히 분리된 제스처라, 3번째
                        // 손가락이 있는 동안은 확대/이동을 전혀 적용하지 않고 스와이프 판정만 한다
                        // (같은 드래그가 캔버스도 밀고 페이지도 넘기면 둘 다 어색해짐).
                        if (!threeFingerSwipeFired) {
                            val dx = mx - multiStartMidX; val dy = my - multiStartMidY
                            if (kotlin.math.abs(dx) > pageSwipeThresholdPx && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                                threeFingerSwipeFired = true
                                onThreeFingerSwipe?.invoke(if (dx < 0f) 1 else -1)
                            }
                        }
                    } else if (e.pointerCount == 2) {
                        // Only apply the pinch/pan transform once we're sure this isn't a tap (past
                        // slop) — otherwise the tiny natural hand tremor of placing/lifting fingers for
                        // a tap gesture (e.g. redo) nudges the canvas by a stray pixel or two every time.
                        if (multiMoved && prevDist > 0f) {
                            // 화면 잠금: 확대·축소는 막되 두 손가락 이동(팬)은 그대로 허용한다.
                            if (!locked) {
                                var ds = d / prevDist
                                val ns = (userScale * ds).coerceIn(MIN_SCALE, MAX_SCALE); ds = ns / userScale; userScale = ns
                                userM.postScale(ds, ds, mx, my)
                            }
                            userM.postTranslate(mx - prevMidX, my - prevMidY)
                            clampAndRefresh()
                        }
                    }
                    prevDist = d; prevMidX = mx; prevMidY = my
                } else if (strokeStarted && !pinching) {
                    // 화면 터치 샘플링 주파수(보통 120Hz+)가 프레임 콜백 주기(보통 60Hz)보다 높으면
                    // 한 ACTION_MOVE에 여러 실제 터치 샘플이 배치로 묶여 도착한다 — e.x/e.y만 읽으면
                    // 중간 샘플들이 통째로 버려져서, 한 스텝에 더 큰 거리를 "뭉텅이"로 이동한 것처럼
                    // 보인다. SMOOTH_TEST처럼 프레임 간 거리에 민감한 필터(EMA)를 걸 때 이게 스무딩
                    // 효과를 깎아먹으면서 지연만 남기는 원인이 될 수 있어, historical point를 먼저
                    // 다 처리하고 마지막에 최신 좌표를 처리한다(2026-08-28).
                    for (i in 0 until e.historySize) {
                        processMovePoint(e.getHistoricalX(i), e.getHistoricalY(i), e.getHistoricalEventTime(i))
                    }
                    processMovePoint(e.x, e.y, now)
                    invalidate()
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
                if (e.pointerCount <= 2) {
                    pinching = false; prevDist = 0f
                } else {
                    // Still 2+ fingers left (e.g. 3 -> 2 after a page-turn swipe) — the survivors'
                    // pointer indices are about to be reassigned, so force the next MOVE to
                    // re-baseline instead of jumping (see resyncPinchBaseline's doc comment).
                    resyncPinchBaseline = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (longPressPending) { longPressPending = false; removeCallbacks(longPressRunnable) }
                if (strokeStarted) endStroke()
                pinching = false; prevDist = 0f; multiDownCount = 0
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun spacing(e: MotionEvent): Float = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun midX(e: MotionEvent): Float = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent): Float = (e.getY(0) + e.getY(1)) / 2f
    private fun angleDeg(e: MotionEvent): Float =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    /** Undo the in-progress stroke without recording it (used when a pinch takes over). Fill mode
     *  never pushed an undo snapshot or touched the canvas in the first place (see [beginStroke]),
     *  so there's nothing to undo here — just drop the pending tap. */
    private fun discardStroke() {
        if (fillMode) { strokeStarted = false; invalidate(); return }
        base?.let { b -> content?.let { it.drawColor(0, PorterDuff.Mode.CLEAR); it.drawBitmap(b, 0f, 0f, null) } }
        undo.removeLastOrNull()
        strokeStarted = false; base = null; invalidate()
    }

    /** [dir] is only meaningful for PAGE_TURN (a tap gesture has no direction, so it defaults to
     *  advancing forward); other actions ignore it. */
    private fun runGesture(action: GestureAction, sx: Float, sy: Float) {
        when (action) {
            GestureAction.UNDO -> undo()
            GestureAction.REDO -> redo()
            GestureAction.EYEDROP -> pickColorAt(sx, sy)?.let { c -> color = c; onEyedrop?.invoke(c) }
            GestureAction.TOGGLE_TOOLBARS -> onToggleToolbars?.invoke()
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

    /** 선택 경로 안쪽 픽셀만 복사해 새 비트맵으로 떼어내고, 원본 캔버스에서는 그 자리를 지운다
     *  (드래그 중엔 이 떼어낸 비트맵을 화면 오프셋만큼 옮겨 그리다가, 손을 떼면 실제 위치에 합성). */
    private fun liftSelection(path: Path): Bitmap {
        val cb = contentBmp!!
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.save(); oc.clipPath(path); oc.drawBitmap(cb, 0f, 0f, null); oc.restore()
        content?.let { c -> c.save(); c.clipPath(path); c.drawColor(0, PorterDuff.Mode.CLEAR); c.restore() }
        return out
    }

    /** 라소로 그린 경로를 닫고 선택 영역으로 확정 — 너무 작거나 자기 자신과 안 겹쳐 빈 영역이면
     *  선택 없이 취소된다. */
    private fun commitLasso() {
        lassoPath.close()
        val region = android.graphics.Region()
        val clip = android.graphics.Region(0, 0, cw, ch)
        val ok = region.setPath(lassoPath, clip)
        if (!ok || region.isEmpty) { lassoPath.reset(); return }
        selectionPath = Path(lassoPath)
        selectionRegion = region
        lassoPath.reset()
        selectionScreenAnchor()?.let { a -> onLassoSelectionChanged?.invoke(true, a[0], a[1]) }
        invalidate()
    }

    /** 드래그(+선택적 크기/회전)로 바뀐 선택을 실제 캔버스 픽셀에 합성하고, 선택 영역 자체도 새
     *  위치/모양으로 옮겨서 계속 선택된 상태를 유지한다(다시 드래그하거나 지우기를 바로 이어갈 수
     *  있게). onDraw의 미리보기와 정확히 같은 행렬(disp→selectionTransform→inv)을 써서 미리보기와
     *  최종 결과가 한 픽셀도 안 어긋난다. */
    private fun commitMove() {
        val sb = selectionBmp
        if (sb == null) { movingSelection = false; selTransforming = false; return }
        val m = Matrix(disp); m.postConcat(selectionTransform); m.postConcat(inv)
        content?.drawBitmap(sb, m, null)
        selectionPath?.transform(m)
        selectionPath?.let { sp -> selectionRegion = android.graphics.Region().apply { setPath(sp, android.graphics.Region(0, 0, cw, ch)) } }
        movingSelection = false; selTransforming = false; selectionTransform = Matrix(); selectionBmp = null
        redo.clear()
        selectionScreenAnchor()?.let { a -> onLassoSelectionChanged?.invoke(true, a[0], a[1]) }
        invalidate(); onStrokeEnd?.invoke()
    }

    /** 페인트통: 터치한 지점과 정확히 같은 색으로 이어진 영역 전체를 찾아 현재 색·불투명도로 단색
     *  채운다(스캔라인 방식 flood fill — 픽셀 하나하나가 아니라 가로줄 구간 단위로 채워서 큰
     *  캔버스에서도 감당할 수 있는 속도로 동작). */
    private fun floodFillAt(sx: Float, sy: Float) {
        val p = mapPoint(sx, sy)
        val x0 = p[0].toInt(); val y0 = p[1].toInt()
        if (x0 !in 0 until cw || y0 !in 0 until ch) return
        val bmp = contentBmp ?: return
        val pixels = IntArray(cw * ch)
        bmp.getPixels(pixels, 0, cw, 0, 0, cw, ch)
        val target = pixels[y0 * cw + x0]
        val replacement = blendOver(target, color, opacity)
        if (target == replacement) return
        pushUndo()
        scanlineFill(pixels, cw, ch, x0, y0, target, replacement)
        bmp.setPixels(pixels, 0, cw, 0, 0, cw, ch)
        redo.clear()
        invalidate()
        onStrokeEnd?.invoke()
    }

    /** 표준 source-over 알파 합성 — [srcColor]를 [srcAlpha01](0~1) 불투명도로 [dst] 위에 얹는다. */
    private fun blendOver(dst: Int, srcColor: Int, srcAlpha01: Float): Int {
        val sa = srcAlpha01.coerceIn(0f, 1f)
        if (sa <= 0f) return dst
        val sr = (srcColor shr 16) and 0xFF; val sg = (srcColor shr 8) and 0xFF; val sb = srcColor and 0xFF
        val da = ((dst ushr 24) and 0xFF) / 255f
        val dr = (dst shr 16) and 0xFF; val dg = (dst shr 8) and 0xFF; val db = dst and 0xFF
        val outA = sa + da * (1f - sa)
        if (outA <= 0f) return 0
        val outR = ((sr * sa + dr * da * (1f - sa)) / outA).toInt().coerceIn(0, 255)
        val outG = ((sg * sa + dg * da * (1f - sa)) / outA).toInt().coerceIn(0, 255)
        val outB = ((sb * sa + db * da * (1f - sa)) / outA).toInt().coerceIn(0, 255)
        val outAi = (outA * 255f).toInt().coerceIn(0, 255)
        return (outAi shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun scanlineFill(pixels: IntArray, w: Int, h: Int, x0: Int, y0: Int, target: Int, replacement: Int) {
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(x0, y0))
        while (stack.isNotEmpty()) {
            val (sx, sy) = stack.removeLast()
            if (sx !in 0 until w || sy !in 0 until h) continue
            if (pixels[sy * w + sx] != target) continue
            var xl = sx
            while (xl - 1 >= 0 && pixels[sy * w + xl - 1] == target) xl--
            var xr = sx
            while (xr + 1 < w && pixels[sy * w + xr + 1] == target) xr++
            for (xx in xl..xr) pixels[sy * w + xx] = replacement
            if (sy - 1 >= 0) seedSpan(pixels, w, xl, xr, sy - 1, target, stack)
            if (sy + 1 < h) seedSpan(pixels, w, xl, xr, sy + 1, target, stack)
        }
    }

    private fun seedSpan(pixels: IntArray, w: Int, xl: Int, xr: Int, y: Int, target: Int, stack: ArrayDeque<IntArray>) {
        var x = xl
        while (x <= xr) {
            if (pixels[y * w + x] == target) {
                stack.addLast(intArrayOf(x, y))
                while (x <= xr && pixels[y * w + x] == target) x++
            } else x++
        }
    }

    /** 페인트통은 손을 뗄 때(endStroke) 딱 한 번만 채운다 — 눌렀다고 바로 칠하지 않는 이유는, 두
     *  손가락 핀치줌으로 이어질 수도 있는 손가락 하나짜리 터치를 다른 브러시들과 똑같이 "잠정적"으로
     *  다뤄야 두 번째 손가락이 닿을 때 discardStroke()로 조용히 취소되고 핀치줌이 정상 작동하기
     *  때문(예전엔 ACTION_DOWN에서 바로 채워서 핀치줌 등 다른 제스처를 아예 못 쓰게 막고 있었음,
     *  2026-08-20). undo 스냅샷도 여기서 남기지 않는다 — floodFillAt이 실제로 채울 때 자체적으로
     *  pushUndo()한다. */
    private fun beginStroke(x: Float, y: Float) {
        if (fillMode) { strokeStarted = true; return }
        pushUndo(); strokePrep(); strokeStart(x, y); strokeStarted = true; invalidate()
    }
    private fun endStroke() {
        strokeStarted = false; base = null
        if (fillMode) { floodFillAt(downX, downY); return }   // floodFillAt이 자체적으로 pushUndo/redo.clear/invalidate/onStrokeEnd 처리
        redo.clear(); onStrokeEnd?.invoke(); invalidate()
    }
    private fun strokePrep() { strokeLayer?.drawColor(0, PorterDuff.Mode.CLEAR); base = contentBmp?.copy(Bitmap.Config.ARGB_8888, false) }
    private fun composite() {
        val c = content ?: return; val b = base ?: return; val sb = strokeBmp ?: return
        c.drawColor(0, PorterDuff.Mode.CLEAR); c.drawBitmap(b, 0f, 0f, null)
        compositeP.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt(); c.drawBitmap(sb, 0f, 0f, compositeP)
    }

    private fun r0() = strokeSize / 2f   // base radius in canvas px — already canvas-px, no fitScale

    /** 지우개 페인트에 현재 불투명도·블러를 반영 — DST_OUT이라 alpha가 낮을수록 덜 지워지고,
     *  eraserBlur가 0보다 크면 경계가 부드러워짐(선명하게 딱 잘리지 않음). */
    private fun applyEraseStyle(paint: Paint) {
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        paint.maskFilter = if (eraserBlur > 0f) BlurMaskFilter(eraserBlur, BlurMaskFilter.Blur.NORMAL) else null
    }

    // 지우개도 브러시들처럼 strokeSize에 배율을 곱한다 — 지우개는 BrushType이 아니라 별도 플래그라
    // scaleFor()의 when절에 못 넣고 여기 따로 둔다.
    private fun eraserDiameter() = strokeSize * EraserScale

    /** ACTION_MOVE 한 번에 실린 실제 터치 샘플 하나(historical 포함)를 처리 — 화면 좌표를 캔버스
     *  좌표로 바꾸고, 속도 EMA·(SMOOTH_TEST면) 위치 EMA를 갱신한 뒤 strokeMove로 찍는다. */
    private fun processMovePoint(screenX: Float, screenY: Float, eventTimeMs: Long) {
        val p = mapPoint(screenX, screenY); val rawX = p[0]; val rawY = p[1]
        val dd = hypot(rawX - lx, rawY - ly); val vRaw = dd / max(1L, eventTimeMs - lt)
        // 지수이동평균(EMA) — 0.35는 반응성:부드러움 비율. 올리면 순간속도에 더 민감하게
        // (더 소세지스럽게), 내리면 더 뭉근하게(더 느리게 두께가 따라옴) 반응한다.
        smoothedSpeed += (vRaw - smoothedSpeed) * 0.05f
        // SMOOTH_TEST만 위치 자체도 스무딩 — 원본 좌표 대신 뒤에서 완만히 따라오는
        // smoothX/Y로 찍는다(다른 브러시는 원본 좌표 그대로, 지금까지와 동일).
        val x: Float; val y: Float
        if (brush == BrushType.SMOOTH_TEST && smoothTimeConstantMs > 0f) {
            // dt 기반 지수감쇠 — 한 프레임에 historical point가 몇 개로 쪼개져 들어오든(샘플 개수와
            // 무관하게) 실제 걸린 시간(dtMs)당 스무딩 정도가 항상 같게 유지된다.
            val dtMs = max(1L, eventTimeMs - lt).toFloat()
            val alpha = (1f - exp(-dtMs / smoothTimeConstantMs)).coerceIn(0.02f, 1f)
            smoothX += (rawX - smoothX) * alpha
            smoothY += (rawY - smoothY) * alpha
            x = smoothX; y = smoothY
        } else { x = rawX; y = rawY }
        strokeMove(lx, ly, x, y, smoothedSpeed); lx = x; ly = y; lt = eventTimeMs
    }

    private fun strokeStart(x: Float, y: Float) {
        when {
            erasing -> { applyEraseStyle(eraseFill); content?.drawCircle(x, y, max(1f, eraserDiameter() / 2f), eraseFill) }
            // SMOOTH_TEST는 PEN과 렌더링이 완전히 동일 — 입력 좌표 스무딩만 다르다(위 onTouchEvent).
            brush == BrushType.PEN || brush == BrushType.SMOOTH_TEST -> { penDot(x, y); composite() }
            brush == BrushType.WATER -> { stampWater(x, y, r0() * scaleFor()); composite() }
            else -> stampDispatch(x, y, r0() * scaleFor())
        }
    }
    private fun strokeMove(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        if (fillMode) return   // 페인트통은 드래그로 아무것도 안 그림 — 손을 뗄 때 endStroke에서 한 번만 채운다.
        when {
            erasing -> {
                eraseStroke.strokeWidth = max(1f, eraserDiameter())
                applyEraseStyle(eraseStroke)
                content?.drawLine(x0, y0, x1, y1, eraseStroke)
            }
            brush == BrushType.PEN || brush == BrushType.SMOOTH_TEST -> { penSeg(x0, y0, x1, y1, speed); composite() }
            brush == BrushType.WATER -> { seg(x0, y0, x1, y1, speed); composite() }
            else -> seg(x0, y0, x1, y1, speed)
        }
    }

    private fun penDot(x: Float, y: Float) { fill.color = color or (0xFF shl 24); strokeLayer?.drawCircle(x, y, max(1f, r0()), fill) }
    private fun penSeg(x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        // PEN 속도별 굵기 조절 — 0.65f: 최대 감소율(65%까지), 0.2f: speed(캔버스 px/ms) 민감도.
        val r = max(1f, r0() * (1 - minOf(0.65f, speed * 0.2f)))
        pen.color = color or (0xFF shl 24); pen.strokeWidth = r * 2; strokeLayer?.drawLine(x0, y0, x1, y1, pen)
    }

    private fun scaleFor(): Float = when (brush) { BrushType.PEN -> 1f; BrushType.PENCIL -> 1f; BrushType.CRAYON -> 2f; BrushType.WATER -> 6f; BrushType.SMOOTH_TEST -> 1f }
    private val EraserScale = 2f

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
