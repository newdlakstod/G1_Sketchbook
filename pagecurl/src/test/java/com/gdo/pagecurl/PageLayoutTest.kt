package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Test

class PageLayoutTest {
    @Test
    fun pageSlotsMatchTheFourUnitSpread() {
        assertEquals(-1f, PageSlot.Left.offsetX, 0f)
        assertEquals(0f, PageSlot.Center.offsetX, 0f)
        assertEquals(1f, PageSlot.Right.offsetX, 0f)
        assertEquals(2f, PageLayoutMode.SinglePage.frameWidth(pageWidth = 2f), 0f)
        assertEquals(4f, PageLayoutMode.TwoPageSpread.frameWidth(pageWidth = 2f), 0f)
    }
}
