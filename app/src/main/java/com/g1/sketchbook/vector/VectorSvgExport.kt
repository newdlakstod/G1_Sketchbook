package com.g1.sketchbook.vector

/** [page]를 아이콘용 SVG 문서 텍스트로 직렬화한다 — 획 하나 = `<path>` 하나(채워진 다각형,
 *  `stroke-width` 아님), 그린 순서 그대로 뒤에 쓰여서 겹친 획의 z-order도 그대로 유지된다. 색은
 *  [VectorStroke.color]의 ARGB Long에서 알파를 버리고 RGB만 "#rrggbb"로 쓴다 — 펜은 항상 불투명
 *  잉크라(기존 래스터 펜과 동일 전제) 알파 채널을 SVG로 따로 표현할 필요가 없다. */
fun vectorPageToSvg(page: VectorPage, sizePx: Int): String {
    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(sizePx)
        .append("\" height=\"").append(sizePx)
        .append("\" viewBox=\"0 0 ").append(sizePx).append(' ').append(sizePx).append("\">")
    for (stroke in page.strokes) {
        val outline = strokeOutline(stroke.points)
        if (outline.isEmpty()) continue
        sb.append("<path d=\"M")
        outline.forEachIndexed { i, p ->
            if (i == 0) sb.append(p.x).append(',').append(p.y)
            else sb.append(" L").append(p.x).append(',').append(p.y)
        }
        sb.append(" Z\" fill=\"").append(colorHex(stroke.color)).append("\"/>")
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun colorHex(argb: Long): String {
    val rgb = argb.toInt() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}
