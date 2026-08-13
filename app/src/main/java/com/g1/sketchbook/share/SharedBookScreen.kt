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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.bgDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** How the two canvases are laid out. */
private enum class ViewMode { EQUAL, LARGE, SOLO }
/** Which canvas is focused (enlarged / shown alone). */
private enum class Focus { MINE, THEIRS }

/**
 * A shared sketchbook: same 15-page book as a personal one, shown with a selectable view mode —
 * EQUAL (even split), LARGE (one big + the other as a small strip), or SOLO (only one). My canvas
 * is interactive; the partner's is a live snapshot. Pages save locally; each stroke pushes a
 * snapshot so the partner sees my current page. Fixed to A4 + watercolor paper.
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
    var color by remember { mutableStateOf(0xFF2B4C9BL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(book.pageCount) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    var partner by remember { mutableStateOf<ShareRepository.Slot?>(null) }
    var partnerBmp by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(ViewMode.EQUAL) }
    var focus by remember { mutableStateOf(Focus.MINE) }

    LaunchedEffect(code) {
        share.observeSession(code).collect { st -> partner = st.slots.firstOrNull { it.uid != myUid } }
    }
    LaunchedEffect(partner?.updatedAt, partner?.snapshot) {
        val s = partner?.snapshot
        partnerBmp = if (s == null) null else withContext(Dispatchers.Default) { decodeSnapshot(s) }
    }

    fun pushMine() {
        val b = view?.exportBitmap() ?: return
        scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, encodeSnapshot(b)) }
    }
    fun saveLocal() {
        val v = view; val pg = page; val b = v?.exportBitmap()
        if (b != null) scope.launch(Dispatchers.IO) { sbRepo.savePage(book.id, pg, b) }
    }
    fun goTo(p: Int) { saveLocal(); page = p; view?.loadContent(sbRepo.loadPage(book.id, p)); pushMine() }
    fun addPage() {
        if (pageCount < MAX_PAGES) {
            saveLocal(); pageCount++; sbRepo.setPageCount(book.id, pageCount); page = pageCount - 1
            view?.loadContent(null); pushMine()
        }
    }
    fun deletePage() {
        if (pageCount <= 1) return
        for (i in page until pageCount - 1) {
            val next = sbRepo.loadPage(book.id, i + 1)
            if (next != null) sbRepo.savePage(book.id, i, next) else sbRepo.pageFile(book.id, i).delete()
        }
        sbRepo.pageFile(book.id, pageCount - 1).delete()
        pageCount--; sbRepo.setPageCount(book.id, pageCount)
        if (page > pageCount - 1) page = pageCount - 1
        view?.loadContent(sbRepo.loadPage(book.id, page)); pushMine()
    }

    // Share my current page as soon as the canvas is ready.
    LaunchedEffect(view) { if (view != null) pushMine() }
    BackHandler { saveLocal(); onBack() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { saveLocal(); onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "나가기")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(book.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    if (partner == null) "상대를 기다리는 중… · 코드 $code" else "${partner!!.name} 님과 함께",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            // View-mode selector; focus (mine/partner) appears when a canvas is enlarged/soloed.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mode != ViewMode.EQUAL) {
                    SegGroup {
                        SegChip("나", focus == Focus.MINE) { focus = Focus.MINE }
                        SegChip("상대", focus == Focus.THEIRS) { focus = Focus.THEIRS }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                SegGroup {
                    SegChip("분할", mode == ViewMode.EQUAL) { mode = ViewMode.EQUAL }
                    SegChip("크게", mode == ViewMode.LARGE) { mode = ViewMode.LARGE }
                    SegChip("하나", mode == ViewMode.SOLO) { mode = ViewMode.SOLO }
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val landscape = maxWidth > maxHeight
            val mw = maxWidth; val mh = maxHeight   // capture out of the layout-scope marker
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
                            update = { v ->
                                v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                                v.erasing = erasing
                                v.onStrokeEnd = {
                                    val pg = page
                                    val b = v.exportBitmap()
                                    if (b != null) {
                                        scope.launch(Dispatchers.IO) { sbRepo.savePage(book.id, pg, b) }
                                        scope.launch(Dispatchers.Default) { share.pushSnapshot(code, myUid, encodeSnapshot(b)) }
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val theirs = remember {
                movableContentOf<Modifier> { m ->
                    PaneFrame(m, partner?.name ?: "상대", accent = false) {
                        val bmp = partnerBmp
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), "상대 그림", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (partner == null) "아직 아무도 없어요\n코드 $code 를 공유해 보세요" else "아직 그리기 전이에요",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            val bigIsMine = focus == Focus.MINE
            when (mode) {
                ViewMode.SOLO -> Box(Modifier.fillMaxSize()) {
                    if (bigIsMine) mine(Modifier.fillMaxSize()) else theirs(Modifier.fillMaxSize())
                    // Keep my BrushView alive (page/strokes) even while only the partner is shown.
                    if (!bigIsMine) Box(Modifier.size(1.dp)) { mine(Modifier.size(1.dp)) }
                }
                ViewMode.LARGE -> {
                    val small: @Composable (Modifier) -> Unit = if (bigIsMine) theirs else mine
                    val big: @Composable (Modifier) -> Unit = if (bigIsMine) mine else theirs
                    if (landscape) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            small(Modifier.fillMaxHeight().width(mw * 0.26f)); big(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            small(Modifier.fillMaxWidth().height(mh * 0.22f)); big(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                ViewMode.EQUAL -> if (landscape) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        theirs(Modifier.weight(1f).fillMaxSize()); mine(Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        theirs(Modifier.weight(1f).fillMaxWidth()); mine(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }

        BrushControls(
            brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
            onSize = { sizeDp = it }, onOpacity = { opacity = it }, onToggleErase = { erasing = !erasing },
            onUndo = { view?.undo() }, onRedo = { view?.redo() },
            onClear = { view?.clearCanvas(); saveLocal(); pushMine() },
            onBack = { saveLocal(); onBack() }, onRotate = { view?.rotate() },
            pageLabel = "${page + 1}/$pageCount",
            onPrevPage = { if (page > 0) goTo(page - 1) },
            onNextPage = { if (page < pageCount - 1) goTo(page + 1) },
            onAddPage = { addPage() }, onDeletePage = { deletePage() },
        )
    }
}

@Composable
private fun SegGroup(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), content = content)
}

@Composable
private fun SegChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A titled, rounded frame around a pane; the active (mine) pane gets a subtle accent border. */
@Composable
private fun PaneFrame(modifier: Modifier, title: String, accent: Boolean, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .border(
                    width = if (accent) 2.dp else 1.dp,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .clipToBounds(),
        ) { content() }
    }
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
