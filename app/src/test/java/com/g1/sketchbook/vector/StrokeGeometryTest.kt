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

    @Test fun tinyBowtieBelowAreaThresholdProducesNoFill() {
        // 기존 bowtieShapeProducesOneTriangularFill과 똑같은 모양을 0.1배로 축소 — 넓이가
        // 25*0.01=0.25로 MIN_FILL_AREA(4)보다 작아서 무시돼야 한다(손떨림으로 생기는 의미 없는
        // 아주 작은 교차를 걸러내는 게 목적).
        val points = listOf(VectorPoint(0f, 0f, 1f), VectorPoint(1f, 1f, 1f), VectorPoint(0f, 1f, 1f), VectorPoint(1f, 0f, 1f))
        assertEquals(emptyList(), selfIntersectionFills(points))
    }

    @Test fun circleShapeOvershootingItsStartProducesOneLargeFill() {
        // 원을 근사하는 다각선을 시작 각도보다 살짝(5%) 더 돌아서 그린다 — 실제로 손으로 원을
        // 그릴 때 닫히는 지점이 정확히 시작점이 아니라 그 근처를 살짝 지나치는 상황과 비슷하다.
        // 결과는 원 넓이(πr²≈7854)에 가까운 큰 다각형 하나여야 한다(작은 오차 폴리곤이 아님).
        val steps = 60
        val radius = 50f
        val points = (0..steps).map { i ->
            val angle = 2 * Math.PI * i / steps * 1.05
            VectorPoint((radius * kotlin.math.cos(angle)).toFloat(), (radius * kotlin.math.sin(angle)).toFloat(), 1f)
        }
        val fills = selfIntersectionFills(points)
        assertEquals(1, fills.size)
        val polygon = fills[0]
        var sum = 0f
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            sum += a.x * b.y - b.x * a.y
        }
        val area = kotlin.math.abs(sum) / 2f
        assertTrue(area > radius * radius, "expected a large near-circle area, got $area")
    }

    @Test fun tinySpuriousCrossingDoesNotPreventLaterLargeLoopFromClosing() {
        // 이 수정이 노리는 바로 그 버그의 회귀 테스트 — 획 맨 앞에서 손떨림 수준의 아주 작은
        // 자기교차(넓이 0.0025)가 한 번 생기고, 한참 뒤에 사용자가 의도한 큰 폐곡선이 그 작은
        // 교차보다 앞선 세그먼트(여기선 세그먼트 1)를 다시 만나며 닫힌다.
        //  - 0..3: 0.1배로 축소한 tiny bowtie — 세그먼트 2가 세그먼트 0과 (0.05,0.05)에서 교차,
        //    넓이 0.0025 < MIN_FILL_AREA라 무시돼야 한다.
        //  - 4..6: 그 작은 영역에서 멀리 떨어져 크게 한 바퀴 돈다(중간에 다른 교차가 안 생기게
        //    원점 주변을 피해서 오른쪽 아래 → 오른쪽 위 → 왼쪽으로 이동).
        //  - 7: (0.05, 5)에서 (0.05, 0.08)로 내려오며 세그먼트 1(y=0.1의 윗변)을 (0.05,0.1)에서
        //    가로질러 큰 루프를 닫는다. y=0.08에서 멈추므로 세그먼트 0/2(둘 다 y=0.05에서 지남)는
        //    건드리지 않는다 — 즉 파트너는 반드시 세그먼트 1이어야만 한다.
        // 수정 전 코드는 작은 교차에서 startSeg를 2로 당겨버려 세그먼트 1이 후보에서 빠지고,
        // 결과가 [작은 삼각형](넓이 0.0025) 하나뿐이 된다 — 큰 루프가 조용히 안 채워진다.
        val points = listOf(
            VectorPoint(0f, 0f, 1f), VectorPoint(0.1f, 0.1f, 1f), VectorPoint(0f, 0.1f, 1f), VectorPoint(0.1f, 0f, 1f),
            VectorPoint(10f, -5f, 1f), VectorPoint(10f, 5f, 1f), VectorPoint(0.05f, 5f, 1f), VectorPoint(0.05f, 0.08f, 1f),
        )
        val fills = selfIntersectionFills(points)
        assertEquals(1, fills.size)
        val polygon = fills[0]
        var sum = 0f
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            sum += a.x * b.y - b.x * a.y
        }
        val area = kotlin.math.abs(sum) / 2f
        assertTrue(area > 20f, "expected the large intended loop to still be filled, got area $area")
    }
}
