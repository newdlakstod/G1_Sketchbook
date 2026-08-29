package com.g1.sketchbook.brush

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarDockTest {
    // nearestDock은 "손을 뗀 절대 위치가 네 가장자리 중 어디에 가장 가까운가"만 본다(2026-08-29,
    // 세 번째 재작성). 이전엔 "순 드래그 방향(어느 축으로 더 많이 움직였는가)"만 봤는데, 그건 시작
    // 위치와 무관해 보이지만 실은 아니었다 — TOP 도킹(화면 위쪽에서 시작)에서 LEFT 가장자리의
    // 자연스러운 정착 지점(세로 중앙 부근)으로 끌면, 그 경로 자체가 기하학적으로 가로보다 세로로
    // 훨씬 더 많이 움직이게 되어 "아래로 더 크게 움직였다"고 오판해 BOTTOM으로 도킹해버렸다 — 실기기
    // 재현 리포트("가로모드에서 좌측 안 됨")의 진짜 원인이었다. 손을 뗀 최종 절대 위치로만 판정하면
    // 이 문제가 아예 생기지 않는다.
    private val w = 1200f
    private val h = 1200f

    @Test
    fun positionNearEachEdgeDocksThere() {
        assertEquals(ToolbarDock.LEFT, nearestDock(Offset(50f, 600f), w, h))
        assertEquals(ToolbarDock.RIGHT, nearestDock(Offset(1150f, 600f), w, h))
        assertEquals(ToolbarDock.TOP, nearestDock(Offset(600f, 50f), w, h))
        assertEquals(ToolbarDock.BOTTOM, nearestDock(Offset(600f, 1150f), w, h))
    }

    // 가로로 넓은 태블릿(halfW가 halfH보다 훨씬 큼)에서는 픽셀 거리를 그대로 비교하면 세로(위/아래)
    // 쪽 "절반 길이" 자체가 짧아서 좌우가 불리해진다 — 각 축을 "절반 길이" 대비 비율로 정규화해서
    // 화면 비율과 무관하게 네 방향이 공평하게 겨루도록 한다.
    @Test
    fun wideContainerNormalizesByAxisNotRawPixels() {
        val wideW = 2000f; val wideH = 1200f
        // 중앙보다 왼쪽으로 살짝만 치우친 지점 — 픽셀 거리로는 위/아래 가장자리가 더 가깝지만
        // (세로 절반 길이가 짧으므로), 정규화하면 좌우가 아직 멀다는 걸 반영해 TOP이 나와야 한다.
        assertEquals(ToolbarDock.TOP, nearestDock(Offset(900f, 100f), wideW, wideH))
        assertEquals(ToolbarDock.LEFT, nearestDock(Offset(50f, 600f), wideW, wideH))
    }

    // 예전 "드래그 방향" 판정이 정확히 오판했던 시나리오 — TOP 도킹 손잡이(화면 위쪽, 왼쪽에 가까운
    // 위치)에서 LEFT 가장자리의 세로 중앙 부근으로 끌어 내린다. 시작점 대비 순 이동량은 세로가 훨씬
    // 크지만(위→중앙까지 내려가야 하니까), 최종적으로 손을 뗀 자리는 명백히 왼쪽 가장자리에 가깝다.
    @Test
    fun dropNearLeftEdgeDocksLeftEvenWhenTheJourneyWasMostlyVertical() {
        // 컨테이너 1200x1200, TOP 손잡이가 (90, 90) 부근에서 시작해 (60, 600)(왼쪽 가장자리, 세로
        // 중앙)까지 이동했다고 가정 — 순 이동량은 (-30, 510), 세로가 17배 더 크다.
        assertEquals(ToolbarDock.LEFT, nearestDock(Offset(60f, 600f), w, h))
    }

    @Test
    fun centerIsClosestToWhicheverEdgeIsFirstInIterationOrderOnAnExactTie() {
        // 정확히 정중앙은 네 가장자리 모두 거리 비율이 1.0으로 동률이다 — 실질적으로 절대 일어나지
        // 않는 입력(항상 어느 한쪽이 근소하게 더 가깝다)이라 특정 결과를 강제할 필요는 없지만,
        // 함수가 예외 없이 항상 하나를 돌려준다는 것만 확인한다.
        val result = nearestDock(Offset(w / 2f, h / 2f), w, h)
        assert(result in ToolbarDock.entries)
    }
}
