package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderPageShadowTest {
    private val activeState = CurlState.at(CurlPhase.Dragging, Vec2(0.5f, 0.5f))
    private val parameters = CurlParameters(
        axisPoint = Vec2(0.6f, 0.5f),
        axisDirection = Vec2(0f, 1f),
        radius = 0.1f,
        progress = 0.5f,
    )

    @Test
    fun activeForwardCurlProjectsShadowOntoDestinationSide() {
        val shadow = underPageShadowFor(
            state = activeState,
            parameters = parameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertTrue(shadow.width > 0f)
        assertTrue(shadow.opacity > 0f)
        assertEquals(0.4f, shadow.axisPoint.x, 1e-5f)
        assertEquals(0f, shadow.axisPoint.y, 1e-5f)
        assertEquals(1f, shadow.normal.x, 1e-5f)
        assertEquals(0f, shadow.normal.y, 1e-5f)
        assertEquals(0.15f, shadow.width, 1e-5f)
        assertEquals(0.22f, shadow.opacity, 1e-5f)
    }

    @Test
    fun backwardCurlMirrorsShadowAcrossSpine() {
        val drag = Vec2(0.5f, 0.35f)
        val realParameters = CurlGeometry().parameters(drag)
        val forward = underPageShadowFor(
            state = CurlState.at(CurlPhase.Dragging, drag),
            parameters = realParameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )
        val backward = underPageShadowFor(
            state = CurlState.at(CurlPhase.Dragging, drag),
            parameters = realParameters,
            direction = PageTurnDirection.Backward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertEquals(-forward.axisPoint.x, backward.axisPoint.x, 1e-5f)
        assertEquals(forward.axisPoint.y, backward.axisPoint.y, 1e-5f)
        assertEquals(-forward.normal.x, backward.normal.x, 1e-5f)
        assertEquals(forward.normal.y, backward.normal.y, 1e-5f)
    }

    @Test
    fun idleCurlDisablesUnderlyingPageShadow() {
        val shadow = underPageShadowFor(
            state = CurlState(),
            parameters = parameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertEquals(UnderPageShadow.Disabled, shadow)
    }

    @Test
    fun completedCurlDisablesUnderlyingPageShadow() {
        val shadow = underPageShadowFor(
            state = CurlState.at(CurlPhase.Completed, Vec2(-1f, 0.5f)),
            parameters = parameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertEquals(UnderPageShadow.Disabled, shadow)
    }

    @Test
    fun bothSettlingPhasesKeepUnderlyingPageShadowActive() {
        val completing = underPageShadowFor(
            state = CurlState.at(CurlPhase.SettlingToNext, activeState.dragPosition),
            parameters = parameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )
        val canceling = underPageShadowFor(
            state = CurlState.at(CurlPhase.SettlingToOrigin, activeState.dragPosition),
            parameters = parameters,
            direction = PageTurnDirection.Forward,
            pageWidth = 2f,
            pageHeight = 8f / 3f,
        )

        assertTrue(completing.opacity > 0f)
        assertTrue(canceling.opacity > 0f)
    }

    @Test
    fun realDragPositionsMoveTheProjectedShadow() {
        val geometry = CurlGeometry()
        val near = Vec2(0.75f, 0.5f)
        val far = Vec2(0.35f, 0.5f)
        val nearShadow = underPageShadowFor(
            CurlState.at(CurlPhase.Dragging, near),
            geometry.parameters(near),
            PageTurnDirection.Forward,
            2f,
            8f / 3f,
        )
        val farShadow = underPageShadowFor(
            CurlState.at(CurlPhase.Dragging, far),
            geometry.parameters(far),
            PageTurnDirection.Forward,
            2f,
            8f / 3f,
        )

        assertNotEquals(nearShadow.axisPoint.x, farShadow.axisPoint.x)
    }

    @Test
    fun positiveProgressWithZeroRadiusDisablesShadow() {
        val drag = Vec2(0.9995f, 0.5f)
        val realParameters = CurlGeometry().parameters(drag)

        assertTrue(realParameters.progress > 0f)
        assertEquals(0f, realParameters.radius, 0f)
        assertEquals(
            UnderPageShadow.Disabled,
            underPageShadowFor(
                CurlState.at(CurlPhase.SettlingToOrigin, drag),
                realParameters,
                PageTurnDirection.Forward,
                2f,
                8f / 3f,
            ),
        )
    }

    @Test
    fun shadowMovesWithItsTurningPageSlot() {
        val local = UnderPageShadow(Vec2(0.4f, 0.1f), Vec2(1f, 0f), 0.2f, 0.2f)

        assertEquals(1.4f, local.offsetX(PageSlot.Right.offsetX).axisPoint.x, 1e-5f)
        assertEquals(-0.6f, local.offsetX(PageSlot.Left.offsetX).axisPoint.x, 1e-5f)
    }
}
