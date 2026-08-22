package com.g1.sketchbook.readmode.curl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class ShadowStrip private constructor(val segments: Int) {
    val vertexCount = (segments + 1) * 2
    val indexCount = segments * 6
    val positions = FloatArray(vertexCount * 3)
    val alpha = FloatArray(vertexCount)
    val indices = IntArray(indexCount)
    val positionBuffer: FloatBuffer = floatBuffer(positions.size)
    val alphaBuffer: FloatBuffer = floatBuffer(alpha.size)
    val indexBuffer: IntBuffer = intBuffer(indices.size)

    init {
        require(segments > 0)
        var indexOffset = 0
        for (segment in 0 until segments) {
            val innerBottom = segment * 2
            val outerBottom = innerBottom + 1
            val innerTop = innerBottom + 2
            val outerTop = innerBottom + 3
            indices[indexOffset++] = innerBottom
            indices[indexOffset++] = outerBottom
            indices[indexOffset++] = innerTop
            indices[indexOffset++] = innerTop
            indices[indexOffset++] = outerBottom
            indices[indexOffset++] = outerTop
        }
        indexBuffer.put(indices)
        indexBuffer.position(0)
    }

    fun upload() {
        positionBuffer.position(0)
        positionBuffer.put(positions)
        positionBuffer.position(0)
        alphaBuffer.position(0)
        alphaBuffer.put(alpha)
        alphaBuffer.position(0)
    }

    companion object {
        fun create(segments: Int = 120): ShadowStrip = ShadowStrip(segments)

        private fun floatBuffer(size: Int): FloatBuffer = ByteBuffer
            .allocateDirect(size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        private fun intBuffer(size: Int): IntBuffer = ByteBuffer
            .allocateDirect(size * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
    }
}
