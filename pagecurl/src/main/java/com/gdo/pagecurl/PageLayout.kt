package com.gdo.pagecurl

internal enum class PageLayoutMode {
    SinglePage,
    TwoPageSpread;

    fun frameWidth(pageWidth: Float): Float =
        if (this == SinglePage) pageWidth else pageWidth * 2f
}

internal data class PageContent(val pageIndex: Int?) {
    companion object {
        val Blank = PageContent(null)

        fun page(index: Int): PageContent {
            require(index >= 0)
            return PageContent(index)
        }
    }

    val textureIndex: Int? get() = pageIndex
}

internal enum class PageSlot(val offsetX: Float, val gutterSide: Float) {
    Center(0f, 0f),
    Left(-1f, -1f),
    Right(1f, 1f),
}

internal data class StaticPageSelection(
    val content: PageContent,
    val slot: PageSlot,
    val receivesMovingShadow: Boolean,
)

internal data class StableLayoutSelection(
    val firstStatic: StaticPageSelection,
    val secondStatic: StaticPageSelection?,
)

internal data class PageTurnRenderSelection(
    val front: PageContent,
    val back: PageContent,
    val firstStatic: StaticPageSelection,
    val secondStatic: StaticPageSelection?,
    val turningSlot: PageSlot,
)

internal fun PageTurnRenderSelection.requiredPageIndices(): Set<Int> = listOfNotNull(
    front.pageIndex,
    back.pageIndex,
    firstStatic.content.pageIndex,
    secondStatic?.content?.pageIndex,
).toSet()
