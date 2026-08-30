package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StrokeGeometryTest {
    @Test fun straightHorizontalStrokeMakesARectangle() {
        val outline = strokeOutline(listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)))
        assertEquals(
            listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f)),
            outline,
        )
    }

    @Test fun singlePointStrokeHasNoOutline() {
        assertEquals(emptyList(), strokeOutline(listOf(VectorPoint(0f, 0f, 4f))))
    }

    @Test fun emptyStrokeHasNoOutline() {
        assertEquals(emptyList(), strokeOutline(emptyList()))
    }

    @Test fun pointInsideRectangleIsInside() {
        val square = listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f))
        assertTrue(pointInPolygon(5f, 0f, square))
    }

    @Test fun pointOutsideRectangleIsOutside() {
        val square = listOf(Point(0f, 2f), Point(10f, 2f), Point(10f, -2f), Point(0f, -2f))
        assertFalse(pointInPolygon(5f, 5f, square))
        assertFalse(pointInPolygon(-1f, 0f, square))
    }

    @Test fun degeneratePolygonNeverContainsAPoint() {
        assertFalse(pointInPolygon(0f, 0f, emptyList()))
    }

    @Test fun contentBoundsOfEmptyStrokesIsNull() {
        assertEquals(null, contentBounds(emptyList()))
    }

    @Test fun contentBoundsWrapsAllPointsWithHalfWidthPadding() {
        val strokes = listOf(
            VectorStroke(0L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f))),
            VectorStroke(0L, listOf(VectorPoint(5f, -20f, 2f), VectorPoint(5f, 20f, 2f))),
        )
        val bounds = contentBounds(strokes)!!
        assertEquals(-2f, bounds.minX); assertEquals(-21f, bounds.minY)
        assertEquals(12f, bounds.maxX); assertEquals(21f, bounds.maxY)
    }

    @Test fun pointsBoundsOfEmptyListIsNull() {
        assertEquals(null, pointsBounds(emptyList()))
    }

    @Test fun pointsBoundsWrapsAllPoints() {
        val bounds = pointsBounds(listOf(Point(3f, 5f), Point(-1f, 8f), Point(2f, -4f)))!!
        assertEquals(-1f, bounds.minX); assertEquals(-4f, bounds.minY)
        assertEquals(3f, bounds.maxX); assertEquals(8f, bounds.maxY)
    }

    @Test fun strokeTouchingLassoIsSelected() {
        val lasso = listOf(Point(0f, -5f), Point(10f, -5f), Point(10f, 5f), Point(0f, 5f))
        val stroke = VectorStroke(0L, listOf(VectorPoint(5f, 0f, 2f), VectorPoint(50f, 50f, 2f)))
        assertTrue(strokeTouchesLasso(stroke, lasso))
    }

    @Test fun strokeEntirelyOutsideLassoIsNotSelected() {
        val lasso = listOf(Point(0f, -5f), Point(10f, -5f), Point(10f, 5f), Point(0f, 5f))
        val stroke = VectorStroke(0L, listOf(VectorPoint(50f, 50f, 2f), VectorPoint(60f, 60f, 2f)))
        assertFalse(strokeTouchesLasso(stroke, lasso))
    }
}
