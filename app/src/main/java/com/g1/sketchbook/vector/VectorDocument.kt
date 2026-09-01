package com.g1.sketchbook.vector

enum class VectorJoin { MITER, ROUND, BEVEL }
enum class VectorBrushKind { BASIC, ART, PATTERN }

data class PathPoint(val x: Float, val y: Float, val widthFactor: Float)
data class PathGeometry(val points: List<PathPoint>, val closed: Boolean = false)
data class FillStyle(val enabled: Boolean = false, val color: Long = 0xFFFFFFFF)
data class StrokeStyle(
    val enabled: Boolean = true,
    val color: Long = 0xFF172E58,
    val width: Float = 8f,
    val cap: VectorCap = VectorCap.ROUND,
    val join: VectorJoin = VectorJoin.ROUND,
)
data class BrushStyle(val kind: VectorBrushKind = VectorBrushKind.BASIC, val profileId: String? = null)
data class PathAppearance(
    val fill: FillStyle = FillStyle(),
    val stroke: StrokeStyle = StrokeStyle(),
    val brush: BrushStyle = BrushStyle(),
)
data class ObjectTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
)

sealed interface VectorObject { val id: String }

data class LegacyStrokeObject(override val id: String, val stroke: VectorStroke) : VectorObject

data class EditablePathObject(
    override val id: String,
    val geometry: PathGeometry,
    val appearance: PathAppearance,
    val transform: ObjectTransform = ObjectTransform(),
) : VectorObject

data class VectorDocument(val version: Int = 2, val objects: List<VectorObject>)

/** Stable across runs so unchanged v1 strokes retain their object IDs. */
fun stableLegacyHash(stroke: VectorStroke): Int {
    var result = 1
    fun add(value: Int) {
        result = 31 * result + value
    }

    add(stroke.color.hashCode())
    stroke.points.forEach { point ->
        add(point.x.toBits())
        add(point.y.toBits())
        add(point.w.toBits())
    }
    add(stroke.cap.name.hashCode())
    add(stroke.fillEnabled.hashCode())
    add(stroke.strokeColor?.hashCode() ?: 0)
    add(stroke.strokeWidthPx.toBits())
    add(stroke.brushProfileId?.hashCode() ?: 0)
    add(stroke.fillColor?.hashCode() ?: 0)
    return result
}

fun legacyPageAsDocument(page: VectorPage): VectorDocument = VectorDocument(
    objects = page.strokes.mapIndexed { index, stroke ->
        LegacyStrokeObject("legacy-$index-${stableLegacyHash(stroke)}", stroke)
    },
)

fun convertLegacyObject(legacy: LegacyStrokeObject): EditablePathObject {
    val stroke = legacy.stroke
    val baseWidth = stroke.points.maxOfOrNull { it.w } ?: 0f
    val geometry = PathGeometry(stroke.points.map { point ->
        PathPoint(point.x, point.y, (point.w / baseWidth).coerceIn(0.05f, 1f))
    })
    val brush = stroke.brushProfileId?.let { BrushStyle(VectorBrushKind.PATTERN, it) } ?: BrushStyle()
    return EditablePathObject(
        id = legacy.id,
        geometry = geometry,
        appearance = PathAppearance(
            fill = FillStyle(stroke.fillEnabled, stroke.fillColor ?: stroke.color),
            stroke = StrokeStyle(
                enabled = stroke.strokeColor != null,
                color = stroke.strokeColor ?: stroke.color,
                width = baseWidth,
                cap = stroke.cap,
            ),
            brush = brush,
        ),
    )
}
