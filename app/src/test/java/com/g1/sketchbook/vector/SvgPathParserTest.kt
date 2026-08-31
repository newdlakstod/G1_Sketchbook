package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgPathParserTest {
    private fun assertPointsClose(expected: Point, actual: Point, tolerance: Float = 0.01f) {
        assertTrue(abs(expected.x - actual.x) < tolerance && abs(expected.y - actual.y) < tolerance,
            "expected $expected but was $actual")
    }

    @Test fun moveAndLineAbsolute() {
        val pts = parseSvgPath("M0,0 L10,0 L10,10 Z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun lineRelative() {
        val pts = parseSvgPath("M0,0 l10,0 l0,10 z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun horizontalAndVerticalLines() {
        val pts = parseSvgPath("M0,0 H10 V10 H0 Z")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f), Point(0f, 10f), Point(0f, 0f)), pts)
    }

    @Test fun repeatedCommandWithoutRepeatingLetter() {
        // "L10,0 10,10"는 두 번째 좌표쌍 앞에 L이 생략된 것 — SVG 스펙상 같은 명령이 반복된다.
        val pts = parseSvgPath("M0,0 L10,0 10,10")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f)), pts)
    }

    @Test fun adjacentNumbersWithoutSeparators() {
        // "1.2.3" -> "1.2"와 ".3" 두 숫자(소수점이 두 번째부터 새 숫자 시작을 뜻함) — 압축된 SVG에서 흔함.
        val pts = parseSvgPath("M0,0L1.2.3,0")
        assertEquals(listOf(Point(0f, 0f), Point(1.2f, 0.3f)), pts)
    }

    @Test fun cubicBezierEndsAtFinalControlPoint() {
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0", steps = 4)
        assertEquals(4, pts.size - 1) // moveTo 점 1개 + steps개
        assertPointsClose(Point(10f, 0f), pts.last())
    }

    @Test fun cubicBezierMidpointMatchesFormula() {
        // t=0.5에서의 3차 베지어 공식값과 비교(대칭 케이스라 y는 7.5가 되어야 함)
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0", steps = 2)
        assertPointsClose(Point(5f, 7.5f), pts[1])
    }

    @Test fun quadraticBezierEndsAtFinalPoint() {
        val pts = parseSvgPath("M0,0 Q5,10 10,0", steps = 4)
        assertPointsClose(Point(10f, 0f), pts.last())
        assertPointsClose(Point(5f, 5f), pts[2]) // t=0.5 지점, 대칭이라 y는 (0+2*10+0)/4=5
    }

    @Test fun smoothCubicReflectsPreviousControlPoint() {
        // 첫 C의 두번째 컨트롤포인트(10,10)를 S가 반사해서 이어받아야 부드럽게 이어짐 —
        // 정확한 좌표보다 "끝점까지 도달"과 "점 개수"만 확인.
        val pts = parseSvgPath("M0,0 C0,10 10,10 10,0 S20,-10 20,0", steps = 4)
        assertPointsClose(Point(20f, 0f), pts.last())
    }

    @Test fun arcFromZeroToTenStaysNearRadius() {
        // 반지름 5인 원호, (0,0)에서 (10,0)까지 — 중심은 (5,0) 근처여야 하고, 중간 지점들은
        // 중심에서 반지름(5)만큼 떨어져 있어야 한다.
        val pts = parseSvgPath("M0,0 A5,5 0 0 1 10,0", steps = 8)
        assertPointsClose(Point(10f, 0f), pts.last())
        for (p in pts) {
            val d = kotlin.math.sqrt((p.x - 5f) * (p.x - 5f) + p.y * p.y)
            assertTrue(abs(d - 5f) < 0.05f, "point $p not on radius-5 circle around (5,0)")
        }
    }

    @Test fun zeroRadiusArcDegeneratesToLine() {
        val pts = parseSvgPath("M0,0 A0,0 0 0 1 10,10")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 10f)), pts)
    }

    @Test fun unknownCommandStopsGracefullyWithPartialResult() {
        val pts = parseSvgPath("M0,0 L10,0 X999,999")
        assertEquals(listOf(Point(0f, 0f), Point(10f, 0f)), pts)
    }

    @Test fun emptyPathReturnsEmptyList() {
        assertEquals(emptyList(), parseSvgPath(""))
    }
}
