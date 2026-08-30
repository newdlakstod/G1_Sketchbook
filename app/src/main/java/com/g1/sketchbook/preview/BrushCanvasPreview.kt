package com.g1.sketchbook.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushPalette
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.ScreenControls
import com.g1.sketchbook.brush.ToolbarDock
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Preview(
    name = "13 Personal canvas",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun BrushCanvasPreview() {
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(BrushPalette.first()) }
    var sizeDp by remember { mutableFloatStateOf(20f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    var lassoActive by remember { mutableStateOf(false) }
    var fillActive by remember { mutableStateOf(false) }
    var lassoDeleteAt by remember { mutableStateOf<Offset?>(null) }
    var preStylusErasing by remember { mutableStateOf(false) }
    var preStylusLasso by remember { mutableStateOf(false) }
    var preStylusFill by remember { mutableStateOf(false) }
    var eraserOpacity by remember { mutableFloatStateOf(100f) }
    var eraserBlur by remember { mutableFloatStateOf(0f) }
    val effectiveOpacity = if (erasing) eraserOpacity else opacity
    var eyedropArmed by remember { mutableStateOf(false) }
    var eyedropPreview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(ToolbarDock.TOP) }
    var dragPx by remember { mutableStateOf(Offset.Zero) }
    var toolbarContainerRootPos by remember { mutableStateOf(Offset.Zero) }
    var pagesOpen by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var readModeOpen by remember { mutableStateOf(false) }
    // 2026-08-30: SketchbookCanvasScreen에 추가된 두 기능(올가미 선택 저장, 페이지 다운로드)을
    // 미리보기에도 같이 반영 — "실제 화면과 같은 구조" 원칙(위 BoxWithConstraints 주석 참고).
    var showDownloadDialog by remember { mutableStateOf(false) }

    DaymoryTheme(mode = ThemeMode.LIGHT) {
        // 실제 화면과 같은 구조: 캔버스가 전체를 채우고, 버튼바는 그 위에 떠 있는 오버레이
        // (기존엔 Column으로 버튼바를 캔버스 아래 고정시켰는데, 그러면 dock/드래그 손잡이를
        // 미리보기에서 확인할 수 없어서 실제와 동일한 BoxWithConstraints 오버레이 구조로 바꿈).
        BoxWithConstraints(
            Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .onGloballyPositioned { toolbarContainerRootPos = it.positionInRoot() },
        ) {
            val toolbarContainerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
            val toolbarContainerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    BrushView(context).apply {
                        initCanvas(390, 600)
                        view = this
                    }
                },
                update = { v ->
                    v.brush = brush
                    v.color = color.toInt()
                    v.strokeSize = sizeDp
                    v.opacity = effectiveOpacity / 100f
                    v.erasing = erasing
                    v.locked = locked
                    v.eraserBlur = eraserBlur
                    v.lassoMode = lassoActive
                    v.fillMode = fillActive
                    v.onLassoSelectionChanged = { has, x, y -> lassoDeleteAt = if (has) Offset(x, y) else null }
                    v.onStylusButtonChanged = { pressed ->
                        if (pressed) {
                            preStylusErasing = erasing; preStylusLasso = lassoActive; preStylusFill = fillActive
                            erasing = true; lassoActive = false; fillActive = false
                        } else {
                            erasing = preStylusErasing; lassoActive = preStylusLasso; fillActive = preStylusFill
                        }
                    }
                    v.eyedropArmed = eyedropArmed
                    v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
                    v.onEyedrop = { c -> color = (c.toLong() and 0xFFFFFFFFL); erasing = false; eyedropArmed = false; eyedropPreview = null }
                    v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
                    v.onToggleToolbars = { collapsed = !collapsed }
                },
            )
            eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
            lassoDeleteAt?.let { p -> com.g1.sketchbook.brush.LassoDeleteButton(p.x, p.y, onDelete = { view?.deleteLassoSelection() }) }
            // 실제 저장(saveToGallery)은 로컬 저장소를 건드리는 부작용이라 미리보기 규칙상 안 함 —
            // 버튼이 뜨고 눌리는지만 확인하는 자리(MockPagePanel/MockReadModeOverlay와 같은 이유).
            lassoDeleteAt?.let { p -> com.g1.sketchbook.brush.LassoSaveButton(p.x, p.y, onSave = {}) }
            fun barModifier(barDock: ToolbarDock, barCollapsed: Boolean, barDragPx: Offset) = Modifier
                .align(barDock.alignment())
                .let {
                    val horizontal = barDock == ToolbarDock.TOP || barDock == ToolbarDock.BOTTOM
                    if (!barCollapsed && horizontal) it.fillMaxWidth() else it
                }
                .offset { IntOffset(barDragPx.x.roundToInt(), barDragPx.y.roundToInt()) }
            BrushControls(
                brush = brush,
                color = color,
                sizeDp = sizeDp,
                opacity = effectiveOpacity,
                erasing = erasing,
                onBrush = { brush = it; erasing = false; lassoActive = false; fillActive = false },
                onColor = { color = it },
                onSize = { sizeDp = it },
                onOpacity = { if (erasing) eraserOpacity = it else opacity = it },
                onToggleErase = { erasing = !erasing; if (erasing) { lassoActive = false; fillActive = false } },
                eraserBlur = eraserBlur,
                onEraserBlur = { eraserBlur = it },
                onUndo = { view?.undo() },
                onRedo = { view?.redo() },
                onClear = { view?.clearCanvas() },
                eyedropArmed = eyedropArmed,
                onToggleEyedrop = { eyedropArmed = !eyedropArmed },
                lassoActive = lassoActive,
                onToggleLasso = { lassoActive = !lassoActive; if (lassoActive) { erasing = false; fillActive = false } },
                fillActive = fillActive,
                onToggleFill = { fillActive = !fillActive; if (fillActive) { erasing = false; lassoActive = false } },
                collapsed = collapsed,
                onToggleCollapsed = { collapsed = !collapsed },
                onDragBar = { d -> dragPx += d },
                onDragBarEnd = { targetDock ->
                    dock = targetDock
                    dragPx = Offset.Zero
                },
                containerRootPos = toolbarContainerRootPos,
                containerWidthPx = toolbarContainerWidthPx,
                containerHeightPx = toolbarContainerHeightPx,
                dock = dock,
                modifier = barModifier(dock, collapsed, dragPx),
            )
            // 화면버튼은 가로/세로 상관없이 항상 우측 상단에 고정된 확장 버튼(2026-08-20).
            ScreenControls(
                onOpenPages = { pagesOpen = true },
                onReadMode = { readModeOpen = true },
                onRotate = { view?.rotate() },
                locked = locked, onToggleLock = { locked = !locked },
                fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
                onDownload = { showDownloadDialog = true },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        if (pagesOpen) {
            MockPagePanel(currentPage, MAX_PAGES, onSelect = { currentPage = it }, onDismiss = { pagesOpen = false })
        }
        if (readModeOpen) {
            MockReadModeOverlay(onClose = { readModeOpen = false })
        }
        if (showDownloadDialog) {
            // 실제 다이얼로그(SketchbookScreens.kt)를 그대로 재사용 — onPlain/onTransparent는
            // 실제 저장 대신 그냥 닫기만 한다(로컬 저장소 안 건드리는 미리보기 규칙).
            com.g1.sketchbook.sketchbook.SketchbookDownloadDialog(
                onDismiss = { showDownloadDialog = false },
                onPlain = { showDownloadDialog = false },
                onTransparent = { showDownloadDialog = false },
            )
        }
    }
}

/** 실제 `ReadModeScreen`(readmode/ReadModeScreen.kt)은 `SketchbookRepository`+실제 그림 비트맵으로
 *  GLSurfaceView 페이지-커얼을 그리는데, Preview는 로컬 저장소를 건드리지 않는다는 규칙이라
 *  [MockPagePanel]과 같은 이유로 그대로 못 쓴다 — Interactive Preview에서 "화면 설정 → 읽기모드"
 *  버튼을 눌렀을 때 진입 자체는 되는지만 확인하는 자리표시자(실제 페이지 넘기기 손맛·GL 렌더링은
 *  실기기/에뮬레이터에서 확인). */
@Composable
private fun MockReadModeOverlay(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text("읽기모드 (미리보기 자리표시자)", color = Color.White)
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(40.dp)
                .clip(RoundedCornerShape(50)).background(Color(0x66000000)),
        ) {
            Icon(Icons.Filled.Close, "닫기", tint = Color.White)
        }
    }
}

/** 실제 `PagePanel`(sketchbook/PagePanel.kt)은 `SketchbookRepository`가 있어야 페이지 썸네일을
 *  읽는데, Preview는 로컬 저장소를 건드리지 않는다는 규칙(PROGRESS.md Decisions)이라 그대로 못
 *  쓴다 — 대신 같은 화면 얼개(페이지 이동 헤더 + 3열 그리드)만 흉내 낸, 저장소 없는 목업.
 *  Interactive Preview에서 "화면 설정 → 페이지" 버튼을 눌렀을 때 팝업 자체가 뜨는지만 확인하는
 *  용도(실제 썸네일·순서변경 드래그는 실제 화면에서 확인). */
@Composable
private fun MockPagePanel(currentPage: Int, pageCount: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp, tonalElevation = 3.dp, modifier = Modifier.width(292.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("페이지", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(320.dp),
                    ) {
                        items(pageCount) { i ->
                            val selected = i == currentPage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().bounceClick { onSelect(i) },
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(0.74f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(if (selected) 2.5.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${i + 1}", fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onDismiss) { Text("취소") }
                        TextButton(onClick = onDismiss) { Text("완료", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
