package com.gdo.pagecurl

import android.graphics.Bitmap
import org.junit.Assert.assertThrows
import org.junit.Test

class BitmapSourceContractTest {
    @Test(expected = IllegalArgumentException::class)
    fun bitmapListRequiresAtLeastOnePage() {
        BitmapListPageSource(emptyList())
    }

    @Test
    fun bitmapListSnapshotsCallerListStructure() {
        val callerPages = mutableListOf<Bitmap?>(null)
        @Suppress("UNCHECKED_CAST")
        val source = BitmapListPageSource(callerPages as List<Bitmap>)

        callerPages += null

        assertThrows(IllegalArgumentException::class.java) {
            source.getPageBitmap(pageIndex = 1, requestedWidth = 1, requestedHeight = 1)
        }
    }
}
