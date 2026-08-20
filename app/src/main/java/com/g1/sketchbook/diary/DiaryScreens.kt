package com.g1.sketchbook.diary

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.sketchbook.Catalog
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------------- 그림일기 편집 (full-screen A4 editor for one date) ----------------

@Composable
fun DiaryEditorScreen(date: String, onBack: () -> Unit, previewMode: Boolean = false) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(0xFF1E2D4CL) }
    var erasing by remember { mutableStateOf(false) }
    var lassoActive by remember { mutableStateOf(false) }
    var fillActive by remember { mutableStateOf(false) }
    var fillCrayonStyle by remember { mutableStateOf(false) }
    var hasLassoSelection by remember { mutableStateOf(false) }
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
    // 스케치북/공유노트와 동일한 오버레이+dock+드래그+최소화+잠금+전체화면 구조로 통일(2026-08-20,
    // 예전엔 캔버스 아래 고정된 단순 바 하나뿐이었음). 다이어리는 하루 단위라 페이지 버튼만 없다.
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var toolbarCollapsed by remember { mutableStateOf(false) }
    var toolbarDock by remember { mutableStateOf(com.g1.sketchbook.brush.ToolbarDock.BOTTOM) }
    var toolbarDragPx by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler {
        when {
            fullscreen -> fullscreen = false
            else -> onBack()
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() },
    ) {
        val density2 = LocalDensity.current
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
                                v.paper = BitmapFactory.decodeResource(c.resources, R.drawable.paper_watercolor)
                                v.initCanvas(cw, ch)
                                v.loadContent(repo?.load(date))
                                view = v
                            }
                        },
                        update = { v ->
                            v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp; v.opacity = opacity / 100f
                            v.erasing = erasing; v.locked = locked; v.eraserBlur = eraserBlur
                            v.lassoMode = lassoActive; v.fillMode = fillActive; v.fillCrayonStyle = fillCrayonStyle
                            v.onLassoSelectionChanged = { hasLassoSelection = it }
                            v.twoFingerTapAction = session?.twoFingerTapAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.threeFingerTapAction = session?.threeFingerTapAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.longPressAction = session?.longPressAction ?: com.g1.sketchbook.brush.GestureAction.NONE
                            v.eyedropArmed = eyedropArmed
                            v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
                            v.onEyedrop = { c -> color = (c.toLong() and 0xFFFFFFFFL); erasing = false; eyedropArmed = false; eyedropPreview = null }
                            v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
                            v.onToggleToolbars = { toolbarCollapsed = !toolbarCollapsed }
                            v.onStrokeEnd = { v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo?.save(date, b) } } }
                        },
                    )
                    eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
                }
            }
        }
        fun barModifier(dock: com.g1.sketchbook.brush.ToolbarDock, collapsed: Boolean, dragPx: androidx.compose.ui.geometry.Offset) = Modifier
            .align(dock.alignment())
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
            onClear = { view?.clearCanvas(); view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo?.save(date, b) } } },
            onBack = onBack,
            favorites = favorites,
            onEditFavorite = { i, c ->
                val nf = favorites.toMutableList(); nf[i] = c; favorites = nf
                session?.let { it.favoriteColors = nf }
            },
            eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
            lassoActive = lassoActive,
            onToggleLasso = { lassoActive = !lassoActive; if (lassoActive) { erasing = false; fillActive = false } },
            hasLassoSelection = hasLassoSelection, onDeleteLassoSelection = { view?.deleteLassoSelection() },
            fillActive = fillActive,
            onToggleFill = { fillActive = !fillActive; if (fillActive) { erasing = false; lassoActive = false } },
            fillCrayonStyle = fillCrayonStyle, onToggleFillStyle = { fillCrayonStyle = !fillCrayonStyle },
            collapsed = toolbarCollapsed, onToggleCollapsed = { toolbarCollapsed = !toolbarCollapsed },
            onDragBar = { d -> toolbarDragPx += d },
            onDragBarEnd = {
                val cwPx = with(density2) { maxWidth.toPx() }; val chPx = with(density2) { maxHeight.toPx() }
                toolbarDock = com.g1.sketchbook.brush.nearestDock(toolbarDock, toolbarDragPx, cwPx, chPx)
                toolbarDragPx = androidx.compose.ui.geometry.Offset.Zero
            },
            dock = toolbarDock,
            modifier = barModifier(toolbarDock, toolbarCollapsed, toolbarDragPx),
        )
        // 화면버튼(회전/잠금/전체화면)은 가로/세로 상관없이 항상 우측 상단에 고정된 확장 버튼 —
        // 탭하면 펼쳐지고 기능을 고르거나 밖을 탭하면 자동으로 닫힌다(2026-08-20).
        com.g1.sketchbook.brush.ScreenControls(
            onRotate = { view?.rotate() },
            locked = locked, onToggleLock = { locked = !locked },
            fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
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

private fun ordinal(d: Int): String = when {
    d in 11..13 -> "th"
    d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"
    else -> "th"
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(Dimens.Calendar.todayDisc), contentAlignment = Alignment.Center) {
                                    if (date == today) Box(Modifier.size(Dimens.Calendar.todayDisc).shadow(4.dp, CircleShape).background(TodayPink))
                                    Text("$day", fontFamily = Cavorting, fontSize = Dimens.Calendar.daySp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(Modifier.padding(top = 3.dp).size(6.dp).clip(CircleShape)
                                    .background(if (date in marked) DiaryDot else Color.Transparent))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Slides 3 & 4 — clean, bar-less full-screen: a bordered 6×7 month (day thumbnails); tapping a day
 *  swaps the grid for that day's sketch inside a hand-drawn frame. Kept UI-free so it can be captured. */
@Composable
fun CleanCalendarScreen(
    year: Int,
    month: Int,
    onBack: () -> Unit,
    previewDetailDate: String? = null,
    previewMode: Boolean = false,
) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(year, month, repo) {
        if (repo != null) thumbs = withContext(Dispatchers.IO) { buildThumbs(repo, year, month) }
    }
    var detailDate by remember(previewDetailDate) { mutableStateOf(previewDetailDate) }

    BackHandler { if (detailDate != null) detailDate = null else onBack() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
        .padding(start = Dimens.CleanCalendar.sidePadding, end = Dimens.CleanCalendar.sidePadding,
            top = Dimens.CleanCalendar.topPadding, bottom = Dimens.CleanCalendar.bottomPadding)) {
        // Shared title — identical for slide 3 and slide 4.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$year", fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.yearSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(MonthNames[month], fontFamily = Cavorting, fontSize = Dimens.CleanCalendar.monthSp, maxLines = 1)
        }
        Spacer(Modifier.height(Dimens.CleanCalendar.titleGap))
        if (detailDate == null) {
            CleanGrid(year, month, thumbs, Modifier.weight(1f).fillMaxWidth()) { detailDate = it }
        } else {
            CleanDetailBody(repo, detailDate!!, Modifier.weight(1f).fillMaxWidth())
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
private fun CleanDetailBody(repo: DiaryRepository?, date: String, modifier: Modifier) {
    val bmp = remember(date, repo) { repo?.load(date) }
    val parts = date.split("-")
    val d = parts[2].toInt()
    val cal = remember(date) { Calendar.getInstance().apply { set(parts[0].toInt(), parts[1].toInt() - 1, d) } }
    val weekday = FullWeekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val ctx = LocalContext.current
    // 보기 전용 확대/축소 — 그림 자체는 수정하지 않으므로 BrushView의 캔버스가 아니라 표준 Compose
    // 제스처로 충분(1x~5x, 다 축소하면 팬도 원점으로 되돌림).
    var scale by remember(date) { mutableFloatStateOf(1f) }
    var offsetX by remember(date) { mutableFloatStateOf(0f) }
    var offsetY by remember(date) { mutableFloatStateOf(0f) }
    Column(modifier) {
        // Weekday / day aligned to the frame's left / right edges (title comes from the shared header).
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(weekday, fontFamily = Cavorting, fontSize = 40.sp, modifier = Modifier.weight(1f))
            Text("$d${ordinal(d)}", fontFamily = Cavorting, fontSize = 40.sp)
        }
        Spacer(Modifier.height(12.dp))
        // Hand-drawn frame; the sketch is cropped to fill it.
        Box(Modifier.weight(1f).fillMaxWidth().padding(4.dp).sketchBorder(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), date, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(4.dp))
                        .pointerInput(date) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
                                else { offsetX += pan.x; offsetY += pan.y }
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
                )
                IconButton(
                    onClick = {
                        val status = saveToGallery(ctx, bmp, "daymory_$date")
                        Toast.makeText(ctx, status, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(40.dp)
                        .clip(CircleShape).background(Color(0x66000000)),
                ) {
                    Icon(Icons.Filled.Save, "갤러리에 저장", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else {
                Text("이 날의 일기가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

/** A wobbly, hand-drawn rectangle stroke drawn behind the content. */
private fun Modifier.sketchBorder(color: Color, stroke: Dp = 2.5.dp): Modifier = this.drawBehind {
    val sw = stroke.toPx()
    val rnd = Random(7)
    val step = 46f          // longer segments = gentler wobble
    val pts = ArrayList<Offset>()
    fun edge(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val dx = toX - fromX; val dy = toY - fromY
        val len = hypot(dx, dy); if (len == 0f) return
        val nx = -dy / len; val ny = dx / len
        val n = max(2, (len / step).toInt())
        for (i in 0..n) {
            val t = i.toFloat() / n
            val j = (rnd.nextFloat() - 0.5f) * sw * 0.7f   // smaller amplitude
            pts.add(Offset(fromX + dx * t + nx * j, fromY + dy * t + ny * j))
        }
    }
    val l = sw; val t = sw; val r = size.width - sw; val b = size.height - sw
    edge(l, t, r, t); edge(r, t, r, b); edge(r, b, l, b); edge(l, b, l, t)
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

private fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): String = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/G1Sketchbook")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "갤러리에 저장했어요 ✨"
        } else "저장 실패"
    } else {
        val dir = java.io.File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "G1Sketchbook").apply { mkdirs() }
        val f = java.io.File(dir, "$name.png")
        java.io.FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "저장됨: ${f.absolutePath}"
    }
} catch (e: Exception) { "저장 실패: ${e.message}" }
