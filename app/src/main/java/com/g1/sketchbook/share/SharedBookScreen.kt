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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.bgDrawable
import com.g1.sketchbook.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

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
    val density = LocalDensity.current.density

    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(0xFF1E2D4CL) }
    var erasing by remember { mutableStateOf(false) }
    val sizeByBrush = remember { mutableStateMapOf(BrushType.PEN to Dimens.Brush.penWidth, BrushType.PENCIL to Dimens.Brush.pencilWidth, BrushType.CRAYON to Dimens.Brush.crayonWidth, BrushType.WATER to Dimens.Brush.waterWidth) }
    val opacityByBrush = remember { mutableStateMapOf(BrushType.PEN to 100f, BrushType.PENCIL to 100f, BrushType.CRAYON to 100f, BrushType.WATER to 100f) }
    var eraserSize by remember { mutableFloatStateOf(Dimens.Brush.eraserWidth) }
    val sizeDp = if (erasing) eraserSize else sizeByBrush[brush] ?: 10f
    val opacity = if (erasing) 100f else opacityByBrush[brush] ?: 100f
    val session = remember { com.g1.sketchbook.data.SessionStore(context) }
    var favorites by remember { mutableStateOf(session.favoriteColors) }
    var eyedropArmed by remember { mutableStateOf(false) }
    var eyedropPreview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var page by remember { mutableIntStateOf(0) }
    val pageCount = book.pageCount   // fixed at MAX_PAGES from creation — no add/remove anymore
    var pagesOpen by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
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
    LaunchedEffect(view, brush, color, sizeDp, opacity, erasing, eyedropArmed, locked) {
        val v = view ?: return@LaunchedEffect
        v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
        v.erasing = erasing; v.locked = locked
        v.twoFingerTapAction = session.twoFingerTapAction
        v.threeFingerTapAction = session.threeFingerTapAction
        v.longPressAction = session.longPressAction
        v.eyedropArmed = eyedropArmed
        v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
        v.onEyedrop = { c -> color = (c.toLong() and 0xFFFFFFFFL); erasing = false; eyedropArmed = false; eyedropPreview = null }
        v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
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

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { saveLocal(); onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "나가기")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(book.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    when {
                        others.isEmpty() -> "상대를 기다리는 중… · 코드 $code"
                        others.size == 1 -> "${others[0].name} 님과 함께"
                        else -> "${others.size}명과 함께"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            // 최대화 모드 안의 "..."+스위치가 참가자 선택을 대신하므로, 여기 헤더에는 GRID/
            // MAXIMIZE 두 모드만 고른다.
            SegGroup {
                SegChip("분할", mode == ViewMode.GRID) { mode = ViewMode.GRID }
                SegChip("최대화", mode == ViewMode.MAXIMIZE) { mode = ViewMode.MAXIMIZE }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
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
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OtherPane(other, code, Modifier.weight(1f).fillMaxSize())
                                mine(Modifier.weight(1f).fillMaxSize())
                            }
                        } else {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OtherPane(other, code, Modifier.weight(1f).fillMaxWidth())
                                mine(Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    // 3~4인: 2x2 그리드 — 나는 항상 우하단 고정, 나머지는 좌상→우상→좌하
                    // 순서(3인이면 좌하단이 빈 칸 — 시안 그대로).
                    else -> {
                        val cellSlots = (others.take(3) + listOf(null, null, null)).take(3)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GridCell(cellSlots[0], code, Modifier.weight(1f).fillMaxHeight())
                                GridCell(cellSlots[1], code, Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }

        BrushControls(
            brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
            onSize = { if (erasing) eraserSize = it else sizeByBrush[brush] = it },
            onOpacity = { if (!erasing) opacityByBrush[brush] = it }, onToggleErase = { erasing = !erasing },
            onUndo = { view?.undo() }, onRedo = { view?.redo() },
            onClear = { view?.clearCanvas(); saveLocal(); pushMine() },
            onRotate = { view?.rotate() },
            onOpenPages = { pagesOpen = true },
            favorites = favorites,
            onEditFavorite = { i, c -> val nf = favorites.toMutableList(); nf[i] = c; favorites = nf; session.favoriteColors = nf },
            eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
            fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
            locked = locked, onToggleLock = { locked = !locked },
        )
    }
    if (pagesOpen) {
        com.g1.sketchbook.sketchbook.PagePanel(sbRepo, book.id, page, pageCount,
            onSelect = { p -> goTo(p); pagesOpen = false }, onDismiss = { pagesOpen = false })
    }
}

@Composable
internal fun SegGroup(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), content = content)
}

@Composable
internal fun SegChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A titled, square-cornered frame around a pane (시안 그대로 — 라운드 코너 없음); the active (mine)
 *  pane gets a subtle accent border. Small generic person-icon avatar next to the name — Slot has no
 *  synced emoji, so every participant (including me) gets the same neutral circle. */
@Composable
internal fun PaneFrame(modifier: Modifier, title: String, accent: Boolean, content: @Composable () -> Unit) {
    Column(modifier) {
        Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp)) }
            Spacer(Modifier.width(4.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RectangleShape)
                .border(
                    width = if (accent) 2.dp else 1.dp,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RectangleShape,
                )
                .clipToBounds(),
        ) { content() }
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
