package com.g1.sketchbook.brush

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
