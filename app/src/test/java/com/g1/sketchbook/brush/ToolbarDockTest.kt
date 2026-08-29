package com.g1.sketchbook.brush

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarDockTest {
    // 가로로 넓은 태블릿(2000×1200)에서, 픽셀 거리를 그대로 비교하면 세로(위/아래) 쪽 "절반 길이"가
    // 훨씬 짧아서 좌우로 꽤 크게 끌어도 위/아래가 계속 더 가깝게 이겨버려 좌우 도킹에 도달할 수 없던
    // 회귀(2026-08-29) — 정규화 후에는 같은 드래그로 RIGHT까지 넘어가야 한다.
    @Test
    fun wideTabletLandscapeCanStillReachLeftAndRight() {
        val w = 2000f; val h = 1200f
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(350f, 0f), w, h))
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(-350f, 0f), w, h))
    }

    @Test
    fun negligibleDragKeepsTheCurrentDock() {
        val w = 2000f; val h = 1200f
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.TOP, Offset.Zero, w, h))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.RIGHT, Offset(2f, 1f), w, h))
    }

    @Test
    fun largeDragTowardEachEdgeDocksThere() {
        val w = 1200f; val h = 1200f
        assertEquals(ToolbarDock.LEFT, nearestDock(ToolbarDock.TOP, Offset(-500f, 0f), w, h))
        assertEquals(ToolbarDock.RIGHT, nearestDock(ToolbarDock.TOP, Offset(500f, 0f), w, h))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(ToolbarDock.TOP, Offset(0f, 500f), w, h))
        assertEquals(ToolbarDock.TOP, nearestDock(ToolbarDock.BOTTOM, Offset(0f, -500f), w, h))
    }
}
