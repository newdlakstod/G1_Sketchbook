package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VectorPageTest {
    @Test fun roundTripsThroughJson() {
        val page = VectorPage(
            listOf(
                VectorStroke(-13421773L, listOf(VectorPoint(1f, 2f, 3f), VectorPoint(4.5f, 5.5f, 6.5f))),
                VectorStroke(-65536L, listOf(VectorPoint(-1f, 0f, 2f))),
            ),
        )
        val decoded = vectorPageFromJson(page.toJson())
        assertEquals(page, decoded)
    }

    @Test fun emptyPageRoundTrips() {
        val page = VectorPage(emptyList())
        assertEquals(page, vectorPageFromJson(page.toJson()))
    }

    @Test fun malformedJsonReturnsNull() {
        assertNull(vectorPageFromJson("not json at all"))
    }

    @Test fun blankStringReturnsNull() {
        assertNull(vectorPageFromJson(""))
    }
}
