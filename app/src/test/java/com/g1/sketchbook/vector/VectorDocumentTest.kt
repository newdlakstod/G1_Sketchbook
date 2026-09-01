package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VectorDocumentTest {
    @Test fun legacyPageBecomesStableLegacyObjectsWithoutChangingStrokes() {
        val stroke = VectorStroke(0xFF123456, listOf(VectorPoint(1f, 2f, 4f), VectorPoint(5f, 6f, 2f)))
        val page = VectorPage(listOf(stroke))

        val document = legacyPageAsDocument(page)

        val legacy = assertIs<LegacyStrokeObject>(document.objects.single())
        assertEquals(stroke, legacy.stroke)
        assertEquals("legacy-0-${stableLegacyHash(stroke)}", legacy.id)
    }

    @Test fun convertingLegacyPreservesRelativeWidthsAndProfileReference() {
        val legacy = LegacyStrokeObject("a", VectorStroke(
            color = 0xFF112233, points = listOf(VectorPoint(0f, 0f, 8f), VectorPoint(10f, 0f, 4f)),
            cap = VectorCap.ROUND, fillEnabled = false, strokeColor = 0xFF445566,
            strokeWidthPx = 3f, brushProfileId = "brush-7",
        ))

        val editable = convertLegacyObject(legacy)

        assertEquals(listOf(1f, 0.5f), editable.geometry.points.map { it.widthFactor })
        assertEquals(8f, editable.appearance.stroke.width)
        assertEquals(VectorBrushKind.PATTERN, editable.appearance.brush.kind)
        assertEquals("brush-7", editable.appearance.brush.profileId)
    }
}
