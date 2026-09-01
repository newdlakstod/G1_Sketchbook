package com.g1.sketchbook.vector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Returns the polygon used for fill. Open paths are closed in this returned value only. */
fun fillPolygon(geometry: PathGeometry): List<Point> {
    if (geometry.points.size < 3) return emptyList()
    val polygon = geometry.points.map { Point(it.x, it.y) }.toMutableList()
    if (polygon.first() != polygon.last()) polygon += polygon.first()
    return polygon
}

/** The actual center-line segments used for a Basic stroke; only a closed model adds its final edge. */
fun basicStrokeCenterSegments(geometry: PathGeometry): List<Pair<Point, Point>> {
    val points = usablePoints(geometry)
    if (points.size < 2) return emptyList()
    val segments = points.zipWithNext().map { (a, b) -> pointOf(a) to pointOf(b) }.toMutableList()
    if (geometry.closed && points.size > 2) segments += pointOf(points.last()) to pointOf(points.first())
    return segments
}

/**
 * Builds a finite outline for the Basic brush. Width factors are multiplied by [StrokeStyle.width];
 * joins are made from offset center-line edges, while caps are appended only for an open path.
 */
fun basicStrokeOutline(geometry: PathGeometry, stroke: StrokeStyle): List<Point> {
    val points = usablePoints(geometry)
    if (points.size < 2) return emptyList()

    val closed = geometry.closed && points.size > 2
    val width = stroke.width.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val tangents = List(if (closed) points.size else points.size - 1) { index ->
        val start = points[index]
        val end = points[(index + 1) % points.size]
        unitTangent(start, end)
    }
    if (tangents.any { it == null }) return emptyList()

    val left = mutableListOf<Point>()
    val right = mutableListOf<Point>()
    for (index in points.indices) {
        val point = points[index]
        val half = width * point.widthFactor.coerceAtLeast(0f) / 2f
        if (!closed && index == 0) {
            val normal = normalOf(tangents.first()!!)
            addDistinct(left, offset(point, normal, half))
            addDistinct(right, offset(point, normal, -half))
        } else if (!closed && index == points.lastIndex) {
            val normal = normalOf(tangents.last()!!)
            addDistinct(left, offset(point, normal, half))
            addDistinct(right, offset(point, normal, -half))
        } else {
            val incoming = tangents[(index - 1 + tangents.size) % tangents.size]!!
            val outgoing = tangents[index % tangents.size]!!
            joinBoundary(point, incoming, outgoing, half, 1f, stroke.join).forEach { addDistinct(left, it) }
            joinBoundary(point, incoming, outgoing, half, -1f, stroke.join).forEach { addDistinct(right, it) }
        }
    }

    val outline = mutableListOf<Point>()
    left.forEach { addDistinct(outline, it) }
    if (!closed) {
        val endTangent = tangents.last()!!
        val endNormal = normalOf(endTangent)
        capArc(pointOf(points.last()), width * points.last().widthFactor.coerceAtLeast(0f) / 2f, endNormal, endTangent, stroke.cap)
            .forEach { addDistinct(outline, it) }
    }
    right.asReversed().forEach { addDistinct(outline, it) }
    if (!closed) {
        val startTangent = tangents.first()!!
        val startNormal = normalOf(startTangent)
        capArc(pointOf(points.first()), width * points.first().widthFactor.coerceAtLeast(0f) / 2f, -startNormal, -startTangent, stroke.cap)
            .forEach { addDistinct(outline, it) }
    }
    return outline.filter { it.x.isFinite() && it.y.isFinite() }
}

private fun usablePoints(geometry: PathGeometry): List<PathPoint> {
    val result = mutableListOf<PathPoint>()
    for (point in geometry.points) {
        if (!point.x.isFinite() || !point.y.isFinite()) continue
        val finiteWidthFactor = point.widthFactor.takeIf { it.isFinite() } ?: 0f
        val normalized = point.copy(widthFactor = finiteWidthFactor)
        if (result.lastOrNull()?.let { it.x == normalized.x && it.y == normalized.y } != true) result += normalized
    }
    if (geometry.closed && result.size > 1 && result.first().let { first -> result.last().x == first.x && result.last().y == first.y }) {
        result.removeAt(result.lastIndex)
    }
    return result
}

private fun pointOf(point: PathPoint) = Point(point.x, point.y)

private fun unitTangent(start: PathPoint, end: PathPoint): Point? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    return if (length > 0.0001f && length.isFinite()) Point(dx / length, dy / length) else null
}

private fun normalOf(tangent: Point) = Point(-tangent.y, tangent.x)
private operator fun Point.unaryMinus() = Point(-x, -y)
private fun offset(point: PathPoint, normal: Point, distance: Float) = Point(point.x + normal.x * distance, point.y + normal.y * distance)

private fun joinBoundary(
    point: PathPoint,
    incoming: Point,
    outgoing: Point,
    half: Float,
    side: Float,
    join: VectorJoin,
): List<Point> {
    val previous = offset(point, normalOf(incoming), half * side)
    val next = offset(point, normalOf(outgoing), half * side)
    val cross = incoming.x * outgoing.y - incoming.y * outgoing.x
    if (abs(cross) < 0.0001f) return if (previous == next) listOf(previous) else listOf(previous, next)

    val outerSide = if (cross > 0f) -1f else 1f
    val intersection = offsetLineIntersection(previous, incoming, next, outgoing)
    if (side != outerSide) return listOf(intersection ?: previous)

    return when (join) {
        VectorJoin.BEVEL -> listOf(previous, next)
        VectorJoin.MITER -> {
            val miter = intersection
            if (miter != null && distance(miter, pointOf(point)) <= max(half * 4f, 0.0001f)) listOf(miter) else listOf(previous, next)
        }
        VectorJoin.ROUND -> roundJoin(pointOf(point), previous, next, cross)
    }
}

private fun offsetLineIntersection(a: Point, directionA: Point, b: Point, directionB: Point): Point? {
    val denominator = directionA.x * directionB.y - directionA.y * directionB.x
    if (abs(denominator) < 0.0001f) return null
    val deltaX = b.x - a.x
    val deltaY = b.y - a.y
    val t = (deltaX * directionB.y - deltaY * directionB.x) / denominator
    val point = Point(a.x + directionA.x * t, a.y + directionA.y * t)
    return point.takeIf { it.x.isFinite() && it.y.isFinite() }
}

private fun roundJoin(center: Point, start: Point, end: Point, turn: Float): List<Point> {
    val radius = distance(start, center)
    if (radius < 0.0001f) return listOf(start)
    val startAngle = atan2(start.y - center.y, start.x - center.x)
    var sweep = atan2(end.y - center.y, end.x - center.x) - startAngle
    if (turn > 0f) while (sweep < 0f) sweep += (2f * PI.toFloat())
    else while (sweep > 0f) sweep -= (2f * PI.toFloat())
    val steps = max(1, kotlin.math.ceil(abs(sweep) / (PI.toFloat() / 4f)).toInt())
    return (0..steps).map { index ->
        val angle = startAngle + sweep * index / steps
        Point(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
    }
}

private fun distance(a: Point, b: Point): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun addDistinct(points: MutableList<Point>, point: Point) {
    if (points.lastOrNull() != point) points += point
}
