package com.g1.sketchbook.readmode.curl.math

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)

    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun length(): Float = sqrt(dot(this))

    fun normalized(fallback: Vec2 = LEFT): Vec2 {
        val length = length()
        return if (length > 1e-6f) this * (1f / length) else fallback
    }

    fun perpendicular(): Vec2 = Vec2(-y, x)

    companion object {
        val LEFT = Vec2(-1f, 0f)
    }
}
