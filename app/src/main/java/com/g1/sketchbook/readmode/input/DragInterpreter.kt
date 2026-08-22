package com.g1.sketchbook.readmode.input

import com.g1.sketchbook.readmode.curl.CurlDirection
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

    fun normalized(x: Float, y: Float, width: Int, height: Int): Vec2 {
        require(width > 0 && height > 0) { "Surface dimensions must be positive" }
        return Vec2(
            x = clamp(x / width),
            y = 1f - clamp(y / height),
        )
    }

    /** [raw] is a full-surface-normalized touch position (see [normalized]) — *not* yet scaled to
     *  the turning page's own width. In landscape only the right half of the surface is the (one)
     *  turning page, so [landscape] halves the effective edge width here and the rescale in
     *  [toWorkingPosition]; portrait's turning page already spans the whole surface, matching the
     *  original single-direction behavior exactly. Callers gate [canBackward] off entirely for
     *  landscape — the left page there never curls (see `ReadModeRenderer`'s "only the right page
     *  curls" simplification), so there is no mesh to animate a backward turn with in that mode. */
    fun directionForStart(raw: Vec2, landscape: Boolean, canForward: Boolean, canBackward: Boolean): CurlDirection? {
        val half = if (landscape) 0.5f else 1f
        return when {
            raw.x >= 1f - edgeFraction * half && canForward -> CurlDirection.Forward
            raw.x <= edgeFraction * half && canBackward -> CurlDirection.Backward
            else -> null
        }
    }

    /** Rescales a full-surface-normalized touch position into the curl math's "working space"
     *  (x=1 at the drag's starting edge, decreasing toward x=0 as the turn completes — the same
     *  convention regardless of [direction]), given the direction locked for this gesture at
     *  [directionForStart]. Forward reads the turning page's own 0..1 range left-to-right as-is;
     *  backward mirrors it (`workingX = 1 - local`) so `CurlGeometry`/`CurlState`/`CurlAnimator`
     *  never have to know which edge a drag actually started from. */
    fun toWorkingPosition(raw: Vec2, direction: CurlDirection, landscape: Boolean): Vec2 {
        val half = if (landscape) 0.5f else 1f
        val originX = if (landscape && direction == CurlDirection.Forward) 0.5f else 0f
        val local = (raw.x - originX) / half
        val workingX = if (direction == CurlDirection.Forward) local else 1f - local
        return Vec2(workingX, raw.y)
    }

    fun shouldComplete(progress: Float, velocityX: Float): Boolean =
        progress >= completeThreshold || velocityX <= flingThreshold
}
