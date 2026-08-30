package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 벡터 스케치북의 그리기 View — 펜 하나만, 속도에 따라 굵기가 변한다. 캔버스는 항상
 *  [CANVAS_SIZE]×[CANVAS_SIZE] 정사각 논리 좌표(스펙: 캔버스 비율 정사각 고정)이고, 화면에 보이는
 *  실제 View 크기에 맞춰 [scale]만큼 축소/확대해서 그린다 — 기존 `BrushView`와 달리 확대/축소·화면
 *  회전 대응이 없는(스펙에 없음) 훨씬 단순한 좌표계다. */
class VectorBrushView(context: Context) : View(context) {
    companion object { const val CANVAS_SIZE = 1024f }

    var color: Long = 0xFF1E2D4CL
    var strokeWidthDp: Float = 8f
    var erasing: Boolean = false
    var onStrokeEnd: (() -> Unit)? = null

    private val committed = mutableListOf<VectorStroke>()
    private var current: MutableList<VectorPoint>? = null
    private var lx = 0f; private var ly = 0f; private var lt = 0L
    private var smoothedSpeed = 0f

    val canUndo: Boolean get() = committed.isNotEmpty()

    fun currentPage(): VectorPage = VectorPage(committed.toList())

    fun loadPage(page: VectorPage) {
        committed.clear(); committed.addAll(page.strokes)
        current = null
        invalidate()
    }

    fun undo() {
        if (committed.isEmpty()) return
        committed.removeAt(committed.size - 1)
        invalidate()
        onStrokeEnd?.invoke()
    }

    /** View px -> 1024×1024 논리 좌표. */
    private fun scale(): Float = CANVAS_SIZE / max(1, width).toFloat()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = scale()
        val x = event.x * s
        val y = event.y * s
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (erasing) {
                    eraseAt(x, y)
                    return true
                }
                lx = x; ly = y; lt = SystemClock.uptimeMillis(); smoothedSpeed = 0f
                current = mutableListOf(VectorPoint(x, y, widthFor(0f)))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (erasing) return true
                val cur = current ?: return true
                val now = SystemClock.uptimeMillis()
                val dd = hypot((x - lx).toDouble(), (y - ly).toDouble()).toFloat()
                val vRaw = dd / max(1L, now - lt)
                smoothedSpeed += (vRaw - smoothedSpeed) * 0.35f
                cur.add(VectorPoint(x, y, widthFor(smoothedSpeed)))
                lx = x; ly = y; lt = now
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (erasing) return true
                val cur = current
                current = null
                if (cur != null && cur.size >= 2) {
                    committed.add(VectorStroke(color, cur))
                    onStrokeEnd?.invoke()
                }
                invalidate()
            }
        }
        return true
    }

    /** 기존 래스터 펜(`BrushView.penSeg`)과 같은 느낌의 속도-굵기 곡선 — 빠를수록 가늘게, 최대
     *  65%까지 얇아진다. */
    private fun widthFor(speed: Float): Float {
        val dp = strokeWidthDp * (1f - min(0.65f, speed * 0.2f))
        return max(1f, dp) * resources.displayMetrics.density
    }

    private fun eraseAt(x: Float, y: Float) {
        for (i in committed.indices.reversed()) {
            val outline = strokeOutline(committed[i].points)
            if (outline.isNotEmpty() && pointInPolygon(x, y, outline)) {
                committed.removeAt(i)
                invalidate()
                onStrokeEnd?.invoke()
                return
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = scale()
        canvas.save()
        canvas.scale(1f / s, 1f / s)
        drawVectorPage(canvas, VectorPage(committed))
        current?.let { pts -> if (pts.size >= 2) drawVectorPage(canvas, VectorPage(listOf(VectorStroke(color, pts)))) }
        canvas.restore()
    }
}
