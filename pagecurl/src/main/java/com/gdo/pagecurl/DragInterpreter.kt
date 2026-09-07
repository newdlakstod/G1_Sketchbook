package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import com.gdo.pagecurl.math.clamp

internal class DragInterpreter(
    private val edgeFraction: Float = 0.15f,
    private val completeThreshold: Float = 0.5f,
    private val flingThreshold: Float = -1.2f,
) {
    init {
        require(edgeFraction in 0f..1f)
        require(completeThreshold in 0f..1f)
        require(flingThreshold < 0f)
    }

    fun directionForStart(
        position: Vec2,
        layoutMode: PageLayoutMode,
        canForward: Boolean,
        canBackward: Boolean,
    ): PageTurnDirection? = when {
        position.x >= 1f - edgeWidthFraction(layoutMode) && canForward -> PageTurnDirection.Forward
        position.x <= edgeWidthFraction(layoutMode) && canBackward -> PageTurnDirection.Backward
        else -> null
    }

    fun edgeWidthFraction(layoutMode: PageLayoutMode): Float =
        edgeFraction / if (layoutMode == PageLayoutMode.TwoPageSpread) 2f else 1f

    fun toWorkingPosition(
        position: Vec2,
        layoutMode: PageLayoutMode,
        direction: PageTurnDirection,
    ): Vec2 {
        val pageLocalX = when {
            layoutMode == PageLayoutMode.SinglePage -> position.x
            direction == PageTurnDirection.Forward -> (position.x - 0.5f) * 2f
            else -> position.x * 2f
        }
        return if (direction == PageTurnDirection.Forward) {
            Vec2(pageLocalX, position.y)
        } else {
            Vec2(1f - pageLocalX, position.y)
        }
    }

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
