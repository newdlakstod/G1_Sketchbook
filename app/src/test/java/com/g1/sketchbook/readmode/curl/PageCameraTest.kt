package com.g1.sketchbook.readmode.curl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageCameraTest {
    @Test
    fun raisedCurlProjectsLargerThanFlatPaper() {
        val camera = PageCamera()
        val distance = camera.distanceFor(
            pageWidth = 2f,
            pageHeight = 8f / 3f,
            viewportAspect = 0.75f,
        )

        assertEquals(1f, camera.apparentScale(distance, depth = 0f), 1e-5f)
        assertTrue(camera.apparentScale(distance, depth = 0.48f) >= 1.15f)
    }
}
