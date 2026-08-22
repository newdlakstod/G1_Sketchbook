package com.g1.sketchbook.readmode.input

import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp

class DragInterpreter(
    private val edgeFraction: Float = 0.15f,
    private val completeThreshold: Float = 0.5f,
    private val flingThreshold: Float = -1.2f,
) {
    init {
        require(edgeFraction in 0f..1f)
        require(completeThreshold in 0f..1f)
        require(flingThreshold < 0f)
    }

    fun canStart(normalized: Vec2): Boolean = normalized.x >= 1f - edgeFraction

    fun normalized(x: Float, y: Float, width: Int, height: Int): Vec2 {
        require(width > 0 && height > 0) { "Surface dimensions must be positive" }
        return Vec2(
            x = clamp(x / width),
            y = 1f - clamp(y / height),
        )
    }

    fun shouldComplete(progress: Float, velocityX: Float): Boolean =
        progress >= completeThreshold || velocityX <= flingThreshold
}
