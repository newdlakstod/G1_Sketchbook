package com.g1.sketchbook.vector

/** Shared metadata for vector brush profiles. The original SVG is retained for future editing. */
sealed interface VectorBrushProfile {
    val id: String
    val name: String
    val originalSvg: String
}

/** A single SVG artwork deformed continuously over the full path, never repeated as stamps. */
data class ArtBrushProfile(
    override val id: String,
    override val name: String,
    val shapes: List<List<Point>>,
    override val originalSvg: String = "",
) : VectorBrushProfile

/** Repeated artwork placed along a path by arc length. */
data class PatternBrushProfile(
    override val id: String,
    override val name: String,
    val shapes: List<List<Point>>,
    val spacingPx: Float = 24f,
    val sizePx: Float = 32f,
    override val originalSvg: String = "",
) : VectorBrushProfile

fun remoteBrushKind(type: String?): VectorBrushKind = when (type?.uppercase()) {
    "ART" -> VectorBrushKind.ART
    else -> VectorBrushKind.PATTERN
}
