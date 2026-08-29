package com.g1.sketchbook.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.ToolbarDock
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.brush.nearestDock
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode
import kotlin.math.roundToInt

// 실제 그리기 화면(SketchbookCanvasScreen 등)엔 @Preview가 없어서, 버튼바 좌/우 도킹처럼 드래그로
// 확인해야 하는 동작은 지금까지 Interactive Preview로 아예 테스트할 방법이 없었다(2026-08-29,
// "프리뷰로 동작해봐도 안 붙어" — 사실 프리뷰 자체가 없었던 것). SketchbookCanvasScreen과 완전히
// 같은 배선(barModifier, onDragBarStart/onDragBar/onDragBarEnd, nearestDock의 절대 위치 방식,
// RIGHT+펼침 시 TopEnd 정렬)을 그대로 옮겨와서, BrushView(실제 캔버스)나 SketchbookRepository 없이도
// 손잡이 드래그 → 도킹 로직만 독립적으로 켜볼 수 있게 했다. 드래그 중 실시간으로 손가락의 컨테이너
// 내 절대 위치·거리 계산·최종 도킹 결과를 화면에 그대로 찍어준다.
@Preview(name = "20 Brush toolbar drag-to-dock", showBackground = true, widthDp = 800, heightDp = 500)
@Composable
private fun BrushToolbarDockPreview() {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        var brush by remember { mutableStateOf(BrushType.PEN) }
        var color by remember { mutableStateOf(0xFF1E2D4CL) }
        var sizeDp by remember { mutableFloatStateOf(10f) }
        var opacity by remember { mutableFloatStateOf(100f) }
        var erasing by remember { mutableStateOf(false) }
        var toolbarCollapsed by remember { mutableStateOf(false) }
        var toolbarDock by remember { mutableStateOf(ToolbarDock.TOP) }
        var toolbarDragPx by remember { mutableStateOf(Offset.Zero) }
        var toolbarDragOrigin by remember { mutableStateOf(Offset.Zero) }
        var containerRootPos by remember { mutableStateOf(Offset.Zero) }
        var lastResult by remember { mutableStateOf("아직 드래그 안 함") }

        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { containerRootPos = it.positionInRoot() },
        ) {
            val density2 = LocalDensity.current
            fun barModifier(dock: ToolbarDock, collapsed: Boolean, dragPx: Offset) = Modifier
                .align(if (dock == ToolbarDock.RIGHT && !collapsed) Alignment.TopEnd else dock.alignment())
                .let {
                    val horizontal = dock == ToolbarDock.TOP || dock == ToolbarDock.BOTTOM
                    if (!collapsed && horizontal) it.fillMaxWidth() else it
                }
                .offset { IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
            BrushControls(
                brush, color, sizeDp, opacity, erasing,
                onBrush = { brush = it; erasing = false },
                onColor = { color = it; erasing = false },
                onSize = { sizeDp = it },
                onOpacity = { opacity = it },
                onToggleErase = { erasing = !erasing },
                onUndo = {}, onRedo = {}, onClear = {},
                collapsed = toolbarCollapsed, onToggleCollapsed = { toolbarCollapsed = !toolbarCollapsed },
                onDragBarStart = { absoluteStart -> toolbarDragOrigin = absoluteStart },
                onDragBar = { d -> toolbarDragPx += d },
                onDragBarEnd = {
                    val cwPx = with(density2) { maxWidth.toPx() }; val chPx = with(density2) { maxHeight.toPx() }
                    val before = toolbarDock
                    val posInContainer = toolbarDragOrigin + toolbarDragPx - containerRootPos
                    val after = nearestDock(before, posInContainer, cwPx, chPx)
                    lastResult = "container=${cwPx.roundToInt()}x${chPx.roundToInt()}  pos=(${posInContainer.x.roundToInt()},${posInContainer.y.roundToInt()})  $before → $after"
                    toolbarDock = after
                    toolbarDragPx = Offset.Zero
                },
                dock = toolbarDock,
                modifier = barModifier(toolbarDock, toolbarCollapsed, toolbarDragPx),
            )
            // 디버그 전용 — 드래그 결과(손가락의 컨테이너 내 절대 위치, 컨테이너 크기, 이전/새 도킹)를
            // 그대로 찍어서 재현 리포트를 숫자로 직접 확인할 수 있게 함.
            val livePos = toolbarDragOrigin + toolbarDragPx - containerRootPos
            Text(
                "dock=$toolbarDock  live pos=(${livePos.x.roundToInt()},${livePos.y.roundToInt()})\n$lastResult",
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                fontSize = 11.sp,
            )
        }
    }
}
