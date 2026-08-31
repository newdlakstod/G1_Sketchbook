package com.g1.sketchbook.vector

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 2D 점 하나 — `android.graphics.PointF`를 안 쓰는 이유는 이 파일이 Android 의존 없는 순수
 *  Kotlin이라 로컬 유닛 테스트(JVM)에서 그대로 돌아가야 하기 때문. */
data class Point(val x: Float, val y: Float)

/** 획 양 끝의 마감 모양 — 일러스트레이터 등 벡터 편집기의 획 마감 옵션과 동일한 개념.
 *  [BUTT]가 기존(이 옵션이 생기기 전) 동작과 완전히 같다 — 구버전 데이터를 읽을 때 값이 없으면
 *  [BUTT]로 취급해 예전 그림의 생김새를 그대로 유지한다([VectorPage.kt]의 역직렬화 참고). */
enum class VectorCap { ROUND, SQUARE, BUTT }

/** 획의 점 목록(중심선 + 지점별 굵기)을 "굵기가 변하는 리본" 모양의 채워진 다각형 외곽선으로
 *  바꾼다 — 각 점에서 진행 방향에 수직인 법선 방향으로 굵기/2만큼 오프셋한 좌표를 위/아래 경계로
 *  삼아, 위쪽 경계를 순서대로 + [cap] 모양의 끝단 + 아래쪽 경계를 역순으로 + 시작단 마감을 이어
 *  닫힌 다각형을 만든다(획 하나 = 다각형 하나, `stroke-width` 아님 — 그려질 때도, SVG로 내보낼
 *  때도 이 모양 그대로 채워 그린다). [cap]이 [VectorCap.BUTT]면 끝단에 아무것도 안 붙어서(원래
 *  이 옵션이 생기기 전과 완전히 같은 모양) 점이 2개 미만이면(찍기만 하고 안 그은 경우) 그릴 게
 *  없어 빈 목록. */
fun strokeOutline(points: List<VectorPoint>, cap: VectorCap = VectorCap.BUTT): List<Point> {
    if (points.size < 2) return emptyList()
    val left = ArrayList<Point>(points.size)
    val right = ArrayList<Point>(points.size)
    val normals = ArrayList<FloatArray>(points.size)
    for (i in points.indices) {
        val p = points[i]
        val (dx, dy) = if (i < points.size - 1) {
            points[i + 1].x - p.x to points[i + 1].y - p.y
        } else {
            p.x - points[i - 1].x to p.y - points[i - 1].y
        }
        val len = sqrt(dx * dx + dy * dy)
        val (nx, ny) = if (len < 0.0001f) 0f to 0f else -dy / len to dx / len
        normals.add(floatArrayOf(nx, ny))
        val half = p.w / 2f
        left.add(Point(p.x + nx * half, p.y + ny * half))
        right.add(Point(p.x - nx * half, p.y - ny * half))
    }
    val startNormal = normals[0]
    val endNormal = normals[points.size - 1]
    // 끝점의 접선(다음 점 방향 계산에 쓴 (dx,dy))은 이미 몸통 밖(진행 방향 연장)을 향해서 그대로
    // outward로 쓴다 — 법선을 -90도 회전하면 그 접선이 나온다: tangent = (ny, -nx).
    val endOutward = floatArrayOf(endNormal[1], -endNormal[0])
    // 시작점의 접선은 몸통 안(다음 점 방향)을 향하므로, outward는 그 반대.
    val startOutward = floatArrayOf(-startNormal[1], startNormal[0])
    val endCap = capArc(points[points.size - 1], fromEdge = endNormal, outward = endOutward, cap = cap)
    val startCap = capArc(points[0], fromEdge = floatArrayOf(-startNormal[0], -startNormal[1]), outward = startOutward, cap = cap)
    return left + endCap + right.asReversed() + startCap
}

/** [p] 끝단의 마감 모양을 이루는 "중간" 점들만 반환한다 — 정확한 left/right 위치 자체는 이미
 *  [strokeOutline]의 left/right 배열에 있으니 여기선 안 겹치게 뺀다. [fromEdge] 방향에서
 *  [outward] 방향을 지나 정반대(=−[fromEdge]) 방향까지 반원을 그리는 셈 — [VectorCap.BUTT]는
 *  중간점 없이 바로 이어짐(직선), [VectorCap.SQUARE]는 바깥으로 [outward]만큼 나간 모서리 2개,
 *  [VectorCap.ROUND]는 매끄러운 호. */
private fun capArc(p: VectorPoint, fromEdge: FloatArray, outward: FloatArray, cap: VectorCap): List<Point> {
    val half = p.w / 2f
    return when (cap) {
        VectorCap.BUTT -> emptyList()
        VectorCap.SQUARE -> listOf(
            Point(p.x + half * (fromEdge[0] + outward[0]), p.y + half * (fromEdge[1] + outward[1])),
            Point(p.x + half * (-fromEdge[0] + outward[0]), p.y + half * (-fromEdge[1] + outward[1])),
        )
        VectorCap.ROUND -> {
            val steps = 8
            (1 until steps).map { i ->
                val t = (i.toFloat() / steps) * Math.PI.toFloat()
                val cosT = cos(t); val sinT = sin(t)
                Point(
                    p.x + half * (fromEdge[0] * cosT + outward[0] * sinT),
                    p.y + half * (fromEdge[1] * cosT + outward[1] * sinT),
                )
            }
        }
    }
}

/** 표준 ray-casting 알고리즘 — [polygon] 안에 [x],[y]가 들어있는지. 지우개(획 단위 삭제)가 탭 지점이
 *  어느 획의 [strokeOutline] 안에 들어가는지 판정하는 데 쓴다. */
fun pointInPolygon(x: Float, y: Float, polygon: List<Point>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]; val pj = polygon[j]
        if ((pi.y > y) != (pj.y > y)) {
            val xIntersect = pi.x + (y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
            if (x < xIntersect) inside = !inside
        }
        j = i
    }
    return inside
}

/** 축에 정렬된 사각 경계상자 하나 — 내보내기 영역, 미리보기 맞춤 등에 쓴다. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/** [strokes] 전체를 감싸는 최소 사각형 — 획이 하나도 없으면 null. 벡터 캔버스 미리보기/썸네일과
 *  "전체" 내보내기(무한 캔버스)가 이 경계상자를 기준으로 삼는다. 지금 펜으로 그린 획은 각 점의
 *  굵기 절반만큼만 바깥으로 확장하면 되지만, [stampBrushes]에서 찾은 스탬프 브러시 획은 찍힌
 *  도장 하나의 반지름(대략 [StampBrushProfile.sizePx]의 대각선 절반)만큼 훨씬 크게 튀어나올 수
 *  있어 그만큼 확장한다 — 안 그러면 미리보기/내보내기에서 스탬프 가장자리가 잘린다. */
fun contentBounds(strokes: List<VectorStroke>, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bounds? {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (stroke in strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        for (p in stroke.points) {
            val half = if (profile != null) profile.sizePx * 0.75f else p.w / 2f
            if (p.x - half < minX) minX = p.x - half
            if (p.y - half < minY) minY = p.y - half
            if (p.x + half > maxX) maxX = p.x + half
            if (p.y + half > maxY) maxY = p.y + half
        }
    }
    return if (minX > maxX) null else Bounds(minX, minY, maxX, maxY)
}

/** [points] 전체를 감싸는 최소 사각형 — 라쏘 폴리곤 자체의 내보내기 영역을 계산하는 데 쓴다
 *  ([contentBounds]와 달리 폭 개념이 없는 순수 점 목록용). */
fun pointsBounds(points: List<Point>): Bounds? {
    if (points.isEmpty()) return null
    var minX = points[0].x; var minY = points[0].y
    var maxX = points[0].x; var maxY = points[0].y
    for (p in points) {
        if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
    }
    return Bounds(minX, minY, maxX, maxY)
}

/** 라쏘 다각형 [lasso] 안에 [stroke]의 점이 하나라도 들어가면 선택된 것으로 본다 — 손가락으로
 *  정확히 완전히 두르기는 어려우니, 살짝 스치기만 해도 선택되는 관대한 판정. */
fun strokeTouchesLasso(stroke: VectorStroke, lasso: List<Point>): Boolean =
    stroke.points.any { pointInPolygon(it.x, it.y, lasso) }

/** 두 선분(p1→p2, p3→p4)이 교차하면 그 교차 좌표, 아니면 null — 각 선분을 0~1로 매개변수화하는
 *  표준 공식(t/u)으로 판정, 그 범위(양 끝 포함) 안에서만 교차로 인정한다. 평행하거나(분모가 0에
 *  가까움) 겹쳐도 교차로 안 본다(길이가 있는 겹침은 이 스펙 범위 밖). [internal]인 이유: 이 파일
 *  안에서만 쓰지만([selfIntersectionFills]), 유닛 테스트에서 직접 검증하기 위해 `private`이
 *  아니라 `internal`로 둔다(같은 모듈의 테스트 소스셋에서 접근 가능). */
internal fun segmentIntersection(p1: VectorPoint, p2: VectorPoint, p3: VectorPoint, p4: VectorPoint): Point? {
    val d1x = p2.x - p1.x; val d1y = p2.y - p1.y
    val d2x = p4.x - p3.x; val d2y = p4.y - p3.y
    val denom = d1x * d2y - d1y * d2x
    if (kotlin.math.abs(denom) < 1e-6f) return null
    val t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
    val u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom
    if (t < 0f || t > 1f || u < 0f || u > 1f) return null
    return Point(p1.x + t * d1x, p1.y + t * d1y)
}

/** [points](획의 중심선)가 자기 자신과 교차해서 만드는 닫힌 구역들을 찾는다 — 원, 8자, 소용돌이
 *  등 손으로 닫힌 도형을 그리면 그 구역마다 다각형 하나씩 반환한다(자기 교차가 없으면 빈 목록).
 *  점 목록을 순서대로 훑으면서, 세그먼트 i가 그 이전(바로 앞 세그먼트는 끝점을 공유하니 제외)의
 *  아직 안 쓰인 세그먼트 j와 교차하면 그 두 교차점 사이(정확히는 교차점부터 세그먼트 i의 교차점
 *  까지, 원래 점들은 [j+1..i]) 를 다각형 하나로 만들고, 다음 탐색은 세그먼트 i부터 이어서(j 이전
 *  구간은 이미 다 쓰였으니 건너뛰고) 계속한다 — 그래서 소용돌이처럼 교차가 여러 번 있어도 구간이
 *  겹치지 않게 각각 한 번씩만 다각형이 된다. 각 다각형은 [교차점, 원래 점들...]로만 이뤄지며(첫
 *  점을 마지막에 다시 안 붙임), 마지막 점에서 다시 그 교차점으로 닫는 건 렌더러의 `path.close()`가
 *  담당한다([strokeOutline]과 같은 컨벤션) — 교차점이 세그먼트 i 위의 한 점이라 이 마지막 변은
 *  세그먼트 i의 일부 구간일 뿐이라 항상 유효하다. */
fun selfIntersectionFills(points: List<VectorPoint>): List<List<Point>> {
    if (points.size < 4) return emptyList()
    val result = mutableListOf<List<Point>>()
    var startSeg = 0
    var i = 0
    while (i < points.size - 1) {
        var found: Pair<Int, Point>? = null
        for (j in startSeg until i - 1) {
            val hit = segmentIntersection(points[j], points[j + 1], points[i], points[i + 1])
            if (hit != null) { found = j to hit; break }
        }
        if (found != null) {
            val (j, hit) = found
            val polygon = mutableListOf(hit)
            for (k in (j + 1)..i) polygon.add(Point(points[k].x, points[k].y))
            result.add(polygon)
            startSeg = i
        }
        i++
    }
    return result
}
