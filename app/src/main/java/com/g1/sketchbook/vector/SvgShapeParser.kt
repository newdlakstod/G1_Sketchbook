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

private val startTagRegex = Regex("<(path|rect|circle|ellipse|g)\\b([^>]*?)(/?)>")
private val attrRegex = Regex("(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"")

private fun attrs(raw: String): Map<String, String> =
    attrRegex.findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

/** [scale]/[dx]/[dy]는 지금까지 누적된 조상 `<g translate/scale>`의 효과 — 자식 도형의 좌표를 이
 *  누적값으로 변환해서 [into]에 최종(정규화 전) 좌표로 추가한다. 정규식 하나로는 같은 이름의 중첩
 *  태그(`<g>` 안의 `<g>`)를 올바르게 매칭할 수 없어서(비탐욕 매칭이 가장 안쪽 닫는 태그에서 멈춤),
 *  `<g>`의 짝이 맞는 닫는 태그는 [findMatchingGClose]로 깊이를 세어가며 직접 찾는다. */
private fun parseElements(xml: String, scale: Float, dx: Float, dy: Float, into: MutableList<List<Point>>) {
    var searchFrom = 0
    while (searchFrom < xml.length) {
        val m = startTagRegex.find(xml, searchFrom) ?: break
        val tag = m.groupValues[1]
        val rawAttrs = attrs(m.groupValues[2])
        val selfClosing = m.groupValues[3] == "/"
        val tagEnd = m.range.last + 1

        if (tag != "g") {
            handleLeafShape(tag, rawAttrs, scale, dx, dy, into)
            searchFrom = tagEnd
            continue
        }
        if (selfClosing) { searchFrom = tagEnd; continue } // 빈 <g/> — 자식 없음

        val closeStart = findMatchingGClose(xml, tagEnd)
        val transform = rawAttrs["transform"]
        if (transform == null || !transform.contains("rotate")) {
            var gScale = 1f; var gDx = 0f; var gDy = 0f
            transform?.let { t ->
                Regex("translate\\(([-\\d.]+)[ ,]+([-\\d.]+)\\)").find(t)?.let { tm ->
                    gDx = tm.groupValues[1].toFloat(); gDy = tm.groupValues[2].toFloat()
                }
                Regex("scale\\(([-\\d.]+)\\)").find(t)?.let { sm -> gScale = sm.groupValues[1].toFloat() }
            }
            // 부모 스케일/이동에 이 그룹 자신의 translate/scale을 이어붙인다(부모 먼저 적용된 좌표계 위에).
            val inner = xml.substring(tagEnd, closeStart)
            parseElements(inner, scale * gScale, dx + gDx * scale, dy + gDy * scale, into)
        }
        searchFrom = if (closeStart < xml.length) closeStart + 4 else xml.length
    }
}

private fun handleLeafShape(tag: String, rawAttrs: Map<String, String>, scale: Float, dx: Float, dy: Float, into: MutableList<List<Point>>) {
    when (tag) {
        "path" -> {
            val d = rawAttrs["d"] ?: return
            val pts = parseSvgPath(d)
            if (pts.size >= 2) into.add(pts.map { Point(it.x * scale + dx, it.y * scale + dy) })
        }
        "rect" -> {
            val x = rawAttrs["x"]?.toFloatOrNull() ?: 0f
            val y = rawAttrs["y"]?.toFloatOrNull() ?: 0f
            val w = rawAttrs["width"]?.toFloatOrNull() ?: return
            val h = rawAttrs["height"]?.toFloatOrNull() ?: return
            val poly = listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))
            into.add(poly.map { Point(it.x * scale + dx, it.y * scale + dy) })
        }
        "circle" -> {
            val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
            val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
            val r = rawAttrs["r"]?.toFloatOrNull() ?: return
            into.add(ellipsePolygon(cx, cy, r, r).map { Point(it.x * scale + dx, it.y * scale + dy) })
        }
        "ellipse" -> {
            val cx = rawAttrs["cx"]?.toFloatOrNull() ?: 0f
            val cy = rawAttrs["cy"]?.toFloatOrNull() ?: 0f
            val rx = rawAttrs["rx"]?.toFloatOrNull() ?: return
            val ry = rawAttrs["ry"]?.toFloatOrNull() ?: return
            into.add(ellipsePolygon(cx, cy, rx, ry).map { Point(it.x * scale + dx, it.y * scale + dy) })
        }
    }
}

/** [from]은 어떤 `<g ...>` 여는 태그의 '>' 바로 다음 인덱스 — 그 지점부터 깊이를 세며 스캔해서
 *  짝이 맞는 `</g>`의 시작 인덱스를 찾는다(내부에 또 다른 `<g>`가 있으면 깊이를 늘리고, 자기
 *  닫힘(`<g/>`)은 깊이에 영향 없음). 못 찾으면(태그가 안 닫혔으면) 문서 끝 인덱스를 반환한다. */
private fun findMatchingGClose(xml: String, from: Int): Int {
    var depth = 1
    var i = from
    while (i < xml.length) {
        val nextOpen = xml.indexOf("<g", i)
        val nextClose = xml.indexOf("</g>", i)
        if (nextClose < 0) return xml.length
        if (nextOpen in 0 until nextClose) {
            val afterName = nextOpen + 2
            val isRealGTag = afterName < xml.length && (xml[afterName].isWhitespace() || xml[afterName] == '>' || xml[afterName] == '/')
            if (isRealGTag) {
                val thisTagEnd = xml.indexOf('>', nextOpen)
                if (thisTagEnd < 0) return xml.length
                val selfClosing = xml[thisTagEnd - 1] == '/'
                if (!selfClosing) depth++
                i = thisTagEnd + 1
            } else {
                i = nextOpen + 2
            }
        } else {
            depth--
            if (depth == 0) return nextClose
            i = nextClose + 4
        }
    }
    return xml.length
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
