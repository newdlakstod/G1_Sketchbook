package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the outgoing page's exact on-screen snapshot as an analog, book-like BEND rather than a
 * single rigid flip: the page is split at its own horizontal MIDPOINT into two segments that fold
 * one after another around that shared crease, instead of the whole page swinging as one flat plane
 * around an outer edge — that two-stage compounding is what actually reads as "paper bending".
 *
 *  - Phase A (t: 0→0.5): the FAR segment — the half away from the fixed spine, i.e. the part your
 *    finger would actually lift first — rotates up and fades away around the midpoint crease, while
 *    the NEAR (spine-adjacent) segment stays flat.
 *  - Phase B (t: 0.5→1): the NEAR segment then folds too, around that same crease, continuing the
 *    turn until the page is fully gone.
 *
 * The FAR segment is composed as a CHILD of the NEAR segment's box so its rotation compounds on top
 * of the NEAR segment's own (which starts at zero and only grows in phase B) — exactly like a real
 * page, whose curl increases the further you get from the spine.
 *
 * Drives both the discrete (chevron/thumbnail) and interactive (drag) turns; [progress] is -1..1
 * where 0 = at rest (nothing drawn) and ±1 = fully turned. Matches whatever zoom/pan/rotation was on
 * screen since the snapshot is captured live from the view, not re-rendered at a fixed scale.
 */
@Composable
fun PageTurnOverlay(snapshot: Bitmap?, progress: Float) {
    if (snapshot == null || progress == 0f) return
    val density = LocalDensity.current.density
    val t = kotlin.math.abs(progress).coerceIn(0f, 1f)
    val dir = if (progress > 0f) 1f else -1f
    // The fixed/pivot (spine) edge — matches the old single-flip's edge convention: forward (dir>0)
    // pivots on the left, backward on the right.
    val hingeLeft = dir > 0f
    val originX = if (hingeLeft) 0f else 1f
    val rotSign = if (hingeLeft) -1f else 1f
    val camera = 12f * density
    val phaseA = (t / 0.5f).coerceIn(0f, 1f)
    val phaseB = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = maxWidth
        val halfW = fullW / 2
        // NEAR segment: anchored at the true spine edge, which never moves from the screen edge.
        Box(
            Modifier.align(if (hingeLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight().width(halfW)
                .graphicsLayer {
                    cameraDistance = camera
                    transformOrigin = TransformOrigin(originX, 0.5f)
                    rotationY = rotSign * phaseB * 70f
                    alpha = 1f - phaseB * phaseB
                },
        ) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Image(
                    snapshot.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxHeight().width(fullW).offset(x = if (hingeLeft) 0.dp else -halfW),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (phaseB * 0.35f).coerceIn(0f, 0.35f))))
            }
            // FAR segment (child of the NEAR box above — its rotation compounds on top of NEAR's).
            if (phaseA > 0f) {
                Box(
                    Modifier.fillMaxHeight().width(halfW)
                        .offset(x = if (hingeLeft) halfW else -halfW)
                        .graphicsLayer {
                            cameraDistance = camera
                            transformOrigin = TransformOrigin(originX, 0.5f)
                            rotationY = rotSign * phaseA * 80f
                            alpha = 1f - ((phaseA - 0.5f) / 0.35f).coerceIn(0f, 1f)
                        },
                ) {
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        Image(
                            snapshot.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxHeight().width(fullW).offset(x = if (hingeLeft) -halfW else 0.dp),
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (phaseA * 0.35f).coerceIn(0f, 0.35f))))
                    }
                }
            }
        }
    }
}

/** Drives [PageTurnOverlay] for one discrete, auto-animated turn (chevron/thumbnail selection) —
 *  animates 0 -> dir over a fixed duration, then clears itself. */
suspend fun Animatable<Float, AnimationVector1D>.playPageTurn(dir: Float) {
    snapTo(dir * 0.001f)
    animateTo(dir, tween(280, easing = FastOutSlowInEasing))
    snapTo(0f)
}

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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("페이지", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (currentPage > 0) onSelect(currentPage - 1) }, enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronLeft, "이전 페이지")
                        }
                        Text("${currentPage + 1}/$pageCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
