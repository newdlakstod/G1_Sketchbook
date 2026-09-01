package com.g1.sketchbook.vector

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Converts artwork coordinates into Art-brush coordinates once at import time. X spans the whole
 * source artwork (0..1); Y is centered and spans -1..1. A source with no horizontal extent cannot
 * describe progress along a path, so it deliberately produces no drawable shapes.
 */
fun normalizeArtBrush(profile: ArtBrushProfile): ArtBrushProfile {
    val finiteShapes = profile.shapes.mapNotNull { shape ->
        val finite = shape.filter { it.x.isFinite() && it.y.isFinite() }
        finite.takeIf { it.isNotEmpty() }
    }
    val points = finiteShapes.flatten()
    if (points.isEmpty()) return profile.copy(shapes = emptyList())

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val width = maxX - minX
    val height = maxY - minY
    if (!width.isFinite() || width <= EPSILON) return profile.copy(shapes = emptyList())
    val centerY = (minY + maxY) / 2f
    val yScale = if (height.isFinite() && height > EPSILON) 2f / height else 0f

    return profile.copy(shapes = finiteShapes.map { shape ->
        shape.map { point ->
            Point(
                ((point.x - minX) / width).safe(),
                ((point.y - centerY) * yScale).safe(),
            )
        }
    })
}

/**
 * Deforms every source vertex exactly once. Call [normalizeArtBrush] when importing SVG artwork;
 * this function expects the profile's X/Y coordinates already normalized and performs no stamping
 * or source repetition.
 */
fun mapArtBrush(
    profile: ArtBrushProfile,
    geometry: PathGeometry,
    stroke: StrokeStyle,
): List<List<Point>> {
    val width = stroke.width.takeIf { it.isFinite() && it > EPSILON } ?: return emptyList()
    val sampler = ArcLengthSampler(geometry)
    if (!sampler.isUsable) return emptyList()

    return profile.shapes.mapNotNull { shape ->
        val mapped = shape.mapNotNull { source ->
            if (!source.x.isFinite() || !source.y.isFinite()) return@mapNotNull null
            val sample = sampler.sample(source.x.coerceIn(0f, 1f)) ?: return@mapNotNull null
            val distance = source.y * width * sample.widthFactor / 2f
            Point(
                (sample.x + sample.normalX * distance).safe(),
                (sample.y + sample.normalY * distance).safe(),
            ).takeIf { it.x.isFinite() && it.y.isFinite() }
        }
        mapped.takeIf { it.isNotEmpty() }
    }
}

private const val EPSILON = 0.0001f

private data class ArcSample(
    val x: Float,
    val y: Float,
    val tangentX: Float,
    val tangentY: Float,
    val widthFactor: Float,
) {
    val normalX: Float get() = -tangentY
    val normalY: Float get() = tangentX
}

/** Finite arc-length sampler for both open and closed target geometry. */
private class ArcLengthSampler(geometry: PathGeometry) {
    private val points = cleanPathPoints(geometry)
    private val closed = geometry.closed && points.size > 2
    private val segmentCount = if (closed) points.size else (points.size - 1).coerceAtLeast(0)
    private val lengths = FloatArray(segmentCount)
    private val cumulative = FloatArray(segmentCount + 1)
    private val vertexTangents: List<Point>
    private val totalLength: Float

    init {
        for (index in 0 until segmentCount) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            lengths[index] = hypot(b.x - a.x, b.y - a.y).takeIf { it.isFinite() } ?: 0f
            cumulative[index + 1] = cumulative[index] + lengths[index]
        }
        totalLength = cumulative.lastOrNull() ?: 0f
        vertexTangents = if (totalLength > EPSILON) buildVertexTangents() else emptyList()
    }

    val isUsable: Boolean get() = totalLength > EPSILON && vertexTangents.size == points.size

    fun sample(fraction: Float): ArcSample? {
        if (!isUsable) return null
        val distance = fraction.coerceIn(0f, 1f) * totalLength
        var segment = segmentCount - 1
        for (index in 0 until segmentCount) {
            if (distance <= cumulative[index + 1] || index == segmentCount - 1) {
                segment = index
                break
            }
        }
        val length = lengths[segment]
        if (length <= EPSILON) return null
        val local = ((distance - cumulative[segment]) / length).coerceIn(0f, 1f)
        val a = points[segment]
        val b = points[(segment + 1) % points.size]
        val ta = vertexTangents[segment]
        val tb = vertexTangents[(segment + 1) % points.size]
        val tangent = normalize(lerp(ta, tb, local)) ?: segmentTangent(a, b) ?: return null
        return ArcSample(
            x = lerp(a.x, b.x, local).safe(),
            y = lerp(a.y, b.y, local).safe(),
            tangentX = tangent.x,
            tangentY = tangent.y,
            widthFactor = lerp(a.widthFactor, b.widthFactor, local).coerceAtLeast(0f).safe(),
        )
    }

    private fun buildVertexTangents(): List<Point> = points.indices.map { index ->
        val previous = if (index == 0) {
            if (closed) points.last() else points[0]
        } else points[index - 1]
        val next = if (index == points.lastIndex) {
            if (closed) points.first() else points.last()
        } else points[index + 1]
        val incoming = segmentTangent(previous, points[index])
        val outgoing = segmentTangent(points[index], next)
        when {
            incoming != null && outgoing != null -> normalize(Point(incoming.x + outgoing.x, incoming.y + outgoing.y)) ?: outgoing
            outgoing != null -> outgoing
            incoming != null -> incoming
            else -> Point(1f, 0f)
        }
    }
}

private fun cleanPathPoints(geometry: PathGeometry): List<PathPoint> {
    val result = mutableListOf<PathPoint>()
    geometry.points.forEach { point ->
        if (!point.x.isFinite() || !point.y.isFinite()) return@forEach
        val width = point.widthFactor.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        // Discard sub-epsilon repeats as well as exact duplicates. Otherwise a source vertex at
        // fraction zero could land on an unusably short first segment and get dropped.
        if (result.lastOrNull()?.let { previous ->
                hypot(point.x - previous.x, point.y - previous.y) <= EPSILON
            } != true) {
            result += PathPoint(point.x, point.y, width)
        }
    }
    if (geometry.closed && result.size > 1 && result.first().let { it.x == result.last().x && it.y == result.last().y }) {
        result.removeAt(result.lastIndex)
    }
    return result
}

private fun segmentTangent(a: PathPoint, b: PathPoint): Point? = normalize(Point(b.x - a.x, b.y - a.y))

private fun normalize(point: Point): Point? {
    val length = sqrt(point.x * point.x + point.y * point.y)
    return if (length.isFinite() && length > EPSILON) Point(point.x / length, point.y / length) else null
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun lerp(a: Point, b: Point, t: Float): Point = Point(lerp(a.x, b.x, t), lerp(a.y, b.y, t))
private fun Float.safe(): Float = if (isFinite()) this else 0f
