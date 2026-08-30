package com.g1.sketchbook.vector

import kotlin.math.sqrt

/** 2D 점 하나 — `android.graphics.PointF`를 안 쓰는 이유는 이 파일이 Android 의존 없는 순수
 *  Kotlin이라 로컬 유닛 테스트(JVM)에서 그대로 돌아가야 하기 때문. */
data class Point(val x: Float, val y: Float)

/** 획의 점 목록(중심선 + 지점별 굵기)을 "굵기가 변하는 리본" 모양의 채워진 다각형 외곽선으로
 *  바꾼다 — 각 점에서 진행 방향에 수직인 법선 방향으로 굵기/2만큼 오프셋한 좌표를 위/아래 경계로
 *  삼아, 위쪽 경계를 순서대로 + 아래쪽 경계를 역순으로 이어 닫힌 다각형을 만든다(획 하나 =
 *  다각형 하나, `stroke-width` 아님 — 그려질 때도, SVG로 내보낼 때도 이 모양 그대로 채워 그린다).
 *  점이 2개 미만이면(찍기만 하고 안 그은 경우) 그릴 게 없어 빈 목록. */
fun strokeOutline(points: List<VectorPoint>): List<Point> {
    if (points.size < 2) return emptyList()
    val left = ArrayList<Point>(points.size)
    val right = ArrayList<Point>(points.size)
    for (i in points.indices) {
        val p = points[i]
        val (dx, dy) = if (i < points.size - 1) {
            points[i + 1].x - p.x to points[i + 1].y - p.y
        } else {
            p.x - points[i - 1].x to p.y - points[i - 1].y
        }
        val len = sqrt(dx * dx + dy * dy)
        val (nx, ny) = if (len < 0.0001f) 0f to 0f else -dy / len to dx / len
        val half = p.w / 2f
        left.add(Point(p.x + nx * half, p.y + ny * half))
        right.add(Point(p.x - nx * half, p.y - ny * half))
    }
    return left + right.asReversed()
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
