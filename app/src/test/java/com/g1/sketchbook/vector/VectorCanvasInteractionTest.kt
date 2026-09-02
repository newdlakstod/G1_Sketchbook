package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorCanvasInteractionTest {
    @Test fun rotationHandleWinsBeforeBoundingBoxBody() {
        val overlay = SelectionOverlay(Bounds(20f, 20f, 120f, 120f), Point(70f, 0f), 12f)
        assertEquals(SelectionHandle.ROTATE, hitSelectionHandle(Point(70f, 4f), overlay))
    }

    @Test fun penSecondPointerCancelsDraftAndStartsViewportGesture() {
        val result = reduceCanvasInput(CanvasInput.PointerAdded(2), InteractionState(tool = VectorTool.PEN, draftPathActive = true))
        assertFalse(result.state.draftPathActive)
        assertEquals(CanvasGesture.VIEWPORT, result.state.gesture)
    }

    @Test fun secondPointerKeepsAnAlreadyOwnedSelectionTransform() {
        val state = InteractionState(tool = VectorTool.SELECT, gesture = CanvasGesture.MOVE_SELECTION)
        assertEquals(CanvasGesture.MOVE_SELECTION, reduceCanvasInput(CanvasInput.PointerAdded(2), state).state.gesture)
    }

    @Test fun eraserGestureReturnsUniqueIds() {
        assertEquals(setOf("a", "b"), uniqueEraseIds(listOf("a", "a", "b")))
    }

    @Test fun selectHandleTakesPrecedenceOverBodyAndBlankStartsLasso() {
        val overlay = SelectionOverlay(Bounds(20f, 20f, 120f, 120f), Point(70f, 0f), 12f)
        assertEquals(CanvasGesture.SCALE_SELECTION, gestureForPointerDown(VectorTool.SELECT, Point(20f, 20f), overlay, true))
        assertEquals(CanvasGesture.LASSO, gestureForPointerDown(VectorTool.SELECT, Point(4f, 4f), overlay, false))
    }

    @Test fun toolDecisionsMapOnePointerToExpectedGesture() {
        assertEquals(CanvasGesture.DRAW, gestureForPointerDown(VectorTool.PEN, Point(0f, 0f), null, false))
        assertEquals(CanvasGesture.ERASE, gestureForPointerDown(VectorTool.ERASER, Point(0f, 0f), null, false))
        assertEquals(CanvasGesture.VIEWPORT, gestureForPointerDown(VectorTool.HAND, Point(0f, 0f), null, false))
    }

    @Test fun completionEmitsExactlyOneCommand() {
        val started = reduceCanvasInput(CanvasInput.PointerDown(Point(3f, 4f)), InteractionState(tool = VectorTool.ERASER))
        val withHits = reduceCanvasInput(CanvasInput.EraseHit("a"), started.state)
        val completed = reduceCanvasInput(CanvasInput.PointerUp, withHits.state)
        assertEquals(DeleteObjects(setOf("a")), completed.command)
        assertEquals(null, reduceCanvasInput(CanvasInput.PointerUp, completed.state).command)
    }

    @Test fun invalidViewportIsRejectedWithoutNaNCoordinates() {
        assertEquals(null, screenToCanvas(Point(4f, 5f), CanvasViewport(scale = 0f)))
        assertTrue(screenToCanvas(Point(4f, 5f), CanvasViewport(scale = 2f, translateX = 1f, translateY = 1f))!!.x.isFinite())
    }

    @Test fun shortSelectionGestureIsATapButDraggedGestureIsALasso() {
        assertTrue(isShortSelectionGesture(Point(10f, 10f), Point(15f, 14f)))
        assertFalse(isShortSelectionGesture(Point(10f, 10f), Point(30f, 10f)))
    }

    @Test fun selectedLowerObjectDoesNotOwnGestureWhenAnUnselectedObjectIsTopmost() {
        assertFalse(selectedTopmostOwnsGesture("top", setOf("lower")))
        assertTrue(selectedTopmostOwnsGesture("top", setOf("top")))
        assertFalse(selectedTopmostOwnsGesture(null, setOf("lower")))
    }

    @Test fun fourEdgeCenterHandlesAreHitBeforeTheBody() {
        val overlay = SelectionOverlay(Bounds(20f, 20f, 120f, 120f), Point(70f, 0f), 12f)
        assertEquals(SelectionHandle.TOP, hitSelectionHandle(Point(70f, 20f), overlay))
        assertEquals(SelectionHandle.RIGHT, hitSelectionHandle(Point(120f, 70f), overlay))
        assertEquals(SelectionHandle.BOTTOM, hitSelectionHandle(Point(70f, 120f), overlay))
        assertEquals(SelectionHandle.LEFT, hitSelectionHandle(Point(20f, 70f), overlay))
    }

    @Test fun scaleAndRotationPreviewUseTheSamePureTransformContractsAsRelease() {
        val objectPath = editablePath("a")
        val document = VectorDocument(objects = listOf(objectPath))
        val bounds = Bounds(0f, 0f, 10f, 10f)

        val scale = selectionScaleTransform(Point(10f, 5f), Point(20f, 5f), bounds, SelectionHandle.RIGHT)
        val rotation = selectionRotationTransform(Point(10f, 5f), Point(5f, 10f), bounds)
        assertEquals(3f, scale.scaleX)
        assertTrue(rotation.rotationDegrees != 0f)
        assertTrue(previewSelectionTransform(document, setOf("a"), scale) != document)
        assertTrue(previewSelectionTransform(document, setOf("a"), rotation) != document)
    }
}
