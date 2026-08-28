package com.g1.sketchbook.brush

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.g1.sketchbook.R
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 버튼바를 붙여둘 화면 가장자리 — 길게 눌러 드래그하면 놓은 위치에서 가장 가까운 쪽으로 붙는다. */
enum class ToolbarDock { TOP, BOTTOM, LEFT, RIGHT }

fun ToolbarDock.alignment(): Alignment = when (this) {
    ToolbarDock.TOP -> Alignment.TopCenter
    ToolbarDock.BOTTOM -> Alignment.BottomCenter
    ToolbarDock.LEFT -> Alignment.CenterStart
    ToolbarDock.RIGHT -> Alignment.CenterEnd
}

/** 버튼바를 드래그로 놓은 위치에서 가장 가까운 화면 가장자리를 계산 — 펼친 상태든 최소화 상태든
 *  동일하게 적용해 자유 2D 드래그 + 재도킹이 되게 한다(2026-08-20, 예전엔 최소화 상태만 지금
 *  도킹된 가장자리의 축으로만 밀리는 특수 케이스였는데 통일함). [dragPx]는 [current] 기준 드래그
 *  누적량, [containerWidthPx]/[containerHeightPx]는 바가 떠 있는 영역(px) 크기. */
fun nearestDock(current: ToolbarDock, dragPx: Offset, containerWidthPx: Float, containerHeightPx: Float): ToolbarDock {
    val baseX = when (current) { ToolbarDock.LEFT -> 0f; ToolbarDock.RIGHT -> containerWidthPx; else -> containerWidthPx / 2f }
    val baseY = when (current) { ToolbarDock.TOP -> 0f; ToolbarDock.BOTTOM -> containerHeightPx; else -> containerHeightPx / 2f }
    val x = baseX + dragPx.x; val y = baseY + dragPx.y
    val distances = mapOf(
        ToolbarDock.LEFT to x, ToolbarDock.RIGHT to (containerWidthPx - x),
        ToolbarDock.TOP to y, ToolbarDock.BOTTOM to (containerHeightPx - y),
    )
    return distances.minByOrNull { it.value }?.key ?: current
}

val BrushPalette = listOf(
    0xFF1E2D4CL, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

private val HueWheel = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

// 버튼 탭 영역 크기 — 아이콘/스와치 자체보다 살짝 크게 잡아 손가락으로 누르기 편하게 하는 값.
// Arrangement.spacedBy로 버튼 간격을 더 좁히면(0 이하 포함) 이 탭 영역끼리 겹칠 수 있는데,
// 겹쳐도 터치는 정상 동작하도록 의도된 것 — 실제 보이는 간격은 이 값과 spacedBy 둘이 함께 정한다.
private val ButtonTapSize = 30.dp

// 즐겨찾기 그리드 — 카드 폭에 실제로 몇 칸이 들어가는지 계산해서 항상 3줄을 채운다("폭에 7개
// 들어가면 21개, 8개 들어가면 24개" 식, 2026-08-26). 카드 폭이 바뀌면 총 개수도 이 값들 그대로
// 다시 계산되므로, 저장 쪽(SessionStore.FavoritesCount)도 지금 카드 폭 기준 결과(7×3=21)에 맞춰뒀다.
private val FavoriteSwatchSize = 24.dp
private val FavoriteSwatchGap = 8.dp
private const val FavoriteGridRows = 3

/** [favorites]를 카드 폭에 맞는 칸 수 × [FavoriteGridRows]줄로 배치 — 칸 하나하나의 생김새/동작은
 *  호출부가 [cell]로 그린다(선택만 하는 미리보기용과, 다시 탭하면 편집 팝업이 뜨는 관리용이 서로
 *  다르게 그려야 해서 여기서는 배치만 책임진다). */
@Composable
private fun FavoritesGrid(favorites: List<Long>, modifier: Modifier = Modifier, cell: @Composable (index: Int, color: Long) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ((maxWidth + FavoriteSwatchGap) / (FavoriteSwatchSize + FavoriteSwatchGap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(FavoriteSwatchGap)) {
            for (row in 0 until FavoriteGridRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(FavoriteSwatchGap)) {
                    for (col in 0 until columns) {
                        val i = row * columns + col
                        cell(i, favorites.getOrElse(i) { BrushPalette[i % BrushPalette.size] })
                    }
                }
            }
        }
    }
}

/** Single-row floating dock for brush/color/eraser tools. Page/rotate/lock/fullscreen live in the
 *  separate [ScreenControls] surface now — this one only keeps `onBack` (used by the diary editor). */
@Composable
fun BrushControls(
    brush: BrushType, color: Long, sizeDp: Float, opacity: Float, erasing: Boolean,
    onBrush: (BrushType) -> Unit, onColor: (Long) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    onToggleErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit,
    /** 지우개 전용 경계 블러(부드러움) 정도 — 브러시에는 없는 지우개만의 슬라이더. 0이면 또렷한 경계. */
    eraserBlur: Float = 0f,
    onEraserBlur: (Float) -> Unit = {},
    onBack: (() -> Unit)? = null,
    favorites: List<Long> = BrushPalette.take(5),
    onEditFavorite: (Int, Long) -> Unit = { _, _ -> },
    eyedropArmed: Boolean = false,
    onToggleEyedrop: () -> Unit = {},
    /** 올가미(라소) 선택 도구 — 켜져 있으면 손가락으로 영역을 그려 선택하고, 안쪽을 드래그해서
     *  옮길 수 있다. 선택을 지우는 버튼은 툴바가 아니라 [LassoDeleteButton]으로 선택 영역 바로
     *  위에 뜬다(호출부가 BrushView.onLassoSelectionChanged를 받아 직접 띄움). */
    lassoActive: Boolean = false,
    onToggleLasso: () -> Unit = {},
    /** 페인트통(채우기) 도구 — 켜져 있으면 탭한 지점과 이어진 같은 색 영역을 현재 색으로 단색 채운다. */
    fillActive: Boolean = false,
    onToggleFill: () -> Unit = {},
    /** 버튼바 최소화: 켜져 있으면 현재 브러시·색상 두 개만 보이는 작은 형태로 줄어든다(둘 다 탭하면
     *  그 자리에서 바로 바뀜). onToggleCollapsed가 null이면 최소화 버튼 자체가 나타나지 않는다. */
    collapsed: Boolean = false,
    onToggleCollapsed: (() -> Unit)? = null,
    /** 버튼바를 길게 눌러 드래그로 옮기기 — 왼쪽 끝 손잡이 아이콘에서만 반응한다(다른 버튼들과
     *  터치 영역이 겹치지 않도록). 둘 다 null이면 손잡이가 나타나지 않는다. */
    onDragBar: ((Offset) -> Unit)? = null,
    onDragBarEnd: (() -> Unit)? = null,
    /** 버튼바가 지금 어느 가장자리에 붙어있는지 — 좌/우면 세로로 눕고(내부 배치·스크롤 방향 전환),
     *  팝업(브러시 패널·색상휠·즐겨찾기 편집)도 화면 밖으로 안 나가는 쪽으로 열린다. */
    dock: ToolbarDock = ToolbarDock.BOTTOM,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var colorWheelOpen by remember { mutableStateOf(false) }
    var editFavAt by remember { mutableIntStateOf(-1) } // -1 none, else favourites index being edited
    var favoritesGridOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Which brush's width/opacity panel is open (hoisted here, same pattern as editFavAt above —
    // per-button local state + Popup turned out unreliable, this mirrors the known-good approach).
    var openBrushPanel by remember { mutableStateOf<BrushType?>(null) }
    var openEraserPanel by remember { mutableStateOf(false) }
    var miniBrushPickerOpen by remember { mutableStateOf(false) }
    var collapsedSizePanelOpen by remember { mutableStateOf(false) } // 최소화 모드: 브러시 아이콘 길게 누르면 굵기/투명도 패널
    var brushCategoryExpanded by remember { mutableStateOf(true) } // 붓 종류(펜/연필/크레파스/수채화/지우개) 묶음 접기
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val screenEdgeMargin = with(LocalDensity.current) { 20.dp.roundToPx() }
    val vertical = dock == ToolbarDock.LEFT || dock == ToolbarDock.RIGHT
    val popupAnchor: PopupPositionProvider = when (dock) {
        ToolbarDock.BOTTOM -> AboveAnchor(gap)
        ToolbarDock.TOP -> BelowAnchor(gap)
        ToolbarDock.LEFT -> SideAnchor(gap, toRight = true)
        ToolbarDock.RIGHT -> SideAnchor(gap, toRight = false)
    }
    // 굵기/투명도 패널(SlidersPanel) 전용 앵커 — 화면 가장자리에서 최소 20dp는 띄워서 뜨게 한다.
    val sizePopupAnchor: PopupPositionProvider = when (dock) {
        ToolbarDock.BOTTOM -> AboveAnchor(gap, screenEdgeMargin)
        ToolbarDock.TOP -> BelowAnchor(gap, screenEdgeMargin)
        ToolbarDock.LEFT -> SideAnchor(gap, toRight = true, edgeMarginPx = screenEdgeMargin)
        ToolbarDock.RIGHT -> SideAnchor(gap, toRight = false, edgeMarginPx = screenEdgeMargin)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("전체 지우기") },
            text = { Text("이 페이지의 그림을 모두 지울까요? 되돌리기로 복구할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text("전체 지우기", color = Color(0xFFE85555))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }

    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp, tonalElevation = 2.dp,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (collapsed) {
            val collapsedContent: @Composable () -> Unit = {
                if (onDragBar != null && onDragBarEnd != null) DragHandle(onDragBar, onDragBarEnd)
                // 현재 브러시(또는 지우개) 아이콘 — 탭하면 4개(+지우개) 미니 팝업, 길게 누르면 굵기/투명도 패널.
                Box {
                    Box(
                        Modifier.size(ButtonTapSize).bounceClick(onLongClick = { collapsedSizePanelOpen = true }) { miniBrushPickerOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(currentToolIcon(brush, erasing)), "현재 브러시 — 탭해서 변경, 길게 눌러 굵기·투명도 조절",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    if (miniBrushPickerOpen) Popup(popupAnchor, { miniBrushPickerOpen = false }, PopupProperties(focusable = true)) {
                        MiniBrushPopup(brush, erasing,
                            onPick = { onBrush(it); miniBrushPickerOpen = false },
                            onEraser = { onToggleErase(); miniBrushPickerOpen = false })
                    }
                    if (collapsedSizePanelOpen) Popup(sizePopupAnchor, { collapsedSizePanelOpen = false }, PopupProperties(focusable = true)) {
                        SlidersPanel(!erasing, sizeDp, opacity, onSize, onOpacity,
                            sizeRange = if (erasing) EraserSizeRange else brushSizeRange(brush))
                    }
                }
                // 현재 색상 — 탭하면 전체 툴바와 같은 색상휠이 뜬다.
                Box {
                    Box(
                        Modifier.size(ButtonTapSize).clickable(indication = null, interactionSource = remember { MutableInteractionSource() },
                            onClickLabel = "현재 색상 — 탭해서 변경") { colorWheelOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                    if (colorWheelOpen) Popup(popupAnchor, { colorWheelOpen = false }, PopupProperties(focusable = true)) {
                        ColorPickerCard(color, onColor = onColor,
                            onEyedrop = { colorWheelOpen = false; onToggleEyedrop() })
                    }
                }
                onToggleCollapsed?.let { IconBtn(Icons.Filled.UnfoldMore, "버튼바 펼치기", onClick = it) }
            }
            if (vertical) {
                Column(
                    Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) { collapsedContent() }
            } else {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) { collapsedContent() }
            }
        } else {
        // 페이지/회전/잠금/전체화면은 별도 ScreenControls 서피스로 옮겼음(2026-08-20) — 여기 남은
        // 건 onBack뿐(다이어리에서만 쓰임).
        val hasNav = onBack != null

        // 버튼바를 구분선 기준 "그룹"으로 나눠서 그린다 — 그룹 안 버튼 간격(GroupButtonGap)과
        // 그룹-구분선 사이 간격(GroupDividerGap)을 서로 다른 Arrangement.spacedBy로 따로 조절하기
        // 위함(공유 spacedBy 하나만으로는 구분선 쪽만 더 좁게 만들 수 없어서 이렇게 나눔).
        val segments = buildList<@Composable () -> Unit> {
            if (onDragBar != null && onDragBarEnd != null) add { DragHandle(onDragBar, onDragBarEnd) }
            if (hasNav) add {
                onBack?.let { IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", onClick = it) }
            }
            add {
                // 붓 종류(펜/연필/크레파스/수채화/지우개) 묶음 — 접으면 지금 쓰는 도구 아이콘 하나만
                // 남고, 탭하면 다시 5개가 펼쳐진다(2026-08-26, 툴바 전체 최소화와 별개로 이 묶음만
                // 따로 접을 수 있게).
                if (brushCategoryExpanded) {
                    // Brush icons: tap to switch; tap the already-selected one again to open ITS OWN
                    // width/opacity panel (anchored, not a single shared control).
                    BrushBtnWithPanel(!erasing && brush == BrushType.PEN, sizeDp, opacity, true, sizePopupAnchor,
                        panelOpen = openBrushPanel == BrushType.PEN,
                        setPanelOpen = { o -> openBrushPanel = if (o) BrushType.PEN else null; if (o) openEraserPanel = false },
                        onClick = { onBrush(BrushType.PEN); openBrushPanel = null; openEraserPanel = false },
                        onSize = onSize, onOpacity = onOpacity, sizeRange = brushSizeRange(BrushType.PEN)) { t ->
                        Image(painterResource(R.drawable.brush_pen), "볼펜", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtnWithPanel(!erasing && brush == BrushType.PENCIL, sizeDp, opacity, true, sizePopupAnchor,
                        panelOpen = openBrushPanel == BrushType.PENCIL,
                        setPanelOpen = { o -> openBrushPanel = if (o) BrushType.PENCIL else null; if (o) openEraserPanel = false },
                        onClick = { onBrush(BrushType.PENCIL); openBrushPanel = null; openEraserPanel = false },
                        onSize = onSize, onOpacity = onOpacity, sizeRange = brushSizeRange(BrushType.PENCIL)) { t ->
                        Image(painterResource(R.drawable.brush_pencil), "연필", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtnWithPanel(!erasing && brush == BrushType.CRAYON, sizeDp, opacity, true, sizePopupAnchor,
                        panelOpen = openBrushPanel == BrushType.CRAYON,
                        setPanelOpen = { o -> openBrushPanel = if (o) BrushType.CRAYON else null; if (o) openEraserPanel = false },
                        onClick = { onBrush(BrushType.CRAYON); openBrushPanel = null; openEraserPanel = false },
                        onSize = onSize, onOpacity = onOpacity, sizeRange = brushSizeRange(BrushType.CRAYON)) { t ->
                        Image(painterResource(R.drawable.brush_crayon), "크레파스", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtnWithPanel(!erasing && brush == BrushType.WATER, sizeDp, opacity, true, sizePopupAnchor,
                        panelOpen = openBrushPanel == BrushType.WATER,
                        setPanelOpen = { o -> openBrushPanel = if (o) BrushType.WATER else null; if (o) openEraserPanel = false },
                        onClick = { onBrush(BrushType.WATER); openBrushPanel = null; openEraserPanel = false },
                        onSize = onSize, onOpacity = onOpacity, sizeRange = brushSizeRange(BrushType.WATER)) { t ->
                        Image(painterResource(R.drawable.brush_water), "수채화", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    BrushBtnWithPanel(erasing, sizeDp, opacity, true, sizePopupAnchor,
                        panelOpen = openEraserPanel,
                        setPanelOpen = { o -> openEraserPanel = o; if (o) openBrushPanel = null },
                        onClick = { onToggleErase(); openBrushPanel = null; openEraserPanel = false },
                        onSize = onSize, onOpacity = onOpacity,
                        showBlur = true, blur = eraserBlur, onBlur = onEraserBlur, sizeRange = EraserSizeRange) { t ->
                        Image(painterResource(R.drawable.brush_eraser), "지우개", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                    }
                    IconBtn(Icons.Filled.UnfoldLess, "붓 종류 접기", onClick = { brushCategoryExpanded = false })
                } else {
                    // 접힌 상태에서도 툴바 전체 최소화 모드와 같은 조작: 탭하면 붓 종류 미니 팝업,
                    // 길게 누르면 지금 붓의 굵기/투명도 패널(2026-08-26). 줄 자체를 다시 펼치려면
                    // 옆의 화살표 버튼.
                    Box {
                        Box(
                            Modifier.size(ButtonTapSize).bounceClick(onLongClick = { collapsedSizePanelOpen = true }) { miniBrushPickerOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(currentToolIcon(brush, erasing)), "현재 붓 — 탭해서 종류 고르기, 길게 눌러 굵기·투명도 조절",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.size(25.dp)) // 브러시 아이콘 크기
                        }
                        if (miniBrushPickerOpen) Popup(popupAnchor, { miniBrushPickerOpen = false }, PopupProperties(focusable = true)) {
                            MiniBrushPopup(brush, erasing,
                                onPick = { onBrush(it); miniBrushPickerOpen = false },
                                onEraser = { onToggleErase(); miniBrushPickerOpen = false })
                        }
                        if (collapsedSizePanelOpen) Popup(sizePopupAnchor, { collapsedSizePanelOpen = false }, PopupProperties(focusable = true)) {
                            SlidersPanel(!erasing, sizeDp, opacity, onSize, onOpacity,
                                sizeRange = if (erasing) EraserSizeRange else brushSizeRange(brush))
                        }
                    }
                    IconBtn(Icons.Filled.UnfoldMore, "붓 종류 펼치기", onClick = { brushCategoryExpanded = true })
                }
                // 올가미(선택)·페인트통(채우기) — 굵기/불투명도 패널이 필요 없는 단순 토글이라
                // 다른 브러시 버튼과 달리 팝업 없이 바로 켜고 끈다(스포이드 버튼과 같은 패턴).
                IconBtn(Icons.Filled.Gesture, "올가미 선택",
                    tint = if (lassoActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    onClick = onToggleLasso)
                IconBtn(Icons.Filled.FormatColorFill, "페인트통",
                    tint = if (fillActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    onClick = onToggleFill)
            }
            add {
                // 5 favourites (of the 20 registered — the rest live in the "즐겨찾기 전체" grid below):
                // tap to pick; tap the already-selected one again to open a colour wheel for it.
                // Touch area is ButtonTapSize (visually bigger than the swatch), but the ripple itself is
                // scoped to the visible 28dp swatch (shared InteractionSource: outer box detects the tap
                // with no indication of its own, inner box — clipped to the swatch's own circle — draws it).
                favorites.take(5).forEachIndexed { i, c ->
                    val on = !erasing && c == color
                    val interaction = remember { MutableInteractionSource() }
                    Box {
                        Box(
                            Modifier.size(ButtonTapSize)
                                .clickable(interactionSource = interaction, indication = null, onClickLabel = "즐겨찾기 색상 ${i + 1}") {
                                    if (on) editFavAt = i else onColor(c)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).indication(interaction, LocalIndication.current)
                                .background(Color(c))
                                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape))
                        }
                        if (editFavAt == i) Popup(popupAnchor, { editFavAt = -1 }, PopupProperties(focusable = true)) {
                            ColorPickerCard(c,
                                onColor = { newColor -> onColor(newColor); onEditFavorite(i, newColor) },
                                onEyedrop = { editFavAt = -1; onToggleEyedrop() })
                        }
                    }
                }
                // Color wheel: opens a hue/saturation/value picker for any custom colour.
                Box {
                    val wheelInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(ButtonTapSize)
                            .clickable(interactionSource = wheelInteraction, indication = null, onClickLabel = "사용자 지정 색상 고르기") {
                                colorWheelOpen = !colorWheelOpen
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).indication(wheelInteraction, LocalIndication.current)
                            .background(Brush.sweepGradient(HueWheel))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                    if (colorWheelOpen) Popup(popupAnchor, { colorWheelOpen = false }, PopupProperties(focusable = true)) {
                        ColorPickerCard(color, onColor = onColor,
                            onEyedrop = { colorWheelOpen = false; onToggleEyedrop() })
                    }
                }
                // 즐겨찾기 전체(20개) 그리드 — 툴바 인라인 자리는 5개뿐이라 나머지는 여기서 고르거나 등록.
                Box {
                    IconBtn(Icons.Filled.Palette, "즐겨찾기 전체",
                        tint = if (favoritesGridOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { favoritesGridOpen = !favoritesGridOpen })
                    if (favoritesGridOpen) Popup(popupAnchor, { favoritesGridOpen = false }, PopupProperties(focusable = true)) {
                        FavoritesGridPopup(favorites, color, erasing, onColor, onEditFavorite,
                            onEyedrop = { favoritesGridOpen = false; onToggleEyedrop() })
                    }
                }
                // Eyedropper: arm it, then the next tap on the canvas picks that colour instead of drawing.
                // 브러시 버튼과 같은 톤 — 비무장 시 흐린 회색, 무장 시 강조색으로 또렷하게 구분.
                IconBtn(Icons.Filled.Colorize, "스포이드",
                    tint = if (eyedropArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    onClick = onToggleEyedrop)
            }
            add {
                IconBtn(Icons.AutoMirrored.Filled.Undo, "되돌리기", onClick = onUndo)
                IconBtn(Icons.AutoMirrored.Filled.Redo, "다시 실행", onClick = onRedo)
                IconBtn(Icons.Filled.Delete, "전체 지우기", tint = Color(0xFFE85555), onClick = { confirmClear = true })
            }
            onToggleCollapsed?.let { toggle -> add { IconBtn(Icons.Filled.UnfoldLess, "버튼바 최소화", onClick = toggle) } }
        }

        if (vertical) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp), // 그룹-구분선 간격
            ) {
                segments.forEachIndexed { i, seg ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) { seg() } // 버튼 간격(세로 도킹)
                    if (i < segments.lastIndex) ToolbarDivider(vertical)
                }
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp), // 그룹-구분선 간격
            ) {
                segments.forEachIndexed { i, seg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) { seg() } // 버튼 간격(가로 도킹, 기본)
                    if (i < segments.lastIndex) ToolbarDivider(vertical)
                }
            }
        }
        }
    }
}

/** 페이지/읽기모드/회전/화면잠금/전체화면 — 그림 자체가 아니라 "화면"을 다루는 버튼들만 모은 작은
 *  확장 버튼 (2026-08-20, 예전엔 드래그로 옮기고 최소화할 수 있는 독립 바였는데, 화면 우측 상단 고정
 *  + 항상 닫힌 상태로 시작하는 팝업 방식으로 교체). 버튼을 탭하면 아래로 펼쳐지고, 기능을 하나 고르거나
 *  팝업 바깥을 탭하면 즉시 다시 닫힌다 — 펼침 상태 자체는 어디에도 남지 않는다. 버튼들 모두 nullable이라,
 *  해당 개념이 없는 화면(다이어리)은 필요한 콜백만 null로 넘기면 자동으로 빠진다. */
@Composable
fun ScreenControls(
    onOpenPages: (() -> Unit)? = null,
    onReadMode: (() -> Unit)? = null,
    onRotate: (() -> Unit)? = null,
    /** 화면 잠금: freezes pinch zoom/pan and the 90° rotate button so they can't be nudged by accident
     *  mid-drawing; drawing itself is unaffected. Icon reflects current state. */
    locked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    /** 전체화면: hides the system status/nav bars for more drawing room. Icon reflects current state. */
    fullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val edgeMargin = with(LocalDensity.current) { 12.dp.roundToPx() }
    Box(modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        // 캔버스 위에 항상 떠 있는 오버레이라 그림을 가리지 않도록 평소엔 반투명(50%)으로 — 탭하면
        // 펼쳐지는 팝업 내용물은 완전 불투명 그대로 둔다. 반투명은 Modifier.alpha()가 아니라
        // Surface color 자체에 알파를 줘서 낸다 — alpha()로 감싸면 그 레이어 크기에 맞춰 그림자까지
        // 잘려버렸다(2026-08-20).
        Surface(
            // shadowElevation을 주면 원형 Surface 뒤로 흰색 팔각형 그림자가 비쳐 보이는 문제가
            // 있어(안드로이드가 원형 아웃라인의 그림자를 다각형으로 근사해서 생기는 렌더링 artifact)
            // 뺐다 — tonalElevation만으로도 은은한 깊이감은 유지된다.
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                IconBtn(Icons.Filled.Tune, "화면 설정 열기", onClick = { expanded = true })
            }
        }
        if (expanded) {
            Popup(BelowAnchor(gap, edgeMargin), { expanded = false }, PopupProperties(focusable = true)) {
                Surface(
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 10.dp, tonalElevation = 3.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        onOpenPages?.let { open -> IconBtn(Icons.Filled.Layers, "페이지") { open(); expanded = false } }
                        onReadMode?.let { read -> IconBtn(Icons.Filled.AutoStories, "읽기모드") { read(); expanded = false } }
                        onRotate?.let { rotate ->
                            // Dimmed (not disabled) while locked — BrushView.rotate() itself no-ops, this just
                            // signals why tapping does nothing instead of silently failing.
                            IconBtn(Icons.Filled.Rotate90DegreesCw, "90° 회전",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (locked) 0.35f else 1f)) { rotate(); expanded = false }
                        }
                        onToggleLock?.let { toggle ->
                            IconBtn(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (locked) "화면 잠금 해제" else "화면 잠금",
                                tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) { toggle(); expanded = false }
                        }
                        onToggleFullscreen?.let { toggle ->
                            IconBtn(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (fullscreen) "전체화면 종료" else "전체화면",
                                tint = if (fullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) { toggle(); expanded = false }
                        }
                    }
                }
            }
        }
    }
}

/** 버튼바 길게 눌러 드래그로 옮길 때 잡는 손잡이 — 다른 버튼들과 터치 영역이 겹치지 않도록 전용
 *  자리 하나에만 반응한다. 짧게 눌러도 아무 동작 없음(탭 기능은 없고 드래그 전용). */
@Composable
private fun DragHandle(onDrag: (Offset) -> Unit, onDragEnd: () -> Unit) {
    Box(
        Modifier.size(32.dp, 48.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {},
                    onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.DragIndicator, "버튼바 이동(길게 눌러 드래그)", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun currentToolIcon(brush: BrushType, erasing: Boolean): Int = when {
    erasing -> R.drawable.brush_eraser
    brush == BrushType.PEN -> R.drawable.brush_pen
    brush == BrushType.PENCIL -> R.drawable.brush_pencil
    brush == BrushType.CRAYON -> R.drawable.brush_crayon
    else -> R.drawable.brush_water
}

/** 최소화 상태에서 브러시 아이콘을 탭하면 뜨는 4개(+지우개) 미니 픽커 — 종류만 빠르게 바꾼다.
 *  같은 아이콘을 길게 누르면 현재 브러시의 굵기·투명도 패널(SlidersPanel)이 뜬다. */
@Composable
private fun MiniBrushPopup(current: BrushType, erasing: Boolean, onPick: (BrushType) -> Unit, onEraser: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                BrushType.PEN to R.drawable.brush_pen, BrushType.PENCIL to R.drawable.brush_pencil,
                BrushType.CRAYON to R.drawable.brush_crayon, BrushType.WATER to R.drawable.brush_water,
            ).forEach { (t, res) ->
                val tint = if (!erasing && t == current) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Box(Modifier.size(48.dp).bounceClick { onPick(t) }, contentAlignment = Alignment.Center) {
                    Image(painterResource(res), null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(38.dp))
                }
            }
            val eraserTint = if (erasing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            Box(Modifier.size(48.dp).bounceClick { onEraser() }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.brush_eraser), null, colorFilter = ColorFilter.tint(eraserTint), modifier = Modifier.size(38.dp))
            }
        }
    }
}

/** Floating colour-preview bubble for the eyedropper — sits above the fingertip and follows it while
 *  armed/dragging on the canvas, so it's obvious a colour is being picked (and which one). [xPx]/[yPx]
 *  are raw screen px in the same coordinate space as the BrushView it's overlaid on. */
@Composable
fun EyedropFloatingPreview(colorArgb: Int, xPx: Float, yPx: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val sizePx = 52f * density
    val liftPx = 64f * density
    Box(
        modifier
            .offset { IntOffset((xPx - sizePx / 2f).roundToInt(), (yPx - liftPx - sizePx / 2f).roundToInt()) }
            .size(52.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .border(3.dp, Color.White, CircleShape),
    )
}

/** 라소 선택 영역 바로 위에 뜨는 삭제 버튼 — BrushView.onLassoSelectionChanged가 주는 화면 px
 *  좌표를 그대로 써서 [EyedropFloatingPreview]와 같은 방식으로 캔버스 위에 직접 띄운다(별도
 *  Popup이 아니라, BrushView를 담은 Box 안에 형제로 넣어 쓰는 오버레이). */
@Composable
fun LassoDeleteButton(xPx: Float, yPx: Float, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val sizePx = 40f * density
    val liftPx = 46f * density
    Box(
        modifier
            .offset { IntOffset((xPx - sizePx / 2f).roundToInt(), (yPx - liftPx).roundToInt()) }
            .size(40.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .bounceClick(onClick = onDelete),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Delete, "선택 영역 지우기", tint = Color(0xFFE85555))
    }
}

/** Width (and, unless erasing, opacity) sliders for ONE specific brush — opened by tapping that
 *  brush's icon again while it's already selected (each brush keeps its own width/opacity, not a
 *  shared value). 팝업 자체가 화면 가장자리에서 20dp 띄워서 뜨도록 anchor 쪽(sizePopupAnchor)에서 처리한다.
 *  showBlur/blur/onBlur은 지우개 전용 — 다른 브러시는 showBlur=false로 호출해 이 줄이 안 보인다. */
@Composable
private fun SlidersPanel(
    showOpacity: Boolean, sizeDp: Float, opacity: Float, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    showBlur: Boolean = false, blur: Float = 0f, onBlur: (Float) -> Unit = {},
    sizeRange: ClosedFloatingPointRange<Float> = SizeRange,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(248.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
            // 굵기 표시는 실제 dp가 아니라 이 슬라이더 안에서의 1~30 단계 번호(브러시 종류마다 범위가
            // 달라도 표시는 항상 1~30으로 통일 — sizeRange 기준으로 위치를 환산).
            IconSliderRow(Icons.Filled.LineWeight, "굵기", "${sizeLevel(sizeDp, sizeRange)}", sizeDp, sizeRange, onSize)
            if (showOpacity) {
                Spacer(Modifier.height(5.dp))
                IconSliderRow(Icons.Filled.Opacity, "불투명도", "${opacity.toInt()}", opacity, 0f..100f, onOpacity)
            }
            if (showBlur) {
                Spacer(Modifier.height(5.dp))
                IconSliderRow(Icons.Filled.BlurOn, "경계 블러", "${blur.toInt()}", blur, BlurRange, onBlur)
            }
        }
    }
}

// 슬라이드 단계 수(30단계) = steps(양 끝 제외 중간값 개수) + 2
internal const val SliderStepCount = 28
// 캔버스 px 기준 굵기 범위 — strokeSize가 화면 밀도/fitScale 나누기 없이 캔버스에 그대로 찍히는
// 값으로 바뀌면서(2026-08-17), 예전 2~48(화면 dp 기준, 실제로는 밀도·fitScale로 몇 배 증폭되던 값)
// 그대로 두면 캔버스에서 거의 안 보일 만큼 얇아져서 2배로 올림. Dimens.Brush.* 기본값도 같이 올렸음.
// 최소/최대 굵기 — 굵기 슬라이더가 오갈 수 있는 양 끝값. 둘 다 여기서 직접 숫자를 바꾸면 된다.
internal const val MinBrushSize = 4f
internal const val MaxBrushSize = 96f
private val SizeRange = MinBrushSize..MaxBrushSize
private val BlurRange = 0f..32f
private val SliderAccentColor = Color(0xFFE85555)
// RGB/HSL 슬라이더 전용 — 이 슬라이더가 조절하는 값 자체가 색상이라, 트랙까지 색이 있으면 어떤 색을
// 만들고 있는지 헷갈린다. 무채색으로 고정.
private val NeutralSliderAccentColor = Color(0xFF8A8A8A)
internal val SliderThumbSize = 30.dp
internal val SliderThumbTouchSize = 40.dp
internal val SliderTrackHeight = 6.dp

/** 실제 dp 값과 무관하게, 슬라이더 내 위치를 1~30 단계 번호로 환산 (표시는 모든 브러시 공통, 범위는 브러시별). */
private fun sizeLevel(sizeDp: Float, range: ClosedFloatingPointRange<Float> = SizeRange): Int {
    val fraction = ((sizeDp - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    return (fraction * (SliderStepCount + 1)).roundToInt() + 1
}

/** 브러시별 굵기 슬라이더 최소/최대값(Dimens.Brush 기준) — 펜처럼 가는 도구와 수채화처럼 굵은 도구가
 *  같은 범위를 쓰면 세밀한 조절이 어려워서 브러시 기본값 스케일에 맞춰 나눠뒀다. */
internal fun brushSizeRange(brush: BrushType): ClosedFloatingPointRange<Float> = when (brush) {
    BrushType.PEN -> Dimens.Brush.penMinWidth..Dimens.Brush.penMaxWidth
    BrushType.PENCIL -> Dimens.Brush.pencilMinWidth..Dimens.Brush.pencilMaxWidth
    BrushType.CRAYON -> Dimens.Brush.crayonMinWidth..Dimens.Brush.crayonMaxWidth
    BrushType.WATER -> Dimens.Brush.waterMinWidth..Dimens.Brush.waterMaxWidth
}
internal val EraserSizeRange = Dimens.Brush.eraserMinWidth..Dimens.Brush.eraserMaxWidth

/** 브러시 굵기/투명도 슬라이더와 동일한 트랙+썸 디자인 — 다른 화면(예: 일기 달력 오버레이 설정)에서도
 *  같은 룩을 쓰되 강조색만 바꾸고 싶을 때를 위해 `accentColor`를 파라미터로 열어둠(기본값은 기존 브러시 빨강).
 *  `onThumbClick`을 넘기면 썸을 탭했을 때(드래그가 아니라 탭) 호출된다 — 값을 직접 타이핑으로
 *  입력하는 UI(달력 오버레이 글자 크기)를 여는 용도. 기본은 null이라 기존 브러시 쪽엔 영향 없음. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconSliderRow(icon: ImageVector, contentDescription: String, valueText: String, value: Float,
                          range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, accentColor: Color = SliderAccentColor,
                          onThumbClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = SliderStepCount,
            track = { state -> GradientSliderTrack(state, accentColor) },
            thumb = { RingSliderThumb(valueText, accentColor, onThumbClick) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GradientSliderTrack(state: SliderState, accentColor: Color = SliderAccentColor) {
    val span = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (span == 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(SliderThumbTouchSize)
    ) {
        val strokeWidthPx = SliderTrackHeight.toPx()
        val y = size.height / 2f
        val thumbX = size.width * fraction
        drawLine(
            color = inactiveColor,
            start = Offset(thumbX, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        if (thumbX > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor, accentColor.copy(alpha = 0.55f)),
                    startX = 0f,
                    endX = thumbX,
                ),
                start = Offset(0f, y),
                end = Offset(thumbX, y),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun RingSliderThumb(valueText: String, accentColor: Color = SliderAccentColor, onThumbClick: (() -> Unit)? = null) {
    Box(Modifier.size(SliderThumbTouchSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(SliderThumbSize)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0x1F000000), CircleShape)
                .let { m ->
                    if (onThumbClick != null) {
                        m.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onThumbClick,
                        )
                    } else m
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(valueText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

/** macOS "색상휠" 탭과 같은 구성: 원형 색상+채도 휠, 그 아래 밝기 바, RGB/HSL 탭, 그리고
 *  (브러시 툴바에서 열 때만) 스포이드(2026-08-27, 불투명도 슬라이더는 브러시 자체 설정과
 *  중복이라 뺐음 — 즐겨찾기 미리보기도 같은 이유로 이미 뺀 상태). 표지색·달력 오버레이 색상
 *  등 브러시가 아닌 곳에서도 재사용하므로 [onEyedrop]은 선택 — null이면(기본값) 해당 구역을
 *  아예 그리지 않는다. */
@Composable
internal fun ColorPickerCard(
    color: Long, onColor: (Long) -> Unit,
    onEyedrop: (() -> Unit)? = null,
) {
    val init = remember { FloatArray(3).also { AndroidColor.colorToHSV((color and 0xFFFFFFFF).toInt(), it) } }
    var hue by remember { mutableFloatStateOf(init[0]) }
    var sat by remember { mutableFloatStateOf(init[1]) }
    var value by remember { mutableFloatStateOf(init[2]) }
    // 원형 휠/밝기 막대의 pointerInput(Unit)은 처음 구성될 때 딱 한 번만 코루틴을 띄우고 계속 재사용
    // 하므로(키가 상수라 재구성돼도 새로 안 뜸), 그 안에서 emit()이 부르는 값은 "호출 시점에 다시
    // 계산"해야 한다 — currentPacked를 val로 미리 굳혀두면 첫 구성 때의 색이 통째로 고정돼버려서
    // 휠 손잡이는 움직여도 실제 색은 안 바뀌는 버그가 났다(2026-08-27, bounceClick과 같은 스테일
    // 클로저 패턴). emit()은 함수 본문에서 매번 새로 읽어 계산하고, current는 렌더링 전용이라
    // val로 둬도 안전하다(재구성될 때마다 다시 계산됨).
    fun packedColor() = (AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFF) or 0xFF000000L
    fun emit() = onColor(packedColor())
    val currentPacked = packedColor()
    val current = Color(currentPacked)

    var tab by remember { mutableStateOf(PickerTab.WHEEL) }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(260.dp).padding(16.dp)) {
            // 상단 탭: 휠 / RGB / HSL — 한 번에 하나만 보여서 카드가 셋 다 항상 펼쳐 보일 때보다
            // 훨씬 덜 길다(macOS 색상피커 탭 구성과 동일, 2026-08-26).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerTabIcon(tab == PickerTab.WHEEL, "색상휠", { tab = PickerTab.WHEEL }) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.sweepGradient(HueWheel)))
                }
                PickerTabIcon(tab == PickerTab.RGB, "RGB 슬라이더", { tab = PickerTab.RGB }) {
                    Text("RGB", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                PickerTabIcon(tab == PickerTab.HSL, "HSL 슬라이더", { tab = PickerTab.HSL }) {
                    Text("HSL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                PickerTab.WHEEL -> {
                    // 원형 휠: 각도=색상, 중심에서의 거리=채도 (밝기는 아래 별도 막대) — sweepGradient(색상)
                    // 위에 중심이 불투명 흰색인 radialGradient를 정상 알파합성으로 겹쳐서, 중심에 가까울수록
                    // 흰색과 섞여 채도가 낮아지는 정확한 HSV(V=1 단면) 색을 얻는다.
                    Box(
                        Modifier.fillMaxWidth().height(220.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val p = awaitPointerEvent().changes.first().position
                                        val cx = size.width / 2f; val cy = size.height / 2f
                                        val radius = min(cx, cy)
                                        val dx = p.x - cx; val dy = p.y - cy
                                        val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                        if (angle < 0f) angle += 360f
                                        hue = angle
                                        sat = if (radius > 0f) dist / radius else 0f
                                        emit()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val radius = min(size.width, size.height) / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(brush = Brush.sweepGradient(HueWheel), radius = radius, center = center)
                            drawCircle(
                                brush = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = center, radius = radius),
                                radius = radius, center = center,
                            )
                            val angleRad = Math.toRadians(hue.toDouble())
                            val pointR = sat * radius
                            val p = Offset(center.x + (cos(angleRad) * pointR).toFloat(), center.y + (sin(angleRad) * pointR).toFloat())
                            drawCircle(Color.White, 7.dp.toPx(), p, style = Stroke(2.5f.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.5f), 7.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 밝기(Value) 막대 — 색상은 이제 휠이 담당하므로 이 막대는 흰색(밝음)~검정(어두움)만 표현.
                    Box(
                        Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))
                            .background(Brush.horizontalGradient(listOf(Color.White, Color.Black)))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val x = awaitPointerEvent().changes.first().position.x
                                        value = (1f - x / size.width).coerceIn(0f, 1f)
                                        emit()
                                    }
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.height / 2f
                            val x = ((1f - value) * size.width).coerceIn(r, size.width - r)
                            drawCircle(Color.White, r - 1.dp.toPx(), Offset(x, r), style = Stroke(2.5f.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.5f), r - 1.dp.toPx(), Offset(x, r), style = Stroke(1.dp.toPx()))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("#%06X".format(0xFFFFFF and AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                PickerTab.RGB -> RgbSliders(hue, sat, value) { h, s, v -> hue = h; sat = s; value = v; emit() }
                PickerTab.HSL -> HslSliders(hue, sat, value) { h, s, v -> hue = h; sat = s; value = v; emit() }
            }
            if (onEyedrop != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    IconBtn(Icons.Filled.Colorize, "스포이드", onClick = onEyedrop)
                }
            }
        }
    }
}

private enum class PickerTab { WHEEL, RGB, HSL }

/** 색상 카드 상단의 작은 탭 버튼 — 선택된 쪽만 옅게 배경이 들어온다. */
@Composable
private fun PickerTabIcon(selected: Boolean, desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** RGB(0~255) 슬라이더 — 타이핑 입력이 아니라 브러시 굵기·불투명도와 같은 드래그 슬라이더 방식
 *  (2026-08-26). 내부적으로는 항상 HSV(휠이 쓰는 표현)로 환산해 돌려준다. */
@Composable
private fun RgbSliders(hue: Float, sat: Float, value: Float, onHsv: (Float, Float, Float) -> Unit) {
    val argb = remember(hue, sat, value) { AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)) }
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF

    fun commit(nr: Int, ng: Int, nb: Int) {
        val out = FloatArray(3)
        AndroidColor.RGBToHSV(nr.coerceIn(0, 255), ng.coerceIn(0, 255), nb.coerceIn(0, 255), out)
        onHsv(out[0], out[1], out[2])
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSliderRow("R", "$r", r.toFloat(), 0f..255f, listOf(Color(0, g, b), Color(255, g, b))) { commit(it.roundToInt(), g, b) }
        LabeledSliderRow("G", "$g", g.toFloat(), 0f..255f, listOf(Color(r, 0, b), Color(r, 255, b))) { commit(r, it.roundToInt(), b) }
        LabeledSliderRow("B", "$b", b.toFloat(), 0f..255f, listOf(Color(r, g, 0), Color(r, g, 255))) { commit(r, g, it.roundToInt()) }
    }
}

/** HSL(H 0~360, S/L 0~100) 슬라이더 — [RgbSliders]와 같은 카드를 다른 축으로 조절하는 쌍둥이 탭. */
@Composable
private fun HslSliders(hue: Float, sat: Float, value: Float, onHsv: (Float, Float, Float) -> Unit) {
    val argb = remember(hue, sat, value) { AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)) }
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val hsl = remember(hue, sat, value) { rgbToHsl(r, g, b) }

    fun commit(nh: Float, ns: Float, nl: Float) {
        val (nr, ng, nb) = hslToRgb(nh.coerceIn(0f, 360f), ns.coerceIn(0f, 1f), nl.coerceIn(0f, 1f))
        val out = FloatArray(3)
        AndroidColor.RGBToHSV(nr.coerceIn(0, 255), ng.coerceIn(0, 255), nb.coerceIn(0, 255), out)
        onHsv(out[0], out[1], out[2])
    }

    // 채도 막대는 지금 색상·밝기는 고정한 채 채도 0%(회색)~100%(꽉 찬 색)만 보여준다 — 밝기 막대는
    // 순수 검정~흰색(어떤 색이든 밝기 축은 똑같이 보이도록, 이미지 시안과 동일).
    val satColors = remember(hue, hsl.third) {
        val (r0, g0, b0) = hslToRgb(hue, 0f, hsl.third)
        val (r1, g1, b1) = hslToRgb(hue, 1f, hsl.third)
        listOf(Color(r0, g0, b0), Color(r1, g1, b1))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSliderRow("H", "${hsl.first.roundToInt()}", hsl.first, 0f..360f, HueWheel) { commit(it, hsl.second, hsl.third) }
        LabeledSliderRow("S", "${(hsl.second * 100).roundToInt()}", hsl.second * 100f, 0f..100f, satColors) { commit(hsl.first, it / 100f, hsl.third) }
        LabeledSliderRow("L", "${(hsl.third * 100).roundToInt()}", hsl.third * 100f, 0f..100f, listOf(Color.Black, Color.White)) { commit(hsl.first, hsl.second, it / 100f) }
    }
}

/** [IconSliderRow]와 비슷한 라벨+슬라이더 한 줄이지만, 아이콘 대신 짧은 글자 라벨(R/G/B/H/S/L)을
 *  쓰고 트랙은 그 축을 움직이면 실제로 어떤 색이 되는지 양 끝~중간까지 그라디언트로 보여준다
 *  (2026-08-26, 무채색 트랙 대신 — 값 자체가 색이라 트랙에서 미리 보는 쪽이 더 쓸모 있었음). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, trackColors: List<Color>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            track = { ColorGradientTrack(trackColors) },
            thumb = { RingSliderThumb(valueText, NeutralSliderAccentColor) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 슬라이더가 조절하는 축 전체(양 끝값)를 그대로 보여주는 트랙 — 지금 값까지만 칠하는 일반
 *  진행바(GradientSliderTrack)와 달리, 위치 전체가 "여기로 옮기면 이 색"이라는 미리보기라 처음부터
 *  끝까지 다 칠한다. */
@Composable
private fun ColorGradientTrack(colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(SliderThumbTouchSize)) {
        val strokeWidthPx = SliderTrackHeight.toPx()
        val y = size.height / 2f
        drawLine(brush = Brush.horizontalGradient(colors), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
    }
}

/** RGB(0~255 각각) → HSL(H 0~360, S/L 0~1) — Android Color 클래스엔 HSL 변환이 없어 직접 구현. */
private fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = 60f * when (max) {
        rf -> ((gf - bf) / d) + (if (gf < bf) 6f else 0f)
        gf -> ((bf - rf) / d) + 2f
        else -> ((rf - gf) / d) + 4f
    }
    return Triple(h, s, l)
}

/** HSL(H 0~360, S/L 0~1) → RGB(0~255 각각). */
private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
    if (s == 0f) { val v = (l * 255).roundToInt().coerceIn(0, 255); return Triple(v, v, v) }
    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hh = h / 360f
    val r = (hue2rgb(p, q, hh + 1f / 3f) * 255).roundToInt().coerceIn(0, 255)
    val g = (hue2rgb(p, q, hh) * 255).roundToInt().coerceIn(0, 255)
    val b = (hue2rgb(p, q, hh - 1f / 3f) * 255).roundToInt().coerceIn(0, 255)
    return Triple(r, g, b)
}

/** 즐겨찾기 전체를 [ColorPickerCard]와 같은 폭(260dp)의 그리드로 보여주는 팝업 — 인라인 5개와 같은
 *  [favorites] 리스트를 그대로 쓰되 전체를 보여준다(같은 index를 그대로 [onEditFavorite]에 넘기므로
 *  인라인 자리와 항상 같은 색을 가리킨다). 탭하면 선택, 이미 선택된 칸을 다시 탭하면 그 칸의 색을
 *  바꾸는 색상휠이 뜬다(인라인 스와치와 동일). */
@Composable
private fun FavoritesGridPopup(
    favorites: List<Long>, color: Long, erasing: Boolean,
    onColor: (Long) -> Unit, onEditFavorite: (Int, Long) -> Unit, onEyedrop: () -> Unit,
) {
    var editAt by remember { mutableIntStateOf(-1) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Box(Modifier.width(260.dp).padding(16.dp)) {
            FavoritesGrid(favorites) { i, c ->
                val on = !erasing && c == color
                val interaction = remember { MutableInteractionSource() }
                Box {
                    Box(
                        Modifier.size(FavoriteSwatchSize).clip(CircleShape).indication(interaction, LocalIndication.current)
                            .background(Color(c))
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable(interactionSource = interaction, indication = null, onClickLabel = "즐겨찾기 색상 ${i + 1}") {
                                if (on) editAt = i else onColor(c)
                            },
                    )
                    if (editAt == i) Popup(AboveAnchor(0, 0), { editAt = -1 }, PopupProperties(focusable = true)) {
                        ColorPickerCard(c,
                            onColor = { newColor -> onColor(newColor); onEditFavorite(i, newColor) },
                            onEyedrop = { editAt = -1; onEyedrop() })
                    }
                }
            }
        }
    }
}

private class AboveAnchor(private val gapPx: Int, private val edgeMarginPx: Int = 0) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val minX = edgeMarginPx
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(minX)
        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2).coerceIn(minX, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** 버튼바가 위쪽 가장자리에 붙어있을 때용 — 팝업이 위로 열리면 화면 밖으로 나가므로 아래로 연다. */
private class BelowAnchor(private val gapPx: Int, private val edgeMarginPx: Int = 0) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val minX = edgeMarginPx
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(minX)
        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2).coerceIn(minX, maxX)
        val y = (anchorBounds.bottom + gapPx).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** 버튼바가 좌/우 가장자리에 세로로 붙어있을 때용 — 팝업을 화면 안쪽(왼쪽 도킹이면 오른쪽으로,
 *  오른쪽 도킹이면 왼쪽으로)으로 연다. */
private class SideAnchor(private val gapPx: Int, private val toRight: Boolean, private val edgeMarginPx: Int = 0) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (toRight) anchorBounds.right + gapPx else anchorBounds.left - popupContentSize.width - gapPx
        val minY = edgeMarginPx
        val maxY = (windowSize.height - popupContentSize.height - edgeMarginPx).coerceAtLeast(minY)
        val y = (anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2).coerceIn(minY, maxY)
        val minX = edgeMarginPx
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(minX)
        return IntOffset(x.coerceIn(minX, maxX), y)
    }
}

/** Tap to select; tap again while already selected opens THIS brush's own width/opacity panel,
 *  anchored right above this icon (not a shared control).
 *  Open/closed state is hoisted by the caller (BrushControls) — mirrors the favourites-edit popup. */
@Composable
private fun BrushBtnWithPanel(
    selected: Boolean, sizeDp: Float, opacity: Float, showOpacity: Boolean, anchor: PopupPositionProvider,
    panelOpen: Boolean, setPanelOpen: (Boolean) -> Unit,
    onClick: () -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    // 지우개 전용 — 다른 브러시 버튼 호출부는 그냥 기본값(showBlur=false)을 쓴다.
    showBlur: Boolean = false, blur: Float = 0f, onBlur: (Float) -> Unit = {},
    sizeRange: ClosedFloatingPointRange<Float> = SizeRange,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box {
        // Tap area is ButtonTapSize, matching the icon's own size exactly — no extra margin
        // between the tap zone and the visible icon (source art has no built-in padding either).
        Box(Modifier.size(ButtonTapSize).bounceClick { if (selected) setPanelOpen(!panelOpen) else onClick() },
            contentAlignment = Alignment.Center) { icon(tint) }
        if (panelOpen) Popup(anchor, { setPanelOpen(false) }, PopupProperties(focusable = true)) {
            SlidersPanel(showOpacity, sizeDp, opacity, onSize, onOpacity, showBlur, blur, onBlur, sizeRange)
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(Modifier.size(ButtonTapSize).bounceClick { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun ToolbarDivider(vertical: Boolean) {
    // 구분선-버튼 이격거리는 이 함수가 아니라 fullContent를 그리는 바깥 Row/Column의
    // Arrangement.spacedBy(8dp, "그룹-구분선 간격")에서 조절한다 — 그룹 내부 버튼 간격(15dp)과는
    // 별도 값이라 여기서 더 손댈 건 없음(선 자체의 두께·길이만 담당).
    if (vertical) {
        Box(Modifier.height(1.dp).width(24.dp).background(MaterialTheme.colorScheme.outlineVariant)) // 구분선 선 두께(height)·길이(width)
    } else {
        Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant)) // 구분선 선 두께(width)·길이(height)
    }
}
