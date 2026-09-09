package com.g1.sketchbook.brush

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.g1.sketchbook.data.ColorLibrary
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** 화면에 표시된 이미지 위 터치 좌표(박스 기준, px)를 실제 비트맵 픽셀 좌표로 되돌린다. 이미지는
 *  [ContentScale.Crop]으로 박스를 꽉 채우게 먼저 맞춘 뒤(큰 쪽 비율 기준, baseScale), 그 위에 사용자가
 *  추가로 확대한 배율([zoom])과 이동([panX]/[panY], 박스 중심을 피벗점으로 한 화면 px 이동 —
 *  `Modifier.graphicsLayer { scaleX = zoom; translationX = panX }`가 기본으로 적용하는 변환과 정확히
 *  같다)이 겹쳐 있다. 결과가 비트맵 범위를 벗어나면 null. */
fun imageEyedropperPixel(
    touchX: Float, touchY: Float,
    boxWidth: Float, boxHeight: Float,
    bitmapWidth: Int, bitmapHeight: Int,
    zoom: Float, panX: Float, panY: Float,
): Pair<Int, Int>? {
    if (boxWidth <= 0f || boxHeight <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return null
    val baseScale = max(boxWidth / bitmapWidth, boxHeight / bitmapHeight)
    val dispW = bitmapWidth * baseScale
    val dispH = bitmapHeight * baseScale
    val baseOffsetX = (boxWidth - dispW) / 2f
    val baseOffsetY = (boxHeight - dispH) / 2f
    val cx = boxWidth / 2f
    val cy = boxHeight / 2f
    val bx = ((touchX - cx - panX) / zoom) + cx - baseOffsetX
    val by = ((touchY - cy - panY) / zoom) + cy - baseOffsetY
    val px = (bx / baseScale).roundToInt()
    val py = (by / baseScale).roundToInt()
    if (px !in 0 until bitmapWidth || py !in 0 until bitmapHeight) return null
    return px to py
}

/** [ColorPickerCard]가 "지금 라이브러리 칸을 편집하는 중"임을 [ImageEyedropperOverlay]에 알리는
 *  묶음 — 스와치 줄을 그리는 데 필요한 라이브러리 전체 색상 목록, 처음에 강조할 칸의 인덱스, 그리고
 *  어떤 칸이든 색이 바뀔 때 부를 콜백을 담는다. */
data class LibrarySwatchContext(
    val library: ColorLibrary,
    val colorIndex: Int,
    val onEditColor: (Int, Long) -> Unit,
)

// BrushControls.kt의 FavoriteSwatchSize(24.dp)와 시각적으로 맞추기 위한 값 — 그쪽은 파일 스코프
// private(같은 패키지라도 다른 파일에서는 접근 불가)이라 그대로 참조할 수 없어 값만 복제했다.
private val EyedropperSwatchSize = 24.dp

/** 갤러리에서 고른 [bitmap]을 핀치줌/드래그로 확대·이동하며, 손가락으로 누른 지점의 색을
 *  [context]가 가리키는 라이브러리 칸에 바로 저장하는 전체화면 오버레이. 대상 칸은 위쪽 스와치
 *  줄에서 바꿀 수 있고, 이미지·확대 상태는 대상을 바꿔도 유지된다. */
@Composable
fun ImageEyedropperOverlay(bitmap: Bitmap, context: LibrarySwatchContext, onDone: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(context.colorIndex) }
    // 손가락으로 누르고 있는 동안의 미리보기 — null이면 지금 안 누르고 있는 것.
    var preview by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val boxHeightPx = with(density) { maxHeight.toPx() }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(bitmap) {
                            awaitPointerEventScope {
                                // 이전 프레임의 2손가락 중점/거리 — 다음 프레임과 비교해 확대/이동 델타를 구한다
                                // (BrushView.onTouchEvent의 핀치줌 처리와 같은 방식, MotionEvent 대신 Compose의
                                // PointerEvent.changes를 씀).
                                var prevMidX = 0f; var prevMidY = 0f; var prevDist = 0f
                                // 이 제스처(포인터 0개→0개 사이의 한 번)가 도중에 2손가락 이상이었던 적이
                                // 있는지 — 있었다면 손가락 하나가 남아도(핀치 중 하나를 뗀 직후) 그 남은
                                // 손가락 위치를 색으로 잘못 확정하지 않도록 샘플링을 계속 막는다. 포인터가
                                // 완전히 0개가 되어야(새 제스처 시작) 다시 풀린다.
                                var hadMultiTouch = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    when {
                                        pressed.size >= 2 -> {
                                            val a = pressed[0].position; val b = pressed[1].position
                                            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                                            val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                                            if (prevDist > 0f) {
                                                zoom = (zoom * (dist / prevDist)).coerceIn(1f, 5f)
                                                panX += mx - prevMidX; panY += my - prevMidY
                                            }
                                            prevMidX = mx; prevMidY = my; prevDist = dist
                                            preview = null
                                            hadMultiTouch = true
                                            pressed.forEach { it.consume() }
                                        }
                                        pressed.size == 1 -> {
                                            prevDist = 0f
                                            if (!hadMultiTouch) {
                                                val p = pressed[0].position
                                                val picked = imageEyedropperPixel(
                                                    p.x, p.y, boxWidthPx, boxHeightPx,
                                                    bitmap.width, bitmap.height, zoom, panX, panY,
                                                )
                                                if (picked != null) {
                                                    val (px, py) = picked
                                                    val c = bitmap.getPixel(px, py)
                                                    preview = Triple(c, p.x, p.y)
                                                } else {
                                                    preview = null
                                                }
                                            }
                                            pressed[0].consume()
                                        }
                                        else -> {
                                            // 손을 뗌 — 누르고 있던 색이 있었으면 지금 대상 칸에 확정 저장.
                                            preview?.let { (c, _, _) -> context.onEditColor(targetIndex, (c.toLong() and 0xFFFFFFFF) or 0xFF000000L) }
                                            preview = null
                                            prevDist = 0f
                                            hadMultiTouch = false
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY },
                    )
                }
                preview?.let { (c, x, y) -> EyedropFloatingPreview(c, x, y) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                context.library.colors.forEachIndexed { i, col ->
                    val selected = i == targetIndex
                    Box(
                        Modifier.size(EyedropperSwatchSize).clip(CircleShape).background(Color(col))
                            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), CircleShape)
                            .clickable(onClickLabel = "${i + 1}번째 색") { targetIndex = i },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDone) { Text("완료") }
            }
        }
    }
}
