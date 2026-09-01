package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test fun profileFilenameCannotEscapeTypedRoot() {
        val root = java.io.File("build/brush-root").canonicalFile
        val candidate = java.io.File(root, vectorBrushFileName("../outside/slash"))
        assertTrue(candidate.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator))
        assertEquals("../outside/slash", decodeVectorBrushProfile(encodeVectorBrushProfile(PatternBrushProfile("../outside/slash", "n", emptyList())))?.id)
    }
}
