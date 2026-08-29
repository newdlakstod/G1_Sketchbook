package com.g1.sketchbook.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.ScreenControls
import com.g1.sketchbook.brush.ToolbarDock
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.brush.nearestDock
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.roundToInt

private enum class PreviewViewMode { GRID, MAXIMIZE }

/** Data-free rendering of the shared-canvas chrome for Android Studio Preview. */
@Composable
internal fun SharedBookPreviewScreen(startMaximized: Boolean) {
    var mode by remember(startMaximized) {
        mutableStateOf(if (startMaximized) PreviewViewMode.MAXIMIZE else PreviewViewMode.GRID)
    }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(0xFF1E2D4CL) }
    var sizeDp by remember { mutableFloatStateOf(Dimens.Brush.penWidth) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    var eraserOpacity by remember { mutableFloatStateOf(100f) }
    var eraserBlur by remember { mutableFloatStateOf(0f) }
    val effectiveOpacity = if (erasing) eraserOpacity else opacity
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(ToolbarDock.TOP) }
    var dragPx by remember { mutableStateOf(Offset.Zero) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 뒤로가기 버튼·헤더 바 없이 캔버스에 화면을 최대한 내준다(실제 SharedBookScreen과 동일,
        // 2026-08-20) — 스케치북 이름은 아래에서 참가자 캔버스 위로 겹쳐 뜨는 라벨로 대신 그린다.
        // 바깥 여백·칸 사이 간격 없음 — 구분은 각 칸의 테두리 선 하나로만.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density2 = LocalDensity.current
            when (mode) {
                PreviewViewMode.GRID -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        PreviewPane("Hana", false, Color(0xFFF2DCCB), Modifier.weight(1f).fillMaxHeight())
                        PreviewPane("Joon", false, Color(0xFFDCE6D6), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        PreviewPane("Mina", false, Color(0xFFD8E2EB), Modifier.weight(1f).fillMaxHeight())
                        PreviewPane("나 · Minjun", true, Color(0xFFF7F1E4), Modifier.weight(1f).fillMaxHeight())
                    }
                }
                PreviewViewMode.MAXIMIZE -> Box(Modifier.fillMaxSize()) {
                    PreviewPane("나 · Minjun", true, Color(0xFFF7F1E4), Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp).size(width = 130.dp, height = 170.dp)
                            .shadow(8.dp, RectangleShape).background(MaterialTheme.colorScheme.background),
                    ) {
                        PreviewPane("Hana", false, Color(0xFFF2DCCB), Modifier.fillMaxSize())
                    }
                    // top padding 64dp: 화면버튼(ScreenControls)이 항상 우측 상단에 고정돼 있어
                    // 이 참가자 선택 줄과 겹치지 않도록 그 아래로 내림(2026-08-20).
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MoreVert, "팝업에 표시할 참가자 선택")
                        Switch(checked = false, onCheckedChange = {})
                    }
                }
            }

            // 스케치북 이름 — 참가자 캔버스 맨 위에 떠서 겹치는 작은 라벨(실제 화면과 동일 구조).
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Draw Together", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            fun barModifier(barDock: ToolbarDock, barCollapsed: Boolean, barDragPx: Offset) = Modifier
                .align(barDock.alignment())
                .let {
                    val horizontal = barDock == ToolbarDock.TOP || barDock == ToolbarDock.BOTTOM
                    if (!barCollapsed && horizontal) it.fillMaxWidth() else it
                }
                .offset { IntOffset(barDragPx.x.roundToInt(), barDragPx.y.roundToInt()) }
            BrushControls(
                brush, color, sizeDp, effectiveOpacity, erasing,
                onBrush = { brush = it; erasing = false },
                onColor = { color = it; erasing = false },
                onSize = { sizeDp = it },
                onOpacity = { if (erasing) eraserOpacity = it else opacity = it },
                onToggleErase = { erasing = !erasing },
                eraserBlur = eraserBlur,
                onEraserBlur = { eraserBlur = it },
                onUndo = {}, onRedo = {}, onClear = {},
                collapsed = collapsed,
                onToggleCollapsed = { collapsed = !collapsed },
                onDragBar = { d -> dragPx += d },
                onDragBarEnd = {
                    val minDragPx = with(density2) { com.g1.sketchbook.brush.DockSwitchMinDrag.toPx() }
                    dock = nearestDock(dock, dragPx, minDragPx)
                    dragPx = Offset.Zero
                },
                dock = dock,
                modifier = barModifier(dock, collapsed, dragPx),
            )
            // 우측 상단: 분할/최대화 아이콘 토글 + 화면버튼, 실제 화면과 동일하게 한 줄에.
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                ModeToggleButton(mode == PreviewViewMode.GRID) {
                    mode = if (mode == PreviewViewMode.GRID) PreviewViewMode.MAXIMIZE else PreviewViewMode.GRID
                }
                ScreenControls(
                    onOpenPages = {},
                    onRotate = {},
                    locked = locked, onToggleLock = { locked = !locked },
                    fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(title: String, accent: Boolean, paper: Color, modifier: Modifier) {
    PaneFrame(modifier, title, accent) {
        Box(Modifier.fillMaxSize().background(paper), contentAlignment = Alignment.Center) {
            Text("Daymory", color = Color(0x44708068), fontSize = 18.sp)
        }
    }
}
