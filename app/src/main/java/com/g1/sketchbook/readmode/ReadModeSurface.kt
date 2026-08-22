package com.g1.sketchbook.readmode

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.input.DragInterpreter

/** GLSurfaceView hosting [ReadModeRenderer]. Touch handling is PageCurlDemo's `PageCurlSurface`
 *  almost unchanged; the one addition is [onTurnCompleted], which fires once a completed drag's
 *  settle animation actually finishes on screen, so the Compose layer can advance the spread index
 *  and swap in the next [SpreadTextures] right as the paper visually lands. */
class ReadModeSurface(context: Context) : GLSurfaceView(context) {
    private val renderer = ReadModeRenderer()
    private val dragInterpreter = DragInterpreter()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastPosition = Vec2(1f, 0.5f)
    private var lastEventTime = 0L
    private var velocityX = 0f

    /** Invoked on the main thread (see [pollForCompletion]) — safe to touch Compose state from it. */
    var onTurnCompleted: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Queues the new spread's bitmaps onto the GL thread — see [ReadModeRenderer.setSpread]. */
    fun setSpread(textures: SpreadTextures, landscape: Boolean) {
        queueEvent { renderer.setSpread(textures, landscape) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startDrag(event)
            MotionEvent.ACTION_MOVE -> moveDrag(event)
            MotionEvent.ACTION_UP -> finishDrag(event, canceled = false)
            MotionEvent.ACTION_CANCEL -> finishDrag(event, canceled = true)
            else -> activePointerId != MotionEvent.INVALID_POINTER_ID
        }
    }

    private fun startDrag(event: MotionEvent): Boolean {
        val position = dragInterpreter.normalized(event.x, event.y, width, height)
        if (!dragInterpreter.canStart(position)) return false
        activePointerId = event.getPointerId(0)
        lastPosition = position
        lastEventTime = event.eventTime
        velocityX = 0f
        queueEvent { renderer.onDragStart(position) }
        return true
    }

    private fun moveDrag(event: MotionEvent): Boolean {
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val position = dragInterpreter.normalized(event.getX(pointerIndex), event.getY(pointerIndex), width, height)
        updateVelocity(position, event.eventTime)
        queueEvent { renderer.onDrag(position) }
        return true
    }

    private fun finishDrag(event: MotionEvent, canceled: Boolean): Boolean {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (!canceled && pointerIndex >= 0) {
            val position = dragInterpreter.normalized(event.getX(pointerIndex), event.getY(pointerIndex), width, height)
            updateVelocity(position, event.eventTime)
            queueEvent { renderer.onDrag(position) }
        }
        val complete = !canceled && dragInterpreter.shouldComplete(progress = 1f - lastPosition.x, velocityX = velocityX)
        queueEvent {
            if (canceled) {
                renderer.cancelDrag()
            } else {
                renderer.onDragEnd(complete)
                if (complete) pollForCompletion()
            }
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    /** The settle-to-next animation takes ~280ms (`ReadModeRenderer.COMPLETE_DURATION_NANOS`).
     *  Re-queues itself once per GL frame until `renderer.didCompleteTurn` flips, then hops to the
     *  main thread via `post` to fire [onTurnCompleted]. Simpler than wiring a formal
     *  animation-listener chain for a single one-shot event. */
    private fun pollForCompletion() {
        queueEvent {
            if (renderer.didCompleteTurn) {
                post { onTurnCompleted?.invoke() }
            } else {
                pollForCompletion()
            }
        }
    }

    private fun updateVelocity(position: Vec2, eventTime: Long) {
        val elapsedSeconds = (eventTime - lastEventTime).coerceAtLeast(1L) / 1_000f
        velocityX = (position.x - lastPosition.x) / elapsedSeconds
        lastPosition = position
        lastEventTime = eventTime
    }
}
