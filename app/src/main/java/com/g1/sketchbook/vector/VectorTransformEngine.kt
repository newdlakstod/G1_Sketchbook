package com.g1.sketchbook.vector

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SelectionTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val pivot: Point,
)

/** Commits a viewport transform into object data. ObjectTransform is intentionally reset after
 * every command, so persisted selected objects have stable IDs and no transform accumulation. */
fun transformObjects(
    objects: List<VectorObject>,
    selectedIds: Set<String>,
    transform: SelectionTransform,
): List<VectorObject> = objects.map { objectPath ->
    if (objectPath.id !in selectedIds) objectPath else transformObject(objectPath, transform)
}

private fun transformObject(objectPath: VectorObject, selection: SelectionTransform): VectorObject = when (objectPath) {
    is LegacyStrokeObject -> objectPath.copy(stroke = objectPath.stroke.copy(
        points = objectPath.stroke.points.map { point ->
            val mapped = transformSelection(Point(point.x, point.y), selection)
            VectorPoint(mapped.x, mapped.y, safeMultiply(point.w, selectionWidthFactor(selection)))
        },
        strokeWidthPx = safeMultiply(objectPath.stroke.strokeWidthPx, selectionWidthFactor(selection)),
    ))
    is EditablePathObject -> {
        val existing = sanitizeTransform(objectPath.transform)
        val existingWidthFactor = objectWidthFactor(existing.scaleX, existing.scaleY)
        val selectionWidthFactor = selectionWidthFactor(selection)
        objectPath.copy(
            geometry = objectPath.geometry.copy(points = objectPath.geometry.points.map { point ->
                val existingPoint = transformPoint(Point(point.x, point.y), existing)
                val mapped = transformSelection(existingPoint, selection)
                PathPoint(mapped.x, mapped.y, point.widthFactor.takeIf { it.isFinite() } ?: 0f)
            }),
            appearance = objectPath.appearance.copy(stroke = objectPath.appearance.stroke.copy(
                width = safeMultiply(safeMultiply(objectPath.appearance.stroke.width, existingWidthFactor), selectionWidthFactor),
            )),
            transform = ObjectTransform(),
        )
    }
}

private fun transformSelection(point: Point, transform: SelectionTransform): Point {
    val pivot = Point(transform.pivot.x.takeIf { it.isFinite() } ?: 0f, transform.pivot.y.takeIf { it.isFinite() } ?: 0f)
    val scaleX = transform.scaleX.takeIf { it.isFinite() } ?: 1f
    val scaleY = transform.scaleY.takeIf { it.isFinite() } ?: 1f
    val angle = transform.rotationDegrees.takeIf { it.isFinite() }?.let { Math.toRadians(it.toDouble()) } ?: 0.0
    val localX = safeFloat((point.x - pivot.x).toDouble() * scaleX)
    val localY = safeFloat((point.y - pivot.y).toDouble() * scaleY)
    val rotatedX = safeFloat(localX.toDouble() * cos(angle) - localY.toDouble() * sin(angle))
    val rotatedY = safeFloat(localX.toDouble() * sin(angle) + localY.toDouble() * cos(angle))
    return Point(
        safeFloat(rotatedX.toDouble() + pivot.x + (transform.translateX.takeIf { it.isFinite() } ?: 0f)),
        safeFloat(rotatedY.toDouble() + pivot.y + (transform.translateY.takeIf { it.isFinite() } ?: 0f)),
    )
}

private fun selectionWidthFactor(transform: SelectionTransform): Float = objectWidthFactor(
    transform.scaleX.takeIf { it.isFinite() } ?: 1f,
    transform.scaleY.takeIf { it.isFinite() } ?: 1f,
)

private fun objectWidthFactor(scaleX: Float, scaleY: Float): Float =
    sqrt(abs(scaleX.toDouble() * scaleY.toDouble())).takeIf { it.isFinite() }?.toFloat() ?: 1f

private fun safeMultiply(value: Float, factor: Float): Float = safeFloat(value.toDouble() * factor)

private fun safeFloat(value: Double): Float = when {
    value.isNaN() -> 0f
    value > Float.MAX_VALUE -> Float.MAX_VALUE
    value < -Float.MAX_VALUE -> -Float.MAX_VALUE
    else -> value.toFloat()
}

private fun sanitizeTransform(transform: ObjectTransform): ObjectTransform = ObjectTransform(
    translateX = transform.translateX.takeIf { it.isFinite() } ?: 0f,
    translateY = transform.translateY.takeIf { it.isFinite() } ?: 0f,
    scaleX = transform.scaleX.takeIf { it.isFinite() } ?: 1f,
    scaleY = transform.scaleY.takeIf { it.isFinite() } ?: 1f,
    rotationDegrees = transform.rotationDegrees.takeIf { it.isFinite() } ?: 0f,
)
