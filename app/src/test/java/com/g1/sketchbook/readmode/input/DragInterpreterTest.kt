package com.g1.sketchbook.readmode.input

import com.g1.sketchbook.readmode.curl.math.Vec2
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class DragInterpreterTest {
    private val interpreter = DragInterpreter()

    @Test
    fun onlyRightmostFifteenPercentStartsCurl() {
        assertFalse(interpreter.canStart(Vec2(0.849f, 0.5f)))
        assertTrue(interpreter.canStart(Vec2(0.85f, 0.5f)))
        assertTrue(interpreter.canStart(Vec2(1f, 0.5f)))
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
