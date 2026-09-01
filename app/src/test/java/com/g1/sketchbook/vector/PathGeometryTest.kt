package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathGeometryTest {
    @Test fun openPathFillVirtuallyClosesButStrokeDoesNot() {
        val geometry = PathGeometry(
            listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f)),
            closed = false,
        )

        assertEquals(Point(0f, 0f), fillPolygon(geometry).last())
        assertFalse(basicStrokeCenterSegments(geometry).contains(Point(10f, 10f) to Point(0f, 0f)))
        assertFalse(geometry.closed)
    }

    @Test fun closedPathAddsLastToFirstStrokeSegment() {
        val geometry = PathGeometry(
            listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, .5f), PathPoint(10f, 10f, 1f)),
            closed = true,
        )

        assertTrue(basicStrokeCenterSegments(geometry).contains(Point(10f, 10f) to Point(0f, 0f)))
    }

    @Test fun strokeWidthMultipliesWidthFactor() {
        val outline = basicStrokeOutline(
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, .5f))),
            StrokeStyle(width = 8f, cap = VectorCap.BUTT),
        )

        assertEquals(4f, abs(outline.first().y), .001f)
        assertEquals(2f, abs(outline[1].y), .001f)
    }

    @Test fun bevelJoinKeepsBothOuterCornerEdges() {
        val outline = cornerOutline(VectorJoin.BEVEL)

        assertTrue(outline.contains(Point(10f, -2f)))
        assertTrue(outline.contains(Point(12f, 0f)))
    }

    @Test fun miterJoinExtendsToOffsetLineIntersection() {
        val outline = cornerOutline(VectorJoin.MITER)

        assertTrue(outline.contains(Point(12f, -2f)))
        assertFalse(outline.contains(Point(10f, -2f)))
    }

    @Test fun roundJoinAddsArcPointBetweenOuterCornerEdges() {
        val outline = cornerOutline(VectorJoin.ROUND)

        assertTrue(outline.any { abs(it.x - 11.414214f) < .001f && abs(it.y + 1.414214f) < .001f })
    }

    @Test fun openPathPreservesSquareEndpointCaps() {
        val outline = basicStrokeOutline(
            PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f))),
            StrokeStyle(width = 4f, cap = VectorCap.SQUARE),
        )

        assertEquals(
            listOf(
                Point(0f, 2f), Point(10f, 2f),
                Point(12f, 2f), Point(12f, -2f),
                Point(10f, -2f), Point(0f, -2f),
                Point(-2f, -2f), Point(-2f, 2f),
            ),
            outline,
        )
    }

    @Test fun closedPathNeverAddsEndpointCaps() {
        val geometry = PathGeometry(
            listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f)),
            closed = true,
        )
        val butt = basicStrokeOutline(geometry, StrokeStyle(width = 4f, cap = VectorCap.BUTT))
        val square = basicStrokeOutline(geometry, StrokeStyle(width = 4f, cap = VectorCap.SQUARE))
        val round = basicStrokeOutline(geometry, StrokeStyle(width = 4f, cap = VectorCap.ROUND))

        assertEquals(butt, square)
        assertEquals(butt, round)
    }

    @Test fun duplicatePointsProduceOnlyFiniteOutlineCoordinates() {
        val outline = basicStrokeOutline(
            PathGeometry(
                listOf(
                    PathPoint(0f, 0f, 1f), PathPoint(0f, 0f, 1f),
                    PathPoint(10f, 0f, 1f), PathPoint(10f, 0f, 1f),
                ),
            ),
            StrokeStyle(width = 4f, cap = VectorCap.ROUND),
        )

        assertTrue(outline.isNotEmpty())
        assertTrue(outline.all { it.x.isFinite() && it.y.isFinite() })
    }

    private fun cornerOutline(join: VectorJoin): List<Point> = basicStrokeOutline(
        PathGeometry(
            listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f)),
        ),
        StrokeStyle(width = 4f, cap = VectorCap.BUTT, join = join),
    )
}
