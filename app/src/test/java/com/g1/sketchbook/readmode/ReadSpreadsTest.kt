package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadSpreadsTest {
    @Test
    fun portraitSpreadsAreOnePageEach() {
        val spreads = buildSpreads(pageCount = 15, landscape = false)
        assertEquals(15, spreads.size)
        assertEquals(listOf(0), spreads[0])
        assertEquals(listOf(14), spreads[14])
    }

    @Test
    fun landscapeSpreadsPairCoverWithPageOneThenStepByTwo() {
        val spreads = buildSpreads(pageCount = 15, landscape = true)
        assertEquals(
            listOf(
                listOf(COVER_PAGE, 0),
                listOf(1, 2),
                listOf(3, 4),
                listOf(5, 6),
                listOf(7, 8),
                listOf(9, 10),
                listOf(11, 12),
                listOf(13, 14),
            ),
            spreads,
        )
    }

    @Test
    fun landscapeWithATrailingOddPageHasAFinalSingleWidePageSpread() {
        val spreads = buildSpreads(pageCount = 4, landscape = true)
        assertEquals(
            listOf(listOf(COVER_PAGE, 0), listOf(1, 2), listOf(3)),
            spreads,
        )
    }

    @Test
    fun spreadIndexForPageFindsTheSpreadContainingThatPage() {
        val spreads = buildSpreads(pageCount = 15, landscape = true)
        assertEquals(0, spreadIndexForPage(spreads, page = 0))
        assertEquals(2, spreadIndexForPage(spreads, page = 4))
        assertEquals(7, spreadIndexForPage(spreads, page = 14))
    }

    @Test
    fun spreadIndexForPageFallsBackToZeroForAnOutOfRangePage() {
        val spreads = buildSpreads(pageCount = 15, landscape = false)
        assertEquals(0, spreadIndexForPage(spreads, page = 99))
    }
}
