package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class VectorDocumentTest {
    @Test fun legacyPageUsesFixedStableIdWithoutChangingStrokes() {
        val stroke = VectorStroke(
            color = 0xFF123456,
            points = listOf(VectorPoint(1f, 2f, 4f), VectorPoint(5f, 6f, 2f)),
            cap = VectorCap.SQUARE,
            fillEnabled = false,
            strokeColor = 0xFF445566,
            strokeWidthPx = 3f,
            brushProfileId = "brush-7",
            fillColor = 0xFF778899,
        )
        val page = VectorPage(listOf(stroke))

        val document = legacyPageAsDocument(page)

        val legacy = assertIs<LegacyStrokeObject>(document.objects.single())
        assertEquals(stroke, legacy.stroke)
        assertEquals("legacy-0-1420665820", legacy.id)
    }

    @Test fun stableLegacyHashChangesWhenAnyPersistedStrokeFieldChanges() {
        val stroke = VectorStroke(
            color = 0xFF123456,
            points = listOf(VectorPoint(1f, 2f, 4f), VectorPoint(5f, 6f, 2f)),
            cap = VectorCap.SQUARE,
            fillEnabled = false,
            strokeColor = 0xFF445566,
            strokeWidthPx = 3f,
            brushProfileId = "brush-7",
            fillColor = 0xFF778899,
        )
        val expected = 1420665820

        assertEquals(expected, stableLegacyHash(stroke))
        listOf(
            stroke.copy(color = 0xFF123457),
            stroke.copy(points = listOf(VectorPoint(9f, 2f, 4f), stroke.points[1])),
            stroke.copy(points = listOf(VectorPoint(1f, 9f, 4f), stroke.points[1])),
            stroke.copy(points = listOf(VectorPoint(1f, 2f, 9f), stroke.points[1])),
            stroke.copy(cap = VectorCap.ROUND),
            stroke.copy(fillEnabled = true),
            stroke.copy(strokeColor = null),
            stroke.copy(strokeWidthPx = 4f),
            stroke.copy(brushProfileId = "brush-8"),
            stroke.copy(fillColor = null),
        ).forEach { changed -> assertNotEquals(expected, stableLegacyHash(changed)) }
    }

    @Test fun convertingLegacyPreservesAppearanceAndDoesNotMutateSource() {
        val legacy = LegacyStrokeObject("a", VectorStroke(
            color = 0xFF112233, points = listOf(VectorPoint(0f, 0f, 8f), VectorPoint(10f, 0f, 4f)),
            cap = VectorCap.SQUARE, fillEnabled = false, strokeColor = 0xFF445566,
            strokeWidthPx = 3f, brushProfileId = "brush-7", fillColor = 0xFF778899,
        ))
        val sourceBefore = legacy.stroke.copy(points = legacy.stroke.points.toList())

        val editable = convertLegacyObject(legacy)

        assertEquals(listOf(1f, 0.5f), editable.geometry.points.map { it.widthFactor })
        assertEquals(FillStyle(enabled = false, color = 0xFF778899), editable.appearance.fill)
        assertEquals(true, editable.appearance.stroke.enabled)
        assertEquals(0xFF445566, editable.appearance.stroke.color)
        assertEquals(8f, editable.appearance.stroke.width)
        assertEquals(VectorCap.SQUARE, editable.appearance.stroke.cap)
        assertEquals(VectorBrushKind.PATTERN, editable.appearance.brush.kind)
        assertEquals("brush-7", editable.appearance.brush.profileId)
        assertEquals(sourceBefore, legacy.stroke)
    }

    @Test fun convertingLegacyWithoutOutlineKeepsStrokeDisabled() {
        val legacy = LegacyStrokeObject("no-outline", VectorStroke(
            color = 0xFF112233,
            points = listOf(VectorPoint(0f, 0f, 8f), VectorPoint(10f, 0f, 4f)),
            cap = VectorCap.BUTT,
            fillEnabled = true,
            strokeColor = null,
            fillColor = null,
        ))
        val sourceBefore = legacy.stroke.copy(points = legacy.stroke.points.toList())

        val editable = convertLegacyObject(legacy)

        assertEquals(FillStyle(enabled = true, color = 0xFF112233), editable.appearance.fill)
        assertEquals(false, editable.appearance.stroke.enabled)
        assertEquals(0xFF112233, editable.appearance.stroke.color)
        assertEquals(VectorCap.BUTT, editable.appearance.stroke.cap)
        assertEquals(sourceBefore, legacy.stroke)
    }
}
