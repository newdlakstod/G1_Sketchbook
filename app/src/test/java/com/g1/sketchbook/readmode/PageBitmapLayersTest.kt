package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class PageBitmapLayersTest {
    @Test
    fun authoredPagesPutTransparentContentOnTopOfPaper() {
        assertEquals(
            listOf(PageBitmapLayer.PAPER, PageBitmapLayer.CONTENT),
            pageBitmapLayers(hasContent = true),
        )
    }

    @Test
    fun emptyPagesStillContainPaper() {
        assertEquals(
            listOf(PageBitmapLayer.PAPER),
            pageBitmapLayers(hasContent = false),
        )
    }
}
