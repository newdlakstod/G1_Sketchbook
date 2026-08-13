package com.g1.sketchbook.sketchbook

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.ui.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun bgDrawable(key: String) = when (key) {
    "drawing" -> R.drawable.paper_drawing
    "canvas" -> R.drawable.paper_canvas
    "recycled" -> R.drawable.paper_recycled
    "kraft" -> R.drawable.paper_kraft
    else -> R.drawable.paper_watercolor
}

private val CoverColors = listOf(
    Color(0xFF1E2D4C), Color(0xFF6E8266), Color(0xFF9C8C82),
    Color(0xFF4F6E6A), Color(0xFFB79A94), Color(0xFF7C8A76),
)
private val PAPER_KEYS = listOf("a5", "a4", "a3")
private val DISPLAY_KEYS = listOf("mobile", "tablet", "desktop")

@Composable
private fun SizeRow(list: List<CanvasSize>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { s ->
            val on = s.key == selected
            Column(
                Modifier.weight(1f).clickable { onSelect(s.key) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SizeIcon(s.key, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.size(56.dp)) // fixed square so icons never distort
                Spacer(Modifier.height(6.dp))
                Text(s.label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text("${s.w} × ${s.h}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Line-art size icons: folded-corner page (paper) and phone/tablet/monitor (display). */
@Composable
private fun SizeIcon(key: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = minOf(w, h) * 0.05f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (key) {
            "mobile" -> {
                val pw = w * 0.40f; val ph = h * 0.94f; val l = (w - pw) / 2; val t = (h - ph) / 2; val r = pw * 0.22f
                drawRoundRect(color, Offset(l, t), Size(pw, ph), CornerRadius(r, r), style = st)
                drawLine(color, Offset(l + pw * 0.34f, t + ph * 0.09f), Offset(l + pw * 0.66f, t + ph * 0.09f), sw, StrokeCap.Round)
                drawLine(color, Offset(l + pw * 0.32f, t + ph * 0.92f), Offset(l + pw * 0.68f, t + ph * 0.92f), sw, StrokeCap.Round)
            }
            "tablet" -> {
                val pw = w * 0.94f; val ph = h * 0.72f; val l = (w - pw) / 2; val t = (h - ph) / 2; val r = ph * 0.14f
                drawRoundRect(color, Offset(l, t), Size(pw, ph), CornerRadius(r, r), style = st)
                drawCircle(color, sw * 0.6f, Offset(w / 2, t + ph * 0.10f))
                drawLine(color, Offset(w / 2 - pw * 0.08f, t + ph * 0.90f), Offset(w / 2 + pw * 0.08f, t + ph * 0.90f), sw, StrokeCap.Round)
            }
            "desktop" -> {
                val pw = w * 0.94f; val ph = h * 0.62f; val l = (w - pw) / 2; val t = h * 0.06f; val r = ph * 0.12f
                drawRoundRect(color, Offset(l, t), Size(pw, ph), CornerRadius(r, r), style = st)
                drawLine(color, Offset(w / 2, t + ph), Offset(w / 2, t + ph + h * 0.16f), sw, StrokeCap.Round)
                drawLine(color, Offset(w / 2 - pw * 0.20f, t + ph + h * 0.16f), Offset(w / 2 + pw * 0.20f, t + ph + h * 0.16f), sw, StrokeCap.Round)
            }
            else -> { // paper page with folded corner
                val pw = w * 0.60f; val ph = h * 0.92f; val l = (w - pw) / 2; val t = (h - ph) / 2; val f = pw * 0.28f
                drawPath(Path().apply {
                    moveTo(l, t); lineTo(l + pw - f, t); lineTo(l + pw, t + f)
                    lineTo(l + pw, t + ph); lineTo(l, t + ph); close()
                }, color, style = st)
                drawPath(Path().apply {
                    moveTo(l + pw - f, t); lineTo(l + pw - f, t + f); lineTo(l + pw, t + f)
                }, color, style = st)
            }
        }
    }
}
/** 스케치북 tab: cover list (personal + shared groups) + step-by-step create wizard. */
@Composable
fun SketchbookTab(nickname: String, myUid: String, onOpenBook: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val books = remember(refresh) { repo.list() }
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        CreateWizard(
            nickname = nickname, myUid = myUid, repo = repo,
            onDismiss = { creating = false },
            onCreated = { book -> creating = false; refresh++; onOpenBook(book.id) },
        )
    }
    SketchbookListScreen(
        books = books,
        onCreate = { creating = true },
        onOpen = { onOpenBook(it.id) },
        onDelete = { repo.delete(it.id); refresh++ },
        onToggleFav = { repo.toggleFav(it.id); refresh++ },
    )
}

private enum class WStep { TYPE, NAME, SIZE, BG, CODE }
private enum class WType { PERSONAL, SHARED_NEW, SHARED_JOIN }

/** Step-by-step popup: pick type → (name → size → bg) for creation, or (code) for joining a shared book. */
@Composable
private fun CreateWizard(
    nickname: String,
    myUid: String,
    repo: SketchbookRepository,
    onDismiss: () -> Unit,
    onCreated: (Sketchbook) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val share = remember { com.g1.sketchbook.share.ShareRepository() }
    var step by remember { mutableStateOf(WStep.TYPE) }
    var type by remember { mutableStateOf(WType.PERSONAL) }
    var name by remember { mutableStateOf("") }
    var sizeKey by remember { mutableStateOf("a4") }
    var bgKey by remember { mutableStateOf("watercolor") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun finishPersonal() { onCreated(repo.create(name, sizeKey, bgKey)) }
    fun finishSharedNew() {
        busy = true; error = null
        scope.launch {
            runCatching { share.createSession(myUid, nickname) }.fold(
                onSuccess = { c -> onCreated(repo.create(name, "a4", "watercolor", shared = true, code = c)) },
                onFailure = { busy = false; error = it.message ?: "공유 세션을 만들지 못했어요." },
            )
        }
    }
    fun finishJoin() {
        busy = true; error = null
        scope.launch {
            share.joinSession(code, myUid, nickname).fold(
                onSuccess = { onCreated(repo.create(name.ifBlank { "공유 스케치북" }, "a4", "watercolor", shared = true, code = code.uppercase())) },
                onFailure = { busy = false; error = it.message ?: "참여하지 못했어요." },
            )
        }
    }

    val title = when (step) {
        WStep.TYPE -> "무엇을 만들까요?"
        WStep.NAME -> "이름을 정해요"
        WStep.SIZE -> "캔버스 크기"
        WStep.BG -> "캔버스 배경"
        WStep.CODE -> "초대 코드 입력"
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                when (step) {
                    WStep.TYPE -> {
                        WizardChoice(Icons.Filled.Book, "개인 스케치북") { type = WType.PERSONAL; step = WStep.NAME }
                        Spacer(Modifier.height(10.dp))
                        WizardChoice(Icons.Filled.Groups, "공유 스케치북 만들기") { type = WType.SHARED_NEW; step = WStep.NAME }
                        Spacer(Modifier.height(10.dp))
                        WizardChoice(Icons.AutoMirrored.Filled.Login, "공유 스케치북 참여") { type = WType.SHARED_JOIN; code = ""; step = WStep.CODE }
                    }
                    WStep.NAME -> {
                        OutlinedTextField(name, { name = it.take(20) }, singleLine = true,
                            label = { Text("스케치북 이름") }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                    }
                    WStep.SIZE -> {
                        Text("종이", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                        SizeRow(Catalog.sizes.filter { it.key in PAPER_KEYS }, sizeKey) { sizeKey = it }
                        Spacer(Modifier.height(12.dp))
                        Text("디스플레이", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                        SizeRow(Catalog.sizes.filter { it.key in DISPLAY_KEYS }, sizeKey) { sizeKey = it }
                    }
                    WStep.BG -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(Catalog.backgrounds) { bg ->
                                val on = bg.key == bgKey
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { bgKey = bg.key }.padding(2.dp)) {
                                    Image(painterResource(bgDrawable(bg.key)), bg.label, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                                            .border(if (on) 3.dp else 1.dp,
                                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(10.dp)))
                                    Text(bg.label, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    WStep.CODE -> {
                        OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6); error = null },
                            singleLine = true, enabled = !busy, label = { Text("초대 코드") },
                            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (error != null) { Spacer(Modifier.height(10.dp)); Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (busy) { Spacer(Modifier.height(12.dp)); androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            when (step) {
                WStep.TYPE -> {}
                WStep.NAME -> TextButton(enabled = !busy, onClick = {
                    if (type == WType.PERSONAL) step = WStep.SIZE else finishSharedNew()
                }) { Text(if (type == WType.PERSONAL) "다음" else "만들기") }
                WStep.SIZE -> TextButton(onClick = { step = WStep.BG }) { Text("다음") }
                WStep.BG -> TextButton(onClick = { finishPersonal() }) { Text("만들기") }
                WStep.CODE -> TextButton(enabled = !busy && code.length >= 4, onClick = { finishJoin() }) { Text("참여") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
    )
}

@Composable
private fun WizardChoice(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SketchbookListScreen(
    books: List<Sketchbook>,
    onCreate: () -> Unit,
    onOpen: (Sketchbook) -> Unit,
    onDelete: (Sketchbook) -> Unit,
    onToggleFav: (Sketchbook) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Sketchbook?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Filled.Add, "새 스케치북") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("스케치북", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (books.isEmpty()) {
                Text("아직 스케치북이 없어요. + 로 만들어보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                val personal = books.filter { !it.shared }
                val shared = books.filter { it.shared }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (personal.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("내 스케치북") }
                        itemsIndexed(personal, key = { _, b -> b.id }) { i, b ->
                            CoverCard(b, CoverColors[i % CoverColors.size], { onOpen(b) }, { pendingDelete = b }, { onToggleFav(b) })
                        }
                    }
                    if (shared.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("함께 그린 스케치북") }
                        itemsIndexed(shared, key = { _, b -> b.id }) { i, b ->
                            CoverCard(b, CoverColors[i % CoverColors.size], { onOpen(b) }, { pendingDelete = b }, { onToggleFav(b) })
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("스케치북 삭제") },
            text = { Text("'${target.name}' 을(를) 삭제할까요?\n안에 그린 그림도 함께 사라지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { onDelete(target); pendingDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable
private fun CoverCard(book: Sketchbook, cover: Color, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFav: () -> Unit) {
    Box(Modifier.aspectRatio(0.78f)) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp))
                .background(cover).bounceClick(onClick = onOpen),
        ) {
            Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.66f).align(Alignment.Center).padding(start = 12.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
                Text(book.name, color = Color(0xFFF3ECD9), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = if (book.shared && book.code != null) "🤝 ${book.code} · ${book.pageCount}쪽" else "${book.pageCount}쪽"
                Text(meta, color = Color(0xFFF3ECD9).copy(alpha = 0.8f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
        }
        IconButton(onClick = onToggleFav, modifier = Modifier.align(Alignment.TopStart).size(30.dp)) {
            Icon(Icons.Filled.Star, "즐겨찾기",
                tint = if (book.fav) Color(0xFFFFD43B) else Color(0xFFF3ECD9).copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
            Icon(Icons.Filled.Delete, "삭제", tint = Color(0xFFF3ECD9).copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SketchbookCanvasScreen(bookId: String, myUid: String, myName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val book = remember(bookId) { repo.get(bookId) }
    if (book == null) { LaunchedEffect(Unit) { onBack() }; return }
    if (book.shared && book.code != null) {
        com.g1.sketchbook.share.SharedBookScreen(bookId, book.code, myUid, myName, onBack)
        return
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF1E2D4CL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(book.pageCount) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    // Save the current page SYNCHRONOUSLY (strokes only, no paper) before any page load, so a page
    // switch can't read the file before an async write finishes (that race dropped recent strokes).
    fun saveCurrent() { val v = view ?: return; val pg = page; val b = v.exportContent() ?: return; repo.savePage(book.id, pg, b) }
    fun goTo(p: Int) { saveCurrent(); page = p; view?.loadContent(repo.loadPage(book.id, p)) }
    fun addPage() { if (pageCount < MAX_PAGES) { saveCurrent(); pageCount++; repo.setPageCount(book.id, pageCount); page = pageCount - 1; view?.loadContent(null) } }
    fun deletePage() {
        if (pageCount <= 1) return
        for (i in page until pageCount - 1) {
            val next = repo.loadPage(book.id, i + 1)
            if (next != null) repo.savePage(book.id, i, next) else repo.pageFile(book.id, i).delete()
        }
        repo.pageFile(book.id, pageCount - 1).delete()
        pageCount--; repo.setPageCount(book.id, pageCount)
        if (page > pageCount - 1) page = pageCount - 1
        view?.loadContent(repo.loadPage(book.id, page))
    }

    BackHandler { saveCurrent(); onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            // BrushView fills the whole area and fits/auto-rotates the fixed-size page inside it.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    BrushView(ctx).also { v ->
                        v.paper = BitmapFactory.decodeResource(ctx.resources, bgDrawable(book.bgKey))
                        v.initCanvas(cw, ch)
                        v.loadContent(repo.loadPage(book.id, 0))
                        view = v
                    }
                },
                update = { v ->
                    v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                    v.erasing = erasing
                    v.onStrokeEnd = { val pg = page; v.exportContent()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, pg, b) } } }
                },
            )
        }
        BrushControls(
            brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
            onSize = { sizeDp = it }, onOpacity = { opacity = it }, onToggleErase = { erasing = !erasing },
            onUndo = { view?.undo() }, onRedo = { view?.redo() }, onClear = { view?.clearCanvas(); saveCurrent() },
            onBack = { saveCurrent(); onBack() }, onRotate = { view?.rotate() },
            pageLabel = "${page + 1}/$pageCount",
            onPrevPage = { if (page > 0) goTo(page - 1) },
            onNextPage = { if (page < pageCount - 1) goTo(page + 1) },
            onAddPage = { addPage() }, onDeletePage = { deletePage() },
        )
    }
}

