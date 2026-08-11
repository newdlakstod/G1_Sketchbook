package com.g1.sketchbook.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.data.model.Member
import com.g1.sketchbook.data.model.Stroke

private val Palette = listOf(
    0xFF1A1A2EL, 0xFFFF6B6BL, 0xFFFFA94DL, 0xFFFFD43BL,
    0xFF51CF66L, 0xFF4ECDC4L, 0xFF4DABF7L, 0xFF9775FAL, 0xFFFF8CC8L, 0xFFFFFFFFL,
)
private val Widths = listOf(4f, 8f, 16f, 32f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    roomId: String,
    members: List<Member>,
    onOpenGallery: () -> Unit,
    onLeaveRoom: () -> Unit,
    vm: CanvasViewModel = viewModel(),
) {
    LaunchedEffect(roomId) { vm.bind(roomId) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("코드 $roomId", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(vm.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLeaveRoom) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "방 나가기")
                    }
                },
                actions = {
                    MembersRow(members)
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "갤러리")
                    }
                },
            )
        },
        bottomBar = {
            ToolBar(
                selectedColor = vm.color,
                erasing = vm.erasing,
                selectedWidth = vm.strokeWidthPx,
                saving = vm.saving,
                onColor = vm::chooseColor,
                onWidth = vm::chooseWidth,
                onErase = vm::toggleErase,
                onUndo = vm::undo,
                onClear = vm::clearAll,
                onSave = {
                    val w = canvasSize.width
                    val h = canvasSize.height
                    if (w > 0 && h > 0) {
                        val bmp = renderStrokesToBitmap(
                            strokes = vm.strokes.map { it.stroke },
                            width = 1080,
                            height = (1080f * h / w).toInt().coerceAtLeast(1),
                        )
                        vm.saveToGallery(bmp)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFFDF7F4))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(roomId) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                vm.onDragStart(pos, Offset(size.width.toFloat(), size.height.toFloat()))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                vm.onDrag(change.position, Offset(size.width.toFloat(), size.height.toFloat()))
                            },
                            onDragEnd = {
                                vm.onDragEnd(Offset(size.width.toFloat(), size.height.toFloat()))
                            },
                        )
                    }
            ) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val w = size.width
                val h = size.height

                vm.strokes.forEach { drawStoredStroke(it.stroke, w, h) }
                vm.liveOthers.values.forEach { drawStoredStroke(it, w, h) }

                // Local in-progress stroke (screen-space width).
                if (vm.currentPoints.isNotEmpty()) {
                    drawFlatPoints(
                        points = vm.currentPoints,
                        color = if (vm.erasing) Color(CanvasViewModel.ERASE_COLOR) else Color(vm.color),
                        strokePx = vm.strokeWidthPx,
                        w = w, h = h,
                    )
                }
            }
        }
    }
}

@Composable
private fun MembersRow(members: List<Member>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items(members) { m ->
            Box(
                Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = m.name.take(1).ifBlank { "?" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ToolBar(
    selectedColor: Long,
    erasing: Boolean,
    selectedWidth: Float,
    saving: Boolean,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onErase: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Palette) { c ->
                    val selected = !erasing && c == selectedColor
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(Color(c), CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                shape = CircleShape,
                            )
                            .clickable { onColor(c) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Widths.forEach { wpx ->
                    val selected = wpx == selectedWidth
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickable { onWidth(wpx) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((wpx / 2 + 6).dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    CircleShape,
                                )
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onErase) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "지우개",
                        tint = if (erasing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "실행취소")
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = "전체 지우기", tint = Color(0xFFE85555))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = "갤러리에 저장", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// --- drawing helpers ---

private fun DrawScope.drawStoredStroke(stroke: Stroke, w: Float, h: Float) {
    drawFlatPoints(
        points = stroke.points,
        color = Color(stroke.color),
        strokePx = (stroke.width * w),
        w = w, h = h,
    )
}

private fun DrawScope.drawFlatPoints(points: List<Float>, color: Color, strokePx: Float, w: Float, h: Float) {
    if (points.size < 2) return
    val sw = strokePx.coerceAtLeast(1f)
    if (points.size == 2) {
        drawCircle(color, sw / 2f, Offset(points[0] * w, points[1] * h))
        return
    }
    val path = Path().apply {
        moveTo(points[0] * w, points[1] * h)
        var i = 2
        while (i + 1 < points.size) {
            lineTo(points[i] * w, points[i + 1] * h)
            i += 2
        }
    }
    drawPath(path, color, style = DrawStroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
