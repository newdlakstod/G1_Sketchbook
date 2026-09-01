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
