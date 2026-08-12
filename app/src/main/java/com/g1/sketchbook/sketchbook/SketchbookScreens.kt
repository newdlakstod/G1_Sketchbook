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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
    Color(0xFF2B4C9B), Color(0xFF7E9A52), Color(0xFFDE7F3C),
    Color(0xFFE0B23C), Color(0xFFCE7A7A), Color(0xFF5B8A8C),
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
/** 스케치북 tab: cover list + create. Opening a book is handled at the app root (full-screen canvas). */
@Composable
fun SketchbookTab(onOpenBook: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val books = remember(refresh) { repo.list() }
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        BackHandler { creating = false }
        CreateSketchbookScreen(
            onCancel = { creating = false },
            onCreate = { name, size, bg -> val sb = repo.create(name, size, bg); creating = false; onOpenBook(sb.id) },
        )
    } else {
        SketchbookListScreen(
            books = books,
            onCreate = { creating = true },
            onOpen = { onOpenBook(it.id) },
            onDelete = { repo.delete(it.id); refresh++ },
            onToggleFav = { repo.toggleFav(it.id); refresh++ },
        )
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(books, key = { _, b -> b.id }) { i, b ->
                        CoverCard(b, CoverColors[i % CoverColors.size], { onOpen(b) }, { onDelete(b) }, { onToggleFav(b) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverCard(book: Sketchbook, cover: Color, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFav: () -> Unit) {
    Box(Modifier.aspectRatio(0.78f)) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp))
                .background(cover).clickable(onClick = onOpen),
        ) {
            Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.66f).align(Alignment.Center).padding(start = 12.dp))
            Text(book.name, color = Color(0xFFF3ECD9), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 8.dp, bottom = 12.dp))
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
private fun CreateSketchbookScreen(onCancel: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sizeKey by remember { mutableStateOf("a4") }
    var bgKey by remember { mutableStateOf("watercolor") }
    Box(Modifier.fillMaxSize()) {
      // Live preview: chosen paper shows faintly behind the form.
      Image(painterResource(bgDrawable(bgKey)), null, contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().alpha(0.4f))
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("새 스케치북", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("이름") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp)); Text("캔버스 크기 · 종이", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        SizeRow(Catalog.sizes.filter { it.key in PAPER_KEYS }, sizeKey) { sizeKey = it }
        Spacer(Modifier.height(16.dp)); Text("캔버스 크기 · 디스플레이", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        SizeRow(Catalog.sizes.filter { it.key in DISPLAY_KEYS }, sizeKey) { sizeKey = it }

        Spacer(Modifier.height(20.dp)); Text("배경", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Catalog.backgrounds) { bg ->
                val on = bg.key == bgKey
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { bgKey = bg.key }.padding(2.dp)) {
                    Image(painterResource(bgDrawable(bg.key)), bg.label, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(10.dp))
                            .border(if (on) 3.dp else 1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)))
                    Text(bg.label, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
            Button(onClick = { onCreate(name, sizeKey, bgKey) }, modifier = Modifier.weight(2f).height(50.dp),
                shape = MaterialTheme.shapes.small) { Text("만들기") }
        }
      }
    }
}

@Composable
fun SketchbookCanvasScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val book = remember(bookId) { repo.get(bookId) }
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

    // Capture the page number NOW so an async save always targets the right page (fixes content loss).
    fun saveCurrent() { val v = view; val pg = page; val b = v?.exportBitmap(); if (b != null) scope.launch(Dispatchers.IO) { repo.savePage(book.id, pg, b) } }
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
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val ratio = cw.toFloat() / ch
            val w = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
            val h = w / ratio
            Box(Modifier.width(w).height(h)) {
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
                        v.onStrokeEnd = { val pg = page; v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, pg, b) } } }
                    },
                )
            }
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

