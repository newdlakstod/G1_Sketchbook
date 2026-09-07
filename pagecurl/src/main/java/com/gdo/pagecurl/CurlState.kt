package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import com.gdo.pagecurl.math.clamp

internal enum class CurlPhase {
    Idle,
    Dragging,
    SettlingToNext,
    SettlingToOrigin,
    Completed,
}

internal data class CurlState(
    val phase: CurlPhase = CurlPhase.Idle,
    val dragPosition: Vec2 = Vec2(1f, 0.5f),
    val grabAnchorY: Float = 0.5f,
    val progress: Float = 0f,
) {
    val drawsTurningPage: Boolean
        get() = phase == CurlPhase.Dragging ||
            phase == CurlPhase.SettlingToNext ||
            phase == CurlPhase.SettlingToOrigin

    companion object {
        fun completionTarget(y: Float): Vec2 = Vec2(-1f, clamp(y))

        fun at(
            phase: CurlPhase,
            dragPosition: Vec2,
            grabAnchorY: Float = dragPosition.y,
        ): CurlState = CurlState(
            phase = phase,
            dragPosition = dragPosition,
            grabAnchorY = clamp(grabAnchorY),
            progress = clamp(1f - dragPosition.x),
        )
    }

    fun withDragPosition(position: Vec2): CurlState = at(
        phase = phase,
        dragPosition = position,
        grabAnchorY = grabAnchorY,
    )
}
