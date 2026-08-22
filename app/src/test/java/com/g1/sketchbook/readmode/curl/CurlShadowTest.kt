package com.g1.sketchbook.readmode.curl

import com.g1.sketchbook.readmode.curl.math.Vec2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurlShadowTest {
    @Test
    fun activeCurlProducesBoundedSoftShadowStrip() {
        val geometry = CurlGeometry()
        val strip = ShadowStrip.create(segments = 40)

        geometry.updateShadow(
            strip = strip,
            parameters = geometry.parameters(Vec2(0.4f, 0.3f)),
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertEquals(82, strip.vertexCount)
        assertTrue(strip.positions.all(Float::isFinite))
        assertTrue(strip.alpha.all { it in 0f..0.42f })
        assertTrue(strip.alpha.any { it > 0.05f })
        assertTrue(strip.alpha.filterIndexed { index, _ -> index % 2 == 1 }.all { it == 0f })
    }

    @Test
    fun idleCurlHasTransparentShadow() {
        val geometry = CurlGeometry()
        val strip = ShadowStrip.create(segments = 8)

        geometry.updateShadow(strip, geometry.parameters(Vec2(1f, 0.5f)), 2f, 8f / 3f)

        assertTrue(strip.alpha.all { it == 0f })
    }

    @Test
    fun defaultShadowStripKeepsFacetsBelowVisibleStepSize() {
        val geometry = CurlGeometry()
        val strip = ShadowStrip.create()
        geometry.updateShadow(strip, geometry.parameters(Vec2(0.4f, 0.3f)), 2f, 8f / 3f)

        val maximum = (0 until strip.segments).maxOf { segment ->
            val lower = segment * 2 * 3
            val upper = (segment + 1) * 2 * 3
            val dx = strip.positions[upper] - strip.positions[lower]
            val dy = strip.positions[upper + 1] - strip.positions[lower + 1]
            sqrt(dx * dx + dy * dy)
        }

        assertTrue(maximum <= 0.03f, "shadow facets are visible")
    }
}
