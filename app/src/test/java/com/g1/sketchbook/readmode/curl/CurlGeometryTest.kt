package com.g1.sketchbook.readmode.curl

import com.g1.sketchbook.readmode.curl.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CurlGeometryTest {
    private val geometry = CurlGeometry()

    @Test
    fun idleGeometryIsFiniteAndFlat() {
        val mesh = PageMesh(40, 40)

        val stats = geometry.deform(mesh, Vec2(1f, 0.5f), pageWidth = 2f, pageHeight = 3f)

        assertEquals(0f, stats.maxDepth, 1e-5f)
        assertEquals(mesh.vertexCount, stats.flatVertices)
        assertTrue(mesh.positions.all(Float::isFinite))
    }

    @Test
    fun middleDragContainsFlatCurvedAndFlippedRegions() {
        val mesh = PageMesh(40, 40)

        val stats = geometry.deform(mesh, Vec2(0.38f, 0.35f), pageWidth = 2f, pageHeight = 3f)

        assertTrue(stats.flatVertices > 0, "flat region missing")
        assertTrue(stats.curvedVertices > 0, "curved region missing")
        assertTrue(stats.flippedVertices > 0, "flipped region missing")
        assertTrue(stats.maxDepth > 0.02f, "curl has no depth")
        assertEquals(mesh.vertexCount, stats.flatVertices + stats.curvedVertices + stats.flippedVertices)
    }

    @Test
    fun cylindricalMappingDoesNotStretchHorizontalEdgesLikeRubber() {
        val mesh = PageMesh(40, 40)

        geometry.deform(mesh, Vec2(0.4f, 0.7f), pageWidth = 2f, pageHeight = 3f)

        assertTrue(mesh.maximumHorizontalEdgeLength() < 0.075f)
    }

    @Test
    fun dragHeightTiltsCurlAxis() {
        val low = geometry.parameters(Vec2(0.5f, 0.2f))
        val high = geometry.parameters(Vec2(0.5f, 0.8f))

        assertNotEquals(low.axisDirection.x, high.axisDirection.x)
        assertTrue(low.axisDirection.y > 0f)
        assertTrue(high.axisDirection.y > 0f)
    }

    @Test
    fun curvedBandHasEnoughContrastToReadAsBentPaper() {
        val mesh = PageMesh(40, 40)

        geometry.deform(mesh, Vec2(0.38f, 0.5f), pageWidth = 2f, pageHeight = 8f / 3f)

        assertTrue(mesh.shade.minOrNull()!! <= 0.55f, "fold shadow is too weak")
    }
}
