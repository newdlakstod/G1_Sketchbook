package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlCompletionTest {
    @Test
    fun completionTargetLeavesNoFlatPageVertices() {
        val state = CurlState.at(CurlPhase.Completed, CurlState.completionTarget(y = 0.5f))
        val mesh = PageMesh()

        val stats = CurlGeometry().deform(mesh, state.dragPosition, 2f, 8f / 3f)

        assertEquals(0, stats.flatVertices)
    }

    @Test
    fun completedStateDoesNotDrawTheTurningPage() {
        val state = CurlState.at(CurlPhase.Completed, CurlState.completionTarget(y = 0.5f))

        assertFalse(state.drawsTurningPage)
    }

    @Test
    fun idleStateUsesOnlyStaticPages() {
        assertFalse(CurlState().drawsTurningPage)
    }

    @Test
    fun dragAndBothSettlesDrawTheTurningPage() {
        val drag = Vec2(0.5f, 0.5f)

        assertTrue(CurlState.at(CurlPhase.Dragging, drag).drawsTurningPage)
        assertTrue(CurlState.at(CurlPhase.SettlingToNext, drag).drawsTurningPage)
        assertTrue(CurlState.at(CurlPhase.SettlingToOrigin, drag).drawsTurningPage)
    }

    @Test
    fun movingFingerKeepsTheGrabAnchorFromDragStart() {
        val started = CurlState.at(CurlPhase.Dragging, Vec2(0.95f, 0.15f))

        val moved = started.withDragPosition(Vec2(0.4f, 0.85f))

        assertEquals(0.15f, moved.grabAnchorY, 0f)
        assertEquals(Vec2(0.4f, 0.85f), moved.dragPosition)
    }
}
