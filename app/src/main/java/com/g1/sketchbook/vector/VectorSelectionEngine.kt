package com.g1.sketchbook.vector

import kotlin.math.max
import kotlin.math.min

private const val MIN_VIEWPORT_SCALE = 0.01f
private const val HIT_TOLERANCE_PX = 12f

/** Selection is deliberately routed through Task 7's rendered-object contracts so Basic, Art,
 * Pattern, Fill, and legacy objects all use the same visible extents as rendering and export. */
fun topmostObjectAt(
    objects: List<VectorObject>,
    point: Point,
    profiles: Map<String, VectorBrushProfile>,
    viewportScale: Float,
): VectorObject? {
    val tolerance = HIT_TOLERANCE_PX / safeViewportScale(viewportScale)
    return objects.asReversed().firstOrNull { objectPath ->
        pointInRenderedObject(point, objectPath, profiles, tolerance)
    }
}

/** Returns IDs in document order for every object whose final rendered extent touches [lasso]. */
fun objectsTouchingLasso(
    objects: List<VectorObject>,
    lasso: List<Point>,
    profiles: Map<String, VectorBrushProfile>,
    viewportScale: Float,
): Set<String> {
    if (lasso.size < 3) return emptySet()
    val lassoBounds = pointsBounds(lasso) ?: return emptySet()
    val tolerance = HIT_TOLERANCE_PX / safeViewportScale(viewportScale)
    return objects.asSequence().filter { objectPath ->
        val objectBounds = renderedObjectBounds(objectPath, profiles) ?: return@filter false
        if (!boundsTouch(objectBounds, lassoBounds)) return@filter false
        lasso.any { pointInRenderedObject(it, objectPath, profiles, tolerance) } ||
            boundsCornersAndCenter(objectBounds).any { pointInPolygon(it.x, it.y, lasso) } ||
            lasso.zipWithNext { start, end -> boundsSegmentTouches(start, end, objectBounds) }.any { it }
    }.mapTo(linkedSetOf()) { it.id }
}

fun selectionBounds(
    objects: List<VectorObject>,
    selectedIds: Set<String>,
    profiles: Map<String, VectorBrushProfile>,
): Bounds? = objects.asSequence()
    .filter { it.id in selectedIds }
    .mapNotNull { renderedObjectBounds(it, profiles) }
    .fold<Bounds, Bounds?>(null) { total, next ->
        total?.let {
            Bounds(min(it.minX, next.minX), min(it.minY, next.minY), max(it.maxX, next.maxX), max(it.maxY, next.maxY))
        } ?: next
    }

private fun safeViewportScale(scale: Float): Float = scale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(MIN_VIEWPORT_SCALE)
    ?: MIN_VIEWPORT_SCALE

private fun boundsTouch(first: Bounds, second: Bounds): Boolean =
    first.minX <= second.maxX && first.maxX >= second.minX && first.minY <= second.maxY && first.maxY >= second.minY

private fun boundsCornersAndCenter(bounds: Bounds): List<Point> = listOf(
    Point(bounds.minX, bounds.minY), Point(bounds.maxX, bounds.minY), Point(bounds.maxX, bounds.maxY), Point(bounds.minX, bounds.maxY),
    Point((bounds.minX + bounds.maxX) / 2f, (bounds.minY + bounds.maxY) / 2f),
)

private fun boundsSegmentTouches(start: Point, end: Point, bounds: Bounds): Boolean =
    segmentTouchesSegment(start, end, Point(bounds.minX, bounds.minY), Point(bounds.maxX, bounds.minY)) ||
        segmentTouchesSegment(start, end, Point(bounds.maxX, bounds.minY), Point(bounds.maxX, bounds.maxY)) ||
        segmentTouchesSegment(start, end, Point(bounds.maxX, bounds.maxY), Point(bounds.minX, bounds.maxY)) ||
        segmentTouchesSegment(start, end, Point(bounds.minX, bounds.maxY), Point(bounds.minX, bounds.minY))

private fun segmentTouchesSegment(a: Point, b: Point, c: Point, d: Point): Boolean {
    fun cross(origin: Point, first: Point, second: Point): Float =
        (first.x - origin.x) * (second.y - origin.y) - (first.y - origin.y) * (second.x - origin.x)
    val abC = cross(a, b, c); val abD = cross(a, b, d)
    val cdA = cross(c, d, a); val cdB = cross(c, d, b)
    return (abC == 0f || abD == 0f || abC > 0f != abD > 0f) && (cdA == 0f || cdB == 0f || cdA > 0f != cdB > 0f)
}
