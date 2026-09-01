package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VectorBrushProfileTest {
    @Test fun missingRemoteTypeDefaultsToPattern() {
        assertEquals(VectorBrushKind.PATTERN, remoteBrushKind(null))
    }

    @Test fun patternProfileRetainsSpacingAndSize() {
        val profile = PatternBrushProfile("p", "dots \"x\"", listOf(listOf(Point(0f, 0f))), 24f, 32f, "<svg a=\"b\"/>\n")
        assertEquals(profile, decodeVectorBrushProfile(encodeVectorBrushProfile(profile)))
    }

    @Test fun artProfileRetainsOriginalSvgAndType() {
        val profile = ArtBrushProfile("a", "chalk", listOf(listOf(Point(0f, 0f), Point(1f, 1f))), "<svg/>\n")
        assertEquals(profile, decodeVectorBrushProfile(encodeVectorBrushProfile(profile)))
    }

    @Test fun unknownRemoteTypeIsRejected() {
        assertNull(decodeVectorBrushProfile("{\"id\":\"x\",\"name\":\"x\",\"type\":\"other\"}"))
    }
}
