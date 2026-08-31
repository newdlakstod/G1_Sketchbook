package com.g1.sketchbook.vector

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 사용자가 임포트한 SVG 하나 = 스탬프 브러시 프로필 하나. [shapes]는 이미 [parseSvgDocument]로
 *  정규화(경계상자 중심 원점, 가장 긴 변 길이 1)된 다각형 목록 — 찍을 때 [sizePx]만큼 스케일만
 *  하면 된다. [spacingPx]/[sizePx] 둘 다 그리는 속도와 무관한 고정값. */
data class StampBrushProfile(
    val id: String,
    val name: String,
    val shapes: List<List<Point>>,
    val spacingPx: Float = 24f,
    val sizePx: Float = 32f,
)

/** [VectorPage.toJson]과 같은 손수 문자열 조립 방식(이 프로젝트는 범용 JSON 파서를 안 씀) —
 *  `shapes`의 각 도형을 `{"points":[...]}` 하나로 감싸서, [VectorPage.strokeRegex]와 똑같은
 *  "비탐욕 정규식으로 `]}` 앞까지 잘라내기" 트릭을 그대로 재사용할 수 있게 한다(중첩 배열을 직접
 *  파싱하는 것보다 이미 검증된 패턴을 반복하는 쪽이 안전). */
fun StampBrushProfile.toJson(): String {
    val sb = StringBuilder("{\"id\":\"").append(id).append("\",\"name\":\"").append(name)
        .append("\",\"spacingPx\":").append(spacingPx).append(",\"sizePx\":").append(sizePx)
        .append(",\"shapes\":[")
    shapes.forEachIndexed { si, shape ->
        if (si > 0) sb.append(',')
        sb.append("{\"points\":[")
        shape.forEachIndexed { pi, p ->
            if (pi > 0) sb.append(',')
            sb.append("{\"x\":").append(p.x).append(",\"y\":").append(p.y).append('}')
        }
        sb.append("]}")
    }
    sb.append("]}")
    return sb.toString()
}

private val stampMetaRegex = Regex("\"id\":\"(.*?)\",\"name\":\"(.*?)\",\"spacingPx\":(-?[0-9.eE+-]+),\"sizePx\":(-?[0-9.eE+-]+)")
private val shapeRegex = Regex("\\{\"points\":\\[(.*?)]\\}")
private val stampPointRegex = Regex("\\{\"x\":(-?[0-9.eE+-]+),\"y\":(-?[0-9.eE+-]+)\\}")

fun stampBrushProfileFromJson(json: String): StampBrushProfile? = runCatching {
    val meta = stampMetaRegex.find(json) ?: return null
    val shapesText = json.substringAfter("\"shapes\":[")
    val shapes = shapeRegex.findAll(shapesText).map { sm ->
        stampPointRegex.findAll(sm.groupValues[1]).map { pm -> Point(pm.groupValues[1].toFloat(), pm.groupValues[2].toFloat()) }.toList()
    }.toList()
    StampBrushProfile(meta.groupValues[1], meta.groupValues[2], shapes, meta.groupValues[3].toFloat(), meta.groupValues[4].toFloat())
}.getOrNull()

/** [points](획의 중심선)를 따라 호 길이 기준 [StampBrushProfile.spacingPx]마다 [StampBrushProfile.shapes]를
 *  하나씩 찍는다 — 각 지점에서: 정규화된 스탬프 다각형들을 그 지점의 진행 방향(접선) 각도만큼
 *  회전 -> [StampBrushProfile.sizePx]만큼 스케일 -> 그 지점 좌표로 평행이동. 중심선 전체 길이가
 *  [StampBrushProfile.spacingPx]보다 짧으면(또는 점이 2개 미만이면) 찍을 자리가 없어 빈 목록. */
fun stampPolygons(profile: StampBrushProfile, points: List<VectorPoint>): List<List<Point>> {
    if (points.size < 2 || profile.shapes.isEmpty() || profile.spacingPx <= 0f) return emptyList()
    // 누적 호 길이 테이블 — 중심선을 따라 이동한 총 거리 하나씩.
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val dx = (points[i].x - points[i - 1].x).toDouble(); val dy = (points[i].y - points[i - 1].y).toDouble()
        cumulative[i] = cumulative[i - 1] + hypot(dx, dy)
    }
    val totalLen = cumulative.last()
    if (totalLen < profile.spacingPx) return emptyList()

    val result = mutableListOf<List<Point>>()
    var target = 0.0
    var seg = 1
    while (target <= totalLen) {
        while (seg < points.size - 1 && cumulative[seg] < target) seg++
        val segStart = cumulative[seg - 1]; val segEnd = cumulative[seg]
        val segLen = segEnd - segStart
        val t = if (segLen > 0.0) ((target - segStart) / segLen).toFloat() else 0f
        val p0 = points[seg - 1]; val p1 = points[seg]
        val px = p0.x + (p1.x - p0.x) * t
        val py = p0.y + (p1.y - p0.y) * t
        val angle = kotlin.math.atan2((p1.y - p0.y).toDouble(), (p1.x - p0.x).toDouble()).toFloat()
        val cosA = cos(angle); val sinA = sin(angle)
        for (shape in profile.shapes) {
            result.add(shape.map { local ->
                val sx = local.x * profile.sizePx; val sy = local.y * profile.sizePx
                Point(px + sx * cosA - sy * sinA, py + sx * sinA + sy * cosA)
            })
        }
        target += profile.spacingPx
    }
    return result
}
