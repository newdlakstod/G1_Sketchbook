package com.g1.sketchbook.diary

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------------- 그림일기 (today's editable canvas) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen() {
    val ctx = LocalContext.current
    val repo = remember { DiaryRepository(ctx) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val today = remember { repo.today() }
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF2B4C9BL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("오늘의 그림일기 · $today", fontSize = 15.sp, fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            BrushControls(brush, color, sizeDp, opacity, erasing,
                onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
                onSize = { sizeDp = it }, onOpacity = { opacity = it }, onToggleErase = { erasing = !erasing },
                onUndo = { view?.undo() }, onRedo = { view?.redo() },
                onClear = { view?.clearCanvas(); view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.save(today, b) } } },
                onRotate = { view?.rotate() })
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val side = if (maxWidth <= maxHeight) maxWidth else maxHeight
            Box(Modifier.size(side)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        BrushView(c).also { v ->
                            v.paper = BitmapFactory.decodeResource(c.resources, R.drawable.paper_watercolor)
                            v.loadContent(repo.load(today))
                            view = v
                        }
                    },
                    update = { v ->
                        v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                        v.erasing = erasing
                        v.onStrokeEnd = { v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.save(today, b) } } }
                    },
                )
            }
        }
    }
}

// ---------------- 일기달력 (browse past diaries) ----------------

@Composable
fun DiaryCalendarScreen() {
    val ctx = LocalContext.current
    val repo = remember { DiaryRepository(ctx) }
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) } // 0-based
    var selected by remember { mutableStateOf(repo.today()) }
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(year, month) { thumbs = withContext(Dispatchers.IO) { buildThumbs(repo, year, month) } }

    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$year.${month + 1}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (month == 0) { month = 11; year-- } else month-- }) { Icon(Icons.Filled.ChevronLeft, "이전 달") }
            IconButton(onClick = { if (month == 11) { month = 0; year++ } else month++ }) { Icon(Icons.Filled.ChevronRight, "다음 달") }
        }
        Spacer(Modifier.height(10.dp))
        if (portrait) {
            CalendarGrid(year, month, thumbs, selected, { selected = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            DiaryPanel(repo, selected, Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxSize()) {
                CalendarGrid(year, month, thumbs, selected, { selected = it }, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                DiaryPanel(repo, selected, Modifier.weight(1f))
            }
        }
    }
}

private val WeekHeaders = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
private fun CalendarGrid(
    year: Int, month: Int, thumbs: Map<String, ImageBitmap>, selected: String,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val cal = remember(year, month) { Calendar.getInstance().apply { set(year, month, 1) } }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = buildList {
        repeat(firstDow) { add(0) }
        for (d in 1..days) add(d)
        while (size % 7 != 0) add(0)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            WeekHeaders.forEach { Text(it, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (day > 0) {
                            val date = "%04d-%02d-%02d".format(year, month + 1, day)
                            val sel = date == selected
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(if (sel) 2.dp else 1.dp,
                                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp))
                                    .clickable { onSelect(date) },
                            ) {
                                thumbs[date]?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) }
                                Text("$day", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (thumbs[date] != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryPanel(repo: DiaryRepository, date: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(date) { repo.load(date) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), date, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("이 날의 일기가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { bmp?.let { Toast.makeText(ctx, saveToGallery(ctx, it, "diary_$date"), Toast.LENGTH_SHORT).show() } },
            enabled = bmp != null, shape = MaterialTheme.shapes.small) { Text("이미지 저장") }
    }
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
