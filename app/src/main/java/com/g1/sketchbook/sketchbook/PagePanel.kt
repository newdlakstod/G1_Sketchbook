package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Page turning + a grid of page thumbnails (checkerboard layout — 3 columns for the fixed 15 pages)
 * to jump straight to any page — everything page-related lives behind the toolbar's one "페이지"
 * button instead of a cluster of separate icons. Each cell shows just its page number below the
 * thumbnail. Rendered as a small, centred popup card (not a near-fullscreen sheet) so it stays out
 * of the way of the canvas behind it; the grid itself scrolls internally if it would otherwise grow
 * taller than the card.
 */
@Composable
fun PagePanel(
    repo: SketchbookRepository,
    bookId: String,
    currentPage: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0x55000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp, tonalElevation = 3.dp,
                modifier = Modifier.width(292.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    // 페이지 번호를 직접 입력해 바로 그 위치로 이동할 수 있는 필드 — 1..pageCount 밖의
                    // 값이나 숫자가 아닌 입력은 무시하고 현재 페이지 번호로 되돌린다.
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
                        Text("/$pageCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { if (currentPage < pageCount - 1) onSelect(currentPage + 1) }, enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronRight, "다음 페이지")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        items(pageCount) { i ->
                            PageGridItem(repo, bookId, i, i == currentPage) { onSelect(i) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageGridItem(repo: SketchbookRepository, bookId: String, index: Int, selected: Boolean, onClick: () -> Unit) {
    var thumb by remember(bookId, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bookId, index) { thumb = withContext(Dispatchers.IO) { repo.loadPageThumb(bookId, index) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.74f).clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(if (selected) 2.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${index + 1}", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
