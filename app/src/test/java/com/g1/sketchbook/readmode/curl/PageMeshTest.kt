package com.g1.sketchbook.readmode.curl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.sqrt

class PageMeshTest {
    @Test
    fun fortyByFortyCellsProduceExpectedIndexedTopology() {
        val mesh = PageMesh(columns = 40, rows = 40)

        assertEquals(1_681, mesh.vertexCount)
        assertEquals(9_600, mesh.indexCount)
        assertEquals(0, mesh.indices.minOrNull())
        assertEquals(1_680, mesh.indices.maxOrNull())
        assertEquals(0f, mesh.uvs.minOrNull()!!, 0f)
        assertEquals(1f, mesh.uvs.maxOrNull()!!, 0f)
    }

    @Test
    fun resetFlatSpansRequestedPageWithoutDepth() {
        val mesh = PageMesh(columns = 2, rows = 2)

        mesh.resetFlat(width = 2f, height = 3f)

        val xValues = mesh.positions.filterIndexed { index, _ -> index % 3 == 0 }
        val yValues = mesh.positions.filterIndexed { index, _ -> index % 3 == 1 }
        val zValues = mesh.positions.filterIndexed { index, _ -> index % 3 == 2 }
        assertEquals(-1f, xValues.minOrNull()!!, 0f)
        assertEquals(1f, xValues.maxOrNull()!!, 0f)
        assertEquals(-1.5f, yValues.minOrNull()!!, 0f)
        assertEquals(1.5f, yValues.maxOrNull()!!, 0f)
        assertTrue(zValues.all { it == 0f })
    }

    @Test
    fun defaultRenderMeshKeepsVerticalFacetsBelowVisibleStepSize() {
        val mesh = PageMesh()
        mesh.resetFlat(width = 2f, height = 8f / 3f)
        val rowStride = (mesh.columns + 1) * 3

        val maximum = (0 until mesh.rows).maxOf { row ->
            val lower = row * rowStride
            val upper = lower + rowStride
            val dx = mesh.positions[upper] - mesh.positions[lower]
            val dy = mesh.positions[upper + 1] - mesh.positions[lower + 1]
            val dz = mesh.positions[upper + 2] - mesh.positions[lower + 2]
            sqrt(dx * dx + dy * dy + dz * dz)
        }

        assertTrue(maximum <= 0.03f, "vertical page facets are visible")
    }
}
