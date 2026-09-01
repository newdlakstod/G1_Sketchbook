package com.g1.sketchbook.vector

/**
 * A collision-safe cache key. The revisions are canonical value snapshots, not hashes: two
 * distinct editable objects can therefore never share a key merely because their hash codes do.
 * Viewport state is intentionally absent because all stored geometry is in document coordinates.
 */
data class VectorGeometryCacheKey(
    val objectId: String,
    val objectRevision: String,
    val profileId: String?,
    val profileRevision: String,
)

data class CachedRenderedObjectGeometry(
    val geometry: RenderedObjectGeometry,
    val bounds: Bounds?,
)

fun geometryCacheKey(objectPath: EditablePathObject, profile: VectorBrushProfile?): VectorGeometryCacheKey =
    VectorGeometryCacheKey(
        objectId = objectPath.id,
        objectRevision = editableSnapshot(objectPath),
        profileId = objectPath.appearance.brush.profileId,
        profileRevision = profileSnapshot(profile),
    )

/** Stores geometry and its bounds atomically so draw, export and selection agree on final extents. */
class VectorGeometryCache {
    private val entries = mutableMapOf<VectorGeometryCacheKey, CachedRenderedObjectGeometry>()

    val size: Int get() = entries.size

    fun geometryFor(
        objectPath: EditablePathObject,
        profiles: Map<String, VectorBrushProfile>,
    ): CachedRenderedObjectGeometry {
        val profile = objectPath.appearance.brush.profileId?.let(profiles::get)
        val key = geometryCacheKey(objectPath, profile)
        // An object replacement invalidates only that object's old snapshot. A changed profile
        // invalidates only cached objects which reference that same profile id.
        entries.keys.removeAll { existing ->
            (existing.objectId == key.objectId && existing != key) ||
                (key.profileId != null && existing.profileId == key.profileId && existing.profileRevision != key.profileRevision)
        }
        return entries.getOrPut(key) {
            val geometry = renderedPolygons(objectPath, profiles)
            CachedRenderedObjectGeometry(geometry, boundsFor(geometry))
        }
    }

    fun clear() = entries.clear()
}

private fun editableSnapshot(objectPath: EditablePathObject): String = buildString {
    appendText(objectPath.id)
    append(objectPath.geometry.closed)
    objectPath.geometry.points.forEach { point ->
        append(point.x.toBits()).append(',').append(point.y.toBits()).append(',').append(point.widthFactor.toBits()).append(';')
    }
    val appearance = objectPath.appearance
    append(appearance.fill.enabled).append(',').append(appearance.fill.color).append(';')
    append(appearance.stroke.enabled).append(',').append(appearance.stroke.color).append(',').append(appearance.stroke.width.toBits())
    append(',').append(appearance.stroke.cap.name).append(',').append(appearance.stroke.join.name).append(';')
    append(appearance.brush.kind.name).append(';').appendText(appearance.brush.profileId)
    val transform = objectPath.transform
    append(transform.translateX.toBits()).append(',').append(transform.translateY.toBits()).append(',')
    append(transform.scaleX.toBits()).append(',').append(transform.scaleY.toBits()).append(',').append(transform.rotationDegrees.toBits())
}

private fun profileSnapshot(profile: VectorBrushProfile?): String = buildString {
    when (profile) {
        null -> append("none")
        is ArtBrushProfile -> {
            append("art;"); appendText(profile.id); appendText(profile.name); appendText(profile.originalSvg)
            appendShapes(profile.shapes)
        }
        is PatternBrushProfile -> {
            append("pattern;"); appendText(profile.id); appendText(profile.name); appendText(profile.originalSvg)
            append(profile.spacingPx.toBits()).append(',').append(profile.sizePx.toBits()).append(';')
            appendShapes(profile.shapes)
        }
    }
}

private fun StringBuilder.appendShapes(shapes: List<List<Point>>) {
    append(shapes.size).append(';')
    shapes.forEach { shape ->
        append(shape.size).append(':')
        shape.forEach { point -> append(point.x.toBits()).append(',').append(point.y.toBits()).append(';') }
    }
}

private fun StringBuilder.appendText(value: String?) {
    if (value == null) append("-1:") else append(value.length).append(':').append(value)
}
