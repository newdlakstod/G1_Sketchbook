package com.gdo.pagecurl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlRendererAdmissionTest {
    @Test
    fun openUiPreflightWithDestinationAllowsPointerOwnership() {
        assertTrue(
            canOwnDragStart(
                rendererReady = true,
                admissionOpen = true,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun closedUiPreflightRejectsPointerOwnershipWithDestination() {
        assertFalse(
            canOwnDragStart(
                rendererReady = true,
                admissionOpen = false,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun openUiPreflightStillRejectsUnavailableSpreadDirection() {
        assertFalse(
            canOwnDragStart(
                rendererReady = true,
                admissionOpen = true,
                bookState = PageBookState(focusedPageIndex = 1),
                layoutMode = PageLayoutMode.TwoPageSpread,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun notReadyUiPreflightRejectsPointerOwnership() {
        assertFalse(
            canOwnDragStart(
                rendererReady = false,
                admissionOpen = true,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun idleStoppedRendererWithDestinationAcceptsDragStart() {
        assertTrue(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = false,
                curlPhase = CurlPhase.Idle,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun runningAnimatorRejectsDragStart() {
        assertFalse(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = true,
                curlPhase = CurlPhase.Idle,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun nonIdleCurlRejectsDragStart() {
        assertFalse(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = false,
                curlPhase = CurlPhase.SettlingToNext,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun missingDestinationRejectsDragStart() {
        assertFalse(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = false,
                curlPhase = CurlPhase.Idle,
                bookState = PageBookState(focusedPageIndex = 3, pageCount = 4),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun notReadyGlRendererRejectsDragStart() {
        assertFalse(
            canAdmitDragStart(
                rendererReady = false,
                animatorRunning = false,
                curlPhase = CurlPhase.Idle,
                bookState = PageBookState(focusedPageIndex = 0),
                layoutMode = PageLayoutMode.SinglePage,
                direction = PageTurnDirection.Forward,
            ),
        )
    }

    @Test
    fun insideSpreadRejectsForwardAndAcceptsBackward() {
        val inside = PageBookState(focusedPageIndex = 3, pageCount = 5)

        assertFalse(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = false,
                curlPhase = CurlPhase.Idle,
                bookState = inside,
                layoutMode = PageLayoutMode.TwoPageSpread,
                direction = PageTurnDirection.Forward,
            ),
        )
        assertTrue(
            canAdmitDragStart(
                rendererReady = true,
                animatorRunning = false,
                curlPhase = CurlPhase.Idle,
                bookState = inside,
                layoutMode = PageLayoutMode.TwoPageSpread,
                direction = PageTurnDirection.Backward,
            ),
        )
    }
}
