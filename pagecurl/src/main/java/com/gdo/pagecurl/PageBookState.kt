package com.gdo.pagecurl

internal enum class PageTurnDirection { Forward, Backward }

internal data class PageBookState(val focusedPageIndex: Int = 0, val pageCount: Int = 3) {
    init {
        require(pageCount >= 1)
        require(focusedPageIndex in 0 until pageCount)
    }

    fun canTurn(layoutMode: PageLayoutMode, direction: PageTurnDirection): Boolean =
        when (layoutMode) {
            PageLayoutMode.SinglePage -> when (direction) {
                PageTurnDirection.Forward -> focusedPageIndex < pageCount - 1
                PageTurnDirection.Backward -> focusedPageIndex > 0
            }
            PageLayoutMode.TwoPageSpread -> when (direction) {
                PageTurnDirection.Forward -> {
                    val front = if (spreadStart() == 0) 0 else spreadStart() + 1
                    front + 1 < pageCount
                }
                PageTurnDirection.Backward -> spreadStart() > 0
            }
        }

    fun preferredDirection(layoutMode: PageLayoutMode): PageTurnDirection =
        if (canTurn(layoutMode, PageTurnDirection.Forward)) {
            PageTurnDirection.Forward
        } else {
            PageTurnDirection.Backward
        }

    fun complete(layoutMode: PageLayoutMode, direction: PageTurnDirection): PageBookState = when {
        !canTurn(layoutMode, direction) -> this
        layoutMode == PageLayoutMode.SinglePage && direction == PageTurnDirection.Forward ->
            copy(focusedPageIndex = focusedPageIndex + 1)
        layoutMode == PageLayoutMode.SinglePage -> copy(focusedPageIndex = focusedPageIndex - 1)
        direction == PageTurnDirection.Forward -> {
            val start = spreadStart()
            copy(focusedPageIndex = if (start == 0) 1 else start + 2)
        }
        else -> {
            val start = spreadStart()
            copy(focusedPageIndex = if (start == 1) 0 else start - 2)
        }
    }

    fun afterSettling(
        layoutMode: PageLayoutMode,
        direction: PageTurnDirection,
        phase: CurlPhase,
    ): PageBookState = if (phase == CurlPhase.Completed) complete(layoutMode, direction) else this

    fun stableSelection(layoutMode: PageLayoutMode): StableLayoutSelection = when (layoutMode) {
        PageLayoutMode.SinglePage -> StableLayoutSelection(
            StaticPageSelection(PageContent.page(focusedPageIndex), PageSlot.Center, false),
            null,
        )
        PageLayoutMode.TwoPageSpread -> {
            val start = spreadStart()
            if (start == 0) {
                StableLayoutSelection(
                    StaticPageSelection(PageContent.Blank, PageSlot.Left, false),
                    StaticPageSelection(contentOrBlank(0), PageSlot.Right, false),
                )
            } else {
                StableLayoutSelection(
                    StaticPageSelection(contentOrBlank(start), PageSlot.Left, false),
                    StaticPageSelection(contentOrBlank(start + 1), PageSlot.Right, false),
                )
            }
        }
    }

    fun turnSelection(
        layoutMode: PageLayoutMode,
        direction: PageTurnDirection,
    ): PageTurnRenderSelection {
        require(canTurn(layoutMode, direction))
        if (layoutMode == PageLayoutMode.SinglePage) {
            val destination = complete(layoutMode, direction).focusedPageIndex
            return PageTurnRenderSelection(
                PageContent.page(focusedPageIndex),
                PageContent.page(destination),
                StaticPageSelection(PageContent.page(destination), PageSlot.Center, true),
                null,
                PageSlot.Center,
            )
        }
        val start = spreadStart()
        return if (direction == PageTurnDirection.Forward) {
            val front = if (start == 0) 0 else start + 1
            PageTurnRenderSelection(
                contentOrBlank(front),
                contentOrBlank(front + 1),
                StaticPageSelection(
                    if (start == 0) PageContent.Blank else contentOrBlank(start),
                    PageSlot.Left,
                    false,
                ),
                StaticPageSelection(contentOrBlank(front + 2), PageSlot.Right, true),
                PageSlot.Right,
            )
        } else {
            PageTurnRenderSelection(
                contentOrBlank(start),
                contentOrBlank(start - 1),
                StaticPageSelection(contentOrBlank(start - 2), PageSlot.Left, true),
                StaticPageSelection(contentOrBlank(start + 1), PageSlot.Right, false),
                PageSlot.Left,
            )
        }
    }

    fun reset(): PageBookState = copy(focusedPageIndex = 0)

    private fun spreadStart(): Int = when {
        focusedPageIndex == 0 -> 0
        focusedPageIndex % 2 == 0 -> focusedPageIndex - 1
        else -> focusedPageIndex
    }

    private fun contentOrBlank(index: Int): PageContent =
        if (index in 0 until pageCount) PageContent.page(index) else PageContent.Blank
}
