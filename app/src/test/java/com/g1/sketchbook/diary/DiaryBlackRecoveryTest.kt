package com.g1.sketchbook.diary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryBlackRecoveryTest {
    private val paper = 0xFFF6F1E6.toInt()
    private val black = 0xFF030303.toInt()

    @Test
    fun wideBlackBandConnectedToTheBottomEdgeIsRecoverable() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { paper }
        for (y in 5 until height) {
            for (x in 0 until width) pixels[y * width + x] = black
        }
        // 검은 손상 영역 위에 남아 있는 실제 색 그림은 복구 마스크에서 제외해야 한다.
        pixels[7 * width + 4] = 0xFFE05454.toInt()

        val mask = edgeConnectedBlackCorruptionMask(pixels, width, height)

        assertNotNull(mask)
        assertTrue(mask[9 * width])
        assertTrue(mask[6 * width + 8])
        assertFalse(mask[7 * width + 4])
        assertFalse(mask[2 * width + 4])
    }

    @Test
    fun interiorBlackDrawingIsNotTreatedAsCorruption() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { paper }
        for (y in 3..6) {
            for (x in 3..6) pixels[y * width + x] = black
        }

        assertNull(edgeConnectedBlackCorruptionMask(pixels, width, height))
    }

    @Test
    fun thinBlackBorderIsNotTreatedAsCorruption() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { paper }
        for (x in 0 until width) pixels[(height - 1) * width + x] = black

        assertNull(edgeConnectedBlackCorruptionMask(pixels, width, height))
    }
}
