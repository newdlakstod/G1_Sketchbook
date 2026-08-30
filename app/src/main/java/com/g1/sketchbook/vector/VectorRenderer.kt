package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

/** [page]의 모든 획을 [canvas]에 그린다 — 획 하나 = [strokeOutline]으로 계산한 다각형 하나를 그
 *  획의 색으로 채워 그린다(그린 순서 그대로라 나중 획이 위에 덮인다). `VectorBrushView.onDraw`와
 *  썸네일 렌더링([renderVectorPage])이 이 함수 하나를 같이 쓴다 — 그리기 중인 화면과 저장되는
 *  썸네일이 항상 같은 방식으로 그려진다. */
fun drawVectorPage(canvas: Canvas, page: VectorPage) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    for (stroke in page.strokes) {
        val outline = strokeOutline(stroke.points)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        paint.color = stroke.color.toInt()
        canvas.drawPath(path, paint)
    }
}

private const val PREVIEW_PADDING_RATIO = 0.08f

/** 목록/캐러셀 미리보기용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 렌더링한다. 캔버스가
 *  무한이든 커스텀이든 상관없이, 항상 [contentBounds]로 계산한 "그려진 내용의 경계상자"에 8% 여백을
 *  더해 정사각형 안에 맞춘다(letterbox, 가운데 정렬) — 캔버스 자체의 크기/경계는 이 렌더링과 무관.
 *  빈 캔버스(경계상자 없음)는 흰 배경만 있는 빈 비트맵으로 폴백. */
fun renderVectorPage(page: VectorPage, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val bounds = contentBounds(page.strokes) ?: return bmp
    val padX = bounds.width * PREVIEW_PADDING_RATIO
    val padY = bounds.height * PREVIEW_PADDING_RATIO
    val left = bounds.minX - padX; val top = bounds.minY - padY
    val w = bounds.width + padX * 2f; val h = bounds.height + padY * 2f
    val scale = min(sizePx / w, sizePx / h)
    canvas.save()
    canvas.translate((sizePx - w * scale) / 2f, (sizePx - h * scale) / 2f)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    drawVectorPage(canvas, page)
    canvas.restore()
    return bmp
}
