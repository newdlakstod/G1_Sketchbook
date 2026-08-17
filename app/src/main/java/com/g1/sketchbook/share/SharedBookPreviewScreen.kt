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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.g1.sketchbook.brush.ToolbarDock
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.roundToInt

private enum class PreviewViewMode { GRID, MAXIMIZE }

// 최소화된 버튼바가 도킹된 가장자리를 따라 미끄러질 때, 화면 밖으로 나가지 않게 남겨두는 여유
// 폭/높이의 절반 정도(정확한 실측 대신 대략치 — 실제 화면의 같은 상수와 동일한 값).
private val CollapsedBarHalfExtent = 90.dp

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
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(ToolbarDock.BOTTOM) }
    var dragPx by remember { mutableStateOf(Offset.Zero) }
    var collapsedOffsetPx by remember { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "나가기")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Draw Together", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("3명과 함께 · 코드 DAY123", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SegGroup {
                SegChip("분할", mode == PreviewViewMode.GRID) { mode = PreviewViewMode.GRID }
                SegChip("최대화", mode == PreviewViewMode.MAXIMIZE) { mode = PreviewViewMode.MAXIMIZE }
            }
        }

        // 버튼바: 분할 패널 위에 떠 있는 오버레이(실제 SharedBookScreen과 동일 구조) — 패널 레이아웃
        // 자체는 그대로 두고, dock/드래그 손잡이를 미리보기에서도 확인할 수 있게 BoxWithConstraints로 감쌈.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val density2 = LocalDensity.current
            when (mode) {
                PreviewViewMode.GRID -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewPane("Hana", false, Color(0xFFF2DCCB), Modifier.weight(1f).fillMaxHeight())
                        PreviewPane("Joon", false, Color(0xFFDCE6D6), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MoreVert, "팝업에 표시할 참가자 선택")
                        Switch(checked = false, onCheckedChange = {})
                    }
                }
            }

            val horizontalDock = dock == ToolbarDock.TOP || dock == ToolbarDock.BOTTOM
            val barModifier = Modifier
                .align(dock.alignment())
                .let { if (!collapsed && horizontalDock) it.fillMaxWidth() else it }
                .offset {
                    if (collapsed) {
                        IntOffset(
                            if (horizontalDock) collapsedOffsetPx.roundToInt() else 0,
                            if (!horizontalDock) collapsedOffsetPx.roundToInt() else 0,
                        )
                    } else {
                        IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt())
                    }
                }
            BrushControls(
                brush, color, sizeDp, opacity, erasing,
                onBrush = { brush = it; erasing = false },
                onColor = { color = it; erasing = false },
                onSize = { sizeDp = it },
                onOpacity = { opacity = it },
                onToggleErase = { erasing = !erasing },
                onUndo = {}, onRedo = {}, onClear = {},
                onOpenPages = {},
                onRotate = {},
                locked = locked,
                onToggleLock = { locked = !locked },
                fullscreen = fullscreen,
                onToggleFullscreen = { fullscreen = !fullscreen },
                collapsed = collapsed,
                onToggleCollapsed = { collapsed = !collapsed },
                onDragBar = { d ->
                    if (collapsed) {
                        val delta = if (horizontalDock) d.x else d.y
                        val limitPx = with(density2) {
                            (if (horizontalDock) maxWidth else maxHeight).toPx() / 2f - CollapsedBarHalfExtent.toPx()
                        }
                        collapsedOffsetPx = (collapsedOffsetPx + delta).coerceIn(-limitPx, limitPx)
                    } else {
                        dragPx += d
                    }
                },
                onDragBarEnd = {
                    if (!collapsed) {
                        val cwPx = with(density2) { maxWidth.toPx() }; val chPx = with(density2) { maxHeight.toPx() }
                        val baseX = when (dock) { ToolbarDock.LEFT -> 0f; ToolbarDock.RIGHT -> cwPx; else -> cwPx / 2f }
                        val baseY = when (dock) { ToolbarDock.TOP -> 0f; ToolbarDock.BOTTOM -> chPx; else -> chPx / 2f }
                        val x = baseX + dragPx.x; val y = baseY + dragPx.y
                        val distances = mapOf(
                            ToolbarDock.LEFT to x, ToolbarDock.RIGHT to (cwPx - x),
                            ToolbarDock.TOP to y, ToolbarDock.BOTTOM to (chPx - y),
                        )
                        dock = distances.minByOrNull { it.value }?.key ?: dock
                        dragPx = Offset.Zero
                    }
                },
                dock = dock,
                modifier = barModifier,
            )
        }
    }
}

@Composable
private fun PreviewPane(title: String, accent: Boolean, paper: Color, modifier: Modifier) {
    PaneFrame(modifier, title, accent) {
        Box(Modifier.fillMaxSize().background(paper), contentAlignment = Alignment.Center) {
            Text("daymory", color = Color(0x44708068), fontSize = 18.sp)
        }
    }
}
