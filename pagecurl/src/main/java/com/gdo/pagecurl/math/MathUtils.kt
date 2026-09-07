package com.gdo.pagecurl.math

import kotlin.math.max
import kotlin.math.min

internal fun clamp(value: Float, minimum: Float = 0f, maximum: Float = 1f): Float =
    max(minimum, min(maximum, value))

internal fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

internal fun smoothStep(value: Float): Float {
    val t = clamp(value)
    return t * t * (3f - 2f * t)
}
