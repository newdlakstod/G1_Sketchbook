package com.g1.sketchbook.sketchbook

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
/** Entry point for the 스케치북 tab: list → create → canvas (internal navigation). */
@Composable
fun SketchbookTab() {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    var books by remember { mutableStateOf(repo.list()) }
    var mode by remember { mutableStateOf<String>("list") } // list | create | <sketchbook id>

    when (mode) {
        "create" -> {
            BackHandler { mode = "list" }
            CreateSketchbookScreen(
                onCancel = { mode = "list" },
                onCreate = { name, size, bg -> val sb = repo.create(name, size, bg); books = repo.list(); mode = sb.id },
            )
        }
        "list" -> SketchbookListScreen(
            books = books,
            onCreate = { mode = "create" },
            onOpen = { mode = it.id },
            onDelete = { repo.delete(it.id); books = repo.list() },
        )
        else -> {
            val sb = repo.get(mode)
            if (sb == null) { mode = "list" } else {
                BackHandler { mode = "list" }
                SketchbookCanvasScreen(sb, repo) { mode = "list"; books = repo.list() }
            }
        }
    }
}

@Composable
private fun SketchbookListScreen(
    books: List<Sketchbook>,
    onCreate: () -> Unit,
    onOpen: (Sketchbook) -> Unit,
    onDelete: (Sketchbook) -> Unit,
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
                        CoverCard(b, CoverColors[i % CoverColors.size], { onOpen(b) }, { onDelete(b) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverCard(book: Sketchbook, cover: Color, onOpen: () -> Unit, onDelete: () -> Unit) {
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("새 스케치북", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("이름") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp)); Text("캔버스 크기", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Catalog.sizes) { s ->
                val on = s.key == sizeKey
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { sizeKey = s.key }.padding(4.dp)) {
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize(0.9f).aspectRatio(s.ratio)
                            .border(if (on) 3.dp else 1.5.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(4.dp)))
                    }
                    Text(s.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SketchbookCanvasScreen(book: Sketchbook, repo: SketchbookRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF2B4C9BL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(book.pageCount) }

    fun saveCurrent() { view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, page, b) } } }
    fun goTo(p: Int) { saveCurrent(); page = p; view?.loadContent(repo.loadPage(book.id, p)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(book.name, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = { saveCurrent(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = { if (page > 0) goTo(page - 1) }, enabled = page > 0) { Icon(Icons.Filled.ChevronLeft, "이전") }
                    Text("${page + 1}/$pageCount", fontSize = 13.sp)
                    IconButton(onClick = { if (page < pageCount - 1) goTo(page + 1) }, enabled = page < pageCount - 1) { Icon(Icons.Filled.ChevronRight, "다음") }
                    IconButton(
                        onClick = { if (pageCount < MAX_PAGES) { pageCount++; repo.setPageCount(book.id, pageCount); goTo(pageCount - 1) } },
                        enabled = pageCount < MAX_PAGES,
                    ) { Icon(Icons.Filled.Add, "페이지 추가") }
                },
            )
        },
        bottomBar = { BrushControls(brush, color, sizeDp, opacity, { brush = it }, { color = it }, { sizeDp = it }, { opacity = it },
            onUndo = { view?.undo() }, onClear = { view?.clearCanvas(); saveCurrent() }) },
    ) { padding ->
        BoxWithConstraints(
            Modifier.padding(padding).fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = book.size.ratio
            val w = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
            val h = w / ratio
            Box(Modifier.width(w).height(h)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        BrushView(ctx).also { v ->
                            v.paper = BitmapFactory.decodeResource(ctx.resources, bgDrawable(book.bgKey))
                            v.loadContent(repo.loadPage(book.id, 0))
                            view = v
                        }
                    },
                    update = { v ->
                        v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                        v.onStrokeEnd = { v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, page, b) } } }
                    },
                )
            }
        }
    }
}

