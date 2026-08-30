package com.g1.sketchbook.vector

import android.widget.Toast
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.saveVectorPageSynced
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.saveSvgToGallery

/** 벡터 스케치북 편집화면 — 기존 `BrushControls`보다 훨씬 단순한 툴바(색상 스와치, 되돌리기,
 *  지우개) 하나만. 페이지 넘김 애니메이션(읽기모드)은 스펙에서 제외됐다 — 여기서 페이지 전환은
 *  그냥 이전/다음 화살표로 인덱스만 바꾼다. */
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, startPage: Int = 0, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SketchbookRepository(context) }
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<VectorBrushView?>(null) }
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, MAX_PAGES - 1)) }
    var color by remember { mutableStateOf(session.brushColor) }
    var erasing by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    val favorites = session.favoriteColors

    fun saveCurrent() {
        val v = view ?: return
        saveVectorPageSynced(scope, repo, backup, myUid, bookId, page, v.currentPage())
    }
    fun goTo(newPage: Int) {
        if (newPage == page || newPage !in 0 until MAX_PAGES) return
        saveCurrent()
        page = newPage
        view?.loadPage(repo.loadVectorPage(bookId, newPage) ?: VectorPage(emptyList()))
        canUndo = view?.canUndo ?: false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 캔버스는 논리적으로 항상 정사각(스펙: 캔버스 비율 정사각 고정)이라, 화면에 보이는 View도
        // 정사각이어야 VectorBrushView.scale()의 단일 비율이 y축에도 그대로 맞는다 — 안 그러면(예:
        // 세로가 긴 폰에서 fillMaxSize) y 논리좌표가 1024를 훌쩍 넘겨 그려지는데, 썸네일/SVG export는
        // 여전히 1024×1024만 캡처해서 그 아래로 그린 내용이 조용히 잘려나간다.
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            val squareSize = minOf(maxWidth, maxHeight)
            AndroidView(
                modifier = Modifier.size(squareSize),
                factory = { ctx ->
                    VectorBrushView(ctx).also { v ->
                        v.loadPage(repo.loadVectorPage(bookId, page) ?: VectorPage(emptyList()))
                        v.onStrokeEnd = { canUndo = v.canUndo; saveCurrent() }
                        view = v
                    }
                },
                update = { v -> v.color = color; v.erasing = erasing },
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            favorites.take(5).forEach { swatch ->
                val selected = swatch == color && !erasing
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick { color = swatch; erasing = false },
                )
            }
            IconButton(enabled = canUndo, onClick = { view?.undo(); canUndo = view?.canUndo ?: false }) {
                Icon(Icons.Filled.Undo, "되돌리기")
            }
            IconButton(onClick = { erasing = !erasing }) {
                Icon(Icons.Filled.Delete, "지우개(획 삭제)", tint = if (erasing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                val v = view ?: return@IconButton
                val svg = vectorPageToSvg(v.currentPage(), VectorBrushView.CANVAS_SIZE.toInt())
                val status = saveSvgToGallery(context, svg, "${book.name}_p${page}")
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            }) {
                Icon(com.g1.sketchbook.brush.IconImageSaveLine, "이미지로 저장")
            }
        }
        Row(Modifier.align(Alignment.TopCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(enabled = page > 0, onClick = { goTo(page - 1) }) { Icon(Icons.Filled.ChevronLeft, "이전 페이지") }
            IconButton(enabled = page < MAX_PAGES - 1, onClick = { goTo(page + 1) }) { Icon(Icons.Filled.ChevronRight, "다음 페이지") }
        }
        IconButton(onClick = { saveCurrent(); onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
