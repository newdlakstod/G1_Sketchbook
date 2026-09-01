package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.hypot

/** New editor canvas bridge. It deliberately owns all document drawing and pointer handling; the
 * legacy [VectorBrushView] remains in use only by the pre-Task-13 screen wiring. */
class VectorCanvasHost(context: Context) : View(context) {
    private var editorState: VectorEditorState? = null
    private var profilesProvider: () -> Map<String, VectorBrushProfile> = { emptyMap() }
    private var snapshot: VectorEditorSnapshot? = null
    private val geometryCache = VectorGeometryCache()
    private var viewport = CanvasViewport()
    private var interaction: InteractionState? = null
    private var draft = mutableListOf<Point>()
    private var lasso = mutableListOf<Point>()
    private var lastScreen = Point(0f, 0f)
    private var transformStart: Point? = null
    private var pinchDistance = 0f
    private var pinchMidpoint = Point(0f, 0f)

    fun bind(state: VectorEditorState, profiles: () -> Map<String, VectorBrushProfile>) {
        editorState = state
        profilesProvider = profiles
        snapshot = state.snapshot.value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = editorState?.snapshot?.value ?: snapshot ?: return
        snapshot = current
        canvas.save()
        canvas.translate(viewport.translateX, viewport.translateY)
        canvas.scale(viewport.scale, viewport.scale)
        drawVectorDocument(canvas, current.document, profilesProvider(), geometryCache)
        draft.takeIf { it.size > 1 }?.let { points ->
            val appearance = current.defaultAppearance
            drawVectorDocument(canvas, VectorDocument(objects = listOf(EditablePathObject("draft", PathGeometry(points.map { PathPoint(it.x, it.y, 1f) }), appearance))), profilesProvider(), geometryCache)
        }
        canvas.restore()
        drawSelection(canvas, current)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = editorState ?: return false
        val current = state.snapshot.value
        val screen = Point(event.x, event.y)
        val canvasPoint = screenToCanvas(screen, viewport) ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val overlay = selectionOverlay(current)
                val body = current.selectedIds.isNotEmpty() && topmostObjectAt(current.document.objects.filter { it.id in current.selectedIds }, canvasPoint, profilesProvider(), viewport.scale) != null
                interaction = reduceCanvasInput(CanvasInput.PointerDown(screen), InteractionState(current.tool, overlay = overlay, selectedBodyHit = body)).state
                if (current.tool == VectorTool.SELECT && interaction?.gesture == CanvasGesture.LASSO) state.select(emptySet())
                if (interaction?.gesture == CanvasGesture.DRAW) draft = mutableListOf(canvasPoint)
                if (interaction?.gesture == CanvasGesture.LASSO) lasso = mutableListOf(canvasPoint)
                transformStart = canvasPoint
                lastScreen = screen
            }
            MotionEvent.ACTION_POINTER_DOWN -> interaction?.let {
                interaction = reduceCanvasInput(CanvasInput.PointerAdded(event.pointerCount), it).state
                draft.clear()
                if (interaction?.gesture == CanvasGesture.VIEWPORT && event.pointerCount >= 2) {
                    pinchDistance = pointerDistance(event)
                    pinchMidpoint = pointerMidpoint(event)
                }
            }
            MotionEvent.ACTION_MOVE -> when (interaction?.gesture) {
                CanvasGesture.DRAW -> draft.add(canvasPoint)
                CanvasGesture.LASSO -> lasso.add(canvasPoint)
                CanvasGesture.ERASE -> topmostObjectAt(current.document.objects, canvasPoint, profilesProvider(), viewport.scale)?.let { hit ->
                    interaction = interaction?.let { reduceCanvasInput(CanvasInput.EraseHit(hit.id), it).state }
                }
                CanvasGesture.VIEWPORT -> if (event.pointerCount >= 2) {
                    val distance = pointerDistance(event)
                    val midpoint = pointerMidpoint(event)
                    if (pinchDistance > 0f && distance.isFinite()) {
                        val oldScale = viewport.scale
                        val newScale = (oldScale * distance / pinchDistance).coerceIn(.01f, 5f)
                        val anchor = screenToCanvas(pinchMidpoint, viewport)
                        viewport = if (anchor != null) CanvasViewport(newScale, midpoint.x - anchor.x * newScale, midpoint.y - anchor.y * newScale) else viewport
                    }
                    pinchDistance = distance; pinchMidpoint = midpoint
                } else viewport = viewport.copy(translateX = viewport.translateX + screen.x - lastScreen.x, translateY = viewport.translateY + screen.y - lastScreen.y)
                else -> Unit
            }
            MotionEvent.ACTION_UP -> finishGesture(state, current)
            MotionEvent.ACTION_CANCEL -> { interaction = interaction?.let { reduceCanvasInput(CanvasInput.Cancel, it).state }; draft.clear(); lasso.clear() }
        }
        lastScreen = screen
        invalidate()
        return true
    }

    private fun finishGesture(state: VectorEditorState, current: VectorEditorSnapshot) {
        val active = interaction ?: return
        when (active.gesture) {
            CanvasGesture.DRAW -> if (draft.size >= 2) state.dispatch(AddObject(EditablePathObject(
                id = "path-${System.nanoTime()}", geometry = PathGeometry(draft.map { PathPoint(it.x, it.y, 1f) }), appearance = current.defaultAppearance,
            )))
            CanvasGesture.MOVE_SELECTION -> transformStart?.let { start ->
                val end = screenToCanvas(lastScreen, viewport) ?: start
                if (start != end && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, SelectionTransform(end.x - start.x, end.y - start.y, pivot = start)))
            }
            CanvasGesture.SCALE_SELECTION -> transformStart?.let { start ->
                val end = screenToCanvas(lastScreen, viewport) ?: start
                val bounds = selectionBounds(current.document.objects, current.selectedIds, profilesProvider()) ?: return@let
                val pivot = Point((bounds.minX + bounds.maxX) / 2f, (bounds.minY + bounds.maxY) / 2f)
                val sx = ratio(end.x - pivot.x, start.x - pivot.x); val sy = ratio(end.y - pivot.y, start.y - pivot.y)
                val transform = when (active.selectionHandle) {
                    SelectionHandle.TOP, SelectionHandle.BOTTOM -> SelectionTransform(scaleY = sy, pivot = pivot)
                    SelectionHandle.LEFT, SelectionHandle.RIGHT -> SelectionTransform(scaleX = sx, pivot = pivot)
                    else -> { val uniform = if (abs(sx - 1f) >= abs(sy - 1f)) sx else sy; SelectionTransform(scaleX = uniform, scaleY = uniform, pivot = pivot) }
                }
                if ((transform.scaleX != 1f || transform.scaleY != 1f) && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, transform))
            }
            CanvasGesture.ROTATE_SELECTION -> transformStart?.let { start ->
                val end = screenToCanvas(lastScreen, viewport) ?: start
                val bounds = selectionBounds(current.document.objects, current.selectedIds, profilesProvider()) ?: return@let
                val pivot = Point((bounds.minX + bounds.maxX) / 2f, (bounds.minY + bounds.maxY) / 2f)
                val degrees = Math.toDegrees((atan2((end.y - pivot.y).toDouble(), (end.x - pivot.x).toDouble()) - atan2((start.y - pivot.y).toDouble(), (start.x - pivot.x).toDouble()))).toFloat()
                if (degrees.isFinite() && degrees != 0f && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, SelectionTransform(rotationDegrees = degrees, pivot = pivot)))
            }
            CanvasGesture.LASSO -> if (lasso.size >= 3) state.select(objectsTouchingLasso(current.document.objects, lasso, profilesProvider(), viewport.scale))
            else -> Unit
        }
        val result = reduceCanvasInput(CanvasInput.PointerUp, active)
        result.command?.let(state::dispatch)
        interaction = result.state
        draft.clear()
        lasso.clear()
    }

    private fun ratio(numerator: Float, denominator: Float): Float =
        if (numerator.isFinite() && denominator.isFinite() && abs(denominator) > .0001f) (numerator / denominator).coerceIn(.01f, 100f) else 1f

    private fun pointerDistance(event: MotionEvent): Float = hypot(
        (event.getX(0) - event.getX(1)).toDouble(), (event.getY(0) - event.getY(1)).toDouble(),
    ).toFloat()

    private fun pointerMidpoint(event: MotionEvent): Point = Point(
        (event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f,
    )

    private fun selectionOverlay(snapshot: VectorEditorSnapshot): SelectionOverlay? {
        val bounds = selectionBounds(snapshot.document.objects, snapshot.selectedIds, profilesProvider()) ?: return null
        val top = canvasToScreen(Point((bounds.minX + bounds.maxX) / 2f, bounds.minY), viewport) ?: return null
        val bottom = canvasToScreen(Point(bounds.maxX, bounds.maxY), viewport) ?: return null
        val start = canvasToScreen(Point(bounds.minX, bounds.minY), viewport) ?: return null
        return SelectionOverlay(Bounds(start.x, start.y, bottom.x, bottom.y), Point(top.x, top.y - 28f), max(12f, 12f * viewport.scale))
    }

    private fun drawSelection(canvas: Canvas, snapshot: VectorEditorSnapshot) {
        val overlay = selectionOverlay(snapshot) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF172E58.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawRect(overlay.bounds.minX, overlay.bounds.minY, overlay.bounds.maxX, overlay.bounds.maxY, paint)
        paint.style = Paint.Style.FILL
        listOf(Point(overlay.bounds.minX, overlay.bounds.minY), Point(overlay.bounds.maxX, overlay.bounds.minY), Point(overlay.bounds.maxX, overlay.bounds.maxY), Point(overlay.bounds.minX, overlay.bounds.maxY), overlay.rotationHandle).forEach { canvas.drawCircle(it.x, it.y, 7f, paint) }
    }
}
