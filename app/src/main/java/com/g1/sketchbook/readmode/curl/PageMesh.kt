package com.g1.sketchbook.readmode.curl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.sqrt

class PageMesh(
    val columns: Int = 80,
    val rows: Int = 120,
) {
    init {
        require(columns > 0 && rows > 0) { "Mesh dimensions must be positive" }
    }

    val vertexCount: Int = (columns + 1) * (rows + 1)
    val indexCount: Int = columns * rows * 6

    val positions = FloatArray(vertexCount * POSITION_COMPONENTS)
    val uvs = FloatArray(vertexCount * UV_COMPONENTS)
    val shade = FloatArray(vertexCount) { 1f }
    val side = FloatArray(vertexCount)
    val indices = IntArray(indexCount)

    val positionBuffer: FloatBuffer = floatBuffer(positions.size)
    val uvBuffer: FloatBuffer = floatBuffer(uvs.size)
    val shadeBuffer: FloatBuffer = floatBuffer(shade.size)
    val sideBuffer: FloatBuffer = floatBuffer(side.size)
    val indexBuffer: IntBuffer = intBuffer(indices.size)

    init {
        buildTopology()
        resetFlat(width = 2f, height = 2f)
        uvBuffer.put(uvs).position(0)
        indexBuffer.put(indices).position(0)
    }

    fun resetFlat(width: Float, height: Float) {
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val vertex = row * (columns + 1) + column
                val positionOffset = vertex * POSITION_COMPONENTS
                val uvOffset = vertex * UV_COMPONENTS

                positions[positionOffset] = (u - 0.5f) * width
                positions[positionOffset + 1] = (v - 0.5f) * height
                positions[positionOffset + 2] = 0f
                uvs[uvOffset] = u
                uvs[uvOffset + 1] = 1f - v
                shade[vertex] = 1f
                side[vertex] = 0f
            }
        }
        uploadMutableAttributes()
    }

    fun uploadMutableAttributes() {
        positionBuffer.position(0)
        positionBuffer.put(positions)
        positionBuffer.position(0)
        shadeBuffer.position(0)
        shadeBuffer.put(shade)
        shadeBuffer.position(0)
        sideBuffer.position(0)
        sideBuffer.put(side)
        sideBuffer.position(0)
    }

    fun maximumHorizontalEdgeLength(): Float {
        var maximum = 0f
        for (row in 0..rows) {
            for (column in 0 until columns) {
                val left = (row * (columns + 1) + column) * POSITION_COMPONENTS
                val right = left + POSITION_COMPONENTS
                val dx = positions[right] - positions[left]
                val dy = positions[right + 1] - positions[left + 1]
                val dz = positions[right + 2] - positions[left + 2]
                maximum = maxOf(maximum, sqrt(dx * dx + dy * dy + dz * dz))
            }
        }
        return maximum
    }

    private fun buildTopology() {
        var indexOffset = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val bottomLeft = row * (columns + 1) + column
                val bottomRight = bottomLeft + 1
                val topLeft = bottomLeft + columns + 1
                val topRight = topLeft + 1

                indices[indexOffset++] = bottomLeft
                indices[indexOffset++] = bottomRight
                indices[indexOffset++] = topLeft
                indices[indexOffset++] = topLeft
                indices[indexOffset++] = bottomRight
                indices[indexOffset++] = topRight
            }
        }
    }

    private fun floatBuffer(size: Int): FloatBuffer = ByteBuffer
        .allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private fun intBuffer(size: Int): IntBuffer = ByteBuffer
        .allocateDirect(size * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer()

    private companion object {
        const val POSITION_COMPONENTS = 3
        const val UV_COMPONENTS = 2
    }
}
