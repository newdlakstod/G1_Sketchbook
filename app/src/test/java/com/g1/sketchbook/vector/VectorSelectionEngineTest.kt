package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorSelectionEngineTest {
    @Test fun hitTestReturnsTopmostMatchingObject() {
        val bottom = editableRect("bottom", 0f, 0f, 20f, 20f)
        val top = editableRect("top", 0f, 0f, 20f, 20f)

        assertEquals("top", topmostObjectAt(listOf(bottom, top), Point(10f, 10f), emptyMap(), viewportScale = 1f)?.id)
    }

    @Test fun hitTestUsesRenderedProfileExtents() {
        val path = editableLine("pattern", Point(0f, 0f), Point(100f, 0f), width = 8f).copy(
            appearance = appearance(8f).copy(brush = BrushStyle(VectorBrushKind.PATTERN, "p")),
        )
        val profile = PatternBrushProfile(
            "p", "large", listOf(listOf(Point(-1f, -1f), Point(1f, -1f), Point(0f, 1f))),
            spacingPx = 100f, sizePx = 20f,
        )

        assertEquals("pattern", topmostObjectAt(listOf(path), Point(0f, 8f), mapOf("p" to profile), 1f)?.id)
    }

    @Test fun hitTestUsesViewportAdjustedToleranceForThinOpenPaths() {
        val thin = editableLine("thin", Point(0f, 0f), Point(10f, 0f), width = 0f)

        assertEquals("thin", topmostObjectAt(listOf(thin), Point(5f, 6f), emptyMap(), viewportScale = 2f)?.id)
        assertEquals(null, topmostObjectAt(listOf(thin), Point(5f, 6.1f), emptyMap(), viewportScale = 2f)?.id)
        assertEquals("thin", topmostObjectAt(listOf(thin), Point(5f, 100f), emptyMap(), viewportScale = Float.NaN)?.id)
    }

    @Test fun lassoAndSelectionBoundsUseRenderedExtents() {
        val path = editableLine("line", Point(0f, 0f), Point(10f, 0f), width = 8f)
        val lasso = listOf(Point(8f, 3f), Point(14f, 3f), Point(14f, 8f), Point(8f, 8f))

        assertEquals(setOf("line"), objectsTouchingLasso(listOf(path), lasso, emptyMap(), viewportScale = 1f))
        assertEquals(Bounds(-4f, -4f, 14f, 4f), selectionBounds(listOf(path), setOf("line"), emptyMap()))
    }

}
