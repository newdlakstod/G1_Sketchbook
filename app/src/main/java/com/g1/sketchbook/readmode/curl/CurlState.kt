package com.g1.sketchbook.readmode.curl

import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp

enum class CurlPhase {
    Idle,
    Dragging,
    SettlingToNext,
    SettlingToOrigin,
    Completed,
}

data class CurlState(
    val phase: CurlPhase = CurlPhase.Idle,
    val dragPosition: Vec2 = Vec2(1f, 0.5f),
    val progress: Float = 0f,
) {
    val drawsTurningPage: Boolean
        get() = phase != CurlPhase.Completed

    companion object {
        fun completionTarget(y: Float): Vec2 = Vec2(-1f, clamp(y))

        fun at(phase: CurlPhase, dragPosition: Vec2): CurlState = CurlState(
            phase = phase,
            dragPosition = dragPosition,
            progress = clamp(1f - dragPosition.x),
        )
    }
}
