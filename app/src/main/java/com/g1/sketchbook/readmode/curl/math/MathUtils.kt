package com.g1.sketchbook.readmode.curl.math

import kotlin.math.max
import kotlin.math.min

fun clamp(value: Float, minimum: Float = 0f, maximum: Float = 1f): Float =
    max(minimum, min(maximum, value))

fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

fun smoothStep(value: Float): Float {
    val t = clamp(value)
    return t * t * (3f - 2f * t)
}
