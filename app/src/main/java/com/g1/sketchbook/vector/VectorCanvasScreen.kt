package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/** 벡터 스케치북 전용 캔버스 화면 — 책 한 권 = 캔버스 한 장(페이지 없음). 도구는 펜/지우개/
 *  라쏘 셋. 두 손가락 핀치로 확대·이동 — 단, 라쏘로 선택한 영역이 있으면 그 영역 안을 눌러
 *  드래그·핀치하는 건 캔버스가 아니라 선택 자체를 이동·크기조절한다. 선택은 저장(내보내기)
 *  버튼을 눌러야 SVG로 저장된다(라쏘를 놓는 즉시 저장되지 않음). */
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
    var brushProfile by remember { mutableStateOf(BrushProfiles[0]) }
    var brushSwatchPanelOpen by remember { mutableStateOf(false) }
    var strokeWidthDp by remember { mutableStateOf(8f) }
    var cap by remember { mutableStateOf(VectorCap.ROUND) }
    var fillEnabled by remember { mutableStateOf(true) }
    var strokeEnabled by remember { mutableStateOf(false) }
    var strokeColor by remember { mutableStateOf(0xFF000000L) }
    var strokeWidthPx by remember { mutableStateOf(2f) }
    var scaleStrokeWidth by remember { mutableStateOf(true) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
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
                    Canvas(Modifier.size(24.dp)) { drawBrushSwatchPreview(Color(color)) }
                }
                DropdownMenu(expanded = brushSwatchPanelOpen, onDismissRequest = { brushSwatchPanelOpen = false }) {
                    BrushProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.label) },
                            leadingIcon = { Canvas(Modifier.size(32.dp)) { drawBrushSwatchPreview(Color(color)) } },
                            trailingIcon = if (profile == brushProfile) { { Icon(Icons.Filled.Check, null) } } else null,
                            onClick = { brushProfile = profile; brushSwatchPanelOpen = false },
                        )
                    }
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
                    val capOptions = listOf(VectorCap.ROUND to "둥글게", VectorCap.SQUARE to "사각형", VectorCap.BUTT to "딱 떨어지게")
                    capOptions.forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = if (cap == option) { { Icon(Icons.Filled.Check, null) } } else null,
                            onClick = { cap = option; view?.cap = option; settingsMenuOpen = false },
                        )
                    }
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
                    DropdownMenuItem(
                        text = { Text("테두리") },
                        trailingIcon = {
                            Switch(checked = strokeEnabled, onCheckedChange = { checked ->
                                strokeEnabled = checked
                                view?.strokeColor = if (checked) strokeColor else null
                            })
                        },
                        onClick = {
                            strokeEnabled = !strokeEnabled
                            view?.strokeColor = if (strokeEnabled) strokeColor else null
                        },
                    )
                    if (strokeEnabled) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            favorites.forEach { swatch ->
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(Color(swatch))
                                        .border(if (swatch == strokeColor) 2.dp else 1.dp,
                                            if (swatch == strokeColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                        .bounceClick { strokeColor = swatch; view?.strokeColor = swatch },
                                )
                            }
                        }
                        Text("테두리 굵기", modifier = Modifier.padding(horizontal = 16.dp))
                        Slider(
                            value = strokeWidthPx, onValueChange = { strokeWidthPx = it; view?.strokeWidthPx = it },
                            valueRange = 1f..20f, modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
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
        IconButton(onClick = { saveCurrent(); onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
