package com.g1.sketchbook.sketchbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverEditSelectionTest {
    @Test
    fun colorSelectionStaysPendingUntilConfirmed() {
        val original = CoverEditSelection(color = 0xFF112233)

        val selecting = original.previewColor(0xFF445566)

        assertEquals(0xFF112233, selecting.color)
        assertEquals(0xFF445566, selecting.pendingColor)

        val confirmed = selecting.confirmColor()

        assertEquals(0xFF445566, confirmed.color)
        assertNull(confirmed.pendingColor)
        assertTrue(confirmed.removeCover)
    }

    @Test
    fun imageSelectionCancelsPendingColorAndKeepsImageEnabled() {
        val colorConfirmed = CoverEditSelection(color = 0xFF112233)
            .previewColor(0xFF445566)
            .confirmColor()

        val imageApplied = colorConfirmed.imageApplied()

        assertNull(imageApplied.pendingColor)
        assertFalse(imageApplied.removeCover)
    }
}
