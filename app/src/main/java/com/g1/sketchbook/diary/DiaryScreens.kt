package com.g1.sketchbook.diary

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.ColorPickerCard
import com.g1.sketchbook.brush.IconSliderRow
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.sketchbook.Catalog
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.excludeSystemGestureEdges
import com.g1.sketchbook.ui.saveToGallery
import com.g1.sketchbook.ui.theme.BodoniMTBlack
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.Pretendard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------------- 그림일기 편집 (full-screen A4 editor for one date) ----------------

@Composable
fun DiaryEditorScreen(date: String, myUid: String = "", onBack: () -> Unit, previewMode: Boolean = false) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(0xFF1E2D4CL) }
    var erasing by remember { mutableStateOf(false) }
    var lassoActive by remember { mutableStateOf(false) }
    var fillActive by remember { mutableStateOf(false) }
    var lassoDeleteAt by remember { mutableStateOf<Offset?>(null) }
    var preLassoErasing by remember { mutableStateOf(false) }
    var preLassoFillActive by remember { mutableStateOf(false) }
    // S펜 버튼을 누르고 있는 동안만 지우개로 전환했다가, 떼면 누르기 직전 도구로 되돌린다.
    var preStylusErasing by remember { mutableStateOf(false) }
    var preStylusLasso by remember { mutableStateOf(false) }
    var preStylusFill by remember { mutableStateOf(false) }
    val sizeByBrush = remember { mutableStateMapOf(BrushType.PEN to Dimens.Brush.penWidth, BrushType.PENCIL to Dimens.Brush.pencilWidth, BrushType.CRAYON to Dimens.Brush.crayonWidth, BrushType.WATER to Dimens.Brush.waterWidth) }
    val opacityByBrush = remember { mutableStateMapOf(BrushType.PEN to 100f, BrushType.PENCIL to 100f, BrushType.CRAYON to 100f, BrushType.WATER to 100f) }
    var eraserSize by remember { mutableFloatStateOf(Dimens.Brush.eraserWidth) }
    var eraserOpacity by remember { mutableFloatStateOf(100f) }
    var eraserBlur by remember { mutableFloatStateOf(0f) }
    val sizeDp = if (erasing) eraserSize else sizeByBrush[brush] ?: 10f
    val opacity = if (erasing) eraserOpacity else opacityByBrush[brush] ?: 100f
    val session = if (previewMode) null else remember(ctx) { com.g1.sketchbook.data.SessionStore(ctx) }
    var favorites by remember(session) {
        mutableStateOf(session?.favoriteColors ?: com.g1.sketchbook.data.SessionStore.DefaultFavorites)
    }
    var eyedropArmed by remember { mutableStateOf(false) }
    var eyedropPreview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    val size = remember { Catalog.size("a4") }
    val cw = size.pxW(); val ch = size.pxH()
    // 종이+필기를 합성한 exportBitmap()은 새 비트맵 할당 + 종이질감 합성 그리기라 붓질마다 부르면
    // (특히 해칭처럼 짧은 붓질을 연달아 그을 때) 메인 스레드 렉으로 체감된다. 되돌리기에 쓰이는
    // 필기 전용 content는 단순 복사라 가볍고 손실 위험도 없으니 매 붓질 그대로 저장하고, 무거운
    // 합성 저장(달력 썸네일·백업용)만 그리기가 잠시 멈춘 뒤 한 번 모아서 한다(2026-08-26).
    var pendingCompositeJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleCompositeSave(v: BrushView) {
        pendingCompositeJob?.cancel()
        pendingCompositeJob = scope.launch {
            delay(800)
            val composited = v.exportBitmap() ?: return@launch
            withContext(Dispatchers.IO) {
                repo?.save(date, composited)
                if (myUid.isNotBlank()) repo?.let { backup.pushDiaryDay(myUid, date, composited, it.updatedAt(date)) }
                composited.recycle()
            }
        }
    }

    /** 화면을 나가거나 전체 지우기처럼 결과를 바로 반영해야 할 때 — 대기 중인 디바운스를 취소하고
     *  지금 상태로 즉시 한 번 합성 저장한다. */
    fun flushCompositeSave(v: BrushView) {
        pendingCompositeJob?.cancel()
        val composited = v.exportBitmap() ?: return
        scope.launch(Dispatchers.IO) {
            repo?.save(date, composited)
            if (myUid.isNotBlank()) repo?.let { backup.pushDiaryDay(myUid, date, composited, it.updatedAt(date)) }
            composited.recycle()
        }
    }

    fun saveCurrent(v: BrushView) {
        // 예전엔 "이 항목이 hasEntry인데 hasContent가 아니면 투명 저장 기능 이전(구형) 일기"로 보고
        // 여기서 껐었는데, 백업 동기화가 다른 기기에서 합성 이미지만 먼저 당겨와도(hasEntry=true,
        // hasContent=false) 오늘 막 그린 일기가 구형으로 오판되는 버그가 있었다(2026-08-26). 지금
        // 그리고 있다는 것 자체가 저장할 필기 데이터가 있다는 뜻이라 조건 없이 항상 저장한다 —
        // 구형 일기도 한 번이라도 다시 손대면 그때부터 투명 저장이 지원된다.
        val content = v.exportContent()
        if (content != null) scope.launch(Dispatchers.IO) { repo?.saveContent(date, content); content.recycle() }
        scheduleCompositeSave(v)
    }
    // 스케치북/공유노트와 동일한 오버레이+dock+드래그+최소화+잠금+전체화면 구조로 통일(2026-08-20,
    // 예전엔 캔버스 아래 고정된 단순 바 하나뿐이었음). 다이어리는 하루 단위라 페이지 버튼만 없다.
    // 공유모드와 동일하게 항상 전체화면 — 상태/내비게이션 바 자리까지 그림 영역으로 쓴다.
    val fullscreen = true
    var locked by remember { mutableStateOf(false) }
    var toolbarCollapsed by remember { mutableStateOf(false) }
    var toolbarDock by remember { mutableStateOf(com.g1.sketchbook.brush.ToolbarDock.TOP) }
    var toolbarDragPx by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // 버튼바 도킹 판정에 쓰는, 버튼바가 떠 있는 바깥 컨테이너(BoxWithConstraints)의 화면(루트)
    // 좌표 — DragHandle이 손을 뗀 절대 위치를 이 컨테이너 기준 상대 좌표로 바꾸는 데 필요하다.
    var toolbarContainerRootPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // 나가기 전엔 디바운스 중이던 합성 저장을 기다리지 않고 바로 한 번 반영 — 마지막 몇 붓질이
    // 최대 800ms 늦게 반영되는 것조차 남지 않도록.
    val flushAndBack: () -> Unit = { view?.let(::flushCompositeSave); onBack() }
    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler { flushAndBack() }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() }
            // 버튼바를 좌/우 가장자리로 끌어 도킹하려는 드래그가 시스템 뒤로가기 스와이프에 터치를
            // 뺏길 수 있었다(2026-08-29).
            .excludeSystemGestureEdges()
            .onGloballyPositioned { toolbarContainerRootPos = it.positionInRoot() },
    ) {
        val toolbarContainerWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val toolbarContainerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize().padding(if (fullscreen) 0.dp else Dimens.Canvas.outerPadding), contentAlignment = Alignment.Center) {
            BoxWithConstraints {
                val ratio = cw.toFloat() / ch
                val w = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
                val h = w / ratio
                Box(Modifier.width(w).height(h)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            BrushView(c).also { v ->
                                // "투명 저장" 이전(구형)에 쓰인 일기는 종이+붓질이 이미 한 장으로
                                // 합쳐진 파일(repo.load)만 있고 붓질만 따로 담은 content 파일이
                                // 없다 — 예전엔 이 합쳐진 파일을 그대로 content에 부어 넣어서, 그
                                // 뒤로 아무리 다시 그려도 content 자체에 종이 질감이 눌러붙어 있는
                                // 채였다(투명 배경으로 저장해도 종이가 같이 나오던 버그, 2026-08-29).
                                // 옛 그림은 다시 붓질만 분리할 수 없으니, 대신 그 합쳐진 이미지를
                                // paper 자리에 "고정 배경"으로 깔고 content는 진짜 빈 투명으로
                                // 시작한다 — 화면에 보이는 그림은 그대로지만, 이제부터 새로 그리는
                                // 것만 content에 쌓여서 투명 저장이 실제로 투명해진다.
                                val content = repo?.loadContent(date)
                                v.paper = if (content != null) {
                                    BitmapFactory.decodeResource(c.resources, R.drawable.paper_watercolor)
                                } else {
                                    repo?.load(date) ?: BitmapFactory.decodeResource(c.resources, R.drawable.paper_watercolor)
                                }
                                v.initCanvas(cw, ch)
                                v.loadContent(content)
                                view = v
                            }
                        },
                        update = { v ->
                            v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp; v.opacity = opacity / 100f
                            v.erasing = erasing; v.locked = locked; v.eraserBlur = eraserBlur
                            v.lassoMode = lassoActive; v.fillMode = fillActive
                            v.onLassoSelectionChanged = { has, x, y -> lassoDeleteAt = if (has) Offset(x, y) else null }
                            v.twoFingerTapAction = session?.twoFingerTapAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.threeFingerTapAction = session?.threeFingerTapAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.longPressAction = session?.longPressAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.eyedropArmed = eyedropArmed
                            v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
                            v.onEyedrop = { c -> color = (c.toLong() and 0xFFFFFFFFL); erasing = false; eyedropArmed = false; eyedropPreview = null }
                            v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
                            v.onToggleToolbars = { toolbarCollapsed = !toolbarCollapsed }
                            v.onLassoTapOutside = { lassoActive = false; erasing = preLassoErasing; fillActive = preLassoFillActive }
                            v.onStylusButtonChanged = { pressed ->
                                if (pressed) {
                                    preStylusErasing = erasing; preStylusLasso = lassoActive; preStylusFill = fillActive
                                    erasing = true; lassoActive = false; fillActive = false
                                } else {
                                    erasing = preStylusErasing; lassoActive = preStylusLasso; fillActive = preStylusFill
                                }
                            }
                            v.onStrokeEnd = { saveCurrent(v) }
                        },
                    )
                    eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
                    lassoDeleteAt?.let { p -> com.g1.sketchbook.brush.LassoDeleteButton(p.x, p.y, onDelete = { view?.deleteLassoSelection() }) }
                }
            }
        }
        fun barModifier(dock: com.g1.sketchbook.brush.ToolbarDock, collapsed: Boolean, dragPx: androidx.compose.ui.geometry.Offset) = Modifier
            // RIGHT + 펼침 상태는 화면 중앙 정렬 대신 우측 상단 기준으로 바꾸고, BrushControls의
            // toolbarPadding이 그 자리만큼 위쪽 여백을 남겨서 ScreenControls와 안 겹치게 한다
            // (TOP 도킹의 ScreenControlsClearance와 같은 대응, 2026-08-29).
            .align(if (dock == com.g1.sketchbook.brush.ToolbarDock.RIGHT && !collapsed) Alignment.TopEnd else dock.alignment())
            .let {
                val horizontal = dock == com.g1.sketchbook.brush.ToolbarDock.TOP || dock == com.g1.sketchbook.brush.ToolbarDock.BOTTOM
                if (!collapsed && horizontal) it.fillMaxWidth() else it
            }
            .offset { androidx.compose.ui.unit.IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
        BrushControls(brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false; lassoActive = false; fillActive = false },
            onColor = { color = it; erasing = false },
            onSize = { if (erasing) eraserSize = it else sizeByBrush[brush] = it },
            onOpacity = { if (erasing) eraserOpacity = it else opacityByBrush[brush] = it },
            onToggleErase = { erasing = !erasing; if (erasing) { lassoActive = false; fillActive = false } },
            eraserBlur = eraserBlur, onEraserBlur = { eraserBlur = it },
            onUndo = { view?.undo() }, onRedo = { view?.redo() },
            onClear = {
                view?.clearCanvas()
                view?.let { v ->
                    v.exportContent()?.let { content ->
                        scope.launch(Dispatchers.IO) { repo?.saveContent(date, content); content.recycle() }
                    }
                    flushCompositeSave(v)
                }
            },
            favorites = favorites,
            onEditFavorite = { i, c ->
                val nf = favorites.toMutableList(); nf[i] = c; favorites = nf
                session?.let { it.favoriteColors = nf }
            },
            eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
            lassoActive = lassoActive,
            onToggleLasso = {
                if (!lassoActive) { preLassoErasing = erasing; preLassoFillActive = fillActive }
                lassoActive = !lassoActive
                if (lassoActive) { erasing = false; fillActive = false }
            },
            fillActive = fillActive,
            onToggleFill = { fillActive = !fillActive; if (fillActive) { erasing = false; lassoActive = false } },
            collapsed = toolbarCollapsed, onToggleCollapsed = { toolbarCollapsed = !toolbarCollapsed },
            onDragBar = { d -> toolbarDragPx += d },
            onDragBarEnd = { targetDock ->
                toolbarDock = targetDock
                toolbarDragPx = androidx.compose.ui.geometry.Offset.Zero
            },
            containerRootPos = toolbarContainerRootPos,
            containerWidthPx = toolbarContainerWidthPx,
            containerHeightPx = toolbarContainerHeightPx,
            dock = toolbarDock,
            modifier = barModifier(toolbarDock, toolbarCollapsed, toolbarDragPx),
        )
        // 화면버튼(회전/잠금/전체화면)은 가로/세로 상관없이 항상 우측 상단에 고정된 확장 버튼 —
        // 탭하면 펼쳐지고 기능을 고르거나 밖을 탭하면 자동으로 닫힌다(2026-08-20).
        com.g1.sketchbook.brush.ScreenControls(
            onRotate = { view?.rotate() },
            locked = locked, onToggleLock = { locked = !locked },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

// ---------------- 일기달력 (browse past diaries) ----------------

/** Slide 2 — calendar tab: huge month title, edit/prev/next, and an airy (borderless) month grid.
 *  Tapping the grid opens the clean full-screen calendar (slides 3/4). */
@Composable
fun DiaryCalendarScreen(
    onOpenDiary: (String) -> Unit,
    onOpenCalendar: (Int, Int) -> Unit,
    previewMarkedDates: Set<String>? = null,
) {
    val ctx = LocalContext.current
    val repo = if (previewMarkedDates == null) remember(ctx) { DiaryRepository(ctx) } else null
    val today = remember(repo) { repo?.today() ?: "2026-08-17" }
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(if (previewMarkedDates == null) now.get(Calendar.YEAR) else 2026) }
    var month by remember { mutableIntStateOf(if (previewMarkedDates == null) now.get(Calendar.MONTH) else 7) } // 0-based
    var marked by remember(previewMarkedDates) { mutableStateOf(previewMarkedDates ?: emptySet()) }
    LaunchedEffect(year, month, repo) {
        if (repo != null) marked = withContext(Dispatchers.IO) { datesWithDiary(repo, year, month) }
    }

    com.g1.sketchbook.ui.main.MainTabPage(
        title = "A piece of today",
        actions = {
            IconButton(onClick = { onOpenDiary(today) }) {
                Image(
                    painterResource(R.drawable.paint_palette_1),
                    "오늘 일기 그리기",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(Dimens.Calendar.editIcon),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "$year.${(month + 1).toString().padStart(2, '0')}", fontFamily = Cavorting, fontSize = Dimens.Calendar.yearMonthSp,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.align(Alignment.Center),
            )
            // 화살표 크기(너비/높이)는 Dimens.Calendar.arrowIconW/arrowIconH에서 조절.
            // 화살표 획(선) 두께는 크기와 별개로 ChevronArrow 함수 안의 `w * 0.5f`에서 조절.
            Box(Modifier.align(Alignment.CenterStart).size(48.dp)
                .bounceClick { if (month == 0) { month = 11; year-- } else month-- }, contentAlignment = Alignment.Center) {
                ChevronArrow(pointLeft = true, modifier = Modifier.width(Dimens.Calendar.arrowIconW).height(Dimens.Calendar.arrowIconH))
            }
            Box(Modifier.align(Alignment.CenterEnd).size(48.dp)
                .bounceClick { if (month == 11) { month = 0; year++ } else month++ }, contentAlignment = Alignment.Center) {
                ChevronArrow(pointLeft = false, modifier = Modifier.width(Dimens.Calendar.arrowIconW).height(Dimens.Calendar.arrowIconH))
            }
        }
        Text(
            "이번 달 ${marked.size}일 기록", fontFamily = Cavorting, fontSize = Dimens.Calendar.summarySp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Spacer(Modifier.height(Dimens.Calendar.titleGap))
        AiryCalendar(year, month, marked, today, onTap = { onOpenCalendar(year, month) }, Modifier.weight(1f).fillMaxWidth())
    }
}

/** A chevron drawn to exactly fill its (non-square) box — Material's ChevronLeft/Right are a square
 *  viewBox, so forcing them into a narrow 10x20dp box just shrinks the whole glyph to fit the 10dp
 *  width (Fit-scaled), reading as "too small". Drawing the two strokes directly avoids that. */
@Composable
private fun ChevronArrow(pointLeft: Boolean, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        // 획 두께(선 굵기) — 박스 너비(w)의 50%. 크기(Dimens.Calendar.arrowIconW/H)와는 별개 값이라
        // 두께만 바꾸고 싶으면 이 배율(0.5f)만 조절하면 된다.
        val stroke = Stroke(width = w * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            if (pointLeft) { moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h) }
            else { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h) }
        }
        drawPath(path, color, style = stroke)
    }
}

private val WeekHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val FullWeekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MonthNames = listOf("January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December")
private val TodayPink = Color(0xFFE3B7B7)
private val DiaryDot = Color(0xFF8FA07E)
private val DiaryDotToday = Color(0xFF4A6741) // 오늘 + 그림 있음 — 오늘 표시(분홍)를 대신하는 짙은 초록

private fun ordinal(d: Int): String = when {
    d in 11..13 -> "th"
    d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"
    else -> "th"
}

/** "YYYY-MM-DD" 문자열을 deltaDays만큼(음수면 이전) 이동한 날짜 문자열로 변환 — 월/연 경계도 처리. */
private fun shiftDate(date: String, deltaDays: Int): String {
    val parts = date.split("-")
    val cal = Calendar.getInstance().apply {
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        add(Calendar.DAY_OF_MONTH, deltaDays)
    }
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

private fun monthCells(year: Int, month: Int): List<Int> {
    val cal = Calendar.getInstance().apply { set(year, month, 1) }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    return buildList {
        repeat(firstDow) { add(0) }
        for (d in 1..days) add(d)
        while (size < 42) add(0)   // 6 week rows × 7 columns
    }
}

/** Airy borderless month: today wears a pink disc, days with a diary get a dot below the number. */
@Composable
private fun AiryCalendar(year: Int, month: Int, marked: Set<String>, today: String, onTap: () -> Unit,
                         modifier: Modifier = Modifier) {
    val cells = remember(year, month) { monthCells(year, month) }
    Column(modifier.bounceClick { onTap() }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            WeekHeaders.forEach { wd ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(wd, fontFamily = Cavorting, fontSize = Dimens.Calendar.weekdaySp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day > 0) {
                            val date = "%04d-%02d-%02d".format(year, month + 1, day)
                            val isToday = date == today
                            val hasDiary = date in marked
                            // 화면이 작으면 숫자 아래 따로 그리던 작은 초록 점이 잘려서 안 보이는
                            // 문제가 있었다(2026-08-26) — 대신 "오늘" 표시와 같은 자리·같은 크기의
                            // 원 하나로 통일: 그림 있음=초록, 오늘=분홍, 오늘+그림 있음=짙은 초록.
                            val circleColor = when {
                                isToday && hasDiary -> DiaryDotToday
                                isToday -> TodayPink
                                hasDiary -> DiaryDot
                                else -> null
                            }
                            Box(Modifier.size(Dimens.Calendar.todayDisc), contentAlignment = Alignment.Center) {
                                circleColor?.let { Box(Modifier.size(Dimens.Calendar.todayDisc).shadow(4.dp, CircleShape).background(it)) }
                                Text("$day", fontFamily = Cavorting, fontSize = Dimens.Calendar.daySp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Slides 3 & 4 — clean, bar-less. 가로일 때만 좌측 썸네일 달력(6×7) + 우측 지금 고른 날짜의
 *  스케치를 항상 같이 보여준다(2026-08-26). 세로는 원래 방향 그대로: 달력만 전체화면으로 보이다가
 *  날짜를 탭하면 그 스케치로 화면이 통째로 바뀌는 1단계 방식(뒤로가기로 달력으로 돌아옴). */
@Composable
fun CleanCalendarScreen(
    year: Int,
    month: Int,
    onBack: () -> Unit,
    /** 오늘 날짜에 아직 일기가 없을 때 상세 화면에 뜨는 "오늘 그리기 시작" 버튼에서 호출 —
     *  DiaryEditorScreen을 열어 바로 스케치모드로 들어간다(2026-08-29). */
    onOpenDiary: (String) -> Unit = {},
    previewDetailDate: String? = null,
    previewMode: Boolean = false,
    previewBitmap: Bitmap? = null,
) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    val today = remember(repo) { repo?.today() ?: "2026-08-17" }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 우측/전체 상세에서 좌우 스와이프로 날짜를 넘기면 월 경계를 넘을 수 있어 연/월을 내부 상태로
    // 들고 있는다(파라미터 year/month는 최초 진입 시점의 값일 뿐).
    var curYear by remember { mutableIntStateOf(year) }
    var curMonth by remember { mutableIntStateOf(month) }
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(curYear, curMonth, repo) {
        if (repo != null) thumbs = withContext(Dispatchers.IO) { buildThumbs(repo, curYear, curMonth) }
    }
    // null = (세로 전용) 달력만 보이는 상태. 가로에선 null이어도 오늘 날짜를 우측에 띄운다.
    var detailDate by remember(previewDetailDate) { mutableStateOf(previewDetailDate) }
    fun navigate(newDate: String) {
        detailDate = newDate
        val parts = newDate.split("-")
        curYear = parts[0].toInt()
        curMonth = parts[1].toInt() - 1
    }

    BackHandler { if (!landscape && detailDate != null) detailDate = null else onBack() }

    if (landscape) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.weight(0.42f).fillMaxHeight().systemBarsPadding()
                    .padding(start = Dimens.CleanCalendar.landscapeSidePadding, end = Dimens.CleanCalendar.landscapeSidePadding,
                        top = Dimens.CleanCalendar.landscapeTopPadding, bottom = Dimens.CleanCalendar.landscapeBottomPadding),
            ) {
                // 가로는 연·월을 한 줄로 붙여서(세로처럼 두 줄로 쌓지 않고) 위쪽에 남는 높이를
                // 최소화 — 그만큼 아래 썸네일 달력이 커진다(2026-08-29, 재요청).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text(MonthNames[curMonth], fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.landscapeMonthSp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("$curYear", fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.landscapeYearSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Dimens.CleanCalendar.titleGap))
                CleanGrid(curYear, curMonth, thumbs, Modifier.weight(1f).fillMaxWidth()) { navigate(it) }
            }
            // 우측 상세는 남은 폭을 꽉 채운다 — 그림이 그 안을 채우고 날짜는 그 위 워터마크로만 표시.
            CleanDetailBody(repo, detailDate ?: today, Modifier.weight(0.58f).fillMaxHeight(), today = today, onOpenDiary = onOpenDiary, previewBitmap = previewBitmap) { navigate(it) }
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (detailDate == null) {
                Column(Modifier.fillMaxSize().systemBarsPadding()
                    .padding(start = Dimens.CleanCalendar.sidePadding, end = Dimens.CleanCalendar.sidePadding,
                        top = Dimens.CleanCalendar.topPadding, bottom = Dimens.CleanCalendar.bottomPadding)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$curYear", fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.yearSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MonthNames[curMonth], fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.monthSp, maxLines = 1)
                    }
                    Spacer(Modifier.height(Dimens.CleanCalendar.titleGap))
                    CleanGrid(curYear, curMonth, thumbs, Modifier.weight(1f).fillMaxWidth()) { navigate(it) }
                }
            } else {
                // 일자 상세는 전체화면 — 그림이 화면을 꽉 채우고 날짜는 그 위 워터마크로만 표시(별도 헤더 없음).
                CleanDetailBody(repo, detailDate!!, Modifier.fillMaxSize(), today = today, onOpenDiary = onOpenDiary, previewBitmap = previewBitmap) { navigate(it) }
            }
        }
    }
}

@Composable
private fun CleanGrid(year: Int, month: Int, thumbs: Map<String, ImageBitmap>, modifier: Modifier, onDay: (String) -> Unit) {
    val cells = remember(year, month) { monthCells(year, month) }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        // Same header height as slide 4's weekday/day row so the table below matches the sketch frame.
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            WeekHeaders.forEach { wd ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(wd, fontFamily = Cavorting, fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).fillMaxWidth().padding(4.dp).border(1.dp, MaterialTheme.colorScheme.outline)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line)) {
                            if (day > 0) {
                                val date = "%04d-%02d-%02d".format(year, month + 1, day)
                                Box(Modifier.fillMaxSize().clickable { onDay(date) }) {
                                    thumbs[date]?.let {
                                        Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                    Text("$day", fontFamily = Cavorting, fontSize = 14.sp,
                                        color = if (thumbs[date] != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanDetailBody(
    repo: DiaryRepository?, date: String, modifier: Modifier,
    today: String = date, onOpenDiary: (String) -> Unit = {},
    previewBitmap: Bitmap? = null,
    onNavigate: (String) -> Unit,
) {
    val bmp = remember(date, repo, previewBitmap) { previewBitmap ?: repo?.load(date) }
    val parts = date.split("-")
    val ctx = LocalContext.current
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showOverlayPlacement by remember(date) { mutableStateOf(false) }
    // 보기 전용 확대/축소 — 그림 자체는 수정하지 않으므로 BrushView의 캔버스가 아니라 표준 Compose
    // 제스처로 충분(1x~5x, 다 축소하면 팬도 원점으로 되돌림).
    var scale by remember(date) { mutableFloatStateOf(1f) }
    var offsetX by remember(date) { mutableFloatStateOf(0f) }
    var offsetY by remember(date) { mutableFloatStateOf(0f) }
    // 좌우 스와이프로 전날/다음날 이동 — 확대 중(scale>1)일 때는 팬으로 쓰이므로 스와이프를 건드리지
    // 않고, 배율이 1일 때만 누적해서 임계값을 넘으면 즉시 날짜를 바꾼다(release까지 기다리지 않음).
    var swipeAccum by remember(date) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    BackHandler(enabled = showOverlayPlacement) { showOverlayPlacement = false }
    val dateLabel = "${parts[0]}.${parts[1]}.${parts[2]}"
    Box(modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), date, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(date) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1f) {
                                    offsetX = 0f; offsetY = 0f
                                    swipeAccum += pan.x
                                    if (swipeAccum > swipeThresholdPx) { onNavigate(shiftDate(date, -1)); swipeAccum = 0f }
                                    else if (swipeAccum < -swipeThresholdPx) { onNavigate(shiftDate(date, 1)); swipeAccum = 0f }
                                } else { offsetX += pan.x; offsetY += pan.y }
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
                )
                // 날짜는 헤더 줄 없이 이미지 위 워터마크로만 — 스케치가 화면을 그대로 꽉 채운다.
                Text(
                    dateLabel, fontFamily = Cavorting, fontSize = 22.sp,
                    color = Color.Black.copy(alpha = 0.75f),
                    style = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.5f), blurRadius = 8f)),
                    modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 18.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 오늘 날짜에만 — 이미 그린 오늘자 일기를 다시 열어 이어 그릴 수 있게 저장 버튼
                    // 왼쪽에 그리기 버튼을 둔다(지난 날짜는 그림일기 특성상 수정 불가, 2026-08-29 재요청).
                    if (date == today) {
                        IconButton(
                            onClick = { onOpenDiary(date) },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)),
                        ) {
                            Icon(Icons.Filled.Edit, "오늘 일기 다시 그리기", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = { showDownloadDialog = true },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)),
                    ) {
                        Icon(Icons.Filled.Save, "다운로드", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (showDownloadDialog) {
                    DownloadOptionsDialog(
                        onDismiss = { showDownloadDialog = false },
                        onPlain = {
                            showDownloadDialog = false
                            val status = saveToGallery(ctx, bmp, "daymory_$date")
                            Toast.makeText(ctx, status, Toast.LENGTH_SHORT).show()
                        },
                        onTransparent = {
                            showDownloadDialog = false
                            val content = repo?.loadContent(date)
                            val status = if (content == null) {
                                "기존 일기는 투명 배경 저장을 지원하지 않아요"
                            } else {
                                saveToGallery(ctx, content, "daymory_${date}_transparent").also {
                                    content.recycle()
                                }
                            }
                            Toast.makeText(ctx, status, Toast.LENGTH_SHORT).show()
                        },
                        onFramed = {
                            showDownloadDialog = false
                            val framed = renderFramedDiaryBitmap(ctx, bmp, date)
                            val status = saveToGallery(ctx, framed, "daymory_${date}_frame")
                            Toast.makeText(ctx, status, Toast.LENGTH_SHORT).show()
                        },
                        onOverlay = {
                            showDownloadDialog = false
                            showOverlayPlacement = true
                        },
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxSize().pointerInput(date) {
                        detectHorizontalDragGestures(onDragCancel = { swipeAccum = 0f }) { change, dragAmount ->
                            swipeAccum += dragAmount
                            if (swipeAccum > swipeThresholdPx) { onNavigate(shiftDate(date, -1)); swipeAccum = 0f }
                            else if (swipeAccum < -swipeThresholdPx) { onNavigate(shiftDate(date, 1)); swipeAccum = 0f }
                            change.consume()
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dateLabel, fontFamily = Cavorting, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("이 날의 일기가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        // 오늘 날짜에 아직 일기가 없을 때만 — 달력에서 여기까지 들어온 김에 바로
                        // 스케치모드로 진입할 수 있게(2026-08-29, 재요청). 지난 날짜는 그림일기 특성상
                        // 새로 그릴 수 없으므로 버튼을 안 보여준다.
                        if (date == today) {
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .bounceClick { onOpenDiary(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Edit, "오늘 그리기 시작", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
        if (showOverlayPlacement && bmp != null) {
            CalendarOverlayPlacementScreen(
                bmp = bmp, date = date,
                onCancel = { showOverlayPlacement = false },
                onSave = { placement ->
                    showOverlayPlacement = false
                    val composited = renderCalendarOverlayDiaryBitmap(ctx, bmp, date, placement)
                    val status = saveToGallery(ctx, composited, "daymory_${date}_calendar")
                    Toast.makeText(ctx, status, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** 저장 아이콘 탭 시 뜨는 다운로드 버전 선택 시트 (4가지 — 스케치 / 투명 PNG / 액자 / 달력).
 *  텍스트 없이 아이콘만 나란히 보여준다. */
@Composable
private fun DownloadOptionsDialog(
    onDismiss: () -> Unit,
    onPlain: () -> Unit,
    onTransparent: () -> Unit,
    onFramed: () -> Unit,
    onOverlay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이미지로 저장") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DownloadChoiceIcon("스케치만 다운로드", onPlain) { tint ->
                    Icon(Icons.Filled.Image, null, tint = tint, modifier = Modifier.size(24.dp))
                }
                DownloadChoiceIcon("투명 배경 PNG로 다운로드", onTransparent) { tint ->
                    Icon(Icons.Filled.Opacity, null, tint = tint, modifier = Modifier.size(24.dp))
                }
                DownloadChoiceIcon("액자 구성으로 다운로드", onFramed) { tint ->
                    PolaroidIcon(tint = tint, modifier = Modifier.size(24.dp))
                }
                DownloadChoiceIcon("달력 오버레이로 다운로드", onOverlay) { tint ->
                    Icon(Icons.Filled.CalendarMonth, null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun DownloadChoiceIcon(contentDescription: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .bounceClick(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon(MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** 폴라로이드 사진 모양 아이콘(테두리 카드 + 위쪽 사진 영역, 아래쪽은 폴라로이드 특유의 여백) —
 *  머티리얼 아이콘 세트엔 이 모양이 없어 Canvas로 직접 그린다. */
@Composable
private fun PolaroidIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = size.width * 0.09f
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
            size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
            cornerRadius = CornerRadius(size.width * 0.08f),
            style = Stroke(width = strokeWidthPx),
        )
        val photoInset = size.width * 0.17f
        val photoTop = size.height * 0.17f
        val photoBottom = size.height * 0.64f
        drawRect(
            color = tint,
            topLeft = Offset(photoInset, photoTop),
            size = Size(size.width - photoInset * 2, photoBottom - photoTop),
        )
    }
}

/** 달력 오버레이의 글자 요소(연/월/일) 하나에 대한 스타일 — 색상·글자 크기·폰트를 각각 독립적으로. */
internal data class OverlayElementStyle(val colorArgb: Long, val fontSp: Float, val fontRes: Int)

/** 달력 오버레이 배치에서 조절 가능한 값들 — 위치(0~1 비율)·표 크기(가로/세로 별도)·연/월/일 각각의
 *  스타일·오늘 날짜 강조 원 표시 여부·배경(껐다 켰다 가능, 켜면 색·불투명도 별도 조절). 배치 화면과
 *  최종 저장 렌더(`renderCalendarOverlayDiaryBitmap`)가 이 하나의 값 묶음을 공유. */
internal data class OverlayPlacement(
    val fracX: Float, val fracY: Float,
    val gridWidthFraction: Float, val gridHeightFraction: Float,
    val year: OverlayElementStyle, val month: OverlayElementStyle, val day: OverlayElementStyle,
    val showTodayCircle: Boolean,
    val backgroundEnabled: Boolean, val backgroundColorArgb: Long, val backgroundOpacity: Float,
)

private enum class OverlayFont(val label: String, val family: FontFamily, val fontRes: Int) {
    HANDWRITTEN("손글씨", Cavorting, R.font.cavorting),
    CLEAN("고딕", Pretendard, R.font.pretendard_medium),
    SERIF("세리프", BodoniMTBlack, R.font.bodoni_mt_black),
}

private fun fontFamilyFor(fontRes: Int): FontFamily = when (fontRes) {
    R.font.pretendard_medium -> Pretendard
    R.font.bodoni_mt_black -> BodoniMTBlack
    else -> Cavorting
}

private val DefaultYearStyle = OverlayElementStyle(0xFF1E2D4CL, 13f, R.font.cavorting)
private val DefaultMonthStyle = OverlayElementStyle(0xFF1E2D4CL, 19f, R.font.cavorting)
private val DefaultDayStyle = OverlayElementStyle(0xFF1E2D4CL, 12f, R.font.cavorting)

private enum class OverlayElement(val letter: String) { YEAR("Y"), MONTH("M"), DAY("D") }
private val OverlayAccentColor = Color(0xFF8A8A8A) // 무채색 슬라이더 강조색(브러시 굵기 슬라이더의 빨강 대신)
private val OverlayFontSizeRange = 8f..60f

/** 달력 오버레이 배치 전용 화면 — 스케치를 원본 비율 그대로 크게 보여주고, 그 위에 미니 달력을
 *  올린다. 상호작용:
 *  - 스티커 본체를 바로 드래그 = 위치 이동(별도 이동 버튼 없음).
 *  - 스티커를 길게 누르면 = "달력 설정" 다이얼로그(연/월/일 각각의 색상·글자크기·폰트, 오늘 강조 원
 *    on/off, 배경 on/off + 색·불투명도).
 *  - 오른쪽 가운데/아래 가운데 필(pill) 손잡이 드래그 = 표의 가로/세로 크기를 각각 독립적으로
 *    (글자 크기는 안 바뀜 — "폰트 폭은 고정").
 *  상단 취소(X)/저장(체크) 버튼만(설정 버튼은 없음 — 길게 누르기로 대체). */
@Composable
internal fun CalendarOverlayPlacementScreen(bmp: Bitmap, date: String, onCancel: () -> Unit, onSave: (OverlayPlacement) -> Unit) {
    val parts = date.split("-")
    val year = parts[0].toInt(); val month = parts[1].toInt() - 1; val day = parts[2].toInt()
    var fracX by remember(date) { mutableFloatStateOf(0.5f) }
    var fracY by remember(date) { mutableFloatStateOf(0.5f) }
    var gridWFrac by remember(date) { mutableFloatStateOf(0.42f) }
    var gridHFrac by remember(date) { mutableFloatStateOf(0.3f) }
    var yearStyle by remember(date) { mutableStateOf(DefaultYearStyle) }
    var monthStyle by remember(date) { mutableStateOf(DefaultMonthStyle) }
    var dayStyle by remember(date) { mutableStateOf(DefaultDayStyle) }
    var showTodayCircle by remember(date) { mutableStateOf(true) }
    var backgroundEnabled by remember(date) { mutableStateOf(false) }
    var backgroundColorArgb by remember(date) { mutableStateOf(0xFFFFFFFFL) }
    var backgroundOpacity by remember(date) { mutableFloatStateOf(0.75f) }
    var showSettings by remember { mutableStateOf(false) }
    var stickerSizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(
            Modifier.align(Alignment.Center).fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
        ) {
            val boxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
            Image(bmp.asImageBitmap(), date, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            val gridWidth = maxWidth * gridWFrac
            val gridHeight = maxHeight * gridHFrac
            Box(
                Modifier.offset {
                    IntOffset(
                        (fracX * boxWidthPx - stickerSizePx.width / 2).roundToInt(),
                        (fracY * boxHeightPx - stickerSizePx.height / 2).roundToInt(),
                    )
                }.onGloballyPositioned { stickerSizePx = it.size },
            ) {
                Box(
                    Modifier
                        .pointerInput(date) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                fracX = (fracX + drag.x / boxWidthPx).coerceIn(0f, 1f)
                                fracY = (fracY + drag.y / boxHeightPx).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(date) {
                            detectTapGestures(onLongPress = { showSettings = true })
                        },
                ) {
                    MiniCalendarSticker(
                        year, month, day, gridWidth, gridHeight, yearStyle, monthStyle, dayStyle,
                        showTodayCircle, backgroundEnabled, backgroundColorArgb, backgroundOpacity,
                    )
                }
                OverlayHandle(Modifier.align(Alignment.CenterEnd).offset(16.dp, 0.dp), pillWidth = 5.dp, pillHeight = 23.dp, "가로 크기") { drag ->
                    gridWFrac = (gridWFrac + drag.x / boxWidthPx).coerceIn(0.12f, 0.9f)
                }
                OverlayHandle(Modifier.align(Alignment.BottomCenter).offset(0.dp, 16.dp), pillWidth = 23.dp, pillHeight = 5.dp, "세로 크기") { drag ->
                    gridHFrac = (gridHFrac + drag.y / boxHeightPx).coerceIn(0.1f, 0.9f)
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)),
            ) { Icon(Icons.Filled.Close, "취소", tint = Color.White) }
            IconButton(
                onClick = {
                    onSave(
                        OverlayPlacement(
                            fracX, fracY, gridWFrac, gridHFrac, yearStyle, monthStyle, dayStyle,
                            showTodayCircle, backgroundEnabled, backgroundColorArgb, backgroundOpacity,
                        ),
                    )
                },
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)),
            ) { Icon(Icons.Filled.Check, "저장", tint = Color.White) }
        }
    }

    if (showSettings) {
        CalendarSettingsDialog(
            year, month, day, yearStyle, monthStyle, dayStyle,
            onYearChange = { yearStyle = it }, onMonthChange = { monthStyle = it }, onDayChange = { dayStyle = it },
            showTodayCircle = showTodayCircle, onShowTodayCircleChange = { showTodayCircle = it },
            backgroundEnabled = backgroundEnabled, onBackgroundEnabledChange = { backgroundEnabled = it },
            backgroundColorArgb = backgroundColorArgb, onBackgroundColorChange = { backgroundColorArgb = it },
            backgroundOpacity = backgroundOpacity, onBackgroundOpacityChange = { backgroundOpacity = it },
            onDismiss = { showSettings = false },
        )
    }
}

/** 크기 조절 손잡이 — 배경 없이 얇은 필(pill) 막대만. 가로 손잡이는 세로로 긴 필, 세로 손잡이는
 *  가로로 긴 필(드래그 방향을 암시). 크기 50%·불투명도 70%로 눈에 덜 띄게. `onDrag`은 화면 px 단위
 *  누적 드래그 벡터를 그대로 받는다(호출부에서 각자 필요한 비율로 변환). */
@Composable
private fun OverlayHandle(
    modifier: Modifier, pillWidth: Dp, pillHeight: Dp, contentDescription: String, onDrag: (Offset) -> Unit,
) {
    Box(
        modifier.size(40.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag) } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(pillWidth, pillHeight).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f))
                .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(50)),
        )
    }
}

/** 달력을 길게 눌러 여는 "달력 설정" 다이얼로그 — 화면 가운데에 뜬다. 위에 실시간 미리보기,
 *  연/월/일 중 어느 걸 편집할지는 Y/M/D 글자 버튼으로 고른다(아이콘은 구분이 잘 안 간다는 피드백
 *  으로 문자로 교체). 글자 크기·색상은 버튼으로 나누지 않고 항상 같이 보인다 — 글자 크기는 브러시
 *  굵기와 같은 슬라이더 디자인(무채색), 값은 슬라이더 썸을 탭하면 직접 숫자로 입력 가능. 색상은
 *  기존 브러시 색상휠(`ColorPickerCard`) 재사용. 서체 칩은 각자의 실제 폰트로 렌더링해서 미리 보인다.
 *  아래에 "오늘 날짜 강조 원"·"달력 배경" on/off 스위치, 배경을 켜면 배경 색·불투명도도 추가로
 *  나온다. 내부 구성요소는 전부 가운데 정렬. */
@Composable
private fun CalendarSettingsDialog(
    year: Int, month: Int, day: Int,
    yearStyle: OverlayElementStyle, monthStyle: OverlayElementStyle, dayStyle: OverlayElementStyle,
    onYearChange: (OverlayElementStyle) -> Unit, onMonthChange: (OverlayElementStyle) -> Unit, onDayChange: (OverlayElementStyle) -> Unit,
    showTodayCircle: Boolean, onShowTodayCircleChange: (Boolean) -> Unit,
    backgroundEnabled: Boolean, onBackgroundEnabledChange: (Boolean) -> Unit,
    backgroundColorArgb: Long, onBackgroundColorChange: (Long) -> Unit,
    backgroundOpacity: Float, onBackgroundOpacityChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedElement by remember { mutableStateOf(OverlayElement.YEAR) }
    val style: OverlayElementStyle
    val onChange: (OverlayElementStyle) -> Unit
    when (selectedElement) {
        OverlayElement.YEAR -> { style = yearStyle; onChange = onYearChange }
        OverlayElement.MONTH -> { style = monthStyle; onChange = onMonthChange }
        OverlayElement.DAY -> { style = dayStyle; onChange = onDayChange }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(
                    Modifier.width(280.dp).padding(16.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("달력 설정", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    MiniCalendarSticker(
                        year, month, day, 160.dp, 190.dp, yearStyle, monthStyle, dayStyle,
                        showTodayCircle, backgroundEnabled, backgroundColorArgb, backgroundOpacity,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OverlayElement.entries.forEach { el ->
                            OverlayToggleText(el.letter, selectedElement == el) { selectedElement = el }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FontSizeEditor(style, selectedElement, onChange)
                    Spacer(Modifier.height(14.dp))
                    ColorPickerCard(style.colorArgb, onColor = { onChange(style.copy(colorArgb = it)) })
                    Spacer(Modifier.height(14.dp))
                    Text("서체", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayFont.entries.forEach { f ->
                            val selected = f.fontRes == style.fontRes
                            Surface(
                                onClick = { onChange(style.copy(fontRes = f.fontRes)) }, shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    f.label, fontFamily = f.family, fontSize = 13.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    OverlayToggleRow("오늘 날짜 강조 원", showTodayCircle, onShowTodayCircleChange)
                    Spacer(Modifier.height(10.dp))
                    OverlayToggleRow("달력 배경", backgroundEnabled, onBackgroundEnabledChange)
                    if (backgroundEnabled) {
                        Spacer(Modifier.height(14.dp))
                        IconSliderRow(
                            Icons.Filled.Opacity, "배경 불투명도", "${(backgroundOpacity * 100).roundToInt()}",
                            backgroundOpacity, 0f..1f, onChange = onBackgroundOpacityChange, accentColor = OverlayAccentColor,
                        )
                        Spacer(Modifier.height(10.dp))
                        ColorPickerCard(backgroundColorArgb, onColor = onBackgroundColorChange)
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) { Text("완료") }
                }
            }
        }
    }
}

@Composable
private fun OverlayToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OverlayToggleText(letter: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(
                letter, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 글자 크기 편집 — 브러시 굵기 슬라이더와 같은 트랙/썸 디자인(무채색). 상시 노출 입력칸은 없고,
 *  슬라이더 썸(동그란 손잡이)을 탭하면 그 자리에서 숫자를 직접 입력하는 모드로 바뀐다(완료 또는
 *  키보드 확인으로 다시 슬라이더로 복귀). `selectedElement`가 바뀔 때만 편집 모드를 초기화한다. */
@Composable
private fun FontSizeEditor(style: OverlayElementStyle, selectedElement: OverlayElement, onChange: (OverlayElementStyle) -> Unit) {
    var editingText by remember(selectedElement) { mutableStateOf(false) }
    var ptText by remember(selectedElement) { mutableStateOf(style.fontSp.roundToInt().toString()) }
    fun commit() {
        ptText.toFloatOrNull()?.let { onChange(style.copy(fontSp = it.coerceIn(OverlayFontSizeRange.start, OverlayFontSizeRange.endInclusive))) }
        editingText = false
    }
    if (editingText) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FormatSize, "글자 크기", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = ptText, onValueChange = { ptText = it },
                modifier = Modifier.width(100.dp).focusRequester(focusRequester),
                singleLine = true, textStyle = TextStyle(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                suffix = { Text("pt", fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { commit() }) { Icon(Icons.Filled.Check, "확인", modifier = Modifier.size(20.dp)) }
        }
    } else {
        IconSliderRow(
            Icons.Filled.FormatSize, "글자 크기", "${style.fontSp.roundToInt()}", style.fontSp, OverlayFontSizeRange,
            onChange = { v -> onChange(style.copy(fontSp = v)) },
            accentColor = OverlayAccentColor,
            onThumbClick = { ptText = style.fontSp.roundToInt().toString(); editingText = true },
        )
    }
}

/** 미니 달력 스티커 — 연도·월·요일 헤더·6주 그리드, 지금 보고 있는 날짜는 `showTodayCircle`이 켜져
 *  있을 때만 분홍 원으로 강조(다른 날의 기록 여부는 표시 안 함). 전체 크기는 `widthDp`×`heightDp`
 *  고정이고 그 안에서 연/월/요일 줄은 각자 글자 크기만큼만 차지, 날짜 그리드가 나머지를 채운다 —
 *  그래서 표 크기(가로/세로)를 조절해도 글자 크기는 안 바뀐다("폰트 폭은 고정"). `backgroundEnabled`가
 *  켜져 있으면 둥근 사각형 배경(지정한 색·불투명도)을 깔고 내용에 살짝 안쪽 여백을 준다. 배치 화면·
 *  설정 다이얼로그 미리보기 양쪽 모두 이 컴포저블 하나를 그대로 재사용(순수 미리보기 — 자체 탭/드래그
 *  없음). */
@Composable
private fun MiniCalendarSticker(
    year: Int, month: Int, day: Int, widthDp: Dp, heightDp: Dp,
    yearStyle: OverlayElementStyle, monthStyle: OverlayElementStyle, dayStyle: OverlayElementStyle,
    showTodayCircle: Boolean, backgroundEnabled: Boolean, backgroundColorArgb: Long, backgroundOpacity: Float,
) {
    val cells = remember(year, month) { monthCells(year, month) }
    Column(
        Modifier.size(widthDp, heightDp)
            .let {
                if (backgroundEnabled) {
                    it.background(Color(backgroundColorArgb).copy(alpha = backgroundOpacity), RoundedCornerShape(12.dp))
                        .padding(6.dp)
                } else it
            },
    ) {
        Text(
            "$year", fontFamily = fontFamilyFor(yearStyle.fontRes), fontSize = yearStyle.fontSp.sp,
            color = Color(yearStyle.colorArgb), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            MonthNames[month], fontFamily = fontFamilyFor(monthStyle.fontRes), fontSize = monthStyle.fontSp.sp,
            color = Color(monthStyle.colorArgb), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            WeekHeaders.forEach { wd ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        wd, fontFamily = fontFamilyFor(dayStyle.fontRes), fontSize = (dayStyle.fontSp * 0.7f).sp,
                        color = Color(dayStyle.colorArgb).copy(alpha = 0.6f),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (d > 0) {
                                if (d == day && showTodayCircle) Box(Modifier.fillMaxSize(0.75f).clip(CircleShape).background(TodayPink))
                                Text("$d", fontFamily = fontFamilyFor(dayStyle.fontRes), fontSize = dayStyle.fontSp.sp, color = Color(dayStyle.colorArgb))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val OverlayLineHeightMultiplier = 1.3f

/** 다운로드 옵션 "달력 오버레이" — 배치 화면에서 고른 `OverlayPlacement`(위치·표 크기·연/월/일 각각의
 *  색상·글자크기·폰트) 그대로 미니 달력을 원본 해상도로 다시 그려 스케치 비트맵 위에 합성한다(배경
 *  없이 텍스트만). 연/월/요일 줄 높이는 Compose 쪽과 마찬가지로 각 줄의 글자 크기(fontSp × 화면
 *  밀도)에서 유도 — 표 크기를 조절해도 글자 크기 자체는 안 바뀐다는 규칙을 저장 결과에도 그대로 반영. */
private fun renderCalendarOverlayDiaryBitmap(ctx: Context, bmp: Bitmap, date: String, placement: OverlayPlacement): Bitmap {
    val parts = date.split("-")
    val year = parts[0].toInt(); val month = parts[1].toInt() - 1; val day = parts[2].toInt()
    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    val density = ctx.resources.displayMetrics.density

    fun paint(style: OverlayElementStyle, sizePx: Float, alphaOverride: Int? = null) =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = style.colorArgb.toInt()
            if (alphaOverride != null) alpha = alphaOverride
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, style.fontRes)
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }

    val gridW = bmp.width * placement.gridWidthFraction
    val stickerH = bmp.height * placement.gridHeightFraction
    val cellW = gridW / 7f

    val yearPx = placement.year.fontSp * density
    val monthPx = placement.month.fontSp * density
    val dayPx = placement.day.fontSp * density
    val weekdayPx = dayPx * 0.7f

    val yearRowH = yearPx * OverlayLineHeightMultiplier
    val monthRowH = monthPx * OverlayLineHeightMultiplier
    val weekdayRowH = weekdayPx * OverlayLineHeightMultiplier
    val gridH = (stickerH - yearRowH - monthRowH - weekdayRowH).coerceAtLeast(cellW)
    val cellH = gridH / 6f

    val left = (placement.fracX * bmp.width - gridW / 2).coerceIn(0f, (bmp.width - gridW).coerceAtLeast(0f))
    val top = (placement.fracY * bmp.height - stickerH / 2).coerceIn(0f, (bmp.height - stickerH).coerceAtLeast(0f))

    if (placement.backgroundEnabled) {
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = placement.backgroundColorArgb.toInt()
            alpha = (placement.backgroundOpacity * 255).roundToInt().coerceIn(0, 255)
        }
        val radius = gridW * 0.05f
        canvas.drawRoundRect(android.graphics.RectF(left, top, left + gridW, top + stickerH), radius, radius, bgPaint)
    }

    canvas.drawText("$year", left + gridW / 2, top + yearRowH * 0.75f, paint(placement.year, yearPx))
    canvas.drawText(MonthNames[month], left + gridW / 2, top + yearRowH + monthRowH * 0.75f, paint(placement.month, monthPx))

    val weekdayPaint = paint(placement.day, weekdayPx, alphaOverride = 160)
    val weekdayY = top + yearRowH + monthRowH + weekdayRowH * 0.75f
    WeekHeaders.forEachIndexed { i, wd -> canvas.drawText(wd, left + cellW * i + cellW / 2, weekdayY, weekdayPaint) }

    val dayPaint = paint(placement.day, dayPx)
    val todayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E3B7B7")
    }
    val gridTop = top + yearRowH + monthRowH + weekdayRowH
    monthCells(year, month).chunked(7).forEachIndexed { row, week ->
        week.forEachIndexed { col, d ->
            if (d > 0) {
                val cx = left + cellW * col + cellW / 2
                val cy = gridTop + cellH * row + cellH / 2
                if (d == day && placement.showTodayCircle) canvas.drawCircle(cx, cy, minOf(cellW, cellH) * 0.38f, todayPaint)
                canvas.drawText("$d", cx, cy + dayPaint.textSize * 0.35f, dayPaint)
            }
        }
    }
    return out
}

/** 다운로드 옵션 "액자 구성" — 예전 일자상세 화면(v1.31.0, `DiaryDetailView`)의 헤더 구조를 그대로
 *  재현: 가운데 정렬로 연도(작게)+월 이름(크게)이 위아래로 쌓이고, 그 아래 요일(왼쪽)/일자서수(오른쪽)
 *  줄, 그 아래 손그림 액자 테두리. 하나의 비트맵으로 그려서 저장용으로 만든다. 라이트 팔레트 색을
 *  그대로 박아 넣어 기기의 다크/라이트 모드와 무관하게 항상 같은 종이 느낌으로 저장된다. */
private fun renderFramedDiaryBitmap(ctx: Context, bmp: Bitmap, date: String): Bitmap {
    val parts = date.split("-")
    val d = parts[2].toInt()
    val month = parts[1].toInt() - 1
    val cal = Calendar.getInstance().apply { set(parts[0].toInt(), month, d) }
    val weekday = FullWeekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val dayLabel = "$d${ordinal(d)}"

    val ivory = android.graphics.Color.parseColor("#F6F1E6")
    val navy = android.graphics.Color.parseColor("#1E2D4C")
    val typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.cavorting)

    val innerPad = bmp.width * 0.012f
    val margin = bmp.width * 0.03f
    val yearTextSize = bmp.width * 0.032f
    val monthTextSize = bmp.width * 0.075f
    val subTextSize = bmp.width * 0.045f
    val yearLineH = yearTextSize * 1.25f
    val monthLineH = monthTextSize * 1.15f
    val subLineH = subTextSize * 1.25f
    val titleGap = bmp.width * 0.015f
    val headerHeight = yearLineH + monthLineH + titleGap + subLineH
    val gap = bmp.width * 0.02f
    val frameW = bmp.width + innerPad * 2
    val frameH = bmp.height + innerPad * 2
    val canvasW = (frameW + margin * 2).toInt()
    val canvasH = (headerHeight + gap + frameH + margin * 2).toInt()

    val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(ivory)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = navy; this.typeface = typeface
    }
    val centerX = margin + frameW / 2
    textPaint.textAlign = android.graphics.Paint.Align.CENTER
    textPaint.textSize = yearTextSize
    canvas.drawText(parts[0], centerX, margin + yearLineH * 0.75f, textPaint)
    textPaint.textSize = monthTextSize
    canvas.drawText(MonthNames[month], centerX, margin + yearLineH + monthLineH * 0.8f, textPaint)

    val subBaselineY = margin + yearLineH + monthLineH + titleGap + subLineH * 0.75f
    textPaint.textSize = subTextSize
    textPaint.textAlign = android.graphics.Paint.Align.LEFT
    canvas.drawText(weekday, margin, subBaselineY, textPaint)
    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText(dayLabel, margin + frameW, subBaselineY, textPaint)

    val frameLeft = margin
    val frameTop = margin + headerHeight + gap
    val frameStrokeW = bmp.width * 0.006f
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = navy; style = android.graphics.Paint.Style.STROKE
        strokeWidth = frameStrokeW
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    canvas.drawPath(
        wobblyFramePath(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH, frameStrokeW),
        borderPaint,
    )
    canvas.drawBitmap(bmp, frameLeft + innerPad, frameTop + innerPad, null)
    return out
}

/** 스케치북 앱 전반의 "손그림 액자 테두리" 느낌을 비트맵 Canvas 위에 그리는 버전 — 씨드(7)를
 *  고정해서 매번 같은 모양의 손떨림이 나오게 한다(달력 탭 CleanCalendarScreen 프리뷰 시절의
 *  Compose sketchBorder 로직과 동일한 수식). */
private fun wobblyFramePath(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float): android.graphics.Path {
    val rnd = Random(7)
    val step = 46f
    val pts = ArrayList<android.graphics.PointF>()
    fun edge(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val dx = toX - fromX; val dy = toY - fromY
        val len = hypot(dx, dy); if (len == 0f) return
        val nx = -dy / len; val ny = dx / len
        val n = max(2, (len / step).toInt())
        for (i in 0..n) {
            val t = i.toFloat() / n
            val j = (rnd.nextFloat() - 0.5f) * strokeWidth * 0.7f
            pts.add(android.graphics.PointF(fromX + dx * t + nx * j, fromY + dy * t + ny * j))
        }
    }
    val l = left + strokeWidth; val t = top + strokeWidth
    val r = right - strokeWidth; val b = bottom - strokeWidth
    edge(l, t, r, t); edge(r, t, r, b); edge(r, b, l, b); edge(l, b, l, t)
    return android.graphics.Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
}

private fun datesWithDiary(repo: DiaryRepository, year: Int, month: Int): Set<String> {
    val cal = Calendar.getInstance().apply { set(year, month, 1) }
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val set = HashSet<String>()
    for (d in 1..days) {
        val date = "%04d-%02d-%02d".format(year, month + 1, d)
        if (repo.hasEntry(date)) set.add(date)
    }
    return set
}

private fun buildThumbs(repo: DiaryRepository, year: Int, month: Int): Map<String, ImageBitmap> {
    val cal = Calendar.getInstance().apply { set(year, month, 1) }
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val map = HashMap<String, ImageBitmap>()
    for (d in 1..days) {
        val date = "%04d-%02d-%02d".format(year, month + 1, d)
        repo.loadThumb(date)?.let { map[date] = it.asImageBitmap() }
    }
    return map
}

