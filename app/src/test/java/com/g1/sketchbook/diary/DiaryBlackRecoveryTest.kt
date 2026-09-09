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
        // 완전 불투명 필기는 이제 paper와 Multiply로 합성된다(BrushView.exportBitmap()과 동일 블렌드)
        // — paper(0xF6F1E6) × ink(0x202020)를 채널별로 곱한 값, 0x202020 그대로 대체되지 않는다.
        assertEquals(0xFF1F1E1D.toInt(), preview[8 * width + 5])
    }

    @Test
    fun opaqueContentIsMultipliedOverPaperNotFlatBlended() {
        val width = 4
        val height = 4
        val stored = IntArray(width * height) { black }
        val paperPixels = IntArray(width * height) { paper }
        val contentPixels = IntArray(width * height) { 0xFF804020.toInt() } // opaque mid-tone ink

        val preview = buildLegacyDiaryPreviewPixels(stored, paperPixels, contentPixels, width, height)

        assertNotNull(preview)
        // Multiply(paper, ink) per channel, not a plain replace with the ink color.
        val expectedRed = (0xF6 * 0x80 + 127) / 255
        val expectedGreen = (0xF1 * 0x40 + 127) / 255
        val expectedBlue = (0xE6 * 0x20 + 127) / 255
        val expected = 0xFF000000.toInt() or (expectedRed shl 16) or (expectedGreen shl 8) or expectedBlue
        assertEquals(expected, preview!![0])
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
