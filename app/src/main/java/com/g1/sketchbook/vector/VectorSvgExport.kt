package com.g1.sketchbook.vector

/** [page]에서 [region]에 해당하는 부분만 아이콘용 SVG 문서 텍스트로 직렬화한다 — 지금 펜으로
 *  그린 획 하나는 리본 `<path>` 하나(항상 [VectorStroke.color]로 채움, [VectorStroke.fillEnabled]와
 *  무관) + [VectorStroke.fillEnabled]면 자기교차 폐곡선마다 [VectorStroke.fillColor](없으면
 *  [VectorStroke.color])로 채운 `<path>`가 추가로 붙는다. [VectorStroke.brushProfileId]가
 *  [stampBrushes]에서 찾아지면 위 전부 대신 찍힌 도장 하나하나를 각각 독립된 `<path>`로 풀어서
 *  쓴다(못 찾으면 지금 펜으로 폴백) — 획 하나가 여러 `<path>`가 될 수 있다는 뜻. 그린 순서 그대로
 *  유지. viewBox는 항상 "0 0 width height"로 시작하도록 [region]만큼 좌표를 평행이동한다(내보낸
 *  SVG가 원본 캔버스 좌표계를 몰라도 되게). 점이 하나도 [region] 안에 없는 도형은 건너뛴다 —
 *  부분적으로만 겹치는 도형은 지금은 잘라내지 않고 그대로 포함한다(잘라내기는 이 스펙 범위 밖).
 *  [VectorStroke.strokeColor]가 있으면 리본 위에 `stroke`/`stroke-width`도 같이 쓴다(SVG의
 *  fill·stroke 개념 그대로). 색은 ARGB Long에서 알파를 버리고 RGB만 "#rrggbb"로 쓴다(펜은 항상
 *  불투명). */
fun vectorPageToSvg(page: VectorPage, region: Bounds, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): String {
    val w = region.width; val h = region.height
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
        .append("\" height=\"").append(h)
        .append("\" viewBox=\"0 0 ").append(w).append(' ').append(h).append("\">")
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val touches = shape.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
                if (!touches) continue
                sb.append("<path d=\"M")
                shape.forEachIndexed { i, p ->
                    val x = p.x - region.minX; val y = p.y - region.minY
                    if (i == 0) sb.append(x).append(',').append(y) else sb.append(" L").append(x).append(',').append(y)
                }
                sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append("\"/>")
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val touchesRegion = outline.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
        if (touchesRegion) {
            sb.append("<path d=\"M")
            outline.forEachIndexed { i, p ->
                val x = p.x - region.minX; val y = p.y - region.minY
                if (i == 0) sb.append(x).append(',').append(y)
                else sb.append(" L").append(x).append(',').append(y)
            }
            sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append('"')
            stroke.strokeColor?.let { sc ->
                sb.append(" stroke=\"").append(colorHex(sc)).append("\" stroke-width=\"").append(stroke.strokeWidthPx).append('"')
            }
            sb.append("/>")
        }
        if (stroke.fillEnabled) {
            val fillHex = colorHex(stroke.fillColor ?: stroke.color)
            for (closedRegion in stroke.fills) {
                if (closedRegion.isEmpty()) continue
                val touches = closedRegion.any { it.x >= region.minX && it.x <= region.maxX && it.y >= region.minY && it.y <= region.maxY }
                if (!touches) continue
                sb.append("<path d=\"M")
                closedRegion.forEachIndexed { i, p ->
                    val x = p.x - region.minX; val y = p.y - region.minY
                    if (i == 0) sb.append(x).append(',').append(y) else sb.append(" L").append(x).append(',').append(y)
                }
                sb.append(" Z\" fill=\"").append(fillHex).append("\" fill-rule=\"evenodd\"/>")
            }
        }
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun colorHex(argb: Long): String {
    val rgb = argb.toInt() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}
