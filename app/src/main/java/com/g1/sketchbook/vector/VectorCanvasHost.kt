package com.g1.sketchbook.vector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.hypot

/** New editor canvas bridge. It deliberately owns all document drawing and pointer handling;
 * [VectorBrushView] is retained only as a deprecated compatibility class with no screen caller. */
class VectorCanvasHost(context: Context) : View(context) {
    private var editorState: VectorEditorState? = null
    private var profilesProvider: () -> Map<String, VectorBrushProfile> = { emptyMap() }
    private var snapshot: VectorEditorSnapshot? = null
    private val geometryCache = VectorGeometryCache()
    private var viewport = CanvasViewport()
    private var interaction: InteractionState? = null
    private var draft = mutableListOf<Point>()
    private var lasso = mutableListOf<Point>()
    private var lassoStartScreen = Point(0f, 0f)
    private var gestureStartScreen = Point(0f, 0f)
    private var previewDocument: VectorDocument? = null
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
        val displayed = current.copy(document = previewDocument ?: current.document)
        canvas.save()
        canvas.translate(viewport.translateX, viewport.translateY)
        canvas.scale(viewport.scale, viewport.scale)
        drawVectorDocument(canvas, displayed.document, profilesProvider(), geometryCache)
        draft.takeIf { it.size > 1 }?.let { points ->
            val appearance = current.defaultAppearance
            drawVectorDocument(canvas, VectorDocument(objects = listOf(EditablePathObject("draft", PathGeometry(points.map { PathPoint(it.x, it.y, 1f) }), appearance))), profilesProvider(), geometryCache)
        }
        canvas.restore()
        drawSelection(canvas, displayed)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = editorState ?: return false
        val current = state.snapshot.value
        val screen = Point(event.x, event.y)
        val canvasPoint = screenToCanvas(screen, viewport) ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val overlay = selectionOverlay(current)
                val topmostHit = topmostObjectAt(current.document.objects, canvasPoint, profilesProvider(), viewport.scale)
                val body = selectedTopmostOwnsGesture(topmostHit?.id, current.selectedIds)
                interaction = reduceCanvasInput(CanvasInput.PointerDown(screen), InteractionState(current.tool, overlay = overlay, selectedBodyHit = body)).state
                if (interaction?.gesture == CanvasGesture.DRAW) draft = mutableListOf(canvasPoint)
                if (interaction?.gesture == CanvasGesture.LASSO) { lasso = mutableListOf(canvasPoint); lassoStartScreen = screen }
                transformStart = canvasPoint
                gestureStartScreen = screen
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
                CanvasGesture.MOVE_SELECTION -> transformStart?.let { start ->
                    if (!isShortSelectionGesture(gestureStartScreen, screen)) {
                        previewDocument = previewSelectionTransform(current.document, current.selectedIds, SelectionTransform(canvasPoint.x - start.x, canvasPoint.y - start.y, pivot = start))
                    }
                }
                CanvasGesture.SCALE_SELECTION -> transformStart?.let { start ->
                    selectionBounds(current.document.objects, current.selectedIds, profilesProvider())?.let { bounds ->
                        previewDocument = previewSelectionTransform(current.document, current.selectedIds, selectionScaleTransform(start, canvasPoint, bounds, interaction?.selectionHandle ?: SelectionHandle.NONE))
                    }
                }
                CanvasGesture.ROTATE_SELECTION -> transformStart?.let { start ->
                    selectionBounds(current.document.objects, current.selectedIds, profilesProvider())?.let { bounds ->
                        previewDocument = previewSelectionTransform(current.document, current.selectedIds, selectionRotationTransform(start, canvasPoint, bounds))
                    }
                }
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
            MotionEvent.ACTION_UP -> {
                if (interaction?.gesture == CanvasGesture.LASSO) lasso.add(canvasPoint)
                finishGesture(state, current, canvasPoint)
            }
            MotionEvent.ACTION_CANCEL -> { interaction = interaction?.let { reduceCanvasInput(CanvasInput.Cancel, it).state }; draft.clear(); lasso.clear(); previewDocument = null }
        }
        lastScreen = screen
        invalidate()
        return true
    }

    private fun finishGesture(state: VectorEditorState, current: VectorEditorSnapshot, releasePoint: Point) {
        val active = interaction ?: return
        val releaseScreen = canvasToScreen(releasePoint, viewport) ?: lastScreen
        when (active.gesture) {
            CanvasGesture.DRAW -> if (draft.size >= 2) state.dispatch(AddObject(EditablePathObject(
                id = "path-${System.nanoTime()}", geometry = PathGeometry(draft.map { PathPoint(it.x, it.y, 1f) }), appearance = current.defaultAppearance,
            )))
            CanvasGesture.MOVE_SELECTION -> transformStart?.let { start ->
                if (!isShortSelectionGesture(gestureStartScreen, releaseScreen) && start != releasePoint && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, SelectionTransform(releasePoint.x - start.x, releasePoint.y - start.y, pivot = start)))
            }
            CanvasGesture.SCALE_SELECTION -> transformStart?.let { start ->
                val end = releasePoint
                val bounds = selectionBounds(current.document.objects, current.selectedIds, profilesProvider()) ?: return@let
                val transform = selectionScaleTransform(start, end, bounds, active.selectionHandle)
                if ((transform.scaleX != 1f || transform.scaleY != 1f) && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, transform))
            }
            CanvasGesture.ROTATE_SELECTION -> transformStart?.let { start ->
                val end = releasePoint
                val bounds = selectionBounds(current.document.objects, current.selectedIds, profilesProvider()) ?: return@let
                val transform = selectionRotationTransform(start, end, bounds)
                if (transform.rotationDegrees != 0f && current.selectedIds.isNotEmpty()) state.dispatch(TransformObjects(current.selectedIds, transform))
            }
            CanvasGesture.LASSO -> if (isShortSelectionGesture(lassoStartScreen, releaseScreen)) {
                state.select(topmostObjectAt(current.document.objects, releasePoint, profilesProvider(), viewport.scale)?.let { setOf(it.id) } ?: emptySet())
            } else if (lasso.size >= 3) state.select(objectsTouchingLasso(current.document.objects, lasso, profilesProvider(), viewport.scale))
            else -> Unit
        }
        val result = reduceCanvasInput(CanvasInput.PointerUp, active)
        result.command?.let(state::dispatch)
        interaction = result.state
        draft.clear()
        lasso.clear()
        previewDocument = null
    }

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
        listOf(
            Point(overlay.bounds.minX, overlay.bounds.minY), Point((overlay.bounds.minX + overlay.bounds.maxX) / 2f, overlay.bounds.minY), Point(overlay.bounds.maxX, overlay.bounds.minY),
            Point(overlay.bounds.maxX, (overlay.bounds.minY + overlay.bounds.maxY) / 2f), Point(overlay.bounds.maxX, overlay.bounds.maxY), Point((overlay.bounds.minX + overlay.bounds.maxX) / 2f, overlay.bounds.maxY),
            Point(overlay.bounds.minX, overlay.bounds.maxY), Point(overlay.bounds.minX, (overlay.bounds.minY + overlay.bounds.maxY) / 2f), overlay.rotationHandle,
        ).forEach { canvas.drawCircle(it.x, it.y, 7f, paint) }
    }
}
