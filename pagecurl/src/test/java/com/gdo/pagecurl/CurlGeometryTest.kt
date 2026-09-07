package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertTrue("flat region missing", stats.flatVertices > 0)
        assertTrue("curved region missing", stats.curvedVertices > 0)
        assertTrue("flipped region missing", stats.flippedVertices > 0)
        assertTrue("curl has no depth", stats.maxDepth > 0.02f)
        assertEquals(mesh.vertexCount, stats.flatVertices + stats.curvedVertices + stats.flippedVertices)
    }

    @Test
    fun backwardMiddleDragContainsAllCurlRegions() {
        val mesh = PageMesh()

        val stats = geometry.deform(
            mesh,
            Vec2(0.38f, 0.35f),
            2f,
            8f / 3f,
            PageTurnDirection.Backward,
        )

        assertTrue(stats.flatVertices > 0)
        assertTrue(stats.curvedVertices > 0)
        assertTrue(stats.flippedVertices > 0)
        assertTrue(mesh.maximumHorizontalEdgeLength() < 0.04f)
    }

    @Test
    fun backwardGeometryMirrorsPositionsWithoutChangingUvs() {
        val forward = PageMesh(columns = 8, rows = 8)
        val backward = PageMesh(columns = 8, rows = 8)

        geometry.deform(forward, Vec2(0.4f, 0.5f), 2f, 8f / 3f, PageTurnDirection.Forward)
        geometry.deform(backward, Vec2(0.4f, 0.5f), 2f, 8f / 3f, PageTurnDirection.Backward)

        for (row in 0..forward.rows) {
            for (column in 0..forward.columns) {
                val forwardVertex = row * (forward.columns + 1) + (forward.columns - column)
                val backwardVertex = row * (backward.columns + 1) + column
                assertEquals(
                    -forward.positions[forwardVertex * 3],
                    backward.positions[backwardVertex * 3],
                    1e-4f,
                )
                assertEquals(column.toFloat() / backward.columns, backward.uvs[backwardVertex * 2], 0f)
            }
        }
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
    fun lowerCornerDragMakesTheLowerOuterEdgeLead() {
        val mesh = PageMesh(columns = 40, rows = 40)

        geometry.deform(mesh, Vec2(0.4f, 0.2f), pageWidth = 2f, pageHeight = 3f)

        assertTrue(bottomRightX(mesh) < topRightX(mesh))
    }

    @Test
    fun upperCornerDragMakesTheUpperOuterEdgeLead() {
        val mesh = PageMesh(columns = 40, rows = 40)

        geometry.deform(mesh, Vec2(0.4f, 0.8f), pageWidth = 2f, pageHeight = 3f)

        assertTrue(topRightX(mesh) < bottomRightX(mesh))
    }

    @Test
    fun lowerGrabKeepsTheLowerEdgeLeadingAfterTheFingerMovesUp() {
        val mesh = PageMesh(columns = 40, rows = 40)
        val currentFinger = Vec2(0.4f, 0.8f)

        val parameters = geometry.parameters(currentFinger, grabAnchorY = 0.2f)
        geometry.deform(
            mesh,
            currentFinger,
            pageWidth = 2f,
            pageHeight = 3f,
            grabAnchorY = 0.2f,
        )

        assertEquals(0.8f, parameters.axisPoint.y, 0f)
        assertTrue(parameters.axisDirection.x > 0f)
        assertTrue(bottomRightX(mesh) < topRightX(mesh))
    }

    @Test
    fun upperGrabKeepsTheUpperEdgeLeadingAfterTheFingerMovesDown() {
        val mesh = PageMesh(columns = 40, rows = 40)
        val currentFinger = Vec2(0.4f, 0.2f)

        val parameters = geometry.parameters(currentFinger, grabAnchorY = 0.8f)
        geometry.deform(
            mesh,
            currentFinger,
            pageWidth = 2f,
            pageHeight = 3f,
            grabAnchorY = 0.8f,
        )

        assertEquals(0.2f, parameters.axisPoint.y, 0f)
        assertTrue(parameters.axisDirection.x < 0f)
        assertTrue(topRightX(mesh) < bottomRightX(mesh))
    }

    private fun bottomRightX(mesh: PageMesh): Float = mesh.positions[mesh.columns * 3]

    private fun topRightX(mesh: PageMesh): Float =
        mesh.positions[(mesh.rows * (mesh.columns + 1) + mesh.columns) * 3]

}
