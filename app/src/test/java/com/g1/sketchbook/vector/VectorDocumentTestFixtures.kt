package com.g1.sketchbook.vector

internal fun editablePath(id: String = "path", width: Float = 8f, brush: BrushStyle = BrushStyle()) =
    EditablePathObject(
        id,
        PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f))),
        PathAppearance(stroke = StrokeStyle(width = width), brush = brush),
    )

internal fun editableLine(id: String, start: Point, end: Point, width: Float = 8f) =
    EditablePathObject(
        id,
        PathGeometry(listOf(PathPoint(start.x, start.y, 1f), PathPoint(end.x, end.y, 1f))),
        PathAppearance(stroke = StrokeStyle(width = width)),
    )

internal fun editableRect(id: String, left: Float, top: Float, right: Float, bottom: Float) =
    EditablePathObject(
        id,
        PathGeometry(
            listOf(
                PathPoint(left, top, 1f), PathPoint(right, top, 1f),
                PathPoint(right, bottom, 1f), PathPoint(left, bottom, 1f),
            ),
            closed = true,
        ),
        PathAppearance(fill = FillStyle(true, 0xFFFFFFFF), stroke = StrokeStyle(enabled = false)),
    )

internal fun legacyLine(id: String, width: Float = 4f) = LegacyStrokeObject(
    id,
    VectorStroke(0xFF000000, listOf(VectorPoint(0f, 0f, width), VectorPoint(10f, 0f, width))),
)

internal fun appearance(width: Float = 8f) = PathAppearance(stroke = StrokeStyle(width = width))
internal fun legacyPage() = VectorPage(listOf((legacyLine("legacy") as LegacyStrokeObject).stroke))
internal fun twoStrokeLegacyPage() = VectorPage(listOf(legacyLine("a").stroke, legacyLine("b").stroke))
