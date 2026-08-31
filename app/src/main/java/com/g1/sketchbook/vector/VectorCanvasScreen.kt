package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.g1.sketchbook.R
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.saveVectorCanvasSynced
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.saveSvgToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 브러시 종류(일러스트레이터의 "브러시" 하나에 해당) — 지금은 [BrushProfiles]에 [BASIC] 하나뿐이라
 *  골라도 아무것도 안 바뀌지만, 스와치 패널 UI 자체는 미리 만들어 둔다 — 나중에 캘리그래픽/텍스처 등
 *  실제로 다르게 그려지는 종류를 추가할 때 이 목록에 항목만 늘리면 된다. */
data class BrushProfile(val id: String, val label: String)
private val BrushProfiles = listOf(BrushProfile("basic", "기본"))

/** 브러시 스와치 미리보기 — 실제 [strokeOutline] 지오메트리 대신 눈에 익은 구불구불한 선 하나를
 *  둥근 끝으로 그려서 "이건 펜 브러시다"만 알아볼 수 있게 하는 장식용 그림이다(일러스트레이터
 *  브러시 패널의 스와치처럼). */
private fun DrawScope.drawBrushSwatchPreview(color: Color) {
    val w = size.width; val h = size.height
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.75f)
        cubicTo(w * 0.1f, h * 0.25f, w * 0.6f, h * 0.15f, w * 0.85f, h * 0.5f)
        cubicTo(w * 1.0f, h * 0.72f, w * 0.62f, h * 0.92f, w * 0.32f, h * 0.68f)
    }
    drawPath(path, color = color, style = Stroke(width = size.minDimension * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 임포트된 스탬프 브러시의 스와치 미리보기 — [drawBrushSwatchPreview]("기본"용 장식 곡선)와 달리
 *  실제로 파싱해서 정규화해 둔 모양([StampBrushProfile.shapes], 중심 원점·가장 긴 변 길이 1)을
 *  스와치 박스 크기에 맞춰 그대로 그린다. */
private fun DrawScope.drawStampShapePreview(shapes: List<List<Point>>, color: Color) {
    val cx = size.width / 2f; val cy = size.height / 2f
    val scale = size.minDimension * 0.85f
    for (shape in shapes) {
        if (shape.isEmpty()) continue
        val path = Path()
        shape.forEachIndexed { i, p ->
            val x = cx + p.x * scale; val y = cy + p.y * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color)
    }
}

private fun VectorCap.toComposeCap(): StrokeCap = when (this) {
    VectorCap.ROUND -> StrokeCap.Round
    VectorCap.SQUARE -> StrokeCap.Square
    VectorCap.BUTT -> StrokeCap.Butt
}

/** 획(테두리) 다이얼로그를 여는 툴바 버튼의 미리보기 — 지금 골라둔 테두리 색·단면을 그대로 짧은
 *  선 하나로 보여준다(테두리가 꺼져 있으면 회색으로). */
private fun DrawScope.drawStrokePreview(strokeEnabled: Boolean, strokeColor: Long, cap: VectorCap) {
    val previewColor = if (strokeEnabled) Color(strokeColor) else Color(0xFF9E9E9E)
    drawLine(
        previewColor,
        Offset(size.width * 0.2f, size.height / 2f), Offset(size.width * 0.8f, size.height / 2f),
        strokeWidth = size.height * 0.45f, cap = cap.toComposeCap(),
    )
}

/** 단면(cap) 고르는 버튼 하나 — 실제 [StrokeCap]을 그대로 적용한 짧은 선을 그려서, 아이콘이 아니라
 *  "이 마감을 고르면 실제로 이렇게 보인다"를 그대로 보여준다. */
@Composable
private fun CapButton(cap: VectorCap, selected: Boolean, onClick: () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp, 14.dp)) {
            drawLine(
                lineColor,
                Offset(size.width * 0.25f, size.height / 2f), Offset(size.width * 0.75f, size.height / 2f),
                strokeWidth = size.height * 0.6f, cap = cap.toComposeCap(),
            )
        }
    }
}

/** 일러스트레이터 "획" 패널을 본떠서 획(테두리) 관련 설정만 따로 모은 다이얼로그 — 단면(cap)은
 *  테두리를 꺼도 펜 몸체 모양 자체에 계속 영향을 주므로 켜짐 여부와 무관하게 항상 보이고, 두께·
 *  색상은 테두리가 켜져 있을 때만 의미가 있어 그때만 보인다. 모퉁이(join)·선 정렬은 아직 실제로
 *  구현되지 않아 이번엔 넣지 않았다. */
@Composable
private fun StrokeDialog(
    cap: VectorCap, onCap: (VectorCap) -> Unit,
    strokeEnabled: Boolean, onStrokeEnabled: (Boolean) -> Unit,
    strokeWidthPx: Float, onStrokeWidthPx: (Float) -> Unit,
    strokeColor: Long, onStrokeColor: (Long) -> Unit,
    favorites: List<Long>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("획") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("테두리")
                    Switch(checked = strokeEnabled, onCheckedChange = onStrokeEnabled)
                }
                Spacer(Modifier.height(12.dp))
                Text("단면")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(VectorCap.BUTT, VectorCap.ROUND, VectorCap.SQUARE).forEach { option ->
                        CapButton(option, selected = cap == option, onClick = { onCap(option) })
                    }
                }
                if (strokeEnabled) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("두께", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onStrokeWidthPx((strokeWidthPx - 1f).coerceAtLeast(1f)) }) {
                            Icon(Icons.Filled.Remove, "굵기 줄이기")
                        }
                        Text("${strokeWidthPx.toInt()} pt")
                        IconButton(onClick = { onStrokeWidthPx((strokeWidthPx + 1f).coerceAtMost(20f)) }) {
                            Icon(Icons.Filled.Add, "굵기 늘리기")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("색상")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        favorites.forEach { swatch ->
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                                    .border(if (swatch == strokeColor) 2.dp else 1.dp,
                                        if (swatch == strokeColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                    .bounceClick { onStrokeColor(swatch) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 벡터 스케치북 전용 캔버스 화면 — 책 한 권 = 캔버스 한 장(페이지 없음). 도구는 펜/지우개/
 *  라쏘 셋. 두 손가락 핀치로 확대·이동 — 단, 라쏘로 선택한 영역이 있으면 그 영역 안을 눌러
 *  드래그·핀치하는 건 캔버스가 아니라 선택 자체를 이동·크기조절한다. 선택은 저장(내보내기)
 *  버튼을 눌러야 SVG로 저장된다(라쏘를 놓는 즉시 저장되지 않음). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var view by remember { mutableStateOf<VectorBrushView?>(null) }
    var color by remember { mutableStateOf(session.brushColor) }
    var tool by remember { mutableStateOf(VectorBrushView.Tool.DRAW) }
    val stampRepo = remember { StampBrushRepository(context) }
    var stampBrushes by remember { mutableStateOf(stampRepo.list()) }
    var selectedStampBrushId by remember { mutableStateOf<String?>(null) }
    var editingBrush by remember { mutableStateOf<StampBrushProfile?>(null) }
    var pendingImportSvgText by remember { mutableStateOf<String?>(null) }
    var importNameDraft by remember { mutableStateOf("") }
    var importNameDialogOpen by remember { mutableStateOf(false) }
    var brushSwatchPanelOpen by remember { mutableStateOf(false) }
    var strokeWidthDp by remember { mutableStateOf(8f) }
    var cap by remember { mutableStateOf(VectorCap.ROUND) }
    var fillEnabled by remember { mutableStateOf(true) }
    var strokeEnabled by remember { mutableStateOf(false) }
    var strokeColor by remember { mutableStateOf(0xFF000000L) }
    var strokeWidthPx by remember { mutableStateOf(2f) }
    var scaleStrokeWidth by remember { mutableStateOf(true) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var strokeDialogOpen by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var selectionAnchor by remember { mutableStateOf<Offset?>(null) }
    val favorites = session.quickFavorites

    fun saveCurrent() {
        val v = view ?: return
        saveVectorCanvasSynced(scope, repo, backup, myUid, bookId, v.currentPage())
    }

    fun exportRegion(page: VectorPage, region: Bounds) {
        scope.launch(Dispatchers.IO) {
            val svg = vectorPageToSvg(page, region)
            val status = saveSvgToGallery(context, svg, book.name)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null || !text.contains("<svg")) {
            Toast.makeText(context, "SVG 파일을 읽을 수 없습니다", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        pendingImportSvgText = text
        importNameDraft = "브러시 ${stampBrushes.size + 1}"
        importNameDialogOpen = true
    }

    fun pushStampBrushAsync(profile: StampBrushProfile) {
        if (myUid.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val svg = stampRepo.originalSvgText(profile.id) ?: return@launch
            backup.pushStampBrush(
                myUid,
                com.g1.sketchbook.backup.RemoteStampBrush(profile.id, profile.name, svg, profile.spacingPx, profile.sizePx, System.currentTimeMillis(), false),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VectorBrushView(ctx).also {
                    it.color = color
                    it.strokeWidthDp = strokeWidthDp
                    it.cap = cap
                    it.fillEnabled = fillEnabled
                    it.strokeColor = if (strokeEnabled) strokeColor else null
                    it.strokeWidthPx = strokeWidthPx
                    it.scaleStrokeWidth = scaleStrokeWidth
                    it.brushProfileId = selectedStampBrushId
                    it.stampBrushes = stampBrushes.associateBy { b -> b.id }
                    it.infinite = book.vectorInfinite
                    it.canvasW = (book.vectorCanvasW ?: 1024).toFloat()
                    it.canvasH = (book.vectorCanvasH ?: 1024).toFloat()
                    it.loadPage(repo.loadVectorCanvas(bookId) ?: VectorPage(emptyList()))
                    it.onStrokeEnd = { saveCurrent(); canUndo = it.canUndo }
                    it.onLassoSelectionChanged = { active, x, y -> selectionAnchor = if (active) Offset(x, y) else null }
                    view = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        selectionAnchor?.let { a ->
            com.g1.sketchbook.brush.LassoSaveButton(a.x, a.y, onSave = {
                val v = view ?: return@LassoSaveButton
                val page = v.exportSelection() ?: return@LassoSaveButton
                contentBounds(page.strokes)?.let { exportRegion(page, it) }
            })
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            favorites.forEach { swatch ->
                val selected = swatch == color && tool == VectorBrushView.Tool.DRAW
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick { color = swatch; view?.color = swatch; tool = VectorBrushView.Tool.DRAW; view?.tool = tool },
                )
            }
            Box {
                IconButton(onClick = { brushSwatchPanelOpen = true }) {
                    Canvas(Modifier.size(24.dp)) {
                        val brush = stampBrushes.firstOrNull { it.id == selectedStampBrushId }
                        if (brush != null) drawStampShapePreview(brush.shapes, Color(color)) else drawBrushSwatchPreview(Color(color))
                    }
                }
                DropdownMenu(expanded = brushSwatchPanelOpen, onDismissRequest = { brushSwatchPanelOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(BrushProfiles[0].label) },
                        leadingIcon = { Canvas(Modifier.size(32.dp)) { drawBrushSwatchPreview(Color(color)) } },
                        trailingIcon = if (selectedStampBrushId == null) { { Icon(Icons.Filled.Check, null) } } else null,
                        onClick = { selectedStampBrushId = null; view?.brushProfileId = null; brushSwatchPanelOpen = false },
                    )
                    stampBrushes.forEach { brush ->
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { selectedStampBrushId = brush.id; view?.brushProfileId = brush.id; brushSwatchPanelOpen = false },
                                    onLongClick = { editingBrush = brush; brushSwatchPanelOpen = false },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(Modifier.size(32.dp)) { drawStampShapePreview(brush.shapes, Color(color)) }
                            Spacer(Modifier.width(12.dp))
                            Text(brush.name, modifier = Modifier.weight(1f))
                            if (brush.id == selectedStampBrushId) Icon(Icons.Filled.Check, null)
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("추가...") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { brushSwatchPanelOpen = false; importLauncher.launch("image/svg+xml") },
                    )
                }
            }
            Box {
                IconButton(onClick = { settingsMenuOpen = true }) {
                    Icon(Icons.Filled.Settings, "브러시 설정")
                }
                DropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                    Text("펜 기준 굵기", modifier = Modifier.padding(horizontal = 16.dp))
                    Slider(
                        value = strokeWidthDp, onValueChange = { strokeWidthDp = it; view?.strokeWidthDp = it },
                        valueRange = com.g1.sketchbook.ui.theme.Dimens.Brush.penMinWidth..com.g1.sketchbook.ui.theme.Dimens.Brush.penMaxWidth,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("라쏘로 크기 바꿀 때 굵기도 같이") },
                        trailingIcon = {
                            Switch(checked = scaleStrokeWidth, onCheckedChange = { scaleStrokeWidth = it; view?.scaleStrokeWidth = it })
                        },
                        onClick = { scaleStrokeWidth = !scaleStrokeWidth; view?.scaleStrokeWidth = scaleStrokeWidth },
                    )
                    DropdownMenuItem(
                        text = { Text("채우기") },
                        trailingIcon = {
                            Switch(checked = fillEnabled, onCheckedChange = { fillEnabled = it; view?.fillEnabled = it })
                        },
                        onClick = { fillEnabled = !fillEnabled; view?.fillEnabled = fillEnabled },
                    )
                }
            }
            Box {
                IconButton(onClick = { strokeDialogOpen = true }) {
                    Canvas(Modifier.size(24.dp)) { drawStrokePreview(strokeEnabled, strokeColor, cap) }
                }
                if (strokeDialogOpen) {
                    StrokeDialog(
                        cap = cap, onCap = { cap = it; view?.cap = it },
                        strokeEnabled = strokeEnabled,
                        onStrokeEnabled = { strokeEnabled = it; view?.strokeColor = if (it) strokeColor else null },
                        strokeWidthPx = strokeWidthPx, onStrokeWidthPx = { strokeWidthPx = it; view?.strokeWidthPx = it },
                        strokeColor = strokeColor, onStrokeColor = { strokeColor = it; view?.strokeColor = it },
                        favorites = favorites,
                        onDismiss = { strokeDialogOpen = false },
                    )
                }
            }
            IconButton(enabled = canUndo, onClick = { view?.undo(); canUndo = view?.canUndo ?: false }) {
                Icon(Icons.Filled.Undo, "되돌리기")
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.ERASE) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.ERASE
                view?.tool = tool
            }) {
                Image(
                    painterResource(R.drawable.brush_eraser), "지우개(획 닿으면 삭제)",
                    colorFilter = ColorFilter.tint(if (tool == VectorBrushView.Tool.ERASE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.LASSO_EXPORT) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.LASSO_EXPORT
                view?.tool = tool
            }) {
                Icon(com.g1.sketchbook.brush.IconLassoLine, "라쏘로 선택 — 이동·크기조절 또는 저장",
                    tint = if (tool == VectorBrushView.Tool.LASSO_EXPORT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                val v = view ?: return@IconButton
                val whole = if (book.vectorInfinite) {
                    contentBounds(v.currentPage().strokes)?.let {
                        val padX = it.width * 0.05f; val padY = it.height * 0.05f
                        Bounds(it.minX - padX, it.minY - padY, it.maxX + padX, it.maxY + padY)
                    } ?: Bounds(0f, 0f, 64f, 64f)
                } else {
                    Bounds(0f, 0f, book.vectorCanvasW?.toFloat() ?: 1024f, book.vectorCanvasH?.toFloat() ?: 1024f)
                }
                exportRegion(v.currentPage(), whole)
            }) {
                Icon(com.g1.sketchbook.brush.IconImageSaveLine, "전체 내보내기")
            }
        }
        if (importNameDialogOpen) {
            AlertDialog(
                onDismissRequest = { importNameDialogOpen = false; pendingImportSvgText = null },
                title = { Text("브러시 이름") },
                text = { TextField(value = importNameDraft, onValueChange = { importNameDraft = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        val svgText = pendingImportSvgText
                        importNameDialogOpen = false; pendingImportSvgText = null
                        if (svgText != null) {
                            val profile = stampRepo.importFromSvg(importNameDraft.ifBlank { "브러시" }, svgText)
                            if (profile != null) {
                                stampBrushes = stampRepo.list()
                                view?.stampBrushes = stampBrushes.associateBy { it.id }
                                selectedStampBrushId = profile.id
                                view?.brushProfileId = profile.id
                                tool = VectorBrushView.Tool.DRAW; view?.tool = tool
                                pushStampBrushAsync(profile)
                            } else {
                                Toast.makeText(context, "지원하지 않는 SVG 형식입니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("추가") }
                },
                dismissButton = { TextButton(onClick = { importNameDialogOpen = false; pendingImportSvgText = null }) { Text("취소") } },
            )
        }
        editingBrush?.let { brush ->
            var nameDraft by remember(brush.id) { mutableStateOf(brush.name) }
            var spacingDraft by remember(brush.id) { mutableStateOf(brush.spacingPx) }
            var sizeDraft by remember(brush.id) { mutableStateOf(brush.sizePx) }
            AlertDialog(
                onDismissRequest = { editingBrush = null },
                title = { Text("브러시 편집") },
                text = {
                    Column {
                        TextField(value = nameDraft, onValueChange = { nameDraft = it }, singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("간격", modifier = Modifier.weight(1f))
                            IconButton(onClick = { spacingDraft = (spacingDraft - 2f).coerceAtLeast(4f) }) { Icon(Icons.Filled.Remove, "간격 줄이기") }
                            Text("${spacingDraft.toInt()}px")
                            IconButton(onClick = { spacingDraft = (spacingDraft + 2f).coerceAtMost(200f) }) { Icon(Icons.Filled.Add, "간격 늘리기") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("크기", modifier = Modifier.weight(1f))
                            IconButton(onClick = { sizeDraft = (sizeDraft - 2f).coerceAtLeast(4f) }) { Icon(Icons.Filled.Remove, "크기 줄이기") }
                            Text("${sizeDraft.toInt()}px")
                            IconButton(onClick = { sizeDraft = (sizeDraft + 2f).coerceAtMost(200f) }) { Icon(Icons.Filled.Add, "크기 늘리기") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        stampRepo.rename(brush.id, nameDraft.ifBlank { brush.name })
                        stampRepo.updateSpacingAndSize(brush.id, spacingDraft, sizeDraft)
                        stampBrushes = stampRepo.list()
                        view?.stampBrushes = stampBrushes.associateBy { it.id }
                        editingBrush = null
                        stampBrushes.firstOrNull { it.id == brush.id }?.let { pushStampBrushAsync(it) }
                    }) { Text("저장") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            stampRepo.delete(brush.id)
                            stampBrushes = stampRepo.list()
                            view?.stampBrushes = stampBrushes.associateBy { it.id }
                            if (selectedStampBrushId == brush.id) { selectedStampBrushId = null; view?.brushProfileId = null }
                            editingBrush = null
                            if (myUid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteStampBrush(myUid, brush.id, System.currentTimeMillis()) }
                        }) { Text("삭제") }
                        TextButton(onClick = { editingBrush = null }) { Text("취소") }
                    }
                },
            )
        }
        IconButton(onClick = { saveCurrent(); onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
