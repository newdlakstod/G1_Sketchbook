package com.g1.sketchbook.readmode.input

import com.g1.sketchbook.readmode.curl.CurlDirection
import com.g1.sketchbook.readmode.curl.math.Vec2
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class DragInterpreterTest {
    private val interpreter = DragInterpreter()

    @Test
    fun portraitOnlyRightmostFifteenPercentStartsForward() {
        assertNull(interpreter.directionForStart(Vec2(0.849f, 0.5f), landscape = false, canForward = true, canBackward = true))
        assertEquals(
            CurlDirection.Forward,
            interpreter.directionForStart(Vec2(0.85f, 0.5f), landscape = false, canForward = true, canBackward = true),
        )
        assertEquals(
            CurlDirection.Forward,
            interpreter.directionForStart(Vec2(1f, 0.5f), landscape = false, canForward = true, canBackward = true),
        )
    }

    @Test
    fun portraitOnlyLeftmostFifteenPercentStartsBackward() {
        assertNull(interpreter.directionForStart(Vec2(0.151f, 0.5f), landscape = false, canForward = true, canBackward = true))
        assertEquals(
            CurlDirection.Backward,
            interpreter.directionForStart(Vec2(0.15f, 0.5f), landscape = false, canForward = true, canBackward = true),
        )
        assertEquals(
            CurlDirection.Backward,
            interpreter.directionForStart(Vec2(0f, 0.5f), landscape = false, canForward = true, canBackward = true),
        )
    }

    @Test
    fun edgeStartsAreGatedByAvailability() {
        assertNull(interpreter.directionForStart(Vec2(1f, 0.5f), landscape = false, canForward = false, canBackward = true))
        assertNull(interpreter.directionForStart(Vec2(0f, 0.5f), landscape = false, canForward = true, canBackward = false))
    }

    @Test
    fun landscapeEdgeStartUsesHalfWidthAsTheTurningPage() {
        // The right page occupies screen x in [0.5, 1] in landscape, so its own rightmost 15% is
        // only the rightmost 7.5% of the full surface.
        assertNull(interpreter.directionForStart(Vec2(0.924f, 0.5f), landscape = true, canForward = true, canBackward = false))
        assertEquals(
            CurlDirection.Forward,
            interpreter.directionForStart(Vec2(0.925f, 0.5f), landscape = true, canForward = true, canBackward = false),
        )
    }

    @Test
    fun portraitWorkingPositionMirrorsBackwardTouchUnchanged() {
        assertEquals(Vec2(0.9f, 0.4f), interpreter.toWorkingPosition(Vec2(0.9f, 0.4f), CurlDirection.Forward, landscape = false))
        assertEquals(Vec2(0.75f, 0.4f), interpreter.toWorkingPosition(Vec2(0.25f, 0.4f), CurlDirection.Backward, landscape = false))
    }

    @Test
    fun landscapeForwardWorkingPositionIsRescaledToTheRightHalf() {
        // Screen x=1 (the right page's own far/outer edge, where a forward drag starts) must map to
        // working x=1 (idle/just-started); screen x=0.5 (the spine, where a fully-turned page ends
        // up) must map to working x=0 (fully turned) — same convention as portrait.
        assertEquals(Vec2(1f, 0.5f), interpreter.toWorkingPosition(Vec2(1f, 0.5f), CurlDirection.Forward, landscape = true))
        assertEquals(Vec2(0f, 0.5f), interpreter.toWorkingPosition(Vec2(0.5f, 0.5f), CurlDirection.Forward, landscape = true))
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
