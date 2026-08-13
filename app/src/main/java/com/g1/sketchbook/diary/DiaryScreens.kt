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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
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
import com.g1.sketchbook.sketchbook.Catalog
import com.g1.sketchbook.ui.theme.Cavorting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------------- 그림일기 편집 (full-screen A4 editor for one date) ----------------

@Composable
fun DiaryEditorScreen(date: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { DiaryRepository(ctx) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF1E2D4CL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    val size = remember { Catalog.size("a4") }
    val cw = size.pxW(); val ch = size.pxH()

    BackHandler { onBack() }
    // Full-bleed canvas at A4 portrait ratio (opens full-screen, like a sketchbook).
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
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
                            v.loadContent(repo.load(date))
                            view = v
                        }
                    },
                    update = { v ->
                        v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                        v.erasing = erasing
                        v.onStrokeEnd = { v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.save(date, b) } } }
                    },
                )
            }
        }
        BrushControls(brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
            onSize = { sizeDp = it }, onOpacity = { opacity = it }, onToggleErase = { erasing = !erasing },
            onUndo = { view?.undo() }, onRedo = { view?.redo() },
            onClear = { view?.clearCanvas(); view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo.save(date, b) } } },
            onBack = onBack, onRotate = { view?.rotate() })
    }
}

// ---------------- 일기달력 (browse past diaries) ----------------

@Composable
fun DiaryCalendarScreen(onOpenDiary: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { DiaryRepository(ctx) }
    val today = remember { repo.today() }
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) } // 0-based
    var selected by remember { mutableStateOf(today) }
    var detailDate by remember { mutableStateOf<String?>(null) } // portrait: a day's diary is shown
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(year, month) { thumbs = withContext(Dispatchers.IO) { buildThumbs(repo, year, month) } }

    val portrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    // Portrait detail: tap a day → show its diary with a dated header (back returns to the calendar).
    if (portrait && detailDate != null) {
        BackHandler { detailDate = null }
        DiaryDetailView(repo, detailDate!!, today, onBack = { detailDate = null }, onOpenDiary = onOpenDiary,
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp))
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$year", fontFamily = Cavorting, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MonthNames[month], fontFamily = Cavorting, fontSize = 30.sp)
            }
            IconButton(onClick = { onOpenDiary(today) }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Edit, "오늘 일기 그리기")
            }
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { if (month == 0) { month = 11; year-- } else month-- }) { Icon(Icons.Filled.ChevronLeft, "이전 달") }
                IconButton(onClick = { if (month == 11) { month = 0; year++ } else month++ }) { Icon(Icons.Filled.ChevronRight, "다음 달") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (portrait) {
            CalendarTable(year, month, thumbs, selected = null, today, onDayClick = { detailDate = it }, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                CalendarTable(year, month, thumbs, selected, today, onDayClick = { selected = it }, Modifier.weight(1.35f).fillMaxHeight())
                Spacer(Modifier.width(16.dp))
                DiarySidePanel(repo, selected, today, onOpenDiary, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

private val WeekHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val FullWeekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MonthNames = listOf("January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December")
private const val A4_RATIO = 210f / 297f   // portrait A4 (w/h), matches the diary canvas

private fun ordinal(d: Int): String = when {
    d in 11..13 -> "th"
    d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"
    else -> "th"
}

/** 6×7 grid (6 week rows) with the weekday header pulled above the border; every month fits.
 *  Day number sits top-right; thumbnails crop into their cell. */
@Composable
private fun CalendarTable(
    year: Int, month: Int, thumbs: Map<String, ImageBitmap>, selected: String?, today: String,
    onDayClick: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val cal = remember(year, month) { Calendar.getInstance().apply { set(year, month, 1) } }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = buildList {
        repeat(firstDow) { add(0) }
        for (d in 1..days) add(d)
        while (size < 42) add(0)   // 6 week rows × 7 columns
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        // Weekday header, outside (above) the bordered grid.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            WeekHeaders.forEach { wd ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(wd, fontFamily = Cavorting, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line)) {
                            if (day > 0) {
                                val date = "%04d-%02d-%02d".format(year, month + 1, day)
                                val isToday = date == today
                                Box(Modifier.fillMaxSize().clickable { onDayClick(date) }) {
                                    thumbs[date]?.let {
                                        Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                    Text("$day", fontFamily = Cavorting, fontSize = 14.sp,
                                        color = if (thumbs[date] != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 3.dp))
                                    if (isToday) Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary))
                                    else if (date == selected) Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.tertiary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-width dated view of one day's diary (portrait), styled after the sample: month/year on top,
 *  weekday + ordinal day, then the framed image. */
@Composable
private fun DiaryDetailView(repo: DiaryRepository, date: String, today: String, onBack: () -> Unit,
                            onOpenDiary: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(date) { repo.load(date) }
    val parts = date.split("-")
    val y = parts[0].toInt(); val m = parts[1].toInt(); val d = parts[2].toInt()
    val cal = remember(date) { Calendar.getInstance().apply { set(y, m - 1, d) } }
    val weekday = FullWeekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]

    Column(modifier) {
        // Header mirrors the calendar header (same height) so the image below lands in the table's spot.
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$y", fontFamily = Cavorting, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MonthNames[m - 1], fontFamily = Cavorting, fontSize = 32.sp)
            }
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "달력으로")
            }
            if (date == today) {
                IconButton(onClick = { onOpenDiary(date) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Edit, "그리기")
                }
            } else if (bmp != null) {
                IconButton(onClick = { Toast.makeText(ctx, saveToGallery(ctx, bmp, "diary_$date"), Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Save, "이미지 저장")
                }
            }
        }
        // Weekday / day aligned to the image's left / right edges.
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(weekday, fontFamily = Cavorting, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Text("$d${ordinal(d)}", fontFamily = Cavorting, fontSize = 24.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Image occupies the same footprint the calendar table did.
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), date, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("이 날의 일기가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DiarySidePanel(repo: DiaryRepository, date: String, today: String, onOpenDiary: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(date) { repo.load(date) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(A4_RATIO).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), date, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text("이 날의 일기가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (date == today) {
            Button(onClick = { onOpenDiary(date) }, shape = MaterialTheme.shapes.small) { Text("오늘 일기 그리기") }
        } else {
            Button(onClick = { bmp?.let { Toast.makeText(ctx, saveToGallery(ctx, it, "diary_$date"), Toast.LENGTH_SHORT).show() } },
                enabled = bmp != null, shape = MaterialTheme.shapes.small) { Text("이미지 저장") }
        }
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
