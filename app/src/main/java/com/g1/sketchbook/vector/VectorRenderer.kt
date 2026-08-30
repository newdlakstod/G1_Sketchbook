package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

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

/** 목록/캐러셀 썸네일용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 한 번 렌더링한다(벡터
 *  페이지는 종이 질감이 없어 배경은 항상 흰색). */
fun renderVectorPage(page: VectorPage, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    drawVectorPage(canvas, page)
    return bmp
}
