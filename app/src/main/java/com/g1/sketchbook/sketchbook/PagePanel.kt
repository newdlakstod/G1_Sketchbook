package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Page turning + a grid of page thumbnails (checkerboard layout — 3 columns for the fixed 15 pages)
 * to jump straight to any page — everything page-related lives behind the toolbar's one "페이지"
 * button instead of a cluster of separate icons. Each cell shows just its page number below the
 * thumbnail; long-pressing a cell picks it up so it can be dragged onto another cell to swap page
 * order. Rendered as a small, centred popup card (not a near-fullscreen sheet) so it stays out of
 * the way of the canvas behind it; the grid itself scrolls internally if it would otherwise grow
 * taller than the card.
 */
@Composable
fun PagePanel(
    repo: SketchbookRepository,
    bookId: String,
    currentPage: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    /** Fires once a drag-reorder finishes: [newOrder]\[position\] = which OLD page index's drawing
     *  now belongs at that position. Caller persists it (`SketchbookRepository.applyPageOrder`) and
     *  remaps whichever page it currently has open, since that page's index may have moved. */
    onReorder: (newOrder: List<Int>) -> Unit,
    onReadMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            // 배경(스크림)을 탭해도 안 닫히게 — 드래그로 순서 바꾸다가 살짝 벗어나면 다이얼로그가
            // 통째로 닫혀버리던 오류의 원인이라, 아래 취소/완료 버튼으로만 나가도록 바꿨다.
            Modifier.fillMaxSize().background(Color(0x55000000)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp, tonalElevation = 3.dp,
                modifier = Modifier.width(292.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    // 페이지 번호를 직접 입력해 바로 그 위치로 이동할 수 있는 필드 — 1..pageCount 밖의
                    // 값이나 숫자가 아닌 입력은 무시하고 현재 페이지 번호로 되돌린다. 전체 쪽수는
                    // 아래 그리드에서 바로 보이므로 여기선 현재 번호 하나만 보여준다.
                    var pageInput by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }
                    fun commitPageInput() {
                        val p = pageInput.toIntOrNull()
                        if (p != null && p in 1..pageCount) onSelect(p - 1) else pageInput = (currentPage + 1).toString()
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("페이지", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (currentPage > 0) onSelect(currentPage - 1) }, enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronLeft, "이전 페이지")
                        }
                        BasicTextField(
                            value = pageInput,
                            onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) pageInput = v },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitPageInput() }),
                            modifier = Modifier.width(22.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                .padding(vertical = 3.dp),
                        )
                        IconButton(onClick = { if (currentPage < pageCount - 1) onSelect(currentPage + 1) }, enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronRight, "다음 페이지")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 표시 순서: order[위치] = 그 자리에 보여줄 "원래" 페이지 인덱스. 드래그 중엔 이
                    // 리스트만 로컬로 바뀌고(파일은 그대로), 손을 떼는 순간 실제 파일을 재배치한 뒤
                    // identity로 되돌린다 — 그 다음부턴 위치가 곧 인덱스이므로.
                    var order by remember(bookId, pageCount) { mutableStateOf((0 until pageCount).toList()) }
                    var draggingPos by remember { mutableStateOf(-1) }
                    var dragOffset by remember { mutableStateOf(Offset.Zero) }
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        itemsIndexed(order, key = { _, pageIndex -> pageIndex }) { pos, pageIndex ->
                            PageGridItem(
                                repo = repo, bookId = bookId, index = pageIndex, displayNumber = pos + 1,
                                selected = pageIndex == currentPage,
                                dragging = pos == draggingPos,
                                dragOffset = if (pos == draggingPos) dragOffset else Offset.Zero,
                                onClick = { onSelect(pageIndex) },
                                onDragStart = { draggingPos = pos; dragOffset = Offset.Zero },
                                onDrag = { delta ->
                                    dragOffset += delta
                                    val from = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingPos } ?: return@PageGridItem
                                    val point = Offset(
                                        from.offset.x + from.size.width / 2f + dragOffset.x,
                                        from.offset.y + from.size.height / 2f + dragOffset.y,
                                    )
                                    val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        point.x >= info.offset.x && point.x <= info.offset.x + info.size.width &&
                                            point.y >= info.offset.y && point.y <= info.offset.y + info.size.height
                                    }
                                    if (target != null && target.index != draggingPos) {
                                        order = order.toMutableList().also { it.add(target.index, it.removeAt(draggingPos)) }
                                        draggingPos = target.index
                                        dragOffset = Offset.Zero
                                    }
                                },
                                onDragEnd = {
                                    draggingPos = -1
                                    val identity = (0 until pageCount).toList()
                                    if (order != identity) { onReorder(order); order = identity }
                                    dragOffset = Offset.Zero
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        onClick = onReadMode, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoStories, "읽기모드", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("읽기모드", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onDismiss) { Text("취소") }
                        TextButton(onClick = onDismiss) { Text("완료", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageGridItem(
    repo: SketchbookRepository, bookId: String, index: Int, displayNumber: Int,
    selected: Boolean, dragging: Boolean, dragOffset: Offset,
    onClick: () -> Unit, onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
) {
    var thumb by remember(bookId, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bookId, index) { thumb = withContext(Dispatchers.IO) { repo.loadPageThumb(bookId, index) } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .let { if (dragging) it.zIndex(1f) else it }
            // 짧게 떼면 탭(페이지로 이동), 길게 누른 채 있으면 드래그(순서 바꾸기) — 직접 손가락을
            // 추적해서 두 제스처를 한 영역에서 구분한다(BrushView의 롱프레스 판정과 같은 원리).
            .pointerInput(bookId, index) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var released = false
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { released = true; break }
                        }
                    }
                    if (released) {
                        onClick()
                    } else {
                        onDragStart()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { onDragEnd(); break }
                            onDrag(change.positionChange())
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.74f)
                .let { if (dragging) it.shadow(8.dp, RoundedCornerShape(8.dp)) else it }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(if (selected) 2.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$displayNumber", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
