package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadModeStateTest {
    @Test
    fun startPageIsClampedToTheAvailablePageRange() {
        assertEquals(0, normalizeReadPage(startPage = -1, pageCount = 15))
        assertEquals(7, normalizeReadPage(startPage = 7, pageCount = 15))
        assertEquals(14, normalizeReadPage(startPage = 99, pageCount = 15))
    }
}
