package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    avatar: String,
    myUid: String,
    onOpenBook: (String) -> Unit,
    initialShowShared: Boolean = false,
    onGoSettings: () -> Unit = {},
    openWizardAs: WType? = null,
    onWizardOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val books = remember(refresh) { repo.list() }
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
        avatar = avatar,
        initialShowShared = initialShowShared,
        onGoSettings = onGoSettings,
        onNewShared = { wizardType = WType.SHARED_NEW; creating = true },
        onJoinShared = { wizardType = WType.SHARED_JOIN; creating = true },
        onOpen = { onOpenBook(it.id) },
        onDelete = { repo.delete(it.id); refresh++ },
        onToggleFav = { repo.toggleFav(it.id); refresh++ },
    )
}

private enum class WStep { TYPE, NAME, SIZE, BG, CODE }
enum class WType { PERSONAL, SHARED_NEW, SHARED_JOIN }

/** Step-by-step popup: pick type → (name → size → bg) for creation, or (code) for joining a shared book. */
@Composable
private fun CreateWizard(
    nickname: String,
    myUid: String,
    repo: SketchbookRepository,
    onDismiss: () -> Unit,
    onCreated: (Sketchbook) -> Unit,
    initialType: WType? = null,
) {
    val scope = rememberCoroutineScope()
    val share = remember { com.g1.sketchbook.share.ShareRepository() }
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
    avatar: String = "🦆",
    initialShowShared: Boolean = false,
    onGoSettings: () -> Unit = {},
    onNewShared: () -> Unit = {},
    onJoinShared: () -> Unit = {},
    onOpen: (Sketchbook) -> Unit,
    onDelete: (Sketchbook) -> Unit,
    onToggleFav: (Sketchbook) -> Unit,
) {
    val context = LocalContext.current
    val session = remember { com.g1.sketchbook.data.SessionStore(context) }
    var pendingDelete by remember { mutableStateOf<Sketchbook?>(null) }
    var showShared by remember { mutableStateOf(initialShowShared) }
    var columns by remember { mutableIntStateOf(session.gridColumns) }
    var columnMenuOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The outer tab Scaffold (MainScreen) already pads for the status bar; without this a
        // second nested inset would push this tab's title lower than Home/Settings.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()
            .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin,
                start = Dimens.Screen.sideMargin, end = Dimens.Screen.sideMargin)) {
            com.g1.sketchbook.ui.main.TabHeader(avatar, onAvatar = onGoSettings) {
                if (showShared) {
                    IconButton(onClick = onNewShared) { Icon(Icons.Filled.Groups, "공유 스케치북 만들기") }
                    IconButton(onClick = onJoinShared) { Icon(Icons.AutoMirrored.Filled.Login, "공유 스케치북 참여") }
                }
            }
            Spacer(Modifier.height(Dimens.Screen.titleGap))
            Text(if (showShared) "Draw together" else "Sketchbook list", fontFamily = com.g1.sketchbook.ui.theme.Cavorting,
                fontSize = Dimens.Screen.titleSp, color = com.g1.sketchbook.ui.theme.DaymoryTeal,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterIconBtn(Icons.Filled.Person, "개인", !showShared) { showShared = false }
                    FilterIconBtn(Icons.Filled.Groups, "공유받음", showShared) { showShared = true }
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
            }
            Spacer(Modifier.height(12.dp))
            val shown = books.filter { it.shared == showShared }
            if (shown.isEmpty()) {
                Text(if (showShared) "아직 공유받은 스케치북이 없어요." else "아직 스케치북이 없어요. 홈 화면에서 만들어보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(shown, key = { _, b -> b.id }) { i, b ->
                        CoverCard(b, { onOpen(b) }, { pendingDelete = b }, { onToggleFav(b) })
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

/** 개인/공유받음 필터 토글 — 선택된 쪽은 원형 배경(nav 선택 표시와 같은 톤)으로 강조. */
@Composable
private fun FilterIconBtn(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .bounceClick(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoverCard(book: Sketchbook, onOpen: () -> Unit, onDelete: () -> Unit, onToggleFav: () -> Unit) {
    // Same cover ratio as the home carousel (Dimens.Home.coverRatio) — every note cover keeps one
    // fixed proportion across the whole app, whichever screen shows it.
    Box(Modifier.aspectRatio(Dimens.Home.coverRatio)) {
        // 목록 표지도 홈과 같은 공용 컴포넌트를 사용해 기본색과 책등 위치를 일치시킵니다.
        SketchbookCover(
            modifier = Modifier.fillMaxSize()
                .shadow(12.dp, SketchbookCoverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .bounceClick(onClick = onOpen),
        ) {
            // Scrim so the cream text stays readable regardless of the cover's own colour (some covers,
            // e.g. the light mauve one, put light text under ~2:1 contrast without this).
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
                Text(book.name, color = Color(0xFFF3ECD9), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = if (book.shared && book.code != null) "🤝 ${book.code} · ${book.pageCount}쪽" else "${book.pageCount}쪽"
                Text(meta, color = Color(0xFFF3ECD9).copy(alpha = 0.8f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
        }
        IconButton(onClick = onToggleFav, modifier = Modifier.align(Alignment.TopStart).padding(2.dp).size(30.dp)
            .clip(CircleShape).background(Color(0x33000000))) {
            Icon(Icons.Filled.Star, "즐겨찾기",
                tint = if (book.fav) Color(0xFFFFD43B) else Color(0xFFF3ECD9),
                modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(30.dp)
            .clip(CircleShape).background(Color(0x33000000))) {
            Icon(Icons.Filled.Delete, "삭제", tint = Color(0xFFF3ECD9), modifier = Modifier.size(16.dp))
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
    // Page-turn visuals: turnSnapshot is the outgoing page's exact on-screen capture; turnProgress
    // (-1..1) drives PageTurnOverlay for BOTH the discrete (chevron/thumbnail/swipe-turn) and the
    // interactive (페이지 넘기기 모드 single-finger drag) turn — snapTo for live drag-following,
    // animateTo for the auto/settle animations.
    var turnSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    val turnProgress = remember { Animatable(0f) }
    var dragBaseSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    // 페이지 넘기기 모드: on, single-finger swipe turns pages and drawing/zoom/pan are fully disabled
    // (see BrushView.pageTurnMode) — turning pages while zoomed used to shift the pinch/pan state
    // unpredictably, so page-turning and normal canvas interaction are now made mutually exclusive.
    var pageTurnMode by remember { mutableStateOf(false) }
    val cw = book.size.pxW(); val ch = book.size.pxH()

    // Save the current page SYNCHRONOUSLY (strokes only, no paper) before any page load, so a page
    // switch can't read the file before an async write finishes (that race dropped recent strokes).
    fun saveCurrent() { val v = view ?: return; val pg = page; val b = v.exportContent() ?: return; repo.savePage(book.id, pg, b) }
    fun goTo(p: Int) {
        if (p == page || p !in 0 until pageCount) return
        val dir = if (p > page) 1f else -1f
        // Snapshot exactly what's on screen right now (current zoom/pan/rotation included) so the
        // outgoing page-turn animation always matches what the user was actually looking at.
        val snap = view?.captureScreenBitmap()
        saveCurrent(); page = p; view?.loadContent(repo.loadPage(book.id, p))
        if (snap != null) {
            turnSnapshot = snap
            scope.launch { turnProgress.playPageTurn(dir); turnSnapshot = null }
        }
    }
    // Interactive page-turn-mode drag: follows the finger live, then either finishes the turn
    // (commit) or springs back to rest (cancel) on release.
    fun onPageDragProgress(p: Float) {
        if (dragBaseSnapshot == null) { dragBaseSnapshot = view?.captureScreenBitmap(); turnSnapshot = dragBaseSnapshot }
        scope.launch { turnProgress.snapTo(p) }
    }
    fun onPageDragEnd(commit: Int) {
        dragBaseSnapshot = null
        val target = page + commit
        if (commit == 0 || target !in 0 until pageCount) {
            scope.launch { turnProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing)); turnSnapshot = null }
        } else {
            scope.launch {
                turnProgress.animateTo(commit.toFloat(), tween(160, easing = FastOutSlowInEasing))
                saveCurrent(); page = target; view?.loadContent(repo.loadPage(book.id, target))
                turnSnapshot = null; turnProgress.snapTo(0f)
            }
        }
    }

    com.g1.sketchbook.ui.ImmersiveModeEffect(hidden = fullscreen)
    BackHandler {
        when {
            pageTurnMode -> { pageTurnMode = false; fullscreen = false }
            fullscreen -> fullscreen = false
            else -> { saveCurrent(); onBack() }
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .let { if (fullscreen) it else it.systemBarsPadding() },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(if (fullscreen) 0.dp else Dimens.Canvas.outerPadding)) {
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
                    v.erasing = erasing; v.locked = locked; v.pageTurnMode = pageTurnMode
                    v.twoFingerTapAction = session.twoFingerTapAction
                    v.threeFingerTapAction = session.threeFingerTapAction
                    v.longPressAction = session.longPressAction
                    v.eyedropArmed = eyedropArmed
                    v.onEyedropPreview = { c, x, y -> eyedropPreview = Triple(c, x, y) }
                    v.onEyedrop = { c -> color = (c.toLong() and 0xFFFFFFFFL); erasing = false; eyedropArmed = false; eyedropPreview = null }
                    v.onEyedropCancel = { eyedropArmed = false; eyedropPreview = null }
                    v.onPageDragProgress = { p -> onPageDragProgress(p) }
                    v.onPageDragEnd = { commit -> onPageDragEnd(commit) }
                    v.onStrokeEnd = { val pg = page; v.exportContent()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, pg, b) } } }
                },
            )
            eyedropPreview?.let { (c, x, y) -> com.g1.sketchbook.brush.EyedropFloatingPreview(c, x, y) }
            PageTurnOverlay(turnSnapshot, turnProgress.value)
            // 페이지 넘기기 모드의 유일한 탈출구 — 툴바가 숨겨져 있는 동안 이 버튼이 대신 그 역할을 함.
            if (pageTurnMode) {
                com.g1.sketchbook.brush.PageTurnConfirmButton(
                    onConfirm = { pageTurnMode = false; fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 20.dp),
                )
            }
        }
        if (!pageTurnMode) {
            BrushControls(
                brush, color, sizeDp, opacity, erasing,
                onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
                onSize = { if (erasing) eraserSize = it else sizeByBrush[brush] = it },
                onOpacity = { if (!erasing) opacityByBrush[brush] = it }, onToggleErase = { erasing = !erasing },
                onUndo = { view?.undo() }, onRedo = { view?.redo() }, onClear = { view?.clearCanvas(); saveCurrent() },
                onRotate = { view?.rotate() },
                onOpenPages = { pagesOpen = true },
                favorites = favorites,
                onEditFavorite = { i, c -> val nf = favorites.toMutableList(); nf[i] = c; favorites = nf; session.favoriteColors = nf },
                eyedropArmed = eyedropArmed, onToggleEyedrop = { eyedropArmed = !eyedropArmed },
                fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
                locked = locked, onToggleLock = { locked = !locked },
                pageTurnMode = pageTurnMode, onTogglePageTurnMode = { pageTurnMode = true; fullscreen = true },
            )
        }
    }
    if (pagesOpen) {
        PagePanel(repo, book.id, page, pageCount, onSelect = { p -> goTo(p); pagesOpen = false }, onDismiss = { pagesOpen = false })
    }
}

