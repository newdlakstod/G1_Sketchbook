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

    @Test fun buttCapExplicitlyMatchesDefaultBehavior() {
        val points = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f))
        assertEquals(strokeOutline(points), strokeOutline(points, VectorCap.BUTT))
    }

    @Test fun squareCapExtendsBothEndsByHalfWidth() {
        val outline = strokeOutline(listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), VectorCap.SQUARE)
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

    @Test fun roundCapAddsSevenPointsPerEndForEightSteps() {
        val outline = strokeOutline(listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), VectorCap.ROUND)
        assertEquals(18, outline.size) // 2 (left) + 7 (end cap) + 2 (right reversed) + 7 (start cap)
    }

    @Test fun roundCapBulgesOutwardAtTheMidpoint() {
        val outline = strokeOutline(listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), VectorCap.ROUND)
        // 끝점(10,0) 쪽 반원의 정중앙(outward 방향 정확히)은 (12,0) 근처여야 한다 — left 2개 다음이
        // endCap이고, steps=8이면 그 4번째(index 3) 점이 t=π/2(outward 정중앙).
        val endCapMidpoint = outline[2 + 3]
        assertTrue(kotlin.math.abs(endCapMidpoint.x - 12f) < 0.01f)
        assertTrue(kotlin.math.abs(endCapMidpoint.y - 0f) < 0.01f)
    }

    @Test fun contentBoundsExpandsForStampBrushStrokes() {
        val square = listOf(Point(-0.5f, -0.5f), Point(0.5f, -0.5f), Point(0.5f, 0.5f), Point(-0.5f, 0.5f))
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 20f, sizePx = 40f)
        val strokes = listOf(VectorStroke(0L, listOf(VectorPoint(0f, 0f, 4f), VectorPoint(10f, 0f, 4f)), brushProfileId = "id1"))
        val bounds = contentBounds(strokes, mapOf("id1" to profile))!!
        assertTrue(bounds.minY < -20f)
    }

    @Test fun crossingSegmentsIntersectAtExpectedPoint() {
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f),
            VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f),
        )
        assertEquals(Point(5f, 5f), hit)
    }

    @Test fun parallelSegmentsDoNotIntersect() {
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f),
            VectorPoint(0f, 10f, 1f), VectorPoint(10f, 10f, 1f),
        )
        assertEquals(null, hit)
    }

    @Test fun segmentsThatWouldCrossOnlyIfExtendedDoNotIntersect() {
        // 두 선분이 놓인 직선끼리는 교차하지만, 그 교차점이 각 선분의 실제 구간(0~1) 밖에 있음.
        val hit = segmentIntersection(
            VectorPoint(0f, 0f, 1f), VectorPoint(1f, 1f, 1f),
            VectorPoint(5f, 0f, 1f), VectorPoint(5f, -1f, 1f),
        )
        assertEquals(null, hit)
    }

    @Test fun selfIntersectionFillsReturnsEmptyForFewerThanFourPoints() {
        assertEquals(emptyList(), selfIntersectionFills(listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f), VectorPoint(10f, 10f, 1f))))
    }

    @Test fun selfIntersectionFillsReturnsEmptyWhenNoCrossing() {
        val points = listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f))
        assertEquals(emptyList(), selfIntersectionFills(points))
    }

    @Test fun bowtieShapeProducesOneTriangularFill() {
        // (0,0)->(10,10)->(0,10)->(10,0): 첫 세그먼트(대각선 /)와 세번째 세그먼트(대각선 \)가
        // 정확히 (5,5)에서 교차 — 손 계산으로 확인된 값.
        val points = listOf(VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f))
        val fills = selfIntersectionFills(points)
        assertEquals(listOf(listOf(Point(5f, 5f), Point(10f, 10f), Point(0f, 10f))), fills)
    }

    @Test fun twoSequentialBowtiesEachProduceTheirOwnFill() {
        // 첫 4점이 bowtie 하나(교차 (5,5)), 그다음 4점이 +100 평행이동한 두번째 bowtie(교차 (105,5)).
        val points = listOf(
            VectorPoint(0f, 0f, 1f), VectorPoint(10f, 10f, 1f), VectorPoint(0f, 10f, 1f), VectorPoint(10f, 0f, 1f),
            VectorPoint(100f, 0f, 1f), VectorPoint(110f, 10f, 1f), VectorPoint(100f, 10f, 1f), VectorPoint(110f, 0f, 1f),
        )
        val fills = selfIntersectionFills(points)
        assertEquals(
            listOf(
                listOf(Point(5f, 5f), Point(10f, 10f), Point(0f, 10f)),
                listOf(Point(105f, 5f), Point(110f, 10f), Point(100f, 10f)),
            ),
            fills,
        )
    }
}
