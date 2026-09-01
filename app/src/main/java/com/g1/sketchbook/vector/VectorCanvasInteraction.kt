package com.g1.sketchbook.vector

import kotlin.math.abs

enum class SelectionHandle { BODY, TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, ROTATE, NONE }
enum class CanvasGesture { NONE, DRAW, ERASE, MOVE_SELECTION, SCALE_SELECTION, ROTATE_SELECTION, VIEWPORT, LASSO }

data class SelectionOverlay(val bounds: Bounds, val rotationHandle: Point, val handleRadius: Float)
data class CanvasViewport(val scale: Float = 1f, val translateX: Float = 0f, val translateY: Float = 0f) {
    fun isUsable(): Boolean = scale.isFinite() && scale > 0f && translateX.isFinite() && translateY.isFinite()
}

sealed interface CanvasInput {
    data class PointerDown(val point: Point) : CanvasInput
    data class PointerAdded(val pointerCount: Int) : CanvasInput
    data class EraseHit(val id: String) : CanvasInput
    data object PointerUp : CanvasInput
    data object Cancel : CanvasInput
}

data class InteractionState(
    val tool: VectorTool,
    val draftPathActive: Boolean = false,
    val gesture: CanvasGesture = CanvasGesture.NONE,
    val erasedIds: Set<String> = emptySet(),
    val overlay: SelectionOverlay? = null,
    val selectedBodyHit: Boolean = false,
    val selectionHandle: SelectionHandle = SelectionHandle.NONE,
)
data class InteractionResult(val state: InteractionState, val command: EditorCommand? = null)

fun hitSelectionHandle(point: Point, overlay: SelectionOverlay): SelectionHandle {
    val radius = overlay.handleRadius.takeIf { it.isFinite() && it > 0f } ?: return SelectionHandle.NONE
    fun near(target: Point) = abs(point.x - target.x) <= radius && abs(point.y - target.y) <= radius
    if (near(overlay.rotationHandle)) return SelectionHandle.ROTATE
    val b = overlay.bounds
    if (!listOf(b.minX, b.minY, b.maxX, b.maxY).all(Float::isFinite)) return SelectionHandle.NONE
    val handles = listOf(
        SelectionHandle.TOP_LEFT to Point(b.minX, b.minY), SelectionHandle.TOP to Point((b.minX + b.maxX) / 2f, b.minY),
        SelectionHandle.TOP_RIGHT to Point(b.maxX, b.minY), SelectionHandle.RIGHT to Point(b.maxX, (b.minY + b.maxY) / 2f),
        SelectionHandle.BOTTOM_RIGHT to Point(b.maxX, b.maxY), SelectionHandle.BOTTOM to Point((b.minX + b.maxX) / 2f, b.maxY),
        SelectionHandle.BOTTOM_LEFT to Point(b.minX, b.maxY), SelectionHandle.LEFT to Point(b.minX, (b.minY + b.maxY) / 2f),
    )
    return handles.firstOrNull { near(it.second) }?.first
        ?: if (point.x in b.minX..b.maxX && point.y in b.minY..b.maxY) SelectionHandle.BODY else SelectionHandle.NONE
}

fun gestureForPointerDown(tool: VectorTool, point: Point, overlay: SelectionOverlay?, selectedBodyHit: Boolean): CanvasGesture = when (tool) {
    VectorTool.PEN -> CanvasGesture.DRAW
    VectorTool.ERASER -> CanvasGesture.ERASE
    VectorTool.HAND -> CanvasGesture.VIEWPORT
    VectorTool.SELECT -> when (overlay?.let { hitSelectionHandle(point, it) } ?: SelectionHandle.NONE) {
        SelectionHandle.ROTATE -> CanvasGesture.ROTATE_SELECTION
        SelectionHandle.BODY -> if (selectedBodyHit) CanvasGesture.MOVE_SELECTION else CanvasGesture.LASSO
        SelectionHandle.NONE -> CanvasGesture.LASSO
        else -> CanvasGesture.SCALE_SELECTION
    }
}

fun reduceCanvasInput(input: CanvasInput, state: InteractionState): InteractionResult = when (input) {
    is CanvasInput.PointerDown -> {
        val handle = state.overlay?.let { hitSelectionHandle(input.point, it) } ?: SelectionHandle.NONE
        InteractionResult(state.copy(
        gesture = gestureForPointerDown(state.tool, input.point, state.overlay, state.selectedBodyHit), selectionHandle = handle,
        draftPathActive = state.tool == VectorTool.PEN,
        erasedIds = emptySet(),
    )) }
    is CanvasInput.PointerAdded -> if (input.pointerCount >= 2 && state.gesture !in setOf(CanvasGesture.MOVE_SELECTION, CanvasGesture.SCALE_SELECTION, CanvasGesture.ROTATE_SELECTION)) {
        InteractionResult(state.copy(draftPathActive = false, gesture = CanvasGesture.VIEWPORT))
    } else InteractionResult(state)
    is CanvasInput.EraseHit -> if (state.gesture == CanvasGesture.ERASE && input.id.isNotBlank()) InteractionResult(state.copy(erasedIds = state.erasedIds + input.id)) else InteractionResult(state)
    CanvasInput.PointerUp -> {
        val command = state.erasedIds.takeIf { it.isNotEmpty() }?.let(::DeleteObjects)
        InteractionResult(state.copy(draftPathActive = false, gesture = CanvasGesture.NONE, erasedIds = emptySet()), command)
    }
    CanvasInput.Cancel -> InteractionResult(state.copy(draftPathActive = false, gesture = CanvasGesture.NONE, erasedIds = emptySet()))
}

fun uniqueEraseIds(hitIds: List<String>): Set<String> = hitIds.filter(String::isNotBlank).toSet()

fun screenToCanvas(point: Point, viewport: CanvasViewport): Point? {
    if (!viewport.isUsable() || !point.x.isFinite() || !point.y.isFinite()) return null
    val x = (point.x - viewport.translateX) / viewport.scale
    val y = (point.y - viewport.translateY) / viewport.scale
    return Point(x, y).takeIf { it.x.isFinite() && it.y.isFinite() }
}

fun canvasToScreen(point: Point, viewport: CanvasViewport): Point? {
    if (!viewport.isUsable() || !point.x.isFinite() || !point.y.isFinite()) return null
    return Point(point.x * viewport.scale + viewport.translateX, point.y * viewport.scale + viewport.translateY)
        .takeIf { it.x.isFinite() && it.y.isFinite() }
}
