package com.g1.sketchbook.brush

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LithoTextureTest {
    @Test
    fun sameSeedProducesSameTexture() {
        val a = generateGrainTexture(size = 32, seed = 42L, blurRadius = 0)
        val b = generateGrainTexture(size = 32, seed = 42L, blurRadius = 0)
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun zeroBlurRadiusKeepsRawNoise() {
        val size = 16
        val rnd = kotlin.random.Random(99L)
        val expected = ByteArray(size * size) { rnd.nextInt(0, 256).toByte() }
        val actual = generateGrainTexture(size = size, seed = 99L, blurRadius = 0)
        assertEquals(expected.toList(), actual.toList())
    }

    @Test
    fun blurReducesNeighborVariance() {
        val size = 64
        val raw = generateGrainTexture(size = size, seed = 5L, blurRadius = 0)
        val blurred = generateGrainTexture(size = size, seed = 5L, blurRadius = 8)

        fun neighborVariance(bytes: ByteArray): Double {
            var sumSq = 0.0
            var n = 0
            for (y in 0 until size) {
                for (x in 0 until size - 1) {
                    val a = bytes[y * size + x].toInt() and 0xFF
                    val b = bytes[y * size + x + 1].toInt() and 0xFF
                    sumSq += (a - b).toDouble() * (a - b)
                    n++
                }
            }
            return sumSq / n
        }

        assertTrue(neighborVariance(blurred) < neighborVariance(raw))
    }

    @Test
    fun stretchContrastExpandsRangeToFull() {
        val bytes = generateGrainTexture(size = 64, seed = 11L, blurRadius = 8)
        val values = bytes.map { it.toInt() and 0xFF }
        // 블러로 좁아진 대비를 재정규화하므로, 최댓값은 255 근처까지, 최솟값은 0 근처까지
        // 다시 늘어나야 한다(안 그러면 뭉개져서 거의 안 보이게 됨).
        assertTrue(values.max() > 200)
        assertTrue(values.min() < 55)
    }
}
