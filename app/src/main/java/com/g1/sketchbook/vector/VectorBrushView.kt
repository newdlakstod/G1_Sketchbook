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
 *  [tool]에 따라 지우기/라쏘 선택 또는 이동), 두 손가락은 핀치 확대·축소와 패닝(단, 라쏘로 선택한
 *  영역 안을 누른 채면 캔버스가 아니라 그 선택을 크기조절·이동한다) — 기존 래스터 `BrushView`의
 *  Matrix 기반 팬/줌·올가미 선택 이동 패턴(disp/inv/userM/selectionTransform)을 그대로 이식했다. */
class VectorBrushView(context: Context) : View(context) {
    /** [LASSO_RESIZE]는 이번 스펙(캔버스 구조 개편)에서는 자리만 마련해 두는 도구 모드다 — 실제
     *  동작(선택한 획들의 굵기를 균일하게 만들고 슬라이더로 조절)은 다음 스펙(편집 기능)에서
     *  구현한다. 지금은 [onTouchEvent]의 `when (tool)`에서 [ERASE]와 똑같이 아무 동작도 없다. */
    enum class Tool { DRAW, ERASE, LASSO_EXPORT, LASSO_RESIZE }

    var color: Long = 0xFF1E2D4CL
    var strokeWidthDp: Float = 8f
    /** 새로 그리는 획의 끝단 마감 — 브러시 설정 버튼에서 고른다. 기존 획(이미 committed에 있는)의
     *  마감은 그 획 자신의 [VectorStroke.cap]을 그대로 쓰므로 이 값은 "다음에 그릴 획"에만 영향. */
    var cap: VectorCap = VectorCap.ROUND
    /** 새로 그리는 획의 채움/테두리 — 역시 "다음에 그릴 획"에만 영향(기존 획은 자기 자신의 값을
     *  그대로 씀). [strokeColor]가 null이면 테두리 없음(기존 채움만인 동작). */
    var fillEnabled: Boolean = true
    var strokeColor: Long? = null
    var strokeWidthPx: Float = 2f
    /** 다음에 그릴 획에 적용할 스탬프 브러시 — null이면 지금 펜. [VectorCanvasScreen]의 브러시
     *  스와치 패널에서 고른다. */
    var brushProfileId: String? = null
    /** id로 [StampBrushProfile]을 찾는 조회용 맵 — 그리기·지우개 히트테스트·undo 미리보기 전부
     *  이 맵으로 렌더링한다. [VectorCanvasScreen]이 [com.g1.sketchbook.vector.StampBrushRepository]에서
     *  읽어 채워 넣는다(이 파일 자체는 저장소를 모른다 — 순수 뷰). */
    var stampBrushes: Map<String, StampBrushProfile> = emptyMap()
    /** 라쏘로 선택한 획을 크기조절할 때 선 굵기도 같이 줄일지(true) 굵기는 그대로 두고 좌표만
     *  키울지(false) — 브러시 설정 버튼에서 고른다. */
    var scaleStrokeWidth: Boolean = true
    /** [Tool.LASSO_EXPORT]로 바뀌지 않는 모든 전환에서 진행 중이던 선택을 자동으로 해제한다 —
     *  래스터 `BrushView.lassoMode` setter와 같은 이유(도구를 바꾸면 선택도 같이 풀리는 게
     *  직관적). */
    var tool: Tool = Tool.DRAW
        set(value) { if (value != Tool.LASSO_EXPORT) clearSelection(); field = value }
    var onStrokeEnd: (() -> Unit)? = null
    /** 라쏘 선택이 생기면(true, 저장 버튼을 띄울 화면 px 좌표와 함께) / 없어지면(false) 알려준다
     *  — 래스터 `BrushView.onLassoSelectionChanged`와 같은 시그니처라 같은 플로팅 버튼 컴포저블을
     *  그대로 재사용한다. 드래그로 옮기는 중엔 위치가 안 맞으니 잠시 false로 숨겼다가, 손을 떼면
     *  새 위치로 다시 true. */
    var onLassoSelectionChanged: ((active: Boolean, xPx: Float, yPx: Float) -> Unit)? = null

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

    // ---- 라쏘 선택(선택 후 이동/크기조절/저장) ----
    /** 지금 선택된 획들의 [committed] 안 인덱스 — undo가 committed를 바꾸면 인덱스가 꼬일 수 있어
     *  [undo]에서 항상 선택을 같이 해제한다. */
    private var selectionIndices: List<Int>? = null
    /** 선택을 확정지은 라쏘 폴리곤(논리 좌표) — 다시 탭했을 때 "선택 안쪽을 눌렀는지" 판정과, 점선
     *  테두리를 그리는 데 쓴다. */
    private var selectionLasso: List<Point>? = null
    private var movingSelection = false
    private var selTransforming = false
    // 화면좌표계 델타(이동+크기)를 매 프레임 누적한다 — 손 뗄 때 이 하나의 행렬을 캔버스좌표계로
    // 바꿔(disp/inv 사잇값) 실제 획 좌표에 한 번에 합성한다(commitSelectionMove 참고). 래스터
    // BrushView.selectionTransform과 같은 패턴(회전은 이 스펙 범위 밖이라 빼고 이동+크기만).
    private val selectionTransform = Matrix()
    private var selectionScale = 1f
    private var prevSelX = 0f; private var prevSelY = 0f
    private var prevSelDist = 0f; private var prevSelMidX = 0f; private var prevSelMidY = 0f

    /** undo는 committed의 마지막 원소를 그냥 지우는 것만으론 안 된다 — eraseAt()도 committed에서
     *  직접 지우기 때문에, 지우개로 획 A를 지운 뒤 undo하면 "지금 committed의 마지막 원소"인 전혀
     *  다른 획 B가 대신 지워져 버린다(A는 영영 사라짐). 그리기/지우기/선택 이동 각각을 별도 이력으로
     *  남겨서 undo가 항상 "가장 최근에 일어난 단일 동작"만 정확히 되돌리게 한다. */
    private sealed class UndoOp {
        data class Drew(val stroke: VectorStroke) : UndoOp()
        data class Erased(val stroke: VectorStroke) : UndoOp()
        data class Transformed(val before: List<VectorStroke>, val indices: List<Int>) : UndoOp()
    }
    private val history = mutableListOf<UndoOp>()

    val canUndo: Boolean get() = history.isNotEmpty()

    fun currentPage(): VectorPage = VectorPage(committed.toList())

    /** 지금 선택된 획만 담은 페이지 — 저장(내보내기) 버튼이 부른다. 선택이 없으면 null. */
    fun exportSelection(): VectorPage? {
        val idx = selectionIndices ?: return null
        if (idx.isEmpty()) return null
        return VectorPage(idx.map { committed[it] })
    }

    fun loadPage(page: VectorPage) {
        committed.clear(); committed.addAll(page.strokes)
        history.clear()
        current = null
        clearSelection()
        invalidate()
    }

    fun undo() {
        val op = history.removeLastOrNull() ?: return
        when (op) {
            is UndoOp.Drew -> committed.remove(op.stroke)
            is UndoOp.Erased -> committed.add(op.stroke)
            is UndoOp.Transformed -> op.indices.forEachIndexed { i, idx -> committed[idx] = op.before[i] }
        }
        clearSelection() // committed 인덱스가 바뀔 수 있어 안전하게 선택 해제
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
                    Tool.LASSO_EXPORT -> {
                        val lasso = selectionLasso
                        if (lasso != null && pointInPolygon(p[0], p[1], lasso)) {
                            // 선택 영역 안쪽을 다시 눌렀다 — 이동/크기조절 시작.
                            movingSelection = true
                            selTransforming = false
                            selectionTransform.reset()
                            selectionScale = 1f
                            prevSelX = event.x; prevSelY = event.y
                            onLassoSelectionChanged?.invoke(false, 0f, 0f)
                        } else {
                            clearSelection()
                            lassoPts = mutableListOf(Point(p[0], p[1]))
                        }
                        invalidate()
                    }
                    Tool.DRAW -> {
                        lx = p[0]; ly = p[1]; lt = SystemClock.uptimeMillis(); smoothedSpeed = 0f
                        current = mutableListOf(VectorPoint(p[0], p[1], widthFor(0f)))
                        invalidate()
                    }
                    Tool.LASSO_RESIZE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (movingSelection && event.pointerCount == 2) {
                    selTransforming = true
                    prevSelDist = spacing(event); prevSelMidX = midX(event); prevSelMidY = midY(event)
                } else if (event.pointerCount == 2) {
                    current = null   // 두 손가락이 닿으면 그리다 만 획은 버린다(핀치 시작)
                    lassoPts = null
                    pinching = true
                    prevDist = spacing(event); prevMidX = midX(event); prevMidY = midY(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (movingSelection && selTransforming && event.pointerCount >= 2) {
                    val d = spacing(event); val mx = midX(event); val my = midY(event)
                    if (prevSelDist > 0f) {
                        val ds = d / prevSelDist
                        selectionTransform.postScale(ds, ds, mx, my)
                        selectionScale *= ds
                    }
                    selectionTransform.postTranslate(mx - prevSelMidX, my - prevSelMidY)
                    prevSelDist = d; prevSelMidX = mx; prevSelMidY = my
                    invalidate()
                } else if (movingSelection) {
                    selectionTransform.postTranslate(event.x - prevSelX, event.y - prevSelY)
                    prevSelX = event.x; prevSelY = event.y
                    invalidate()
                } else if (pinching && event.pointerCount >= 2) {
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
                    Tool.ERASE -> {
                        val p = mapPoint(event.x, event.y)
                        eraseAt(p[0], p[1])
                    }
                    Tool.LASSO_RESIZE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (movingSelection && selTransforming && event.pointerCount == 2) {
                    // 트랜스폼용 손가락 둘 중 하나가 떨어짐 — 남은 손가락 하나로 이동만 계속하되,
                    // 지금 위치를 새 기준점으로 다시 잡아야 다음 MOVE에서 안 튄다.
                    selTransforming = false
                    val survivorIndex = if (event.actionIndex == 0) 1 else 0
                    prevSelX = event.getX(survivorIndex); prevSelY = event.getY(survivorIndex)
                } else if (event.pointerCount <= 2) {
                    pinching = false; prevDist = 0f
                } else resyncPinchBaseline = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinching = false; prevDist = 0f
                if (movingSelection) {
                    commitSelectionMove()
                } else {
                    when (tool) {
                        Tool.DRAW -> {
                            val cur = current; current = null
                            if (cur != null && cur.size >= 2) {
                                val stroke = VectorStroke(color, cur, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId)
                                committed.add(stroke)
                                history.add(UndoOp.Drew(stroke))
                                onStrokeEnd?.invoke()
                            }
                        }
                        Tool.LASSO_EXPORT -> {
                            val lasso = lassoPts; lassoPts = null
                            if (lasso != null && lasso.size >= 3) {
                                val idx = committed.indices.filter { strokeTouchesLasso(committed[it], lasso) }
                                if (idx.isNotEmpty()) {
                                    selectionIndices = idx
                                    selectionLasso = lasso
                                    selectionScreenAnchor()?.let { a -> onLassoSelectionChanged?.invoke(true, a[0], a[1]) }
                                }
                            }
                        }
                        Tool.ERASE -> {}
                        Tool.LASSO_RESIZE -> {}
                    }
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
            val stroke = committed[i]
            val profile = stroke.brushProfileId?.let { stampBrushes[it] }
            val hit = if (profile != null) {
                stampPolygons(profile, stroke.points).any { it.isNotEmpty() && pointInPolygon(x, y, it) }
            } else {
                val outline = strokeOutline(stroke.points, stroke.cap)
                outline.isNotEmpty() && pointInPolygon(x, y, outline)
            }
            if (hit) {
                committed.removeAt(i)
                history.add(UndoOp.Erased(stroke))
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }

    /** 드래그(+선택적 두 손가락 크기조절)로 바뀐 선택을 실제 획 좌표에 합성하고, 선택 영역(라쏘
     *  폴리곤) 자체도 새 위치/크기로 옮겨서 계속 선택된 상태를 유지한다(바로 이어서 다시
     *  옮기거나 저장할 수 있게) — 래스터 `BrushView.commitMove()`와 같은 원리(disp→
     *  selectionTransform→inv 하나의 행렬로 화면좌표 델타를 논리좌표 델타로 바꿔서 적용). */
    private fun commitSelectionMove() {
        val idx = selectionIndices
        if (idx == null || selectionTransform.isIdentity) {
            movingSelection = false; selTransforming = false; selectionTransform.reset(); selectionScale = 1f
            invalidate()
            return
        }
        val before = idx.map { committed[it] }
        val m = Matrix(disp); m.postConcat(selectionTransform); m.postConcat(inv)
        val mapped = FloatArray(2)
        idx.forEach { i ->
            val stroke = committed[i]
            val newPoints = stroke.points.map { pt ->
                mapped[0] = pt.x; mapped[1] = pt.y
                m.mapPoints(mapped)
                val newW = if (scaleStrokeWidth) pt.w * selectionScale else pt.w
                VectorPoint(mapped[0], mapped[1], newW)
            }
            committed[i] = stroke.copy(points = newPoints)
        }
        history.add(UndoOp.Transformed(before, idx))
        selectionLasso = selectionLasso?.map { p ->
            mapped[0] = p.x; mapped[1] = p.y
            m.mapPoints(mapped)
            Point(mapped[0], mapped[1])
        }
        movingSelection = false; selTransforming = false; selectionTransform.reset(); selectionScale = 1f
        invalidate()
        onStrokeEnd?.invoke()
        selectionScreenAnchor()?.let { a -> onLassoSelectionChanged?.invoke(true, a[0], a[1]) }
    }

    private fun clearSelection() {
        if (selectionIndices == null && !movingSelection) return
        selectionIndices = null; selectionLasso = null
        movingSelection = false; selTransforming = false
        selectionTransform.reset(); selectionScale = 1f
        onLassoSelectionChanged?.invoke(false, 0f, 0f)
    }

    /** 선택 라쏘의 화면 좌표 경계상자에서 (가운데 위) 지점 — 저장 버튼을 그 자리에 띄운다. 래스터
     *  `BrushView.selectionScreenAnchor()`와 같은 계산(단, 회전이 없는 [disp]라 네 꼭짓점 대신
     *  좌상단·우상단만 매핑해도 충분). */
    private fun selectionScreenAnchor(): FloatArray? {
        val lasso = selectionLasso ?: return null
        val b = pointsBounds(lasso) ?: return null
        val pts = floatArrayOf(b.minX, b.minY, b.maxX, b.minY)
        disp.mapPoints(pts)
        return floatArrayOf((pts[0] + pts[2]) / 2f, pts[1])
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dimens.Canvas.lassoStrokeWidthDp * resources.displayMetrics.density
        color = 0xFF444444.toInt()
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val selIdx = selectionIndices
        canvas.save()
        canvas.concat(disp)
        if (selIdx != null && movingSelection) {
            val selSet = selIdx.toSet()
            drawVectorPage(canvas, VectorPage(committed.filterIndexed { i, _ -> i !in selSet }), stampBrushes)
        } else {
            drawVectorPage(canvas, VectorPage(committed), stampBrushes)
        }
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts, cap, fillEnabled, strokeColor, strokeWidthPx, brushProfileId))), stampBrushes) }
        canvas.restore()

        if (selIdx != null && movingSelection) {
            val m = Matrix(disp); m.postConcat(selectionTransform)
            canvas.save()
            canvas.setMatrix(m)
            drawVectorPage(canvas, VectorPage(selIdx.map { committed[it] }), stampBrushes)
            canvas.restore()
        }
        selectionLasso?.let { drawLasso(canvas, it, if (movingSelection) selectionTransform else null) }
        lassoPts?.let { pts -> if (pts.size >= 2) drawLasso(canvas, pts, null) }
    }

    private fun drawLasso(canvas: android.graphics.Canvas, pts: List<Point>, screenTransform: Matrix?) {
        val path = Path()
        pts.forEachIndexed { i, p ->
            tmp[0] = p.x; tmp[1] = p.y; disp.mapPoints(tmp)
            if (i == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1])
        }
        path.close()
        if (screenTransform != null) path.transform(screenTransform)
        canvas.drawPath(path, lassoPaint)
    }
}
