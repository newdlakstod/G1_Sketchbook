package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class PageTextureProviderTest {
    @Test
    fun sizeWithinBudgetIsUnchanged() {
        assertEquals(800 to 600, downsampleTargetSize(800, 600, maxEdge = 1600))
    }

    @Test
    fun oversizedSquareScalesDownToMaxEdge() {
        assertEquals(1600 to 1600, downsampleTargetSize(3200, 3200, maxEdge = 1600))
    }

    @Test
    fun oversizedRectangleScalesProportionally() {
        assertEquals(1600 to 800, downsampleTargetSize(3200, 1600, maxEdge = 1600))
    }

    @Test
    fun exactlyAtBudgetIsUnchanged() {
        assertEquals(1600 to 1200, downsampleTargetSize(1600, 1200, maxEdge = 1600))
    }

    /** `loadPageThumb`'s reqPx bounds the decoded *width*, so a portrait page has to ask for less
     *  than the full longest-edge budget or its height blows past it. */
    @Test
    fun portraitPageAsksForAnAspectScaledWidth() {
        // A4/A3 at 200dpi: 1654x2339 / 2339x3307, aspect ~0.707.
        assertEquals(1131, decodeRequestWidth(maxEdge = 1600, pageAspect = 1654f / 2339f))
    }

    /** A landscape page's width already *is* its longest edge, so the budget passes through. */
    @Test
    fun landscapePageAsksForTheFullBudget() {
        assertEquals(1600, decodeRequestWidth(maxEdge = 1600, pageAspect = 1920f / 1080f))
    }

    @Test
    fun squarePageAsksForTheFullBudget() {
        assertEquals(1600, decodeRequestWidth(maxEdge = 1600, pageAspect = 1f))
    }
}
