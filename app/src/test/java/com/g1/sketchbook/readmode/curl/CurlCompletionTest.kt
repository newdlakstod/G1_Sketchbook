package com.g1.sketchbook.readmode.curl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
