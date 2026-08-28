package com.g1.sketchbook.sketchbook

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.share.ShareRepository
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.brush.alignment
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun bgDrawable(key: String) = when (key) {
    "drawing" -> R.drawable.paper_drawing
    "canvas" -> R.drawable.paper_canvas
    "recycled" -> R.drawable.paper_recycled
    "kraft" -> R.drawable.paper_kraft
    else -> R.drawable.paper_watercolor
}
private val PAPER_KEYS = listOf("a5", "a4", "a3")
private val DISPLAY_KEYS = listOf("desktop", "mobile", "tablet")

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
                drawCircle(color, sw * 0.7f, Offset(l + pw / 2, t + ph * 0.09f))   // front-camera dot
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
fun SketchbookTab(
    nickname: String,
    myUid: String,
    syncGeneration: Int = 0,
    onOpenBook: (String) -> Unit,
    initialShowShared: Boolean = false,
    openWizardAs: WType? = null,
    onWizardOpened: () -> Unit = {},
    previewBooks: List<Sketchbook>? = null,
) {
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var refresh by remember { mutableIntStateOf(0) }
    // 백그라운드 동기화가 파일을 직접 써서 Compose가 모르므로, 동기화가 끝나면 목록을 다시 읽는다.
    LaunchedEffect(syncGeneration) { if (syncGeneration > 0) refresh++ }
    val books = previewBooks ?: remember(refresh) { repo!!.list() }
    var creating by remember { mutableStateOf(false) }
    var wizardType by remember { mutableStateOf<WType?>(null) }

    LaunchedEffect(openWizardAs) {
        if (openWizardAs != null) { wizardType = openWizardAs; creating = true; onWizardOpened() }
    }

    if (creating) {
        CreateWizard(
            nickname = nickname, myUid = myUid, repo = repo, initialType = wizardType,
            onDismiss = { creating = false; wizardType = null },
            onCreated = { book -> creating = false; wizardType = null; refresh++; onOpenBook(book.id) },
        )
    }
    SketchbookListScreen(
        books = books,
        repo = repo,
        initialShowShared = initialShowShared,
        onNewPersonal = { wizardType = WType.PERSONAL; creating = true },
        onNewShared = { wizardType = WType.SHARED_NEW; creating = true },
        onJoinShared = { wizardType = WType.SHARED_JOIN; creating = true },
        onOpen = { onOpenBook(it.id) },
        onDelete = { repo?.let { r -> deleteSynced(scope, r, backup, myUid, it.id) }; refresh++ },
        onToggleFav = { repo?.let { r -> toggleFavSynced(scope, r, backup, myUid, it.id) }; refresh++ },
        onEditBook = { book, name, newCover, removeCover, newColor ->
            repo?.let { r -> renameSynced(scope, r, backup, myUid, book.id, name) }
            if (newCover != null) repo?.let { r -> saveCoverSynced(scope, r, backup, myUid, book.id, newCover) }
            else if (removeCover) repo?.let { r -> removeCoverSynced(scope, r, backup, myUid, book.id) }
            repo?.let { r -> setCoverColorSynced(scope, r, backup, myUid, book.id, newColor) }
            refresh++
        },
    )
}

private enum class WStep { TYPE, NAME, SIZE, BG, CODE }
enum class WType { PERSONAL, SHARED_NEW, SHARED_JOIN }

/** Step-by-step popup: pick type → (name → size → bg) for creation, or (code) for joining a shared book. */
@Composable
private fun CreateWizard(
    nickname: String,
    myUid: String,
    repo: SketchbookRepository?,
    onDismiss: () -> Unit,
    onCreated: (Sketchbook) -> Unit,
    initialType: WType? = null,
) {
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    // A preset type (e.g. from the home screen's 새 노트/공유/참여 buttons) skips straight past
    // the "무엇을 만들까요?" step into the flow for that type.
    var step by remember { mutableStateOf(if (initialType == null) WStep.TYPE else if (initialType == WType.SHARED_JOIN) WStep.CODE else WStep.NAME) }
    var type by remember { mutableStateOf(initialType ?: WType.PERSONAL) }
    var name by remember { mutableStateOf("") }
    var sizeKey by remember { mutableStateOf("a4") }
    var bgKey by remember { mutableStateOf("watercolor") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun finishPersonal() { repo?.let { createSynced(scope, it, backup, myUid, name, sizeKey, bgKey) }?.let(onCreated) }
    // 그림 자체가 아니라 "이 계정이 이 코드에 참여 중"이라는 사실만 백업에 올려서, 같은 계정의
    // 다른 기기가 다음 동기화 때 이 카드를 자동으로 만들어 보게 한다(그림은 이미 ShareRepository
    // 실시간 세션이 기기 상관없이 공유).
    fun pushSharedRef(book: Sketchbook) {
        val code = book.code ?: return
        if (myUid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.pushSharedBookRef(myUid, code, book.name, book.sizeKey, book.bgKey, book.createdAt) }
    }
    fun finishSharedNew() {
        val targetRepo = repo ?: return
        busy = true; error = null
        scope.launch {
            val share = ShareRepository()
            runCatching { share.createSession(myUid, nickname) }.fold(
                onSuccess = { c -> onCreated(targetRepo.create(name, "a4", "watercolor", shared = true, code = c).also(::pushSharedRef)) },
                onFailure = { busy = false; error = it.message ?: "공유 세션을 만들지 못했어요." },
            )
        }
    }
    fun finishJoin() {
        val targetRepo = repo ?: return
        busy = true; error = null
        scope.launch {
            val share = ShareRepository()
            share.joinSession(code, myUid, nickname).fold(
                onSuccess = { onCreated(targetRepo.create(name.ifBlank { "공유 스케치북" }, "a4", "watercolor", shared = true, code = code.uppercase()).also(::pushSharedRef)) },
                onFailure = { busy = false; error = it.message ?: "참여하지 못했어요." },
            )
        }
    }

    when {
        // 개인 스케치북 — 이름/사이즈/배경을 단계로 나누지 않고 카드 한 화면에서 전부 선택.
        // 시안 예시 없이도 재진입 가능하도록 TYPE 선택 단계는 남겨두되(현재 진입 경로는 항상
        // initialType을 주므로 사실상 도달하지 않음), PERSONAL은 항상 이 카드로 처리한다.
        step == WStep.TYPE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("무엇을 만들까요?") },
                text = {
                    Column {
                        WizardChoice(Icons.Filled.Book, "개인 스케치북") { type = WType.PERSONAL; step = WStep.NAME }
                        Spacer(Modifier.height(10.dp))
                        WizardChoice(Icons.Filled.Groups, "공유 스케치북 만들기") { type = WType.SHARED_NEW; step = WStep.NAME }
                        Spacer(Modifier.height(10.dp))
                        WizardChoice(Icons.AutoMirrored.Filled.Login, "공유 스케치북 참여") { type = WType.SHARED_JOIN; code = ""; step = WStep.CODE }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            )
        }
        step == WStep.CODE -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("초대 코드 입력") },
                text = {
                    Column {
                        OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6); error = null },
                            singleLine = true, enabled = !busy, label = { Text("초대 코드") },
                            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                        if (error != null) { Spacer(Modifier.height(10.dp)); Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (busy) { Spacer(Modifier.height(12.dp)); androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                },
                confirmButton = { TextButton(enabled = !busy && code.length >= 4, onClick = { finishJoin() }) { Text("참여") } },
                dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
            )
        }
        type == WType.SHARED_NEW -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("이름을 정해요") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it.take(20) }, singleLine = true,
                            label = { Text("스케치북 이름") }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                        if (error != null) { Spacer(Modifier.height(10.dp)); Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (busy) { Spacer(Modifier.height(12.dp)); androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                },
                confirmButton = { TextButton(enabled = !busy, onClick = { finishSharedNew() }) { Text("만들기") } },
                dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
            )
        }
        else -> {
            PersonalCreateCard(
                name = name, onName = { name = it.take(20) },
                sizeKey = sizeKey, onSize = { sizeKey = it },
                bgKey = bgKey, onBg = { bgKey = it },
                onCancel = onDismiss, onCreate = { finishPersonal() },
            )
        }
    }
}

/** 새 스케치북(개인) 카드 — 이름/종이/디스플레이/배경을 한 화면에서 고른다.
 *  배경 스와치를 고르면 그 재질을 팝업 바로 뒤 전체 배경에 즉시 적용해 실제 느낌을 미리 볼 수 있다. */
@Composable
private fun PersonalCreateCard(
    name: String, onName: (String) -> Unit,
    sizeKey: String, onSize: (String) -> Unit,
    bgKey: String, onBg: (String) -> Unit,
    onCancel: () -> Unit, onCreate: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            // 팝업 뒷배경 = 선택된 종이 재질 (스와치 탭 시 즉시 갱신되어 바로 느낌을 확인할 수 있음).
            Image(painterResource(bgDrawable(bgKey)), "배경 미리보기", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = Dimens.Screen.bottomMargin),
                contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = Dimens.Wizard.cardWidth).fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.Wizard.cardRadius))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                ) {
                    OutlinedTextField(name, onName, singleLine = true,
                        placeholder = { Text("스케치북 이름 입력") }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(18.dp))
                    Text("종이", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                    SizeRow(Catalog.sizes.filter { it.key in PAPER_KEYS }, sizeKey, onSize)
                    Spacer(Modifier.height(14.dp))
                    Text("디스플레이", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                    SizeRow(Catalog.sizes.filter { it.key in DISPLAY_KEYS }, sizeKey, onSize)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("배경", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        items(Catalog.backgrounds) { bg ->
                            val on = bg.key == bgKey
                            Image(painterResource(bgDrawable(bg.key)), bg.label, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onBg(bg.key) }
                                    .border(if (on) 3.dp else 1.dp,
                                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onCancel) { Text("취소") }
                        TextButton(onClick = onCreate, enabled = name.isNotBlank()) { Text("생성", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
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
    repo: SketchbookRepository?,
    initialShowShared: Boolean = false,
    onNewPersonal: () -> Unit = {},
    onNewShared: () -> Unit = {},
    onJoinShared: () -> Unit = {},
    onOpen: (Sketchbook) -> Unit,
    onDelete: (Sketchbook) -> Unit,
    onToggleFav: (Sketchbook) -> Unit,
    onEditBook: (Sketchbook, String, Bitmap?, Boolean, Long?) -> Unit,
) {
    val context = LocalContext.current
    val session = remember { com.g1.sketchbook.data.SessionStore(context) }
    var pendingDelete by remember { mutableStateOf<Sketchbook?>(null) }
    var editing by remember { mutableStateOf<Sketchbook?>(null) }
    val showShared = initialShowShared
    var columns by remember { mutableIntStateOf(session.gridColumns) }
    var columnMenuOpen by remember { mutableStateOf(false) }
    // 가로모드 3열(서브패널) 전용 — 2열 그리드에서 탭한 책의 페이지 썸네일을 보여준다. 세로모드에선
    // 안 쓰임(MainTabPage가 landscape가 아니면 sidePanel 자체를 그리지 않음).
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedId by remember { mutableStateOf<String?>(null) }
    val shown = books.filter { it.shared == showShared }
    val selectedBook = shown.firstOrNull { it.id == selectedId }
    com.g1.sketchbook.ui.main.MainTabPage(
        title = if (showShared) "Draw together" else "Sketchbook list",
        sidePanel = {
            PageThumbnailPanel(
                repo = repo, book = selectedBook,
                onOpen = { selectedBook?.let(onOpen) },
                onEdit = { selectedBook?.let { editing = it } },
            )
        },
        actions = {
            if (showShared) {
                IconButton(onClick = onNewShared) { Icon(Icons.Filled.GroupAdd, "공유 스케치북 만들기") }
                IconButton(onClick = onJoinShared) { Icon(Icons.Filled.Key, "참여코드로 입장하기") }
            } else {
                IconButton(onClick = onNewPersonal) { Icon(Icons.Filled.Add, "스케치북 추가") }
            }
            // 그리드 열 수(3/4/5) 설정 — 선택하면 즉시 반영 + 저장.
            Box {
                IconButton(onClick = { columnMenuOpen = true }) { Icon(Icons.Filled.Menu, "목록 배열") }
                DropdownMenu(expanded = columnMenuOpen, onDismissRequest = { columnMenuOpen = false }) {
                    (3..5).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("${n}열", fontWeight = if (n == columns) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { columns = n; session.gridColumns = n; columnMenuOpen = false },
                        )
                    }
                }
            }
        },
    ) {
            if (shown.isEmpty()) {
                Text(if (showShared) "아직 공유받은 스케치북이 없어요." else "아직 스케치북이 없어요. 홈 화면에서 만들어보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    // 그리드에 여백이 없으면 가장자리 칸의 표지 그림자(shadow(12.dp, clip=false))가
                    // 스크롤 뷰포트 경계에서 그대로 잘렸다 — 그림자가 번질 여유를 사방에 준다(2026-08-27,
                    // 홈 캐러셀의 shadowSlack과 같은 문제·같은 해결).
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    gridItems(shown, key = { it.id }) { b ->
                        // 가로모드: 탭 = 3열에 미리보기 선택, 길게 누르면 바로 열림(표지 수정은 3열
                        // 패널의 연필 아이콘으로 이동). 세로모드는 기존 그대로(탭=열기, 길게=수정).
                        if (landscape) {
                            CoverCard(b, repo, onOpen = { selectedId = b.id }, onEdit = { onOpen(b) })
                        } else {
                            CoverCard(b, repo, onOpen = { onOpen(b) }, onEdit = { editing = b })
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

    editing?.let { target ->
        // books에서 최신 상태를 다시 찾아 쓴다 — 그래야 즐겨찾기를 토글해도 다이얼로그를 닫지 않고
        // 별 아이콘이 바로 갱신된다(editing 자체는 다이얼로그를 연 시점의 스냅샷이라 갱신되지 않음).
        val current = books.firstOrNull { it.id == target.id } ?: target
        EditCoverDialog(
            book = current,
            repo = repo,
            onCancel = { editing = null },
            onSave = { name, newCover, removeCover, newColor -> onEditBook(current, name, newCover, removeCover, newColor); editing = null },
            onToggleFav = { onToggleFav(current) },
            onDelete = { editing = null; pendingDelete = current },
        )
    }
}

/** 가로모드 3열(서브패널) — 2열 그리드에서 탭으로 선택한 책의 페이지 썸네일을 위에서 아래로
 *  스크롤해서 훑어본다. 아무 것도 선택 안 됐으면 안내 문구만, 선택되면 제목+수정/열기 아이콘과
 *  [MAX_PAGES]장의 썸네일 목록. */
@Composable
private fun PageThumbnailPanel(repo: SketchbookRepository?, book: Sketchbook?, onOpen: () -> Unit, onEdit: () -> Unit) {
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "표지를 탭해 페이지를 미리보세요", fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(book.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Edit, "표지 수정", modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onOpen, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.OpenInNew, "열기", modifier = Modifier.size(17.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(book.pageCount) { index -> PageThumbnailCell(repo, book.id, index) }
        }
    }
}

@Composable
private fun PageThumbnailCell(repo: SketchbookRepository?, bookId: String, index: Int) {
    var thumb by remember(bookId, index) { mutableStateOf<Bitmap?>(null) }
    // 3열 패널의 썸네일은 PagePanel 그리드 셀(기본 160px)보다 훨씬 넓게 그려져서, 기본값 그대로면
    // 확대돼 흐릿하게 보였다 — 이 자리 전용으로 더 높은 해상도를 요청한다.
    LaunchedEffect(bookId, index, repo) { thumb = withContext(Dispatchers.IO) { repo?.loadPageThumb(bookId, index, reqPx = 480) } }
    Box(
        Modifier.fillMaxWidth().aspectRatio(Dimens.Home.coverRatio).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        thumb?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
        }
        Text(
            "${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = if (thumb != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(6.dp),
        )
    }
}

@Composable
private fun CoverCard(book: Sketchbook, repo: SketchbookRepository?, onOpen: () -> Unit, onEdit: () -> Unit) {
    // 갤러리에서 고른 표지 이미지가 있으면 그걸, 없으면 (커스텀 지정 시) coverColor, 그것도 없으면
    // 기본색을 보여준다. coverVersion을 키에 넣어야 같은 id라도 표지 사진이 바뀌면 다시 읽어온다.
    var cover by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.id, book.coverVersion, repo) { cover = withContext(Dispatchers.IO) { repo?.loadCoverThumb(book.id) } }
    // Same cover ratio as the home carousel (Dimens.Home.coverRatio) — every note cover keeps one
    // fixed proportion across the whole app, whichever screen shows it.
    Box(Modifier.aspectRatio(Dimens.Home.coverRatio)) {
        // 목록 표지도 홈과 같은 공용 컴포넌트를 사용해 기본색과 책등 위치를 일치시킵니다.
        SketchbookCover(
            modifier = Modifier.fillMaxSize()
                .shadow(12.dp, SketchbookCoverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .bounceClick(onClick = onOpen, onLongClick = onEdit),
            coverColor = book.coverColor?.let { Color(it) } ?: DefaultSketchbookCoverColor,
            coverImage = cover?.let { BitmapPainter(it.asImageBitmap()) },
        ) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
                Text(book.name, color = Color(0xFFF3ECD9), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = if (book.shared && book.code != null) "🤝 ${book.code} · ${book.dateLabel}" else book.dateLabel
                Text(meta, color = Color(0xFFF3ECD9).copy(alpha = 0.8f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** 표지 길게 눌러 여는 수정 시트 — 이름, 표지(색상 또는 갤러리 사진), 즐겨찾기 토글, 삭제를 거의
 *  전체화면 시트 하나에서 처리한다(2026-08-20, 시안 이미지 기준 재구성 — 예전엔 작은 팝업 카드).
 *  종이 재질(bgKey)은 그림 그릴 때 쓰는 캔버스 배경이라 표지 디자인과는 별개이고, 사이즈도 이미
 *  그려둔 페이지 비율이 깨질 수 있어 여기서 건드리지 않는다.
 *  표지 변경은 색상휠/갤러리 아이콘 2개뿐 — 즐겨찾기·삭제도 설명글 없이 아이콘 버튼 2개로 압축
 *  (2026-08-20, 텍스트가 많던 첫 재구성 버전에서 한 번 더 정리). 갤러리 사진은 고르는 즉시 가운데로
 *  자동 크롭하지 않고, [CoverImageCropDialog]에서 확대·이동으로 표지에 실제로 쓰일 범위를 직접
 *  고른 뒤 적용한다. 이름 글자수 제한은 마법사 등 앱 전체가 20자 기준이라 그대로 유지(표시만 카운터로). */
@Composable
internal fun EditCoverDialog(
    book: Sketchbook,
    repo: SketchbookRepository?,
    onCancel: () -> Unit,
    onSave: (name: String, newCover: Bitmap?, removeCover: Boolean, newColor: Long?) -> Unit,
    onToggleFav: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(book.id) { mutableStateOf(book.name) }
    // 이미 저장된 표지 사진(있으면) 먼저 불러온다.
    var existingCover by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.id, book.coverVersion, repo) { existingCover = withContext(Dispatchers.IO) { repo?.loadCover(book.id) } }
    // 갤러리에서 새로 고른 사진(크롭 적용 후, 저장 전 미리보기) — 색상 버튼으로 색을 고르면 기존
    // 사진도 지우도록 표시(색/사진 둘 다 있으면 사진이 우선 표시되어 색 선택이 무의미해 보이므로).
    var pickedCover by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    var selection by remember(book.id) { mutableStateOf(CoverEditSelection(book.coverColor)) }
    var colorWheelOpen by remember { mutableStateOf(false) }
    // 갤러리에서 방금 고른 원본(크롭 전) — null이 아니면 범위 선택 다이얼로그가 뜬다.
    var rawPicked by remember { mutableStateOf<Bitmap?>(null) }
    var imageLoading by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val previewCover = pickedCover ?: existingCover.takeUnless { selection.removeCover }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            imageLoading = true
            imageError = null
            val decoded = withContext(Dispatchers.IO) {
                runCatching { decodeCoverBitmap(context, uri, 1600) }.getOrNull()
            }
            imageLoading = false
            if (decoded != null) rawPicked = decoded
            else imageError = "이미지를 불러오지 못했습니다. 다른 이미지를 선택해주세요."
        }
    }

    // rawPicked·크롭 화면을 별도 Dialog로 새로 띄우지 않고 이 Dialog 하나 안에서 내용만 바꿔치기
    // 한다 — 갤러리(다른 앱) 다녀온 직후 새 Dialog 창을 하나 더 여는 조합이 일부 기기에서 창이
    // 제대로 붙지 않아 "사진을 골라도 적용이 안 됨" 현상으로 이어졌던 것으로 보임(2026-08-20).
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val raw = rawPicked
        if (raw != null) {
            CoverImageCropContent(
                source = raw, targetRatio = Dimens.Home.coverRatio,
                onApply = { cropped ->
                    pickedCover = cropped
                    selection = selection.imageApplied()
                    rawPicked = null
                },
                onCancel = { rawPicked = null },
            )
            return@Dialog
        }
        Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxHeight().padding(vertical = 28.dp)
                    .widthIn(max = Dimens.Home.editCoverCardWidth).fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp)) {
                    Text("스케치북 표지 변경", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.CenterStart)) {
                        IconButton(onClick = onToggleFav) {
                            Icon(
                                if (book.fav) Icons.Filled.Star else Icons.Filled.StarBorder, "즐겨찾기에 추가",
                                tint = if (book.fav) Color(0xFFFFD43B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, "이 스케치북 삭제", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Filled.Close, "닫기")
                    }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(24.dp))
                    // 큰 미리보기 — 지금 고른 색/사진이 표지 모양(책등 포함) 그대로 어떻게 보일지 확인.
                    SketchbookCover(
                        modifier = Modifier.width(200.dp).aspectRatio(Dimens.Home.coverRatio)
                            .align(Alignment.CenterHorizontally)
                            .shadow(16.dp, SketchbookCoverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black),
                        coverColor = selection.color?.let { Color(it) } ?: DefaultSketchbookCoverColor,
                        coverImage = previewCover?.let { BitmapPainter(it.asImageBitmap()) },
                    )
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider()

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { Text("${name.length} / 20", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        Box {
                            CoverActionIcon(Icons.Filled.Palette, "색상 변경") {
                                if (colorWheelOpen) {
                                    selection = selection.cancelColor()
                                    colorWheelOpen = false
                                } else {
                                    selection = selection.startColor(
                                        DefaultSketchbookCoverColor.toArgb().toLong() and 0xFFFFFFFFL,
                                    )
                                    colorWheelOpen = true
                                }
                            }
                            if (colorWheelOpen) Popup(
                                BelowCenterAnchor(with(LocalDensity.current) { 8.dp.roundToPx() }),
                                {
                                    selection = selection.cancelColor()
                                    colorWheelOpen = false
                                },
                                PopupProperties(focusable = true),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    com.g1.sketchbook.brush.ColorPickerCard(selection.pendingColor!!, onColor = {
                                        selection = selection.previewColor(it)
                                    })
                                    Row(
                                        Modifier.width(248.dp).background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        TextButton(onClick = {
                                            selection = selection.cancelColor()
                                            colorWheelOpen = false
                                        }) { Text("취소") }
                                        TextButton(onClick = {
                                            selection = selection.confirmColor()
                                            pickedCover = null
                                            colorWheelOpen = false
                                        }) { Text("확인", fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                        CoverActionIcon(Icons.Filled.AddPhotoAlternate, "이미지로 변경") {
                            if (!imageLoading) {
                                imageError = null
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        }
                        if (previewCover != null) {
                            CoverActionIcon(Icons.Filled.HideImage, "사진 빼기") {
                                pickedCover = null
                                selection = selection.requestImageRemoval()
                            }
                        }
                    }
                    if (imageLoading) {
                        Text(
                            "이미지를 불러오는 중…",
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    imageError?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onCancel) { Text("취소") }
                    TextButton(
                        onClick = { onSave(name, pickedCover, selection.removeCover, selection.color) },
                        enabled = name.isNotBlank() && !imageLoading,
                    ) { Text("완료", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** 표지 편집 중인 선택 상태. 색상휠은 [pendingColor]만 바꾸고 확인할 때만 실제 [color]에 반영한다. */
internal data class CoverEditSelection(
    val color: Long?,
    val pendingColor: Long? = null,
    val removeCover: Boolean = false,
) {
    fun startColor(defaultColor: Long) = copy(pendingColor = color ?: defaultColor)
    fun previewColor(value: Long) = copy(pendingColor = value)
    fun cancelColor() = copy(pendingColor = null)

    fun confirmColor(): CoverEditSelection {
        val confirmed = pendingColor ?: return this
        return copy(color = confirmed, pendingColor = null, removeCover = true)
    }

    fun imageApplied() = copy(pendingColor = null, removeCover = false)
    fun requestImageRemoval() = copy(pendingColor = null, removeCover = true)
}

/** 표지 변경/즐겨찾기/삭제 공용 — 원형 배경 위에 아이콘 하나만 두는 텍스트 없는 액션 버튼. */
@Composable
private fun CoverActionIcon(
    icon: ImageVector, contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit,
) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** 색상휠 팝업을 앵커(색상 버튼) 아래 가운데에 띄운다 — 화면 밖으로 나가지 않게 좌우로만 clamp. */
private class BelowCenterAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.bottom + gapPx).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** 갤러리에서 고른 원본 사진에서 표지로 쓸 범위를 직접 고르는 화면 — 핀치로 확대, 드래그로 이동
 *  (다이어리 상세 보기의 확대/이동 제스처와 같은 패턴). 미리보기 창이 곧 최종 표지 비율이라 "적용"
 *  시 보이는 그대로가 잘려 저장된다. 별도 Dialog로 새로 띄우지 않고 [EditCoverDialog]가 이미 열어
 *  둔 Dialog 창 안에서 내용만 바꿔치기 한다 — 갤러리(다른 앱) 다녀온 직후 Dialog를 하나 더 여는
 *  조합이 일부 기기에서 창이 제대로 붙지 않는 문제가 있었다(2026-08-20). */
@Composable
private fun CoverImageCropContent(source: Bitmap, targetRatio: Float, onApply: (Bitmap) -> Unit, onCancel: () -> Unit) {
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val frameWidthDp = 240.dp
    val frameHeightDp = frameWidthDp / targetRatio

    Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background, shadowElevation = 16.dp, tonalElevation = 3.dp) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("표지에 쓸 범위 선택", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("손가락으로 확대·이동해서 고르세요", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.width(frameWidthDp).height(frameHeightDp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset += pan
                            }
                        },
                ) {
                    Image(
                        bitmap = source.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                    Button(
                        onClick = {
                            val frameWpx = with(density) { frameWidthDp.toPx() }
                            val frameHpx = with(density) { frameHeightDp.toPx() }
                            onApply(cropSelectedRegion(source, frameWpx, frameHpx, scale, offset))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("적용", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** [CoverImageCropContent]에서 화면에 보이던 영역을 실제 소스 픽셀 좌표로 환산해 그대로 잘라낸다 —
 *  보이는 그대로가 저장돼야 하므로, 표시용 graphicsLayer 변환(scale/translate)을 소스 좌표계로
 *  거꾸로 계산. baseScale은 ContentScale.Crop이 화면을 꽉 채우기 위해 원본에 곱하는 배율, 거기에
 *  사용자가 더 확대한 배율(scale)까지 곱한 게 실제 화면-원본 간 총 배율(totalScale)이다. */
private fun cropSelectedRegion(src: Bitmap, frameWpx: Float, frameHpx: Float, scale: Float, offset: Offset): Bitmap {
    val srcW = src.width.toFloat(); val srcH = src.height.toFloat()
    val baseScale = maxOf(frameWpx / srcW, frameHpx / srcH)
    val totalScale = baseScale * scale
    val visW = (frameWpx / totalScale).coerceIn(1f, srcW)
    val visH = (frameHpx / totalScale).coerceIn(1f, srcH)
    val cx = (srcW / 2f - offset.x / totalScale).coerceIn(visW / 2f, srcW - visW / 2f)
    val cy = (srcH / 2f - offset.y / totalScale).coerceIn(visH / 2f, srcH - visH / 2f)
    val x0 = (cx - visW / 2f).roundToInt().coerceIn(0, (srcW - visW).roundToInt().coerceAtLeast(0))
    val y0 = (cy - visH / 2f).roundToInt().coerceIn(0, (srcH - visH).roundToInt().coerceAtLeast(0))
    val w = visW.roundToInt().coerceIn(1, src.width - x0)
    val h = visH.roundToInt().coerceIn(1, src.height - y0)
    return Bitmap.createBitmap(src, x0, y0, w, h)
}

/** 갤러리 원본은 화면·저장용으로 쓰기엔 너무 커서, 긴 변이 [maxDim]을 넘지 않도록 다운샘플링해
 *  디코드한다(표지 그림과 같은 원리, `SketchbookRepository.loadPageThumb`와 동일 패턴). internal —
 *  계정 아바타 사진 선택(SettingsTab)에서도 그대로 재사용한다. */
internal fun decodeCoverBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > maxDim) {
                val ratio = maxDim.toFloat() / longest
                decoder.setTargetSize(
                    (width * ratio).roundToInt().coerceAtLeast(1),
                    (height * ratio).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
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
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var view by remember { mutableStateOf<BrushView?>(null) }
    // 색상/굵기/투명도는 SessionStore에 저장해 앱을 다시 켜도 이어서 쓸 수 있게 한다(브러시 종류
    // 자체나 지우개 여부는 저장하지 않고 매번 펜으로 시작).
    val session = remember { com.g1.sketchbook.data.SessionStore(context) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(session.brushColor) }
    var erasing by remember { mutableStateOf(false) }
    // 올가미(선택)·페인트통(채우기)은 브러시/지우개와 상호배타적인 별도 도구 — 하나를 켜면 나머지는
    // 꺼진다(아래 onBrush/onToggleErase/onToggleLasso/onToggleFill이 서로를 끈다).
    var lassoActive by remember { mutableStateOf(false) }
    var fillActive by remember { mutableStateOf(false) }
    var lassoDeleteAt by remember { mutableStateOf<Offset?>(null) }
    // 라소 켜기 직전 상태를 기억해뒀다가, 캔버스 바깥을 탭해 라소를 나갈 때 그대로 복원한다.
    var preLassoErasing by remember { mutableStateOf(false) }
    var preLassoFillActive by remember { mutableStateOf(false) }
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
    var page by remember { mutableIntStateOf(0) }
    val pageCount = book.pageCount   // fixed at MAX_PAGES from creation — no add/remove anymore
    var pagesOpen by remember { mutableStateOf(false) }
    var readModeOpen by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var toolbarCollapsed by remember { mutableStateOf(false) }
    var toolbarDock by remember { mutableStateOf(com.g1.sketchbook.brush.ToolbarDock.BOTTOM) }
    var toolbarDragPx by remember { mutableStateOf(Offset.Zero) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    // Save the current page SYNCHRONOUSLY (strokes only, no paper) before any page load, so a page
    // switch can't read the file before an async write finishes (that race dropped recent strokes).
    fun saveCurrent() { val v = view ?: return; val pg = page; val b = v.exportContent() ?: return; repo.savePage(book.id, pg, b) }
    fun goTo(p: Int) {
        if (p == page || p !in 0 until pageCount) return
        saveCurrent(); page = p; view?.loadContent(repo.loadPage(book.id, p))
    }

    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler {
        when {
            fullscreen -> fullscreen = false
            else -> { saveCurrent(); onBack() }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() },
    ) {
        val density2 = LocalDensity.current
        Box(Modifier.fillMaxSize().padding(if (fullscreen) 0.dp else Dimens.Canvas.outerPadding)) {
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
                    v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp; v.opacity = opacity / 100f
                    v.erasing = erasing; v.locked = locked; v.eraserBlur = eraserBlur
                    v.lassoMode = lassoActive; v.fillMode = fillActive
                    v.onLassoSelectionChanged = { has, x, y -> lassoDeleteAt = if (has) Offset(x, y) else null }
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
                    v.onLassoTapOutside = { lassoActive = false; erasing = preLassoErasing; fillActive = preLassoFillActive }
                    v.onStylusButtonChanged = { pressed ->
                        if (pressed) {
                            preStylusErasing = erasing; preStylusLasso = lassoActive; preStylusFill = fillActive
                            erasing = true; lassoActive = false; fillActive = false
                        } else {
                            erasing = preStylusErasing; lassoActive = preStylusLasso; fillActive = preStylusFill
                        }
                    }
                    v.onStrokeEnd = { val pg = page; v.exportContent()?.let { b -> savePageSynced(scope, repo, backup, myUid, book.id, pg, b) } }
                },
            )
            eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
            lassoDeleteAt?.let { p -> com.g1.sketchbook.brush.LassoDeleteButton(p.x, p.y, onDelete = { view?.deleteLassoSelection() }) }
            // 현재 페이지 표기 — 페이지 우측 하단에 작게 떠 있는 배지(다른 화면들의 반투명 라벨과
            // 같은 스타일: 검정 60% 배경 + 흰 글자).
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("${page + 1} / $pageCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 버튼바 둘 다: 기본 위치에 붙지만(떠 있는 오버레이라 캔버스 크기는 안 바뀜), 손잡이를 길게
        // 눌러 드래그하면 자유롭게 2D로 움직이다가 놓은 위치에서 가장 가까운 가장자리로 옮겨 붙는다
        // (최소화 상태여도 동일 — 2026-08-20 이전엔 최소화 시 도킹된 축으로만 밀리는 특수 케이스였음).
        fun barModifier(dock: com.g1.sketchbook.brush.ToolbarDock, collapsed: Boolean, dragPx: Offset) = Modifier
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
            onUndo = { view?.undo() }, onRedo = { view?.redo() }, onClear = { view?.clearCanvas(); saveCurrent() },
            favorites = favorites,
            onEditFavorite = { i, c -> val nf = favorites.toMutableList(); nf[i] = c; favorites = nf; session.favoriteColors = nf },
            eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
            lassoActive = lassoActive,
            onToggleLasso = {
                if (!lassoActive) { preLassoErasing = erasing; preLassoFillActive = fillActive }
                lassoActive = !lassoActive
                if (lassoActive) { erasing = false; fillActive = false }
            },
            fillActive = fillActive,
            onToggleFill = { fillActive = !fillActive; if (fillActive) { erasing = false; lassoActive = false } },
            collapsed = toolbarCollapsed, onToggleCollapsed = { toolbarCollapsed = !toolbarCollapsed },
            onDragBar = { d -> toolbarDragPx += d },
            onDragBarEnd = {
                val cwPx = with(density2) { maxWidth.toPx() }; val chPx = with(density2) { maxHeight.toPx() }
                toolbarDock = com.g1.sketchbook.brush.nearestDock(toolbarDock, toolbarDragPx, cwPx, chPx)
                toolbarDragPx = Offset.Zero
            },
            dock = toolbarDock,
            modifier = barModifier(toolbarDock, toolbarCollapsed, toolbarDragPx),
        )
        // 화면버튼(페이지/회전/잠금/전체화면)은 가로/세로 상관없이 항상 우측 상단에 고정된 확장
        // 버튼 — 탭하면 펼쳐지고 기능을 고르거나 밖을 탭하면 자동으로 닫힌다(2026-08-20).
        com.g1.sketchbook.brush.ScreenControls(
            onOpenPages = { pagesOpen = true },
            onReadMode = { saveCurrent(); readModeOpen = true },
            onRotate = { view?.rotate() },
            locked = locked, onToggleLock = { locked = !locked },
            fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
    if (pagesOpen) {
        PagePanel(
            repo, book.id, page, pageCount,
            onSelect = { p -> goTo(p) },
            onReorder = { order ->
                saveCurrent()
                reorderPagesSynced(scope, repo, backup, myUid, book.id, order, pageCount)
                val newPage = order.indexOf(page)
                if (newPage != -1 && newPage != page) { page = newPage; view?.loadContent(repo.loadPage(book.id, newPage)) }
            },
            onDismiss = { pagesOpen = false },
        )
    }
    if (readModeOpen) {
        com.g1.sketchbook.readmode.ReadModeScreen(
            repo = repo, book = book, startPage = page,
            onClose = { lastPage -> readModeOpen = false; goTo(lastPage) },
        )
    }
}
