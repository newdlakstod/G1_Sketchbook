package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragInterpreterTest {
    private val interpreter = DragInterpreter()

    @Test
    fun edgeStartChoosesAvailableDirection() {
        assertEquals(
            PageTurnDirection.Forward,
            interpreter.directionForStart(Vec2(0.9f, 0.5f), PageLayoutMode.SinglePage, canForward = true, canBackward = true),
        )
        assertEquals(
            PageTurnDirection.Backward,
            interpreter.directionForStart(Vec2(0.1f, 0.5f), PageLayoutMode.SinglePage, canForward = true, canBackward = true),
        )
        assertNull(interpreter.directionForStart(Vec2(0.5f, 0.5f), PageLayoutMode.SinglePage, true, true))
        assertNull(interpreter.directionForStart(Vec2(0.1f, 0.5f), PageLayoutMode.SinglePage, true, false))
    }

    @Test
    fun backwardInputMirrorsIntoForwardWorkingSpace() {
        assertEquals(
            Vec2(0.75f, 0.4f),
            interpreter.toWorkingPosition(Vec2(0.25f, 0.4f), PageLayoutMode.SinglePage, PageTurnDirection.Backward),
        )
    }

    @Test
    fun spreadStartsOnlyInsideOuterFifteenPercentOfEachPage() {
        assertEquals(PageTurnDirection.Forward, interpreter.directionForStart(Vec2(0.94f, 0.5f), PageLayoutMode.TwoPageSpread, true, false))
        assertEquals(PageTurnDirection.Backward, interpreter.directionForStart(Vec2(0.06f, 0.5f), PageLayoutMode.TwoPageSpread, false, true))
        assertNull(interpreter.directionForStart(Vec2(0.90f, 0.5f), PageLayoutMode.TwoPageSpread, true, false))
        assertNull(interpreter.directionForStart(Vec2(0.50f, 0.5f), PageLayoutMode.TwoPageSpread, true, true))
    }

    @Test
    fun forwardSpreadDragMapsRightOuterEdgeThroughGutterToLeftEdge() {
        assertEquals(Vec2(1f, 0.4f), interpreter.toWorkingPosition(Vec2(1f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward))
        assertEquals(Vec2(0f, 0.4f), interpreter.toWorkingPosition(Vec2(0.5f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward))
        assertEquals(Vec2(-1f, 0.4f), interpreter.toWorkingPosition(Vec2(0f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward))
    }

    @Test
    fun backwardSpreadDragMirrorsLeftOuterEdgeThroughGutterToRightEdge() {
        assertEquals(Vec2(1f, 0.4f), interpreter.toWorkingPosition(Vec2(0f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward))
        assertEquals(Vec2(0f, 0.4f), interpreter.toWorkingPosition(Vec2(0.5f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward))
        assertEquals(Vec2(-1f, 0.4f), interpreter.toWorkingPosition(Vec2(1f, 0.4f), PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward))
    }

    @Test
    fun screenCoordinatesAreClampedAndYIsConvertedToPageSpace() {
        assertEquals(Vec2(0.5f, 0.75f), interpreter.normalized(50f, 25f, 100, 100))
        assertEquals(Vec2(1f, 1f), interpreter.normalized(120f, -20f, 100, 100))
        assertEquals(Vec2(0f, 0f), interpreter.normalized(-20f, 120f, 100, 100))
    }

    @Test
    fun releaseCompletesByDistanceOrLeftwardFling() {
        assertFalse(interpreter.shouldComplete(progress = 0.49f, velocityX = 0f))
        assertTrue(interpreter.shouldComplete(progress = 0.5f, velocityX = 0f))
        assertTrue(interpreter.shouldComplete(progress = 0.2f, velocityX = -1.3f))
    }
}
