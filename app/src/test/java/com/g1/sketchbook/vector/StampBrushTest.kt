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

    @Test fun tooShortCenterlineProducesNoStamps() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 100f, sizePx = 10f)
        val centerline = listOf(VectorPoint(0f, 0f, 4f), VectorPoint(1f, 0f, 4f))
        assertEquals(0, stampPolygons(profile, centerline).size)
    }

    @Test fun singlePointCenterlineProducesNoStamps() {
        val profile = StampBrushProfile("id1", "테스트", listOf(square), spacingPx = 10f, sizePx = 10f)
        assertEquals(0, stampPolygons(profile, listOf(VectorPoint(0f, 0f, 4f))).size)
    }
}
