package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outgoing-page snapshot for the page-turn transition, plus which way it should slide off
 *  (1 = turning forward/next, -1 = turning backward/prev). */
data class PageTurn(val bitmap: Bitmap, val dir: Float)

/** Slides the outgoing page's exact on-screen snapshot off in the swipe direction while fading it,
 *  revealing the already-updated new page underneath — a light "page turn" feel without needing a
 *  literal paper-curl render. Matches whatever zoom/pan/rotation was on screen since the snapshot is
 *  captured live from the view, not re-rendered at a fixed scale. */
@Composable
fun PageTurnOverlay(turn: PageTurn?, onFinished: () -> Unit) {
    if (turn == null) return
    val anim = remember(turn) { Animatable(0f) }
    LaunchedEffect(turn) {
        anim.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        onFinished()
    }
    Image(
        turn.bitmap.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            translationX = turn.dir * anim.value * size.width
            alpha = 1f - anim.value * 0.25f
        },
    )
}

/**
 * Page turning + a scrollable vertical list of page thumbnails to jump straight to any page —
 * everything page-related lives behind the toolbar's one "페이지" button instead of a cluster of
 * separate icons.
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
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {})
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("페이지", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (currentPage > 0) onSelect(currentPage - 1) }, enabled = currentPage > 0) {
                        Icon(Icons.Filled.ChevronLeft, "이전 페이지")
                    }
                    Text("${currentPage + 1}/$pageCount", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { if (currentPage < pageCount - 1) onSelect(currentPage + 1) }, enabled = currentPage < pageCount - 1) {
                        Icon(Icons.Filled.ChevronRight, "다음 페이지")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pageCount) { i ->
                        PageRow(repo, bookId, i, i == currentPage) { onSelect(i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageRow(repo: SketchbookRepository, bookId: String, index: Int, selected: Boolean, onClick: () -> Unit) {
    var thumb by remember(bookId, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bookId, index) { thumb = withContext(Dispatchers.IO) { repo.loadPageThumb(bookId, index) } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 70.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Text("${index + 1}쪽", fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, "현재 페이지", tint = MaterialTheme.colorScheme.primary)
    }
}
