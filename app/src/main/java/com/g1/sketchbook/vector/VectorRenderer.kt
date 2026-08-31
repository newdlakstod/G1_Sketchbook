package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

/** [page]의 모든 획을 [canvas]에 그린다 — 지금 펜으로 그린 획은 [strokeOutline]으로 계산한 리본
 *  다각형을 [VectorStroke.color]로 항상 채우고([VectorStroke.fillEnabled]와 무관 — 리본은 펜이
 *  실제로 지나간 자리라 항상 보여야 함), [VectorStroke.strokeColor]가 있으면 그 위에 폴리곤
 *  테두리를 그 색·[VectorStroke.strokeWidthPx] 굵기로 덧그린다. [VectorStroke.fillEnabled]면 그
 *  다음으로 [selfIntersectionFills]로 찾은 자기교차 폐곡선들을 [VectorStroke.fillColor]
 *  (없으면 [VectorStroke.color])로 채워 리본 위에 덧그린다 — 손으로 닫힌 도형을 그리면 그 내부가
 *  자동으로 채워지는 효과. [VectorStroke.brushProfileId]가 [stampBrushes]에서 찾아지면 위 전부
 *  대신 [stampPolygons]로 계산한 도장들을 [VectorStroke.color]로 채워 그린다(못 찾으면 지금
 *  펜으로 폴백). 그린 순서 그대로라 나중 획이 위에 덮인다. `VectorBrushView.onDraw`와 썸네일
 *  렌더링([renderVectorPage])이 이 함수 하나를 같이 쓴다 — 그리기 중인 화면과 저장되는 썸네일이
 *  항상 같은 방식으로 그려진다. */
fun drawVectorPage(canvas: Canvas, page: VectorPage, stampBrushes: Map<String, StampBrushProfile> = emptyMap()) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            fillPaint.color = stroke.color.toInt()
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val path = Path()
                shape.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        fillPaint.color = stroke.color.toInt()
        canvas.drawPath(path, fillPaint)
        stroke.strokeColor?.let { sc ->
            strokePaint.color = sc.toInt()
            strokePaint.strokeWidth = stroke.strokeWidthPx
            canvas.drawPath(path, strokePaint)
        }
        if (stroke.fillEnabled) {
            fillPaint.color = (stroke.fillColor ?: stroke.color).toInt()
            for (region in stroke.fills) {
                if (region.isEmpty()) continue
                val fillPath = Path()
                region.forEachIndexed { i, p -> if (i == 0) fillPath.moveTo(p.x, p.y) else fillPath.lineTo(p.x, p.y) }
                fillPath.close()
                fillPath.fillType = Path.FillType.EVEN_ODD
                canvas.drawPath(fillPath, fillPaint)
            }
        }
    }
}

private const val PREVIEW_PADDING_RATIO = 0.08f

/** 목록/캐러셀 미리보기용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 렌더링한다. 캔버스가
 *  무한이든 커스텀이든 상관없이, 항상 [contentBounds]로 계산한 "그려진 내용의 경계상자"(스탬프
 *  브러시 획은 [stampBrushes]로 그 반경까지 포함)에 8% 여백을 더해 정사각형 안에 맞춘다(letterbox,
 *  가운데 정렬) — 캔버스 자체의 크기/경계는 이 렌더링과 무관. 빈 캔버스(경계상자 없음)는 흰 배경만
 *  있는 빈 비트맵으로 폴백. */
fun renderVectorPage(page: VectorPage, sizePx: Int, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val bounds = contentBounds(page.strokes, stampBrushes) ?: return bmp
    val padX = bounds.width * PREVIEW_PADDING_RATIO
    val padY = bounds.height * PREVIEW_PADDING_RATIO
    val left = bounds.minX - padX; val top = bounds.minY - padY
    val w = bounds.width + padX * 2f; val h = bounds.height + padY * 2f
    val scale = min(sizePx / w, sizePx / h)
    canvas.save()
    canvas.translate((sizePx - w * scale) / 2f, (sizePx - h * scale) / 2f)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    drawVectorPage(canvas, page, stampBrushes)
    canvas.restore()
    return bmp
}
