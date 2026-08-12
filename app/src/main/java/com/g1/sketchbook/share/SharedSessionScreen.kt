package com.g1.sketchbook.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.systemBarsPadding
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.sketchbook.Catalog
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// Shared canvas uses a real catalog size (A4) so a saved copy imports cleanly into 스케치북.
private val SHARE_SIZE = Catalog.size("a4")
private const val SHARE_BG = "drawing"

/**
 * "Draw together" split view: my interactive canvas on one half, the partner's live snapshot on the
 * other. Portrait stacks top/bottom, landscape sits left/right. Each stroke pushes a small snapshot.
 */
@Composable
fun SharedSessionScreen(
    code: String,
    isHost: Boolean,
    myUid: String,
    myName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ShareRepository() }
    val sbRepo = remember { SketchbookRepository(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF2B4C9BL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }

    var partner by remember { mutableStateOf<ShareRepository.Slot?>(null) }
    var partnerBmp by remember { mutableStateOf<Bitmap?>(null) }

    // Listen for the partner's slot updates.
    LaunchedEffect(code) {
        repo.observeSession(code).collect { st ->
            partner = st.slots.firstOrNull { it.uid != myUid }
        }
    }
    // Decode the partner's latest snapshot off the main thread.
    LaunchedEffect(partner?.updatedAt, partner?.snapshot) {
        val s = partner?.snapshot
        partnerBmp = if (s == null) null else withContext(Dispatchers.Default) { decodeSnapshot(s) }
    }

    fun leave() { repo.leaveSession(code, myUid, isHost) }
    fun saveMine() {
        val bmp = view?.exportBitmap() ?: return
        scope.launch(Dispatchers.Default) {
            val name = "함께 그리기 " + SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
            val book = sbRepo.create(name, SHARE_SIZE.key, SHARE_BG)
            sbRepo.savePage(book.id, 0, bmp)
            withContext(Dispatchers.Main) { Toast.makeText(context, "스케치북에 저장했어요", Toast.LENGTH_SHORT).show() }
        }
    }
    BackHandler { leave(); onBack() }
    DisposableEffect(Unit) { onDispose { /* snapshot listener closes via LaunchedEffect scope */ } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        // Top strip: back + invite code + partner status.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { leave(); onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "나가기")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("초대코드  $code", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text(
                    if (partner == null) "상대를 기다리는 중…" else "${partner!!.name} 님과 함께 그리는 중",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(40.dp).clickable { saveMine() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Save, "내 그림 저장", tint = MaterialTheme.colorScheme.primary)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val landscape = maxWidth > maxHeight
            // movableContentOf preserves the panes' nodes (esp. the BrushView + its bitmaps) when the
            // layout swaps between Row and Column on rotation, so the drawing isn't lost.
            val mine = remember {
                movableContentOf<Modifier> { m ->
                    PaneFrame(m, "나 · $myName", accent = true) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                BrushView(ctx).also { v ->
                                    v.paper = BitmapFactory.decodeResource(ctx.resources, R.drawable.paper_drawing)
                                    v.initCanvas(SHARE_SIZE.pxW(), SHARE_SIZE.pxH())
                                    view = v
                                }
                            },
                            update = { v ->
                                v.brush = brush; v.color = color.toInt(); v.strokeSize = sizeDp * density; v.opacity = opacity / 100f
                                v.erasing = erasing
                                v.onStrokeEnd = {
                                    val bmp = v.exportBitmap()
                                    if (bmp != null) scope.launch(Dispatchers.Default) {
                                        repo.pushSnapshot(code, myUid, encodeSnapshot(bmp))
                                    }
                                }
                            },
                        )
                    }
                }
            }
            val theirs = remember {
                movableContentOf<Modifier> { m ->
                    PaneFrame(m, partner?.name ?: "상대", accent = false) {
                        val bmp = partnerBmp
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), "상대 그림", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (partner == null) "아직 아무도 없어요\n초대코드를 공유해 보세요" else "아직 그리기 전이에요",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (landscape) {
                // Partner on the left, me on the right.
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    theirs(Modifier.weight(1f).fillMaxSize()); mine(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                // Partner on top, me at the bottom.
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    theirs(Modifier.weight(1f).fillMaxWidth()); mine(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        BrushControls(
            brush, color, sizeDp, opacity, erasing,
            onBrush = { brush = it; erasing = false }, onColor = { color = it; erasing = false },
            onSize = { sizeDp = it }, onOpacity = { opacity = it }, onToggleErase = { erasing = !erasing },
            onUndo = { view?.undo() }, onRedo = { view?.redo() },
            onClear = {
                view?.clearCanvas()
                view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.Default) { repo.pushSnapshot(code, myUid, encodeSnapshot(b)) } }
            },
            onRotate = { view?.rotate() },
        )
    }
}

/** A titled, rounded frame around a pane; the active (mine) pane gets a subtle accent border. */
@Composable
private fun PaneFrame(modifier: Modifier, title: String, accent: Boolean, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .border(
                    width = if (accent) 2.dp else 1.dp,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .clipToBounds(),
        ) { content() }
    }
}

// ---- snapshot codec (downscaled JPEG -> Base64, API 24 safe) ----
private fun encodeSnapshot(src: Bitmap): String {
    val maxSide = 700
    val s = min(1f, maxSide.toFloat() / max(src.width, src.height))
    val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeSnapshot(b64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(b64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
