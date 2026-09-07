package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Test

class PagePixelSizeTest {
    @Test(expected = IllegalArgumentException::class)
    fun pageAspectRejectsRatiosThatWouldExceedTheFixedCameraClipRange() {
        requireSupportedPageAspectRatio(0.24f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageAspectRejectsImplausiblyWideRatios() {
        requireSupportedPageAspectRatio(4.01f)
    }

    @Test
    fun requestedSizeAndWorldHeightFollowLandscapePageAspect() {
        assertEquals(
            PagePixelSize(1920, 1080),
            requestedPagePixelSize(
                viewportWidth = 1920,
                viewportHeight = 1080,
                layoutMode = PageLayoutMode.SinglePage,
                maxTextureSize = 4096,
                pageAspectRatio = 16f / 9f,
            ),
        )
        assertEquals(1.125f, pageWorldHeight(pageWidth = 2f, pageAspectRatio = 16f / 9f))
    }

    @Test
    fun portraitRequestsTheVisiblePagePixelSize() {
        assertEquals(
            PagePixelSize(1248, 1664),
            requestedPagePixelSize(1248, 1972, PageLayoutMode.SinglePage, 4096),
        )
    }

    @Test
    fun landscapeRequestsOneHalfOfTheSpread() {
        assertEquals(
            PagePixelSize(1224, 1632),
            requestedPagePixelSize(2448, 1848, PageLayoutMode.TwoPageSpread, 4096),
        )
    }

    @Test
    fun textureLimitPreservesTheThreeToFourPageAspect() {
        assertEquals(
            PagePixelSize(1536, 2048),
            requestedPagePixelSize(5000, 5000, PageLayoutMode.SinglePage, 2048),
        )
    }
}
