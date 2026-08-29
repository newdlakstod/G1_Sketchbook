package com.g1.sketchbook.brush

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarDockTest {
    // nearestDock은 "화면 중앙 기준 누적 델타"가 아니라 "손가락이 컨테이너 안 어디에 있는지"(왼쪽/위
    // 모서리가 (0,0)) 절대 위치를 받는다(2026-08-29) — 예전 델타 방식은 손잡이의 실제 시작 위치가
    // 도킹 방향마다 달라서(TOP은 화면 왼쪽 가장자리 근처, LEFT/RIGHT는 세로 중앙 근처) 한쪽으로만
    // 드래그가 잘 먹히는 비대칭이 있었다.

    // 가로로 넓은 태블릿(2000×1200)에서, 픽셀 거리를 그대로 비교하면 세로(위/아래) 쪽 "절반 길이"가
    // 훨씬 짧아서 좌우로 꽤 크게 끌어도 위/아래가 계속 더 가깝게 이겨버려 좌우 도킹에 도달할 수 없던
    // 회귀 — 정규화 후에는 화면 끝 쪽 위치면 RIGHT/LEFT까지 넘어가야 한다.
    @Test
    fun wideTabletLandscapeCanStillReachLeftAndRight() {
        val w = 2000f; val h = 1200f
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(1650f, 600f), w, h))
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(350f, 600f), w, h))
    }

    @Test
    fun negligibleMovementKeepsTheCurrentDock() {
        val w = 2000f; val h = 1200f
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.TOP, Offset(1000f, 600f), w, h))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.RIGHT, Offset(1002f, 601f), w, h))
    }

    // LEFT/RIGHT 도킹은 손잡이가 자기 도킹된 가장자리에서 불과 수십 px 떨어진 곳에서 시작한다(세로
    // 버튼바의 손잡이는 목록 맨 위 항목이라 위/아래로는 컨테이너 중앙 근처이지만, 좌/우로는 화면
    // 가장자리에 바짝 붙어 있다). 예전 2%p 고정 마진은 이 "이미 매우 작은 현재 거리"에서 다시 2%p를
    // 더 빼야 했기 때문에, TOP/BOTTOM으로 건너가려면 손잡이를 반대쪽 모서리 몇 px 안까지 몰아넣어야
    // 할 만큼 문턱이 비현실적으로 높았다 — 실사용자가 왼쪽 중간에서 위쪽으로 자연스럽게(정확히 수직이
    // 아니어도) 드래그하면 건너가야 정상이다(2026-08-29, "세로모드에서 가로모드로 고정이 안된다"
    // 재현 리포트).
    @Test
    fun edgeHuggingHandleStillReachesThePerpendicularAxisOnANaturalDiagonalDrag() {
        val w = 2000f; val h = 1200f
        // LEFT 도킹, 손잡이가 왼쪽 가장자리에서 30px, 세로로는 컨테이너 중앙보다 위(펼친 버튼바 안
        // 맨 위 항목)인 대략적인 실측 위치에서 시작.
        val leftHandleRest = Offset(30f, 375f)
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.LEFT, leftHandleRest, w, h))
        // 위쪽으로 자연스럽게(완전한 수직이 아니라 약간 오른쪽으로 흘러도) 드래그하면 TOP으로 건너가야 한다.
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.LEFT, Offset(400f, 50f), w, h))
    }

    @Test
    fun positionNearEachEdgeDocksThere() {
        val w = 1200f; val h = 1200f
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(100f, 600f), w, h))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(1100f, 600f), w, h))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.TOP, Offset(600f, 1100f), w, h))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.BOTTOM, Offset(600f, 100f), w, h))
    }

    // 손잡이가 도킹 방향마다 다른 위치에서 시작해도(TOP=화면 왼쪽 근처, RIGHT=세로 중앙 근처) 같은
    // 절대 위치로 끌면 같은 결과가 나와야 한다 — "TOP에서는 LEFT로 못 가는데 RIGHT에서는 LEFT로 잘
    // 간다"던 재현 리포트(2026-08-29)를 직접 겨냥한 회귀 테스트.
    @Test
    fun reachingTheSameAbsolutePositionDocksTheSameWayRegardlessOfStartingDock() {
        val w = 2000f; val h = 1200f
        val nearLeftEdge = Offset(120f, 600f)
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, nearLeftEdge, w, h))
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.RIGHT, nearLeftEdge, w, h))

        val nearTopEdge = Offset(1000f, 80f)
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.LEFT, nearTopEdge, w, h))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.RIGHT, nearTopEdge, w, h))
    }
}
