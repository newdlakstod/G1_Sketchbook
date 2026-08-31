package com.g1.sketchbook.vector

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** SVG 문서 텍스트 전체를 파싱해서 도형별 다각형 목록으로 바꾼다 — `<path>`/`<rect>`/`<circle>`/
 *  `<ellipse>`와, `translate`/`scale`만 걸린 `<g>` 그룹(자식들에 그 이동/배율을 적용)을 지원한다.
 *  `rotate`가 걸린 `<g>`는 그 그룹 전체를 건너뛴다(에러 아님 — 나머지 도형은 그대로 씀). 일반 XML
 *  파서 대신 이 프로젝트가 이미 쓰는 방식대로 정규식으로 태그를 하나씩 찾아 처리한다. 결과는
 *  [normalizeShapes]로 정규화(경계상자 중심 원점, 가장 긴 변 길이 1)해서 반환 — 도형이 하나도
 *  없거나 `<svg` 태그 자체가 없으면 null. */
fun parseSvgDocument(svgText: String): List<List<Point>>? {
    if (!svgText.contains("<svg")) return null
    val shapes = mutableListOf<List<Point>>()
    parseElements(svgText, 1f, 0f, 0f, shapes)
    if (shapes.isEmpty()) return null
    return normalizeShapes(shapes)
}

private val tagRegex = Regex("<(path|rect|circle|ellipse|g)\\b([^>/]*?)(/>|>(.*?)</\\1>)", RegexOption.DOT_MATCHES_ALL)
private val attrRegex = Regex("(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"")

private fun attrs(raw: String): Map<String, String> =
    attrRegex.findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

/** [scale]/[dx]/[dy]는 지금까지 누적된 조상 `<g translate/scale>`의 효과 — 자식 도형의 좌표를 이
 *  누적값으로 변환해서 [into]에 최종(정규화 전) 좌표로 추가한다. */
private fun parseElements(xml: String, scale: Float, dx: Float, dy: Float, into: MutableList<List<Point>>) {
    for (m in tagRegex.findAll(xml)) {
        val tag = m.groupValues[1]
        val rawAttrs = attrs(m.groupValues[2])
        when (tag) {
            "path" -> {
                val d = rawAttrs["d"] ?: continue
                val pts = parseSvgPath(d)
                if (pts.size >= 2) into.add(pts.map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "rect" -> {
                val x = rawAttrs["x"]?.toFloatOrNull() ?: 0f
                val y = rawAttrs["y"]?.toFloatOrNull() ?: 0f
                val w = rawAttrs["width"]?.toFloatOrNull() ?: continue
                val h = rawAttrs["height"]?.toFloatOrNull() ?: continue
                val poly = listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))
                into.add(poly.map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "circle" -> {
                val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
                val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
                val r = rawAttrs["r"]?.toFloatOrNull() ?: continue
                into.add(ellipsePolygon(cx, cy, r, r).map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "ellipse" -> {
                val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
                val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
                val rx = rawAttrs["rx"]?.toFloatOrNull() ?: continue
                val ry = rawAttrs["ry"]?.toFloatOrNull() ?: continue
                into.add(ellipsePolygon(cx, cy, rx, ry).map { Point(it.x * scale + dx, it.y * scale + dy) })
            }
            "g" -> {
                val transform = rawAttrs["transform"]
                if (transform != null && transform.contains("rotate")) continue // 회전 그룹은 건너뜀
                var gScale = 1f; var gDx = 0f; var gDy = 0f
                transform?.let { t ->
                    Regex("translate\\(([-\\d.]+)[ ,]+([-\\d.]+)\\)").find(t)?.let { tm ->
                        gDx = tm.groupValues[1].toFloat(); gDy = tm.groupValues[2].toFloat()
                    }
                    Regex("scale\\(([-\\d.]+)\\)").find(t)?.let { sm -> gScale = sm.groupValues[1].toFloat() }
                }
                val inner = m.groupValues[4]
                // 부모 스케일/이동에 이 그룹 자신의 translate/scale을 이어붙인다(부모 먼저 적용된 좌표계 위에).
                parseElements(inner, scale * gScale, dx + gDx * scale, dy + gDy * scale, into)
            }
        }
    }
}

private fun ellipsePolygon(cx: Float, cy: Float, rx: Float, ry: Float, sides: Int = 24): List<Point> =
    (0 until sides).map { i ->
        val t = 2 * PI * i / sides
        Point(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat())
    }

/** 모든 도형을 합친 경계상자를 계산해서, 그 중심이 원점에 오고 가장 긴 변이 길이 1이 되도록
 *  전부 같은 배율로 스케일+평행이동한다. */
private fun normalizeShapes(shapes: List<List<Point>>): List<List<Point>> {
    val all = shapes.flatten()
    val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
    val w = maxX - minX; val h = maxY - minY
    val longest = maxOf(w, h).takeIf { it > 0f } ?: 1f
    val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
    return shapes.map { shape -> shape.map { Point((it.x - cx) / longest, (it.y - cy) / longest) } }
}
