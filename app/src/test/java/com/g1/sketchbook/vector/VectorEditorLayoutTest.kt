package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorEditorLayoutTest {
    @Test fun wideLandscapeUsesRightPanel() {
        assertEquals(PanelPlacement.RIGHT, choosePanelPlacement(900f, 600f))
    }

    @Test fun portraitAndNarrowLandscapeUseBottomPanel() {
        assertEquals(PanelPlacement.BOTTOM, choosePanelPlacement(700f, 1000f))
        assertEquals(PanelPlacement.BOTTOM, choosePanelPlacement(700f, 400f))
    }

    @Test fun rightInspectorIsCappedAtThirtyTwoPercentAnd336Dp() {
        assertEquals(288f, rightInspectorWidthDp(900f))
        assertEquals(336f, rightInspectorWidthDp(2000f))
        assertTrue(bottomPanelInitialFraction() in 0f..1f)
    }
}
