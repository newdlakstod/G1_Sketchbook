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
}
