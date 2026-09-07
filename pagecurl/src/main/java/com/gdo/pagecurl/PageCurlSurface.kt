package com.gdo.pagecurl

import android.content.Context
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.MotionEvent
import com.gdo.pagecurl.math.Vec2

internal class DocumentGenerationCoordinator {
    var currentGeneration: Long = 0L
        private set
    private var released = false

    fun advance(): Long {
        check(!released) { "PageCurlSurface is released" }
        return ++currentGeneration
    }

    fun release() {
        if (released) return
        currentGeneration++
        released = true
    }

    fun deliverIfCurrent(generation: Long, delivery: () -> Unit): Boolean {
        if (released || generation != currentGeneration) return false
        delivery()
        return true
    }

    fun deliverErrorIfCurrent(
        generation: Long,
        failure: Throwable,
        delivery: (Throwable) -> Unit,
    ): Boolean = deliverIfCurrent(generation) { delivery(failure) }
}

internal class PageCurlSurface(
    context: Context,
    initialSource: PageCurlBitmapSource,
    initialFocusedPageIndex: Int,
    initialLayoutMode: PageLayoutMode,
    private val onFocusedPageCommitted: (Int) -> Unit,
    private val onError: (Throwable) -> Unit,
) : GLSurfaceView(context) {
    private val documentGeneration = DocumentGenerationCoordinator()
    private val renderer = PageCurlRenderer(
        initialSource,
        initialFocusedPageIndex,
        initialLayoutMode,
        initialDocumentGeneration = documentGeneration.currentGeneration,
        onFocusedPageCommitted = { index, generation ->
            post {
                documentGeneration.deliverIfCurrent(generation) {
                    focusedPageIndex = index
                    onFocusedPageCommitted(index)
                    updateGestureExclusion()
                }
            }
        },
        onError = { failure, generation ->
            post {
                documentGeneration.deliverErrorIfCurrent(generation, failure, onError)
            }
        },
    )
    private val dragInterpreter = DragInterpreter()
    private var source = initialSource
    private var focusedPageIndex = initialFocusedPageIndex
    private var layoutMode = initialLayoutMode
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeDirection: PageTurnDirection? = null
    private var activeLayoutMode: PageLayoutMode? = null
    private var lastPosition = Vec2(1f, 0.5f)
    private var lastEventTime = 0L
    private var velocityX = 0f
    private var released = false

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (released || width <= 0 || height <= 0) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startDrag(event)
            MotionEvent.ACTION_MOVE -> moveDrag(event)
            MotionEvent.ACTION_UP -> finishDrag(event, canceled = false)
            MotionEvent.ACTION_CANCEL -> finishDrag(event, canceled = true)
            else -> activePointerId != MotionEvent.INVALID_POINTER_ID
        }
    }

    fun resetCurl() {
        if (released) return
        clearActiveDrag()
        queueEvent {
            renderer.reset()
            post(::updateGestureExclusion)
        }
    }

    fun setDocument(
        newSource: PageCurlBitmapSource,
        pageIndex: Int,
        newLayoutMode: PageLayoutMode,
    ) {
        if (released) return
        if (newSource === source &&
            pageIndex == focusedPageIndex &&
            newLayoutMode == layoutMode
        ) return
        clearActiveDrag()
        source = newSource
        focusedPageIndex = pageIndex
        layoutMode = newLayoutMode
        val newGeneration = documentGeneration.advance()
        queueEvent {
            renderer.setDocument(newSource, pageIndex, newLayoutMode, newGeneration)
            post(::updateGestureExclusion)
        }
    }

    fun release() {
        if (released) return
        released = true
        clearActiveDrag()
        documentGeneration.release()
        queueEvent(renderer::release)
        onPause()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateGestureExclusion()
    }

    private fun startDrag(event: MotionEvent): Boolean {
        val raw = dragInterpreter.normalized(event.x, event.y, width, height)
        val gestureLayoutMode = layoutMode
        val direction = dragInterpreter.directionForStart(
            raw,
            gestureLayoutMode,
            canForward = renderer.canStartDrag(PageTurnDirection.Forward),
            canBackward = renderer.canStartDrag(PageTurnDirection.Backward),
        ) ?: return false
        val working = dragInterpreter.toWorkingPosition(raw, gestureLayoutMode, direction)
        activePointerId = event.getPointerId(0)
        activeDirection = direction
        activeLayoutMode = gestureLayoutMode
        lastPosition = working
        lastEventTime = event.eventTime
        velocityX = 0f
        queueEvent { renderer.onDragStart(working, direction) }
        return true
    }

    private fun moveDrag(event: MotionEvent): Boolean {
        val direction = activeDirection ?: return false
        val gestureLayoutMode = activeLayoutMode ?: return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val raw = dragInterpreter.normalized(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            width,
            height,
        )
        val position = dragInterpreter.toWorkingPosition(raw, gestureLayoutMode, direction)
        updateVelocity(position, event.eventTime)
        queueEvent { renderer.onDrag(position) }
        return true
    }

    private fun finishDrag(event: MotionEvent, canceled: Boolean): Boolean {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val direction = activeDirection ?: return false
        val gestureLayoutMode = activeLayoutMode ?: return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (!canceled && pointerIndex >= 0) {
            val raw = dragInterpreter.normalized(
                event.getX(pointerIndex),
                event.getY(pointerIndex),
                width,
                height,
            )
            val position = dragInterpreter.toWorkingPosition(raw, gestureLayoutMode, direction)
            updateVelocity(position, event.eventTime)
            queueEvent { renderer.onDrag(position) }
        }
        val complete = !canceled && dragInterpreter.shouldComplete(
            progress = 1f - lastPosition.x,
            velocityX = velocityX,
        )
        queueEvent {
            if (canceled) renderer.cancelDrag() else renderer.onDragEnd(complete)
        }
        clearActiveDrag()
        return true
    }

    private fun clearActiveDrag() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeDirection = null
        activeLayoutMode = null
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val edgeWidth = (width * dragInterpreter.edgeWidthFraction(layoutMode)).toInt()
        val exclusions = buildList {
            if (renderer.canTurn(PageTurnDirection.Backward)) add(Rect(0, 0, edgeWidth, height))
            if (renderer.canTurn(PageTurnDirection.Forward)) add(Rect(width - edgeWidth, 0, width, height))
        }
        systemGestureExclusionRects = exclusions
    }

    private fun updateVelocity(position: Vec2, eventTime: Long) {
        val elapsedSeconds = (eventTime - lastEventTime).coerceAtLeast(1L) / 1_000f
        velocityX = (position.x - lastPosition.x) / elapsedSeconds
        lastPosition = position
        lastEventTime = eventTime
    }
}
