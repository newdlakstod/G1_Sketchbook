package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

/** 벡터 스케치북 전용 캔버스 화면 — 책 한 권 = 캔버스 한 장(페이지 없음). 도구는 펜/지우개/
 *  내보내기용 라쏘 셋. 두 손가락 핀치로 확대·이동. */
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
    var canUndo by remember { mutableStateOf(false) }
    val favorites = session.favoriteColors

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
                    it.infinite = book.vectorInfinite
                    it.canvasW = (book.vectorCanvasW ?: 1024).toFloat()
                    it.canvasH = (book.vectorCanvasH ?: 1024).toFloat()
                    it.loadPage(repo.loadVectorCanvas(bookId) ?: VectorPage(emptyList()))
                    it.onStrokeEnd = { saveCurrent(); canUndo = it.canUndo }
                    view = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            favorites.take(5).forEach { swatch ->
                val selected = swatch == color && tool == VectorBrushView.Tool.DRAW
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick { color = swatch; view?.color = swatch; tool = VectorBrushView.Tool.DRAW; view?.tool = tool },
                )
            }
            IconButton(enabled = canUndo, onClick = { view?.undo(); canUndo = view?.canUndo ?: false }) {
                Icon(Icons.Filled.Undo, "되돌리기")
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.ERASE) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.ERASE
                view?.tool = tool
            }) {
                Image(
                    painterResource(R.drawable.brush_eraser), "지우개(획 삭제)",
                    colorFilter = ColorFilter.tint(if (tool == VectorBrushView.Tool.ERASE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                )
            }
            IconButton(onClick = {
                tool = if (tool == VectorBrushView.Tool.LASSO_EXPORT) VectorBrushView.Tool.DRAW else VectorBrushView.Tool.LASSO_EXPORT
                view?.tool = tool
                view?.onLassoComplete = { selected, lasso ->
                    pointsBounds(lasso)?.let { exportRegion(VectorPage(selected), it) }
                    tool = VectorBrushView.Tool.DRAW; view?.tool = tool
                }
            }) {
                Icon(com.g1.sketchbook.brush.IconLassoLine, "라쏘로 영역 선택해 내보내기",
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
