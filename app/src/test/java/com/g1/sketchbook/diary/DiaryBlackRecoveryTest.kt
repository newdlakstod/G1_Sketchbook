package com.g1.sketchbook.diary

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiaryBlackRecoveryTest {
    private val paper = 0xFFF6F1E6.toInt()
    private val black = 0xFF030303.toInt()
    private val red = 0xFFE05454.toInt()

    @Test
    fun previewReplacesOnlyTheBlackBandAndNeverMutatesTheStoredComposite() {
        val width = 10
        val height = 10
        val stored = IntArray(width * height) { paper }
        for (y in 5 until height) {
            for (x in 0 until width) stored[y * width + x] = black
        }
        stored[7 * width + 4] = red
        val before = stored.copyOf()
        val transparentContent = IntArray(width * height)
        transparentContent[8 * width + 5] = 0xFF202020.toInt()

        val preview = buildLegacyDiaryPreviewPixels(
            storedComposite = stored,
            paperPixels = IntArray(width * height) { paper },
            contentPixels = transparentContent,
            width = width,
            height = height,
        )

        assertNotNull(preview)
        assertContentEquals(before, stored)
        assertEquals(paper, preview[9 * width])
        assertEquals(red, preview[7 * width + 4])
        assertEquals(0xFF202020.toInt(), preview[8 * width + 5])
    }

    @Test
    fun normalInteriorBlackDrawingDoesNotProduceAReplacementPreview() {
        val width = 10
        val height = 10
        val stored = IntArray(width * height) { paper }
        for (y in 3..6) {
            for (x in 3..6) stored[y * width + x] = black
        }

        assertNull(
            buildLegacyDiaryPreviewPixels(
                storedComposite = stored,
                paperPixels = IntArray(width * height) { paper },
                contentPixels = null,
                width = width,
                height = height,
            ),
        )
    }
}
