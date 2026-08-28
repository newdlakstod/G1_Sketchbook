package com.g1.sketchbook.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.bgDrawable
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How the (up to 4) canvases are laid out. GRID auto-switches between a simple 2-pane split (2
 *  people) and a 2x2 grid (3-4 people) based on how many are actually in the session — no manual
 *  toggle needed for that part. MAXIMIZE shows one big pane plus a small floating popup, with a
 *  picker for who's in the popup and a switch to swap big<->popup. */
private enum class ViewMode { GRID, MAXIMIZE }

/**
 * A shared sketchbook: same 15-page book as a personal one, shown with a selectable view mode —
 * GRID (auto 2-pane/2x2 split) or MAXIMIZE (one big + a floating popup). My canvas is interactive;
 * everyone else's is a live snapshot. Pages save locally; each stroke pushes a snapshot so others
 * see my current page. Fixed to A4 + watercolor paper. Up to ShareRepository.MAX_SLOTS participants.
 */
@Composable
fun SharedBookScreen(
    bookId: String,
    code: String,
    myUid: String,
    myName: String,
    /** 목록/공유 탭 3열 페이지 썸네일 더블탭 전용 — 이 페이지를 펼친 채로 시작한다(기본 0). */
    startPage: Int = 0,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sbRepo = remember { SketchbookRepository(context) }
    val share = remember { ShareRepository() }
    val book = remember(bookId) { sbRepo.get(bookId) }
    if (book == null) { LaunchedEffect(Unit) { onBack() }; return }
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf<BrushView?>(null) }
    // 색상/굵기/투명도는 SessionStore에 저장(개인 스케치북과 동일한 키)해 앱을 다시 켜도, 개인·공유
    // 화면을 오가도 이어서 쓸 수 있게 한다.
    val session = remember { com.g1.sketchbook.data.SessionStore(context) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(session.brushColor) }
    var erasing by remember { mutableStateOf(false) }
    var lassoActive by remember { mutableStateOf(false) }
    var fillActive by remember { mutableStateOf(false) }
    var lassoDeleteAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    // S펜 버튼을 누르고 있는 동안만 지우개로 전환했다가, 떼면 누르기 직전 도구로 되돌린다.
    var preStylusErasing by remember { mutableStateOf(false) }
    var preStylusLasso by remember { mutableStateOf(false) }
    var preStylusFill by remember { mutableStateOf(false) }
    val sizeByBrush = remember { mutableStateMapOf(*BrushType.entries.map { it to session.brushSize(it) }.toTypedArray()) }
    val opacityByBrush = remember { mutableStateMapOf(*BrushType.entries.map { it to session.brushOpacity(it) }.toTypedArray()) }
    var eraserSize by remember { mutableFloatStateOf(session.eraserSize) }
    var eraserOpacity by remember { mutableFloatStateOf(session.eraserOpacity) }
    var eraserBlur by remember { mutableFloatStateOf(session.eraserBlur) }
    val sizeDp = if (erasing) eraserSize else sizeByBrush[brush] ?: 10f
    val opacity = if (erasing) eraserOpacity else opacityByBrush[brush] ?: 100f
    var favorites by remember { mutableStateOf(session.favoriteColors) }
    var eyedropArmed by remember { mutableStateOf(false) }
    var eyedropPreview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, book.pageCount - 1)) }
    val pageCount = book.pageCount   // fixed at MAX_PAGES from creation — no add/remove anymore
    var pagesOpen by remember { mutableStateOf(false) }
    // 공유모드는 항상 전체화면 — 여러 명이 같이 보는 캔버스라 상태/내비게이션 바 자리까지 그림 영역으로 쓴다.
    val fullscreen = true
    var locked by remember { mutableStateOf(false) }
    var toolbarCollapsed by remember { mutableStateOf(false) }
    var toolbarDock by remember { mutableStateOf(com.g1.sketchbook.brush.ToolbarDock.TOP) }
    var toolbarDragPx by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    var others by remember { mutableStateOf<List<ShareRepository.Slot>>(emptyList()) }
    var host by remember { mutableStateOf<String?>(null) }
    var teacherMode by remember { mutableStateOf(false) }
    // 선생님모드 투명도는 공유받는 쪽(뷰어)이 자기 화면에서 직접 조절 — host나 서버로 동기화되지
    // 않는 순전히 로컬 취향값이라 SessionStore에 저장하지 않고 화면 안에서만 기억한다.
    var teacherOverlayOpacity by remember { mutableFloatStateOf(50f) }
    val isHost = myUid == host
    var mode by remember { mutableStateOf(ViewMode.GRID) }
    // MAXIMIZE mode: null = "나", else a participant's uid. popupUid is just a preference — the
    // effective value (popupDisplay, computed below near the layout code) self-heals if the person
    // it points to has left the session or is now the same as whoever's maximized.
    var maximizedUid by remember { mutableStateOf<String?>(null) }
    var popupUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(code) {
        share.observeSession(code).collect { st ->
            others = st.slots.filter { it.uid != myUid }
            host = st.host
            teacherMode = st.teacherMode
        }
    }

    // 선생님모드: host가 아닌 참가자가, host와 같은 페이지를 보고 있을 때만 host의 최신 스냅샷을
    // 뷰어가 고른 투명도로 내 캔버스 위에 겹쳐 보여준다. host 본인 화면엔 표시하지 않는다.
    val hostSlot = others.firstOrNull { it.uid == host }
    val teacherOverlayBmp = if (!isHost && teacherMode && hostSlot != null && hostSlot.currentPage == page) {
        participantBitmap(hostSlot, page)
    } else null
    LaunchedEffect(view, teacherOverlayBmp, teacherOverlayOpacity) {
        view?.teacherOverlay = teacherOverlayBmp
        view?.teacherOverlayOpacity = teacherOverlayOpacity / 100f
    }

    fun pushMine() {
        val b = view?.exportBitmap() ?: return
        val pg = page
        scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, pg, encodeSnapshot(b)) }
    }
    // Sync save of the current page (strokes only) before any page load — avoids the switch race.
    fun saveLocal() {
        val v = view ?: return; val pg = page; val b = v.exportContent() ?: return
        sbRepo.savePage(book.id, pg, b)
    }
    fun goTo(p: Int) {
        if (p == page || p !in 0 until pageCount) return
        saveLocal(); page = p; view?.loadContent(sbRepo.loadPage(book.id, p)); pushMine()
    }

    // 선생님모드가 켜져 있는 동안엔 host가 페이지를 넘기면 참가자도 자동으로 같은 페이지를 따라간다 —
    // 안 그러면 host가 이미 다른 페이지에서 그리고 있는 도중 참가자가 들어오거나(항상 0페이지부터
    // 시작) 참가자가 다른 페이지를 보고 있던 채로 host가 페이지를 넘기면, 위 teacherOverlayBmp의
    // "같은 페이지일 때만" 조건이 계속 false로 남아 오버레이가 아예 반영 안 되는 것처럼 보였다
    // (2026-08-29).
    LaunchedEffect(teacherMode, hostSlot?.currentPage) {
        val hp = hostSlot?.currentPage
        if (!isHost && teacherMode && hp != null) goTo(hp)
    }

    // Share my current page as soon as the canvas is ready.
    LaunchedEffect(view) { if (view != null) pushMine() }
    // Apply brush settings via an effect rather than AndroidView.update: the pane is wrapped in
    // movableContent, and after it's moved (rotation / view-mode change) update() stops re-observing
    // state — so selections would silently stop applying. This effect always re-syncs.
    LaunchedEffect(view, brush, color, sizeDp, opacity, erasing, lassoActive, fillActive, eyedropArmed, locked, page) {
        val v = view ?: return@LaunchedEffect
        v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp; v.opacity = opacity / 100f
        v.erasing = erasing; v.locked = locked; v.eraserBlur = eraserBlur
        v.lassoMode = lassoActive; v.fillMode = fillActive
        v.onLassoSelectionChanged = { has, x, y -> lassoDeleteAt = if (has) androidx.compose.ui.geometry.Offset(x, y) else null }
        v.onStylusButtonChanged = { pressed ->
            if (pressed) {
                preStylusErasing = erasing; preStylusLasso = lassoActive; preStylusFill = fillActive
                erasing = true; lassoActive = false; fillActive = false
            } else {
                erasing = preStylusErasing; lassoActive = preStylusLasso; fillActive = preStylusFill
            }
        }
        v.twoFingerTapAction = session.twoFingerTapAction
        v.threeFingerTapAction = session.threeFingerTapAction
        v.longPressAction = session.longPressAction
        v.eyedropArmed = eyedropArmed
        v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
        v.onEyedrop = { c ->
            val col = c.toLong() and 0xFFFFFFFFL
            color = col; session.brushColor = col; erasing = false; eyedropArmed = false; eyedropPreview = null
        }
        v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
        v.onToggleToolbars = { toolbarCollapsed = !toolbarCollapsed }
        v.onThreeFingerSwipe = { dir -> goTo(page + dir) }
        v.onStrokeEnd = {
            val pg = page
            v.exportContent()?.let { c -> scope.launch(Dispatchers.IO) { sbRepo.savePage(book.id, pg, c) } }   // local page: strokes only
            v.exportBitmap()?.let { b -> scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, pg, encodeSnapshot(b)) } } // partner: with paper
        }
    }
    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler { saveLocal(); onBack() }

    // 뒤로가기 버튼·상단 헤더 바를 없애고 캔버스에 화면을 최대한 내줬다(2026-08-20) — 나가기는
    // 시스템 뒤로가기(BackHandler, 위에서 처리)로만. 스케치북 이름은 화면 맨 위에 참가자 캔버스
    // 위로 겹쳐서 떠 있는 작은 라벨 하나로 대신한다.
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() },
    ) {
        // 가용 영역 전부를 스케치북으로 — 바깥 여백 없음, 칸 사이 구분도 간격이 아니라 PaneFrame
        // 자체 테두리 선 하나로만(2026-08-20).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density2 = LocalDensity.current
            val landscape = maxWidth > maxHeight
            val mine = remember {
                movableContentOf<Modifier> { m ->
                    PaneFrame(m, "나 · $myName", accent = true) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                BrushView(ctx).also { v ->
                                    v.paper = BitmapFactory.decodeResource(ctx.resources, bgDrawable(book.bgKey))
                                    v.initCanvas(cw, ch)
                                    v.loadContent(sbRepo.loadPage(book.id, page))
                                    view = v
                                }
                            },
                            update = { /* brush state is applied via LaunchedEffect (movableContent-safe) */ },
                        )
                        eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
                        lassoDeleteAt?.let { p -> com.g1.sketchbook.brush.LassoDeleteButton(p.x, p.y, onDelete = { view?.deleteLassoSelection() }) }
                    }
                }
            }

            when (mode) {
                ViewMode.GRID -> when {
                    // 2인 이하: 반반 분할(세로면 상대=위/나=아래 — 시안 "2인 모드"와 동일).
                    others.size <= 1 -> {
                        val other = others.getOrNull(0)
                        if (landscape) {
                            Row(Modifier.fillMaxSize()) {
                                OtherPane(other, code, Modifier.weight(1f).fillMaxSize())
                                mine(Modifier.weight(1f).fillMaxSize())
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                OtherPane(other, code, Modifier.weight(1f).fillMaxWidth())
                                mine(Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    // 3~4인: 2x2 그리드 — 나는 항상 우하단 고정, 나머지는 좌상→우상→좌하
                    // 순서(3인이면 좌하단이 빈 칸 — 시안 그대로).
                    else -> {
                        val cellSlots = (others.take(3) + listOf(null, null, null)).take(3)
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                GridCell(cellSlots[0], code, Modifier.weight(1f).fillMaxHeight())
                                GridCell(cellSlots[1], code, Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                GridCell(cellSlots[2], code, Modifier.weight(1f).fillMaxHeight())
                                mine(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
                ViewMode.MAXIMIZE -> {
                    // bigUid/popupUid are just preferences — recomputed each render so a participant
                    // leaving (or the picker landing on the currently-maximized person) self-heals
                    // instead of leaving stale state around.
                    val bigUid = maximizedUid.takeIf { it == null || others.any { o -> o.uid == it } }
                    val popupCandidates = buildList {
                        if (bigUid != null) add(null)
                        addAll(others.filter { it.uid != bigUid }.map { it.uid })
                    }
                    val popupDisplay = popupUid.takeIf { it in popupCandidates } ?: popupCandidates.firstOrNull()
                    var pickerOpen by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize()) {
                        if (bigUid == null) mine(Modifier.fillMaxSize())
                        else OtherPane(others.first { it.uid == bigUid }, code, Modifier.fillMaxSize())

                        // popupCandidates is only empty when bigUid==null AND there's nobody else —
                        // in that case there's nothing left to show in the popup (calling mine() again
                        // here would violate movableContentOf's one-placement-per-composition rule).
                        if (popupCandidates.isNotEmpty()) {
                            Box(
                                Modifier.align(Alignment.TopStart).padding(8.dp).size(width = 130.dp, height = 170.dp)
                                    .shadow(8.dp, RectangleShape)
                                    .background(MaterialTheme.colorScheme.background, RectangleShape),
                            ) {
                                if (popupDisplay == null) mine(Modifier.fillMaxSize())
                                else OtherPane(others.first { it.uid == popupDisplay }, code, Modifier.fillMaxSize())
                            }
                        }

                        // top padding 64dp: 화면버튼(ScreenControls)이 항상 우측 상단에 고정돼 있어
                        // 이 참가자 선택 줄과 겹치지 않도록 그 아래로 내림(2026-08-20).
                        Row(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { pickerOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, "팝업에 표시할 참가자 선택")
                                }
                                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                                    popupCandidates.forEach { uid ->
                                        DropdownMenuItem(
                                            text = { Text(if (uid == null) "나" else others.first { it.uid == uid }.name) },
                                            onClick = { popupUid = uid; pickerOpen = false },
                                        )
                                    }
                                }
                            }
                            // 시안의 "내 화면 최대화"/"참가자 최대화" 두 상태 — 이 스위치가 큰 화면과
                            // 팝업을 맞바꾼다.
                            Switch(checked = bigUid != null, onCheckedChange = { maximizedUid = popupDisplay; popupUid = bigUid })
                        }
                    }
                }
            }

            // 스케치북 이름 — 참가자 캔버스 맨 위에 떠서 겹치는 작은 라벨(2026-08-20, 전용 헤더
            // 바를 없앤 자리를 대신). 대기 중/인원수 같은 부가 정보는 각 참가자 칸 안내문구가
            // 이미 담당하므로(OtherPane) 여기서는 이름만.
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(book.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp))
            }

            // 버튼바 둘 다: 캔버스 분할 영역 위에 떠 있는 오버레이라 패널 레이아웃은 그대로다. 손잡이를
            // 길게 눌러 드래그하면 자유 2D로 움직이다가 놓은 위치에서 가장 가까운 가장자리로 옮겨
            // 붙는다(최소화 상태여도 동일).
            fun barModifier(dock: com.g1.sketchbook.brush.ToolbarDock, collapsed: Boolean, dragPx: androidx.compose.ui.geometry.Offset) = Modifier
                .align(dock.alignment())
                .let {
                    val horizontal = dock == com.g1.sketchbook.brush.ToolbarDock.TOP || dock == com.g1.sketchbook.brush.ToolbarDock.BOTTOM
                    if (!collapsed && horizontal) it.fillMaxWidth() else it
                }
                .offset { IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
            BrushControls(
                brush, color, sizeDp, opacity, erasing,
                onBrush = { brush = it; erasing = false; lassoActive = false; fillActive = false },
                onColor = { color = it; erasing = false; session.brushColor = it },
                onSize = { if (erasing) { eraserSize = it; session.eraserSize = it } else { sizeByBrush[brush] = it; session.setBrushSize(brush, it) } },
                onOpacity = { if (erasing) { eraserOpacity = it; session.eraserOpacity = it } else { opacityByBrush[brush] = it; session.setBrushOpacity(brush, it) } },
                onToggleErase = { erasing = !erasing; if (erasing) { lassoActive = false; fillActive = false } },
                eraserBlur = eraserBlur, onEraserBlur = { eraserBlur = it; session.eraserBlur = it },
                onUndo = { view?.undo() }, onRedo = { view?.redo() },
                onClear = { view?.clearCanvas(); saveLocal(); pushMine() },
                favorites = favorites,
                onEditFavorite = { i, c -> val nf = favorites.toMutableList(); nf[i] = c; favorites = nf; session.favoriteColors = nf },
                eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
                lassoActive = lassoActive,
                onToggleLasso = { lassoActive = !lassoActive; if (lassoActive) { erasing = false; fillActive = false } },
                fillActive = fillActive,
                onToggleFill = { fillActive = !fillActive; if (fillActive) { erasing = false; lassoActive = false } },
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
            // 우측 상단: 분할/최대화 아이콘 토글 + 화면버튼(페이지/회전/잠금/전체화면) 한 줄에 나란히
            // (2026-08-20, 예전엔 "분할"/"최대화" 텍스트 세그먼트가 헤더 바에 따로 있었음). 화면버튼은
            // 탭하면 펼쳐지고 기능을 고르거나 밖을 탭하면 자동으로 닫힌다.
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                if (isHost) {
                    TeacherModeButton(teacherMode) { share.setTeacherMode(code, !teacherMode) }
                } else if (teacherMode) {
                    TeacherOpacityButton(teacherOverlayOpacity) { teacherOverlayOpacity = it }
                }
                ModeToggleButton(mode == ViewMode.GRID) { mode = if (mode == ViewMode.GRID) ViewMode.MAXIMIZE else ViewMode.GRID }
                com.g1.sketchbook.brush.ScreenControls(
                    onOpenPages = { pagesOpen = true },
                    onRotate = { view?.rotate() },
                    locked = locked, onToggleLock = { locked = !locked },
                )
            }
        }
    }
    if (pagesOpen) {
        com.g1.sketchbook.sketchbook.PagePanel(
            sbRepo, book.id, page, pageCount,
            onSelect = { p -> goTo(p) },
            onReorder = { order ->
                saveLocal()
                sbRepo.applyPageOrder(book.id, order)
                val newPage = order.indexOf(page)
                if (newPage != -1 && newPage != page) { page = newPage; view?.loadContent(sbRepo.loadPage(book.id, newPage)); pushMine() }
            },
            onDismiss = { pagesOpen = false },
        )
    }
}

/** 분할/최대화 아이콘 토글 — 화면버튼(ScreenControls)의 닫힌 상태 트리거와 같은 반투명 원형 버튼
 *  스타일로 그 왼쪽에 나란히 둔다(2026-08-20, 예전엔 "분할"/"최대화" 텍스트 세그먼트가 헤더 바에
 *  따로 있었음). 아이콘은 지금 모드가 아니라 탭하면 "바뀔 모드"를 보여준다 — 그래야 눌렀을 때
 *  뭐가 될지 미리 알 수 있다(2026-08-20, 처음엔 반대로 지금 모드를 보여주고 있었음).
 *  반투명은 Modifier.alpha()(레이어 변환)가 아니라 Surface color 자체에 알파를 줘서 낸다 —
 *  alpha()로 감싸면 그 레이어 크기에 맞춰 그림자까지 잘려버리는 문제가 있었다. */
@Composable
internal fun ModeToggleButton(gridMode: Boolean, onToggle: () -> Unit) {
    Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Surface(
            // shadowElevation을 주면 원형 Surface 뒤로 흰색 팔각형 그림자가 비쳐 보인다(안드로이드가
            // 원형 아웃라인의 그림자를 다각형으로 근사해서 생기는 렌더링 artifact, ScreenControls와
            // 같은 문제라 같은 방법으로 뺐다) — tonalElevation만으로도 은은한 깊이감은 유지된다.
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                Box(Modifier.size(30.dp).bounceClick(onClick = onToggle), contentAlignment = Alignment.Center) {
                    Icon(
                        if (gridMode) Icons.Filled.OpenInFull else Icons.Filled.GridView,
                        if (gridMode) "최대화 보기로 전환" else "분할 보기로 전환",
                    )
                }
            }
        }
    }
}

/** "선생님모드" 토글 — host에게만 보인다. 켜면 다른 참가자들 캔버스에 내(host) 현재 페이지가
 *  가이드로 겹쳐 뜬다(투명도는 각자 TeacherOpacityButton으로 직접 조절). ModeToggleButton과 같은
 *  반투명 원형 버튼 스타일, 켜져 있을 때는 강조색으로 활성 상태를 알려준다. */
@Composable
private fun TeacherModeButton(active: Boolean, onToggle: () -> Unit) {
    Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Surface(
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                Box(Modifier.size(30.dp).bounceClick(onClick = onToggle), contentAlignment = Alignment.Center) {
                    // 아이콘은 "지금 상태"가 아니라 "눌렀을 때 벌어질 동작"을 보여준다 — 공유 중(on)일
                    // 땐 "끄기" 동작을 뜻하는 unshared 아이콘, 꺼져 있을 땐 "켜기" 동작을 뜻하는 shared
                    // 아이콘(2026-08-29, 상태 표시에서 동작 표시로 변경).
                    Icon(
                        if (active) com.g1.sketchbook.brush.IconScreenUnsharedLine else com.g1.sketchbook.brush.IconScreenSharedLine,
                        "선생님모드 " + (if (active) "끄기" else "켜기"),
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

/** 공유받는 쪽(뷰어)이 선생님모드 오버레이 투명도를 직접 조절 — host가 아닌 참가자에게만, teacherMode가
 *  켜져 있을 때만 보인다. TeacherModeButton과 같은 반투명 원형 버튼 스타일, 탭하면 슬라이더 팝업이 뜬다. */
@Composable
private fun TeacherOpacityButton(opacity: Float, onOpacity: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val density3 = LocalDensity.current
    Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                Box(Modifier.size(30.dp).bounceClick(onClick = { open = !open }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Opacity, "선생님모드 투명도 조절")
                }
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density3) { 48.dp.roundToPx() }),
                onDismissRequest = { open = false },
            ) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
                    Box(Modifier.width(248.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        com.g1.sketchbook.brush.IconSliderRow(Icons.Filled.Opacity, "투명도", "${opacity.toInt()}", opacity, 0f..100f, onOpacity)
                    }
                }
            }
        }
    }
}

/** 참가자 캔버스 프레임(시안 그대로 — 라운드 코너 없음, 활성(나) 칸은 살짝 강조 테두리). 이름표는
 *  더 이상 캔버스 위 별도 줄이 아니라 캔버스 좌측 상단에 겹쳐 뜨는 작은 배지 — 화면 공간을 최대한
 *  캔버스에 내주기 위해(2026-08-20). Slot에는 동기화된 이모지가 없어 모두 같은 사람 아이콘을 쓴다. */
@Composable
internal fun PaneFrame(modifier: Modifier, title: String, accent: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface, RectangleShape)
            .border(
                width = if (accent) 2.dp else 1.dp,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RectangleShape,
            )
            .clipToBounds(),
    ) {
        content()
        Row(
            Modifier.align(Alignment.TopStart).padding(4.dp)
                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(8.dp)) }
            Spacer(Modifier.width(3.dp))
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

/** Decodes one participant's snapshot for a specific [page] — re-decodes only when that page's
 *  data actually changes. Others' panes are plain images (no BrushView), so unlike `mine` they
 *  don't need movableContentOf: moving between grid/maximize layouts is cheap to just re-render. */
@Composable
private fun participantBitmap(slot: ShareRepository.Slot?, page: Int): Bitmap? {
    var bmp by remember(slot?.uid, page) { mutableStateOf<Bitmap?>(null) }
    val b64 = slot?.snapshots?.get(page)
    LaunchedEffect(b64) {
        bmp = if (b64 == null) null else withContext(Dispatchers.Default) { decodeSnapshot(b64) }
    }
    return bmp
}

/** One other participant's pane — a snapshot of whichever page is being viewed, or a waiting/
 *  blank-canvas message. [slot] null means "nobody's joined yet" (only meaningful in the 2-pane
 *  branch — [GridCell] renders a plain empty box instead once there's already at least one other
 *  participant). Defaults to their "live" page ([ShareRepository.Slot.currentPage]) — tapping the
 *  page badge lets me pin a look at any of their [MAX_PAGES] pages via [PartnerPagePicker]. */
@Composable
private fun OtherPane(slot: ShareRepository.Slot?, code: String, modifier: Modifier) {
    var viewedPage by remember(slot?.uid) { mutableStateOf<Int?>(null) }
    var pagePickerOpen by remember { mutableStateOf(false) }
    val effectivePage = viewedPage ?: slot?.currentPage ?: 0
    val bmp = participantBitmap(slot, effectivePage)
    PaneFrame(modifier, slot?.name ?: "상대", accent = false) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), "${slot?.name ?: "상대"} 그림", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        slot == null -> "아직 아무도 없어요\n코드 $code 를 공유해 보세요"
                        viewedPage != null -> "이 페이지는 아직 안 그렸어요"
                        else -> "아직 그리기 전이에요"
                    },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        if (slot != null) {
            // 상대방 페이지 선택 배지 — 우측 하단, 탭하면 상대가 지금까지 올린 페이지들을 골라 볼
            // 수 있는 선택창이 뜬다. LIVE면 지금 상대가 그리고 있는 페이지를 실시간으로 따라간다.
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).bounceClick { pagePickerOpen = true }
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    "${effectivePage + 1}p" + if (viewedPage == null) " · LIVE" else "",
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                )
            }
        }
    }
    if (pagePickerOpen && slot != null) {
        PartnerPagePicker(
            name = slot.name, snapshots = slot.snapshots, currentPage = slot.currentPage,
            selected = viewedPage,
            onSelect = { p -> viewedPage = p; pagePickerOpen = false },
            onDismiss = { pagePickerOpen = false },
        )
    }
}

/** Popup for picking which of a partner's [MAX_PAGES] pages to view — pages they haven't pushed a
 *  snapshot for yet show as blank placeholders (still tappable, just empty). "실시간으로" clears
 *  the pin and goes back to following whichever page they're actively drawing. */
@Composable
private fun PartnerPagePicker(
    name: String, snapshots: Map<Int, String>, currentPage: Int, selected: Int?,
    onSelect: (Int?) -> Unit, onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp, tonalElevation = 3.dp,
                modifier = Modifier.width(292.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$name 의 페이지", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSelect(null) }, enabled = selected != null) { Text("실시간으로") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        items(MAX_PAGES) { index ->
                            PartnerPageCell(
                                index = index, base64 = snapshots[index],
                                isLive = index == currentPage,
                                isSelected = (selected ?: currentPage) == index,
                                onClick = { onSelect(index) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("닫기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerPageCell(index: Int, base64: String?, isLive: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    var thumb by remember(base64) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(base64) { thumb = base64?.let { b -> withContext(Dispatchers.Default) { decodeSnapshotThumb(b) } } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(Dimens.Home.coverRatio).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                ),
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            }
            if (isLive) {
                Text(
                    "LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("${index + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A 2x2 grid cell — a participant pane, or a plain blank cell (3-person grid's empty 4th slot). */
@Composable
private fun GridCell(slot: ShareRepository.Slot?, code: String, modifier: Modifier) {
    if (slot != null) OtherPane(slot, code, modifier)
    else Box(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape))
}

// ---- snapshot codec (downscaled JPEG -> Base64, API 24 safe) ----
// 2026-08-23: maxSide 700/quality 70이었을 때 상대방 화면이 눈에 띄게 흐릿하다는 피드백으로 상향
// (A4 캔버스 기준 원본 장변이 ~2300px라 700은 거의 1/3까지 줄던 것) — 스트로크마다 쏘는 스냅샷이라
// 페이로드가 너무 커지지 않는 선(스케치는 대부분 단색 배경이라 JPEG 압축이 잘 먹음)에서 올렸다.
private fun encodeSnapshot(src: Bitmap): String {
    val maxSide = 1400
    val s = min(1f, maxSide.toFloat() / max(src.width, src.height))
    val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeSnapshot(b64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(b64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/** Downsampled decode for [PartnerPageCell] thumbnails — the page picker can decode up to
 *  [MAX_PAGES] snapshots at once, so full-resolution decodes there would be wasteful/janky
 *  (same `inSampleSize` technique as `SketchbookRepository.loadPageThumb`). */
private fun decodeSnapshotThumb(b64: String, reqPx: Int = 200): Bitmap? = runCatching {
    val bytes = Base64.decode(b64, Base64.NO_WRAP)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqPx) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
