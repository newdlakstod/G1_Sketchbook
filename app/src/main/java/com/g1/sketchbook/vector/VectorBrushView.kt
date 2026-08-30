package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 벡터 스케치북의 그리기 View — 펜 하나만, 속도에 따라 굵기가 변한다. [infinite]=true면 경계 없는
 *  무한 캔버스, false면 [canvasW]×[canvasH] 논리 좌표의 고정 캔버스. 한 손가락은 그리기(또는
 *  [tool]에 따라 지우기/라쏘 선택), 두 손가락은 핀치 확대·축소와 패닝 — 기존 래스터 `BrushView`의
 *  Matrix 기반 팬/줌 패턴(disp/inv/userM/userScale/pinching)을 그대로 이식했다. */
class VectorBrushView(context: Context) : View(context) {
    /** [LASSO_RESIZE]는 이번 스펙(캔버스 구조 개편)에서는 자리만 마련해 두는 도구 모드다 — 실제
     *  동작(선택한 획들의 굵기를 균일하게 만들고 슬라이더로 조절)은 다음 스펙(편집 기능)에서
     *  구현한다. 지금은 [onTouchEvent]의 `when (tool)`에서 [ERASE]와 똑같이 아무 동작도 없다. */
    enum class Tool { DRAW, ERASE, LASSO_EXPORT, LASSO_RESIZE }

    var color: Long = 0xFF1E2D4CL
    var strokeWidthDp: Float = 8f
    var tool: Tool = Tool.DRAW
    var onStrokeEnd: (() -> Unit)? = null
    /** 라쏘를 다 그리고 손을 뗐을 때 호출 — [tool]이 [Tool.LASSO_EXPORT]일 때만 발생. 라쏘와 겹친
     *  획 목록과 라쏘 폴리곤 자체(내보내기 영역 계산용)를 같이 준다. 점 3개 미만인 라쏘는 무시. */
    var onLassoComplete: ((selected: List<VectorStroke>, lasso: List<Point>) -> Unit)? = null

    /** 무한 캔버스 여부 — 생성 직후 한 번만 설정하고 이후엔 안 바뀐다(스펙: 생성 시점에 고정). */
    var infinite: Boolean = false
    /** 커스텀(고정) 캔버스의 논리 크기 — [infinite]=false일 때만 의미 있음. */
    var canvasW: Float = 1024f
    var canvasH: Float = 1024f

    private val committed = mutableListOf<VectorStroke>()
    private var current: MutableList<VectorPoint>? = null
    private var lx = 0f; private var ly = 0f; private var lt = 0L
    private var smoothedSpeed = 0f
    private var lassoPts: MutableList<Point>? = null

    /** undo는 committed의 마지막 원소를 그냥 지우는 것만으론 안 된다 — eraseAt()도 committed에서
     *  직접 지우기 때문에, 지우개로 획 A를 지운 뒤 undo하면 "지금 committed의 마지막 원소"인 전혀
     *  다른 획 B가 대신 지워져 버린다(A는 영영 사라짐). 그리기/지우기 각각을 별도 이력으로 남겨서
     *  undo가 항상 "가장 최근에 일어난 단일 동작"만 정확히 되돌리게 한다. */
    private sealed class UndoOp {
        data class Drew(val stroke: VectorStroke) : UndoOp()
        data class Erased(val stroke: VectorStroke) : UndoOp()
    }
    private val history = mutableListOf<UndoOp>()

    val canUndo: Boolean get() = history.isNotEmpty()

    fun currentPage(): VectorPage = VectorPage(committed.toList())

    fun loadPage(page: VectorPage) {
        committed.clear(); committed.addAll(page.strokes)
        history.clear()
        current = null
        invalidate()
    }

    fun undo() {
        val op = history.removeLastOrNull() ?: return
        when (op) {
            is UndoOp.Drew -> committed.remove(op.stroke)
            is UndoOp.Erased -> committed.add(op.stroke)
        }
        invalidate()
        onStrokeEnd?.invoke()
    }

    // ---- 팬/줌 (BrushView.kt의 disp/inv/userM 패턴 이식) ----
    private val disp = Matrix()
    private val inv = Matrix()
    private val userM = Matrix()
    private var userScale = 1f
    private var pinching = false
    private var prevDist = 0f; private var prevMidX = 0f; private var prevMidY = 0f
    private var resyncPinchBaseline = false
    private var displayReady = false
    private val tmp = FloatArray(2)

    private fun computeDisplay() {
        if (width <= 0 || height <= 0) return
        disp.reset()
        if (infinite) {
            // 논리 원점(0,0)이 뷰 중앙에서 시작 — 경계가 없으니 "맞춤" 기준이 없다.
            disp.postTranslate(width / 2f, height / 2f)
        } else {
            val fitScale = min(width / canvasW, height / canvasH)
            disp.postScale(fitScale, fitScale)
            disp.postTranslate((width - canvasW * fitScale) / 2f, (height - canvasH * fitScale) / 2f)
        }
        disp.postConcat(userM)
        disp.invert(inv)
        displayReady = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        computeDisplay()
    }

    private fun mapPoint(x: Float, y: Float): FloatArray { tmp[0] = x; tmp[1] = y; inv.mapPoints(tmp); return tmp }

    private fun spacing(e: MotionEvent) = hypot((e.getX(0) - e.getX(1)).toDouble(), (e.getY(0) - e.getY(1)).toDouble()).toFloat()
    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!displayReady) computeDisplay()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                val p = mapPoint(event.x, event.y)
                when (tool) {
                    Tool.ERASE -> eraseAt(p[0], p[1])
                    Tool.LASSO_EXPORT -> { lassoPts = mutableListOf(Point(p[0], p[1])); invalidate() }
                    Tool.DRAW -> {
                        lx = p[0]; ly = p[1]; lt = SystemClock.uptimeMillis(); smoothedSpeed = 0f
                        current = mutableListOf(VectorPoint(p[0], p[1], widthFor(0f)))
                        invalidate()
                    }
                    Tool.LASSO_RESIZE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    current = null   // 두 손가락이 닿으면 그리다 만 획은 버린다(핀치 시작)
                    lassoPts = null
                    pinching = true
                    prevDist = spacing(event); prevMidX = midX(event); prevMidY = midY(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    val d = spacing(event); val mx = midX(event); val my = midY(event)
                    if (resyncPinchBaseline) { resyncPinchBaseline = false; prevDist = d; prevMidX = mx; prevMidY = my }
                    if (prevDist > 0f) {
                        var ds = d / prevDist
                        val ns = (userScale * ds).coerceIn(Dimens.Canvas.minZoom, Dimens.Canvas.maxZoom)
                        ds = ns / userScale; userScale = ns
                        userM.postScale(ds, ds, mx, my)
                    }
                    userM.postTranslate(mx - prevMidX, my - prevMidY)
                    computeDisplay(); invalidate()
                    prevDist = d; prevMidX = mx; prevMidY = my
                } else when (tool) {
                    Tool.LASSO_EXPORT -> {
                        val p = mapPoint(event.x, event.y)
                        lassoPts?.add(Point(p[0], p[1])); invalidate()
                    }
                    Tool.DRAW -> {
                        val cur = current ?: return true
                        val p = mapPoint(event.x, event.y)
                        val now = SystemClock.uptimeMillis()
                        val dd = hypot((p[0] - lx).toDouble(), (p[1] - ly).toDouble()).toFloat()
                        val vRaw = dd / max(1L, now - lt)
                        smoothedSpeed += (vRaw - smoothedSpeed) * 0.35f
                        cur.add(VectorPoint(p[0], p[1], widthFor(smoothedSpeed)))
                        lx = p[0]; ly = p[1]; lt = now
                        invalidate()
                    }
                    Tool.ERASE -> {}
                    Tool.LASSO_RESIZE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) { pinching = false; prevDist = 0f } else resyncPinchBaseline = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinching = false; prevDist = 0f
                when (tool) {
                    Tool.DRAW -> {
                        val cur = current; current = null
                        if (cur != null && cur.size >= 2) {
                            val stroke = VectorStroke(color, cur)
                            committed.add(stroke)
                            history.add(UndoOp.Drew(stroke))
                            onStrokeEnd?.invoke()
                        }
                    }
                    Tool.LASSO_EXPORT -> {
                        val lasso = lassoPts; lassoPts = null
                        if (lasso != null && lasso.size >= 3) {
                            val selected = committed.filter { strokeTouchesLasso(it, lasso) }
                            onLassoComplete?.invoke(selected, lasso)
                        }
                    }
                    Tool.ERASE -> {}
                    Tool.LASSO_RESIZE -> {}
                }
                invalidate()
            }
        }
        return true
    }

    /** 기존 래스터 펜(`BrushView.penSeg`)과 같은 느낌의 속도-굵기 곡선 — 빠를수록 가늘게, 최대
     *  65%까지 얇아진다. */
    private fun widthFor(speed: Float): Float {
        val w = strokeWidthDp * (1f - min(0.65f, speed * 0.2f))
        return max(1f, w)
    }

    private fun eraseAt(x: Float, y: Float) {
        for (i in committed.indices.reversed()) {
            val outline = strokeOutline(committed[i].points)
            if (outline.isNotEmpty() && pointInPolygon(x, y, outline)) {
                val erased = committed[i]
                committed.removeAt(i)
                history.add(UndoOp.Erased(erased))
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF3D6BFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(disp)
        drawVectorPage(canvas, VectorPage(committed))
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts)))) }
        canvas.restore()
        lassoPts?.let { pts -> if (pts.size >= 2) drawLasso(canvas, pts) }
    }

    private fun drawLasso(canvas: android.graphics.Canvas, pts: List<Point>) {
        val path = Path()
        pts.forEachIndexed { i, p ->
            tmp[0] = p.x; tmp[1] = p.y; disp.mapPoints(tmp)
            if (i == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1])
        }
        path.close()
        canvas.drawPath(path, lassoPaint)
    }
}
