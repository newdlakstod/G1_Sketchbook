package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VectorTransformEngineTest {
    @Test fun rotationUsesSelectionCenter() {
        val objectPath = editableLine("a", Point(0f, 0f), Point(10f, 0f))

        val transformed = transformObjects(
            listOf(objectPath), setOf("a"),
            SelectionTransform(rotationDegrees = 90f, pivot = Point(5f, 0f)),
        )

        val points = assertIs<EditablePathObject>(transformed.single()).geometry.points
        assertEquals(5f, points[0].x, .01f); assertEquals(-5f, points[0].y, .01f)
        assertEquals(5f, points[1].x, .01f); assertEquals(5f, points[1].y, .01f)
    }

    @Test fun transformAppliesScaleThenRotationThenTranslationAndPreservesOrder() {
        val selected = editableLine("selected", Point(1f, 1f), Point(3f, 1f))
        val untouched = editableLine("untouched", Point(0f, 0f), Point(1f, 1f))

        val transformed = transformObjects(
            listOf(selected, untouched), setOf("selected"),
            SelectionTransform(translateX = 10f, translateY = 20f, scaleX = 2f, scaleY = 3f, rotationDegrees = 90f, pivot = Point(1f, 1f)),
        )

        val points = assertIs<EditablePathObject>(transformed.first()).geometry.points
        assertEquals(11f, points[0].x, .01f); assertEquals(21f, points[0].y, .01f)
        assertEquals(11f, points[1].x, .01f); assertEquals(25f, points[1].y, .01f)
        assertEquals(untouched, transformed[1])
        assertEquals(ObjectTransform(), assertIs<EditablePathObject>(transformed.first()).transform)
    }

    @Test fun scalingScalesEditableWidthByAreaFactorIncludingNegativeAxes() {
        val path = editableLine("a", Point(0f, 0f), Point(10f, 0f), width = 8f)

        val transformed = transformObjects(
            listOf(path), setOf("a"), SelectionTransform(scaleX = -4f, scaleY = .25f, pivot = Point(0f, 0f)),
        )

        assertEquals(8f, assertIs<EditablePathObject>(transformed.single()).appearance.stroke.width)
    }

    @Test fun scalingAlsoScalesLegacyPointWidths() {
        val legacy = legacyLine("old", width = 4f)

        val scaled = transformObjects(
            listOf(legacy), setOf("old"), SelectionTransform(scaleX = 2f, scaleY = 2f, pivot = Point(0f, 0f)),
        )

        assertEquals(8f, assertIs<LegacyStrokeObject>(scaled.single()).stroke.points.first().w)
    }

    @Test fun degenerateInputsRemainFinite() {
        val path = editableLine("a", Point(0f, 0f), Point(10f, 0f))

        val transformed = transformObjects(
            listOf(path), setOf("a"),
            SelectionTransform(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.NaN, Point(Float.NaN, Float.NEGATIVE_INFINITY)),
        )

        val editable = assertIs<EditablePathObject>(transformed.single())
        assertTrue(editable.geometry.points.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(editable.appearance.stroke.width.isFinite())
    }
}
