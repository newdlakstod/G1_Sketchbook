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
}
