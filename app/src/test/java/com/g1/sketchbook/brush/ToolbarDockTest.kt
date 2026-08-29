package com.g1.sketchbook.brush

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarDockTest {
    // nearestDock은 "손잡이가 컨테이너 안 어디에 있는지"(절대 위치 vs 네 가장자리 거리)가 아니라
    // "드래그 시작부터 지금까지 순 이동량이 어느 축으로, 어느 방향으로 더 컸는지"만 본다(2026-08-29,
    // 두 번째 재작성). 절대 위치 기반으로 두 차례(2026-08-29) 고쳐봤지만 실기기에서 "세로모드에서
    // 가로모드로 고정이 안된다"가 계속 재현됐다 — 펼친 세로(LEFT/RIGHT) 버튼바는 항목이 20개 가까이
    // 되어 verticalScroll이 필요할 만큼 길고, Surface 자신이 컨테이너 높이 전체로 늘어나면서 맨 위
    // 항목인 손잡이가 항상 "화면 위쪽 가장자리 근처"에도 동시에 있게 돼, "지금 위치가 어느 가장자리에
    // 가까운가"라는 기준 자체가 손잡이의 우연한 시작 위치에 계속 휘둘렸다. 방향 기반이면 손잡이가
    // 어디서 시작했는지와 완전히 무관해진다.
    private val minDragPx = 32f

    @Test
    fun negligibleMovementKeepsTheCurrentDock() {
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.TOP, Offset(2f, 3f), minDragPx))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.RIGHT, Offset.Zero, minDragPx))
    }

    @Test
    fun draggingPastTheThresholdDocksInThatDirection() {
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(-200f, 5f), minDragPx))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(200f, -5f), minDragPx))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.LEFT, Offset(5f, -200f), minDragPx))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.LEFT, Offset(-5f, 200f), minDragPx))
    }

    // 세로(LEFT/RIGHT) 도킹에서 가로(TOP/BOTTOM)로, 그리고 그 반대로도 건너갈 수 있어야 한다 —
    // 정확히 사용자가 재현한 "세로모드에서 가로모드로 고정이 안된다" 패턴.
    @Test
    fun crossesFromVerticalDockToHorizontalDockAndBack() {
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.LEFT, Offset(10f, -150f), minDragPx))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.LEFT, Offset(-10f, 150f), minDragPx))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.RIGHT, Offset(-10f, -150f), minDragPx))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.RIGHT, Offset(10f, 150f), minDragPx))
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(-150f, 10f), minDragPx))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.BOTTOM, Offset(150f, -10f), minDragPx))
    }

    // 손잡이가 화면 어디서 시작했든(컨테이너 크기와도 무관) 같은 순 이동량이면 항상 같은 방향으로
    // 도킹돼야 한다 — 절대 위치를 아예 안 쓰므로 시작 위치 편향이 원천적으로 없다.
    @Test
    fun resultIsIndependentOfContainerSizeAndStartingDock() {
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.LEFT, Offset(0f, -100f), minDragPx))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.RIGHT, Offset(0f, -100f), minDragPx))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.BOTTOM, Offset(0f, -100f), minDragPx))
    }

    @Test
    fun diagonalDragPicksTheDominantAxis() {
        // |dx| > |dy| → 수평
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(100f, 40f), minDragPx))
        // |dy| > |dx| → 수직
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.TOP, Offset(40f, 100f), minDragPx))
    }
}
