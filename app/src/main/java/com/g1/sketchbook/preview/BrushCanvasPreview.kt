package com.g1.sketchbook.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushPalette
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.ToolbarDock
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode
import kotlin.math.roundToInt

// 최소화된 버튼바가 도킹된 가장자리를 따라 미끄러질 때, 화면 밖으로 나가지 않게 남겨두는 여유
// 폭/높이의 절반 정도(정확한 실측 대신 대략치 — 실제 화면의 같은 상수와 동일한 값).
private val CollapsedBarHalfExtent = 90.dp

@Preview(
    name = "13 Personal canvas",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun BrushCanvasPreview() {
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(BrushPalette.first()) }
    var sizeDp by remember { mutableFloatStateOf(20f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    var eraserOpacity by remember { mutableFloatStateOf(100f) }
    var eraserBlur by remember { mutableFloatStateOf(0f) }
    val effectiveOpacity = if (erasing) eraserOpacity else opacity
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(ToolbarDock.BOTTOM) }
    var dragPx by remember { mutableStateOf(Offset.Zero) }
    var collapsedOffsetPx by remember { mutableStateOf(0f) }

    DaymoryTheme(mode = ThemeMode.LIGHT) {
        // 실제 화면과 같은 구조: 캔버스가 전체를 채우고, 버튼바는 그 위에 떠 있는 오버레이
        // (기존엔 Column으로 버튼바를 캔버스 아래 고정시켰는데, 그러면 dock/드래그 손잡이를
        // 미리보기에서 확인할 수 없어서 실제와 동일한 BoxWithConstraints 오버레이 구조로 바꿈).
        BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
            val density2 = LocalDensity.current
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    BrushView(context).apply {
                        initCanvas(390, 600)
                        drawEnabled = false
                    }
                },
                update = { view ->
                    view.brush = brush
                    view.color = color.toInt()
                    view.strokeSize = sizeDp
                    view.opacity = effectiveOpacity / 100f
                    view.erasing = erasing
                    view.eraserBlur = eraserBlur
                },
            )
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
                brush = brush,
                color = color,
                sizeDp = sizeDp,
                opacity = effectiveOpacity,
                erasing = erasing,
                onBrush = { brush = it },
                onColor = { color = it },
                onSize = { sizeDp = it },
                onOpacity = { if (erasing) eraserOpacity = it else opacity = it },
                onToggleErase = { erasing = !erasing },
                eraserBlur = eraserBlur,
                onEraserBlur = { eraserBlur = it },
                onUndo = {},
                onRedo = {},
                onClear = {},
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
