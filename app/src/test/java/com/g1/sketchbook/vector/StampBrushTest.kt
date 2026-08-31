package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StampBrushTest {
    private val square = listOf(Point(-0.5f, -0.5f), Point(0.5f, -0.5f), Point(0.5f, 0.5f), Point(-0.5f, 0.5f))

    @Test fun roundTripsThroughJson() {
        val profile = StampBrushProfile("id1", "내 브러시", listOf(square), spacingPx = 20f, sizePx = 16f)
        assertEquals(profile, stampBrushProfileFromJson(profile.toJson()))
    }

    @Test fun malformedJsonReturnsNull() {
        assertNull(stampBrushProfileFromJson("not json"))
    }

    @Test fun stampPolygonsPlacesOneStampPerSpacingInterval() {
        // 길이 100인 수평선, 간격 25 -> 0,25,50,75,100 다섯 지점에 찍힘.
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 25f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        val stamped = stampPolygons(profile, centerline)
        assertEquals(5, stamped.size)
    }

    @Test fun stampIsScaledToSizePx() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        val stamped = stampPolygons(profile, centerline)
        val first = stamped[0]
        val w = first.maxOf { it.x } - first.minOf { it.x }
        assertTrue(kotlin.math.abs(w - 10f) < 0.01f) // 정규화된 사각형(폭 1) * sizePx(10) = 10
    }

    @Test fun stampIsCenteredOnCenterlinePoint() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(50f, 30f, 4f), VectorPoint(150f, 30f, 4f))
        val stamped = stampPolygons(profile, centerline)
        val first = stamped[0]
        val cx = (first.minOf { it.x } + first.maxOf { it.x }) / 2f
        val cy = (first.minOf { it.y } + first.maxOf { it.y }) / 2f
        assertTrue(kotlin.math.abs(cx - 50f) < 0.01f && kotlin.math.abs(cy - 30f) < 0.01f)
    }

    @Test fun shortCenterlineStillProducesOneStampAtTheStart() {
        // 총 길이가 spacingPx보다 짧아도 완전히 안 보이면(투명 유령 획) 안 되므로, 최소 한 개는
        // 시작 지점에 찍혀야 한다(2026-08-31 최종 리뷰에서 수정 — 예전엔 아예 안 찍혔음).
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(1f, 0f, 4f))
        assertEquals(1, stampPolygons(profile, centerline).size)
    }

    @Test fun singlePointCenterlineProducesNoStamps() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 10f, sizePx = 10f)
        assertEquals(0, stampPolygons(profile, listOf(VectorPoint(0f, 0f, 4f))).size)
    }

    @Test fun nonPositiveSpacingProducesNoStampsInsteadOfHanging() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 0f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        assertEquals(0, stampPolygons(profile, centerline).size)
    }

    @Test fun stampRotatesToMatchVerticalCenterlineTangent() {
        // 세로 방향 중심선 -> 회전 행렬이 항등행렬이 아닌 실제로 작동하는지 확인. 정사각형이 아니라
        // 가로로 긴 직사각형(폭 1, 높이 0.2)을 써서, 세로 중심선을 따라가면 그 직사각형이 세로로
        // 길게 서 있어야 한다(각도 계산이 틀렸다면 여전히 가로로 누워 있을 것).
        val wideRect = listOf(Point(-0.5f, -0.1f), Point(0.5f, -0.1f), Point(0.5f, 0.1f), Point(-0.5f, 0.1f))
        val profile = StampBrushProfile("id1", "테스트", listOf(wideRect), spacingPx = 100f, sizePx = 20f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(0f, 100f, 4f))
        val stamped = stampPolygons(profile, centerline)
        val first = stamped[0]
        val w = first.maxOf { it.x } - first.minOf { it.x }
        val h = first.maxOf { it.y } - first.minOf { it.y }
        assertTrue(h > w, "expected the stamp to rotate to vertical (w=$w, h=$h)")
    }

    @Test fun multiShapeProfileEmitsOneOutputPolygonPerShapePerStampPosition() {
        val triangle = listOf(Point(-0.5f, 0.5f), Point(0.5f, 0.5f), Point(0f, -0.5f))
        val profile = StampBrushProfile("id1", "테스트", listOf(square, triangle), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(100f, 0f, 4f))
        val stamped = stampPolygons(profile, centerline)
        assertEquals(4, stamped.size)
    }
}
