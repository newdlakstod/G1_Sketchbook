package com.g1.sketchbook.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class RenderRoute { BASIC, ART, PATTERN }

/** Final document-coordinate shapes. Fill is separate because an open path is only virtually
 * closed for filling; every stroke shape remains the actual expanded brush geometry. */
data class RenderedObjectGeometry(
    val fill: List<Point>,
    val strokeShapes: List<List<Point>>,
)

fun renderRoute(brush: BrushStyle, profiles: Map<String, VectorBrushProfile>): RenderRoute {
    val profile = brush.profileId?.takeIf { it.isNotBlank() }?.let(profiles::get)
    return when {
        brush.kind == VectorBrushKind.ART && profile is ArtBrushProfile && profile.isUsableArtProfile() -> RenderRoute.ART
        brush.kind == VectorBrushKind.PATTERN && profile is PatternBrushProfile && profile.isUsablePatternProfile() -> RenderRoute.PATTERN
        else -> RenderRoute.BASIC
    }
}

/** One pure routing entry point for every editable object consumer. Invalid typed profile data
 * deliberately takes the Basic route and never changes the persisted profile id. */
fun renderedPolygons(
    objectPath: EditablePathObject,
    profiles: Map<String, VectorBrushProfile>,
): RenderedObjectGeometry {
    val appearance = objectPath.appearance
    val fill = if (appearance.fill.enabled) fillPolygon(appearanceSafeGeometry(objectPath.geometry)) else emptyList()
    val profile = appearance.brush.profileId?.let(profiles::get)
    val localStrokeShapes = if (!appearance.stroke.enabled) {
        emptyList()
    } else when (renderRoute(appearance.brush, profiles)) {
        RenderRoute.BASIC -> listOf(basicStrokeOutline(appearanceSafeGeometry(objectPath.geometry), appearance.stroke))
        RenderRoute.ART -> (profile as? ArtBrushProfile)?.let { mapArtBrush(it, appearanceSafeGeometry(objectPath.geometry), appearance.stroke) }.orEmpty()
        RenderRoute.PATTERN -> (profile as? PatternBrushProfile)?.let { mapPatternBrush(it, appearanceSafeGeometry(objectPath.geometry), appearance.stroke) }.orEmpty()
    }
    return RenderedObjectGeometry(
        fill = transformShape(fill, objectPath.transform),
        strokeShapes = localStrokeShapes.map { transformShape(it, objectPath.transform) }.filter { it.isNotEmpty() },
    )
}

fun mapPatternBrush(profile: PatternBrushProfile, geometry: PathGeometry, stroke: StrokeStyle): List<List<Point>> {
    val points = geometry.points.filter { it.x.isFinite() && it.y.isFinite() && it.widthFactor.isFinite() }
    if (points.size < 2) return emptyList()
    return stampPolygons(profile, points.map { VectorPoint(it.x, it.y, stroke.width * it.widthFactor.coerceAtLeast(0f)) })
}

fun renderedObjectBounds(objectPath: VectorObject, profiles: Map<String, VectorBrushProfile>): Bounds? = when (objectPath) {
    is EditablePathObject -> boundsFor(renderedPolygons(objectPath, profiles))
    is LegacyStrokeObject -> boundsFor(legacyRenderedGeometry(objectPath, profiles))
}

/** Hit testing uses the same expanded output as draw/export. For a visually thin open path, the
 * caller's document-space tolerance remains effective even when its expanded polygon is empty. */
fun pointInRenderedObject(
    point: Point,
    objectPath: VectorObject,
    profiles: Map<String, VectorBrushProfile>,
    tolerance: Float = 4f,
): Boolean = when (objectPath) {
    is EditablePathObject -> pointInEditableGeometry(point, objectPath, profiles, tolerance)
    is LegacyStrokeObject -> pointInLegacyGeometry(point, objectPath, profiles, tolerance)
}

/** Draws a v2 document while preserving every legacy stroke's v1 draw path and mixed ordering. */
fun drawVectorDocument(
    canvas: Canvas,
    document: VectorDocument,
    profiles: Map<String, VectorBrushProfile> = emptyMap(),
    cache: VectorGeometryCache = VectorGeometryCache(),
) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    for (objectPath in document.objects) {
        when (objectPath) {
            is LegacyStrokeObject -> drawVectorPage(canvas, VectorPage(listOf(objectPath.stroke)), legacyStampBrushes(profiles))
            is EditablePathObject -> {
                val geometry = cache.geometryFor(objectPath, profiles).geometry
                if (geometry.fill.size >= 3) {
                    fillPaint.color = objectPath.appearance.fill.color.toInt()
                    canvas.drawPolygon(geometry.fill, fillPaint)
                }
                if (objectPath.appearance.stroke.enabled) {
                    fillPaint.color = objectPath.appearance.stroke.color.toInt()
                    geometry.strokeShapes.filter { it.size >= 3 }.forEach { canvas.drawPolygon(it, fillPaint) }
                }
            }
        }
    }
}

private fun Canvas.drawPolygon(points: List<Point>, paint: Paint) {
    val path = Path()
    points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
    path.close()
    drawPath(path, paint)
}

internal fun boundsFor(geometry: RenderedObjectGeometry): Bounds? =
    pointsBounds(geometry.fill + geometry.strokeShapes.flatten())

private fun pointInEditableGeometry(point: Point, objectPath: EditablePathObject, profiles: Map<String, VectorBrushProfile>, tolerance: Float): Boolean {
    val geometry = renderedPolygons(objectPath, profiles)
    if (geometry.fill.size >= 3 && pointInPolygon(point.x, point.y, geometry.fill)) return true
    if (geometry.strokeShapes.any { it.size >= 3 && pointInPolygon(point.x, point.y, it) }) return true
    if (!objectPath.appearance.stroke.enabled || renderRoute(objectPath.appearance.brush, profiles) != RenderRoute.BASIC) return false
    val segments = basicStrokeCenterSegments(appearanceSafeGeometry(objectPath.geometry))
        .map { (start, end) -> transformPoint(start, objectPath.transform) to transformPoint(end, objectPath.transform) }
    return segments.any { (start, end) -> distanceToSegment(point, start, end) <= tolerance.coerceAtLeast(0f) }
}

private fun legacyRenderedGeometry(objectPath: LegacyStrokeObject, profiles: Map<String, VectorBrushProfile>): RenderedObjectGeometry {
    val stroke = objectPath.stroke
    val profile = stroke.brushProfileId?.let { legacyStampBrushes(profiles)[it] }
    val shapes = if (profile == null) listOf(strokeOutline(stroke.points, stroke.cap)) else stampPolygons(profile, stroke.points)
    return RenderedObjectGeometry(if (stroke.fillEnabled) stroke.fills.flatten() else emptyList(), shapes.filter { it.isNotEmpty() })
}

private fun pointInLegacyGeometry(point: Point, objectPath: LegacyStrokeObject, profiles: Map<String, VectorBrushProfile>, tolerance: Float): Boolean {
    val geometry = legacyRenderedGeometry(objectPath, profiles)
    if (geometry.fill.size >= 3 && pointInPolygon(point.x, point.y, geometry.fill)) return true
    if (geometry.strokeShapes.any { it.size >= 3 && pointInPolygon(point.x, point.y, it) }) return true
    if (objectPath.stroke.brushProfileId?.let { legacyStampBrushes(profiles)[it] } != null) return false
    return objectPath.stroke.points.zipWithNext().any { (start, end) ->
        distanceToSegment(point, Point(start.x, start.y), Point(end.x, end.y)) <= tolerance.coerceAtLeast(0f)
    }
}

private fun legacyStampBrushes(profiles: Map<String, VectorBrushProfile>): Map<String, StampBrushProfile> =
    profiles.filterValues { it is PatternBrushProfile }.mapValues { it.value as PatternBrushProfile }

private fun ArtBrushProfile.isUsableArtProfile(): Boolean {
    val points = shapes.flatten()
    return shapes.isNotEmpty() && shapes.all { it.isNotEmpty() && it.all { point -> point.x.isFinite() && point.y.isFinite() } } &&
        points.size >= 2 &&
        points.maxOf { it.x } - points.minOf { it.x } > 0.0001f
}

private fun PatternBrushProfile.isUsablePatternProfile(): Boolean =
    spacingPx.isFinite() && spacingPx > 0.0001f && sizePx.isFinite() && sizePx > 0.0001f &&
        shapes.isNotEmpty() && shapes.all { shape ->
            shape.size >= 3 && shape.all { it.x.isFinite() && it.y.isFinite() } && abs(twiceArea(shape)) > 0.0001f
        }

private fun twiceArea(shape: List<Point>): Float = shape.indices.sumOf { index ->
    val current = shape[index]; val next = shape[(index + 1) % shape.size]
    (current.x * next.y - current.y * next.x).toDouble()
}.toFloat()

private fun appearanceSafeGeometry(geometry: PathGeometry): PathGeometry = geometry.copy(
    points = geometry.points.filter { it.x.isFinite() && it.y.isFinite() && it.widthFactor.isFinite() },
)

internal fun transformPoint(point: Point, transform: ObjectTransform): Point {
    val scaleX = transform.scaleX.takeIf { it.isFinite() } ?: 1f
    val scaleY = transform.scaleY.takeIf { it.isFinite() } ?: 1f
    val angle = transform.rotationDegrees.takeIf { it.isFinite() }?.let { Math.toRadians(it.toDouble()) } ?: 0.0
    val x = point.x * scaleX
    val y = point.y * scaleY
    return Point(
        (x * cos(angle).toFloat() - y * sin(angle).toFloat()) + (transform.translateX.takeIf { it.isFinite() } ?: 0f),
        (x * sin(angle).toFloat() + y * cos(angle).toFloat()) + (transform.translateY.takeIf { it.isFinite() } ?: 0f),
    )
}

private fun transformShape(shape: List<Point>, transform: ObjectTransform): List<Point> =
    shape.map { transformPoint(it, transform) }.filter { it.x.isFinite() && it.y.isFinite() }

private fun distanceToSegment(point: Point, start: Point, end: Point): Float {
    val dx = end.x - start.x; val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0001f) return sqrt((point.x - start.x) * (point.x - start.x) + (point.y - start.y) * (point.y - start.y))
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val x = start.x + t * dx; val y = start.y + t * dy
    return sqrt((point.x - x) * (point.x - x) + (point.y - y) * (point.y - y))
}

/** [page]의 모든 획을 [canvas]에 그린다 — 지금 펜으로 그린 획은 [strokeOutline]으로 계산한 리본
 *  다각형을 [VectorStroke.color]로 항상 채우고([VectorStroke.fillEnabled]와 무관 — 리본은 펜이
 *  실제로 지나간 자리라 항상 보여야 함), [VectorStroke.strokeColor]가 있으면 그 위에 폴리곤
 *  테두리를 그 색·[VectorStroke.strokeWidthPx] 굵기로 덧그린다. [VectorStroke.fillEnabled]면 그
 *  다음으로 [selfIntersectionFills]로 찾은 자기교차 폐곡선들을 [VectorStroke.fillColor]
 *  (없으면 [VectorStroke.color])로 채워 리본 위에 덧그린다 — 손으로 닫힌 도형을 그리면 그 내부가
 *  자동으로 채워지는 효과. [VectorStroke.brushProfileId]가 [stampBrushes]에서 찾아지면 위 전부
 *  대신 [stampPolygons]로 계산한 도장들을 [VectorStroke.color]로 채워 그린다(못 찾으면 지금
 *  펜으로 폴백). 그린 순서 그대로라 나중 획이 위에 덮인다. `VectorBrushView.onDraw`와 썸네일
 *  렌더링([renderVectorPage])이 이 함수 하나를 같이 쓴다 — 그리기 중인 화면과 저장되는 썸네일이
 *  항상 같은 방식으로 그려진다. */
fun drawVectorPage(canvas: Canvas, page: VectorPage, stampBrushes: Map<String, StampBrushProfile> = emptyMap()) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (stroke in page.strokes) {
        val profile = stroke.brushProfileId?.let { stampBrushes[it] }
        if (profile != null) {
            fillPaint.color = stroke.color.toInt()
            for (shape in stampPolygons(profile, stroke.points)) {
                if (shape.isEmpty()) continue
                val path = Path()
                shape.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            continue
        }
        val outline = strokeOutline(stroke.points, stroke.cap)
        if (outline.isEmpty()) continue
        val path = Path()
        outline.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        fillPaint.color = stroke.color.toInt()
        canvas.drawPath(path, fillPaint)
        stroke.strokeColor?.let { sc ->
            strokePaint.color = sc.toInt()
            strokePaint.strokeWidth = stroke.strokeWidthPx
            canvas.drawPath(path, strokePaint)
        }
        if (stroke.fillEnabled) {
            fillPaint.color = (stroke.fillColor ?: stroke.color).toInt()
            for (region in stroke.fills) {
                if (region.isEmpty()) continue
                val fillPath = Path()
                region.forEachIndexed { i, p -> if (i == 0) fillPath.moveTo(p.x, p.y) else fillPath.lineTo(p.x, p.y) }
                fillPath.close()
                fillPath.fillType = Path.FillType.EVEN_ODD
                canvas.drawPath(fillPath, fillPaint)
            }
        }
    }
}

private const val PREVIEW_PADDING_RATIO = 0.08f

/** 목록/캐러셀 미리보기용 — [page]를 [sizePx]×[sizePx] 흰 배경 비트맵으로 렌더링한다. 캔버스가
 *  무한이든 커스텀이든 상관없이, 항상 [contentBounds]로 계산한 "그려진 내용의 경계상자"(스탬프
 *  브러시 획은 [stampBrushes]로 그 반경까지 포함)에 8% 여백을 더해 정사각형 안에 맞춘다(letterbox,
 *  가운데 정렬) — 캔버스 자체의 크기/경계는 이 렌더링과 무관. 빈 캔버스(경계상자 없음)는 흰 배경만
 *  있는 빈 비트맵으로 폴백. */
fun renderVectorPage(page: VectorPage, sizePx: Int, stampBrushes: Map<String, StampBrushProfile> = emptyMap()): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val bounds = contentBounds(page.strokes, stampBrushes) ?: return bmp
    val padX = bounds.width * PREVIEW_PADDING_RATIO
    val padY = bounds.height * PREVIEW_PADDING_RATIO
    val left = bounds.minX - padX; val top = bounds.minY - padY
    val w = bounds.width + padX * 2f; val h = bounds.height + padY * 2f
    val scale = min(sizePx / w, sizePx / h)
    canvas.save()
    canvas.translate((sizePx - w * scale) / 2f, (sizePx - h * scale) / 2f)
    canvas.scale(scale, scale)
    canvas.translate(-left, -top)
    drawVectorPage(canvas, page, stampBrushes)
    canvas.restore()
    return bmp
}
