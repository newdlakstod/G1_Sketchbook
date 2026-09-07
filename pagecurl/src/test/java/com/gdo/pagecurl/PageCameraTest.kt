package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCameraTest {
    @Test
    fun spreadCameraFramesFourByEightThirdsAtLandscapeAspect() {
        val camera = PageCamera()
        val distance = camera.distanceFor(4f, 8f / 3f, viewportAspect = 1.5f)

        assertEquals(1f, camera.apparentScale(distance, 0f), 1e-5f)
        assertTrue(distance < camera.farPlane)
    }

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
