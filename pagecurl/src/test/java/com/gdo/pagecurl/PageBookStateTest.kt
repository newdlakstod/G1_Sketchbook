package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBookStateTest {
    @Test
    fun coverSpreadUsesRealForwardSheetFaces() {
        val selection = PageBookState(focusedPageIndex = 0)
            .turnSelection(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward)

        assertEquals(PageContent.page(0), selection.front)
        assertEquals(PageContent.page(1), selection.back)
        assertEquals(StaticPageSelection(PageContent.Blank, PageSlot.Left, false), selection.firstStatic)
        assertEquals(StaticPageSelection(PageContent.page(2), PageSlot.Right, true), selection.secondStatic)
        assertEquals(PageSlot.Right, selection.turningSlot)
    }

    @Test
    fun insideSpreadUsesRealBackwardSheetFaces() {
        val selection = PageBookState(focusedPageIndex = 2)
            .turnSelection(PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward)

        assertEquals(PageContent.page(1), selection.front)
        assertEquals(PageContent.page(0), selection.back)
        assertEquals(StaticPageSelection(PageContent.Blank, PageSlot.Left, true), selection.firstStatic)
        assertEquals(StaticPageSelection(PageContent.page(2), PageSlot.Right, false), selection.secondStatic)
        assertEquals(PageSlot.Left, selection.turningSlot)
    }

    @Test
    fun settledSpreadsExposeBothVisiblePagesForTheGutter() {
        assertEquals(
            StableLayoutSelection(
                StaticPageSelection(PageContent.Blank, PageSlot.Left, false),
                StaticPageSelection(PageContent.page(0), PageSlot.Right, false),
            ),
            PageBookState(0).stableSelection(PageLayoutMode.TwoPageSpread),
        )
        assertEquals(
            StableLayoutSelection(
                StaticPageSelection(PageContent.page(1), PageSlot.Left, false),
                StaticPageSelection(PageContent.page(2), PageSlot.Right, false),
            ),
            PageBookState(2).stableSelection(PageLayoutMode.TwoPageSpread),
        )
    }

    @Test
    fun spreadCompletionCommitsOnlyCoverOrInsideFocus() {
        assertEquals(1, PageBookState(0).complete(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward).focusedPageIndex)
        assertEquals(0, PageBookState(2).complete(PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward).focusedPageIndex)
    }

    @Test
    fun rotatingPageThreeToSpreadAndBackPreservesExactFocus() {
        val state = PageBookState(focusedPageIndex = 2)
        assertEquals(PageTurnDirection.Backward, state.preferredDirection(PageLayoutMode.TwoPageSpread))
        assertEquals(2, state.focusedPageIndex)
    }

    @Test
    fun portraitTurnRendersCurrentFrontAndDestinationOnBackAndUnderneath() {
        val selection = PageBookState(focusedPageIndex = 1)
            .turnSelection(PageLayoutMode.SinglePage, PageTurnDirection.Forward)

        assertEquals(PageContent.page(1), selection.front)
        assertEquals(PageContent.page(2), selection.back)
        assertEquals(PageContent.page(2), selection.firstStatic.content)
        assertEquals(PageSlot.Center, selection.turningSlot)
    }

    @Test
    fun onlyCompletedSettleCommitsTheTurn() {
        val middle = PageBookState(focusedPageIndex = 1)

        assertEquals(
            2,
            middle.afterSettling(PageLayoutMode.SinglePage, PageTurnDirection.Forward, CurlPhase.Completed).focusedPageIndex,
        )
        assertEquals(
            0,
            middle.afterSettling(PageLayoutMode.SinglePage, PageTurnDirection.Backward, CurlPhase.Completed).focusedPageIndex,
        )
        CurlPhase.entries
            .filterNot { it == CurlPhase.Completed }
            .forEach { phase ->
                assertEquals(
                    "phase $phase must not commit",
                    1,
                    middle.afterSettling(PageLayoutMode.SinglePage, PageTurnDirection.Forward, phase).focusedPageIndex,
                )
            }
    }

    @Test
    fun advancesAndReturnsAcrossThreePages() {
        var state = PageBookState()
        state = state.complete(PageLayoutMode.SinglePage, PageTurnDirection.Forward)
        assertEquals(1, state.focusedPageIndex)
        state = state.complete(PageLayoutMode.SinglePage, PageTurnDirection.Forward)
        assertEquals(2, state.focusedPageIndex)
        state = state.complete(PageLayoutMode.SinglePage, PageTurnDirection.Backward)
        assertEquals(1, state.focusedPageIndex)
        state = state.complete(PageLayoutMode.SinglePage, PageTurnDirection.Backward)
        assertEquals(0, state.focusedPageIndex)
    }

    @Test
    fun boundariesDoNotExposeInvalidDestinations() {
        val first = PageBookState(0)
        val last = PageBookState(2)
        assertFalse(first.canTurn(PageLayoutMode.SinglePage, PageTurnDirection.Backward))
        assertFalse(last.canTurn(PageLayoutMode.SinglePage, PageTurnDirection.Forward))
        assertEquals(0, first.complete(PageLayoutMode.SinglePage, PageTurnDirection.Backward).focusedPageIndex)
        assertEquals(2, last.complete(PageLayoutMode.SinglePage, PageTurnDirection.Forward).focusedPageIndex)
    }

    @Test
    fun resetReturnsToFirstPage() {
        assertEquals(0, PageBookState(2).reset().focusedPageIndex)
    }

    @Test
    fun fivePagesAdvanceAcrossEveryPortraitPage() {
        var state = PageBookState(focusedPageIndex = 0, pageCount = 5)
        repeat(4) { state = state.complete(PageLayoutMode.SinglePage, PageTurnDirection.Forward) }
        assertEquals(4, state.focusedPageIndex)
        assertFalse(state.canTurn(PageLayoutMode.SinglePage, PageTurnDirection.Forward))
    }

    @Test
    fun fivePagesExposeRealSheetFacesAcrossThreeSpreads() {
        val middle = PageBookState(focusedPageIndex = 1, pageCount = 5)
        assertEquals(
            StableLayoutSelection(
                StaticPageSelection(PageContent.page(1), PageSlot.Left, false),
                StaticPageSelection(PageContent.page(2), PageSlot.Right, false),
            ),
            middle.stableSelection(PageLayoutMode.TwoPageSpread),
        )
        val forward = middle.turnSelection(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward)
        assertEquals(PageContent.page(2), forward.front)
        assertEquals(PageContent.page(3), forward.back)
        assertEquals(PageContent.page(4), forward.secondStatic?.content)
        assertEquals(3, middle.complete(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward).focusedPageIndex)
    }

    @Test
    fun transitionWarmupIncludesEveryIndexedSurface() {
        val transition = PageBookState(focusedPageIndex = 1, pageCount = 5)
            .turnSelection(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward)

        assertEquals(setOf(1, 2, 3, 4), transition.requiredPageIndices())
    }

    @Test
    fun evenLastPageLandsBesideBlankPaper() {
        val last = PageBookState(focusedPageIndex = 3, pageCount = 4)
            .stableSelection(PageLayoutMode.TwoPageSpread)
        assertEquals(PageContent.page(3), last.firstStatic.content)
        assertEquals(PageContent.Blank, last.secondStatic?.content)
    }

    @Test
    fun twoPagesTurnFromCoverToFinalSpreadAndStopForward() {
        val cover = PageBookState(pageCount = 2)
        val turn = cover.turnSelection(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward)
        assertEquals(PageContent.page(0), turn.front)
        assertEquals(PageContent.page(1), turn.back)
        assertEquals(PageContent.Blank, turn.secondStatic?.content)

        val final = cover.complete(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward)
        assertEquals(1, final.focusedPageIndex)
        assertFalse(final.canTurn(PageLayoutMode.TwoPageSpread, PageTurnDirection.Forward))
        assertTrue(final.canTurn(PageLayoutMode.TwoPageSpread, PageTurnDirection.Backward))
    }

    @Test
    fun onePageHasNoTurnInEitherLayout() {
        val only = PageBookState(pageCount = 1)
        PageLayoutMode.entries.forEach { layout ->
            PageTurnDirection.entries.forEach { direction ->
                assertFalse(only.canTurn(layout, direction))
            }
        }
    }
}
