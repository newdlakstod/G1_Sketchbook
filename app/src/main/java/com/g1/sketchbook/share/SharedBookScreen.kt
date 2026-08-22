package com.g1.sketchbook.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.alignment
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
    var hasLassoSelection by remember { mutableStateOf(false) }
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
    var page by remember { mutableIntStateOf(0) }
    val pageCount = book.pageCount   // fixed at MAX_PAGES from creation — no add/remove anymore
    var pagesOpen by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var toolbarCollapsed by remember { mutableStateOf(false) }
    var toolbarDock by remember { mutableStateOf(com.g1.sketchbook.brush.ToolbarDock.BOTTOM) }
    var toolbarDragPx by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    var others by remember { mutableStateOf<List<ShareRepository.Slot>>(emptyList()) }
    var mode by remember { mutableStateOf(ViewMode.GRID) }
    // MAXIMIZE mode: null = "나", else a participant's uid. popupUid is just a preference — the
    // effective value (popupDisplay, computed below near the layout code) self-heals if the person
    // it points to has left the session or is now the same as whoever's maximized.
    var maximizedUid by remember { mutableStateOf<String?>(null) }
    var popupUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(code) {
        share.observeSession(code).collect { st -> others = st.slots.filter { it.uid != myUid } }
    }

    fun pushMine() {
        val b = view?.exportBitmap() ?: return
        scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, encodeSnapshot(b)) }
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
        v.onLassoSelectionChanged = { hasLassoSelection = it }
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
            v.exportBitmap()?.let { b -> scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, encodeSnapshot(b)) } } // partner: with paper
        }
    }
    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler {
        when {
            fullscreen -> fullscreen = false
            else -> { saveLocal(); onBack() }
        }
    }

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
                                    v.loadContent(sbRepo.loadPage(book.id, 0))
                                    view = v
                                }
                            },
                            update = { /* brush state is applied via LaunchedEffect (movableContent-safe) */ },
                        )
                        eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
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
                hasLassoSelection = hasLassoSelection, onDeleteLassoSelection = { view?.deleteLassoSelection() },
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
                ModeToggleButton(mode == ViewMode.GRID) { mode = if (mode == ViewMode.GRID) ViewMode.MAXIMIZE else ViewMode.GRID }
                com.g1.sketchbook.brush.ScreenControls(
                    onOpenPages = { pagesOpen = true },
                    onRotate = { view?.rotate() },
                    locked = locked, onToggleLock = { locked = !locked },
                    fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
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
            onReadMode = {},
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
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            shadowElevation = 8.dp, tonalElevation = 2.dp,
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

/** 참가자 캔버스 프레임(시안 그대로 — 라운드 코너 없음, 활성(나) 칸은 살짝 강조 테두리). 이름표는
 *  더 이상 캔버스 위 별도 줄이 아니라 캔버스 좌측 상단에 겹쳐 뜨는 작은 배지 — 화면 공간을 최대한
 *  캔버스에 내주기 위해(2026-08-20). Slot에는 동기화된 이모지가 없어 모두 같은 사람 아이콘을 쓴다. */
@Composable
internal fun PaneFrame(modifier: Modifier, title: String, accent: Boolean, content: @Composable () -> Unit) {
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

/** Decodes one participant's latest snapshot — re-decodes only when their data actually changes.
 *  Others' panes are plain images (no BrushView), so unlike `mine` they don't need movableContentOf:
 *  moving between grid/maximize layouts is cheap to just re-render. */
@Composable
private fun participantBitmap(slot: ShareRepository.Slot?): Bitmap? {
    var bmp by remember(slot?.uid) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(slot?.updatedAt, slot?.snapshot) {
        val s = slot?.snapshot
        bmp = if (s == null) null else withContext(Dispatchers.Default) { decodeSnapshot(s) }
    }
    return bmp
}

/** One other participant's pane — their latest snapshot, or a waiting/blank-canvas message.
 *  [slot] null means "nobody's joined yet" (only meaningful in the 2-pane branch — [GridCell]
 *  renders a plain empty box instead once there's already at least one other participant). */
@Composable
private fun OtherPane(slot: ShareRepository.Slot?, code: String, modifier: Modifier) {
    val bmp = participantBitmap(slot)
    PaneFrame(modifier, slot?.name ?: "상대", accent = false) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), "${slot?.name ?: "상대"} 그림", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (slot == null) "아직 아무도 없어요\n코드 $code 를 공유해 보세요" else "아직 그리기 전이에요",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A 2x2 grid cell — a participant pane, or a plain blank cell (3-person grid's empty 4th slot). */
@Composable
private fun GridCell(slot: ShareRepository.Slot?, code: String, modifier: Modifier) {
    if (slot != null) OtherPane(slot, code, modifier)
    else Box(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape))
}

// ---- snapshot codec (downscaled JPEG -> Base64, API 24 safe) ----
private fun encodeSnapshot(src: Bitmap): String {
    val maxSide = 700
    val s = min(1f, maxSide.toFloat() / max(src.width, src.height))
    val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeSnapshot(b64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(b64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
