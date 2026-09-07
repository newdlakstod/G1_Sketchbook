package com.gdo.pagecurl

import android.graphics.Bitmap

class BitmapListPageSource(pages: List<Bitmap>) : PageCurlBitmapSource {
    private val pages = pages.toList()

    init {
        require(this.pages.isNotEmpty()) { "PageCurl requires at least one page" }
    }

    override val pageCount: Int = pages.size

    override fun getPageBitmap(
        pageIndex: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap {
        require(pageIndex in pages.indices)
        return pages[pageIndex]
    }
}
