package com.g1.sketchbook.readmode.curl

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.tan

class PageCamera(
    val verticalFieldOfViewDegrees: Float = 45f,
    val nearPlane: Float = 0.5f,
    val farPlane: Float = 10f,
) {
    fun distanceFor(pageWidth: Float, pageHeight: Float, viewportAspect: Float): Float {
        require(pageWidth > 0f && pageHeight > 0f && viewportAspect > 0f)
        val halfFovTangent = tan(verticalFieldOfViewDegrees * PI.toFloat() / 360f)
        val verticalDistance = pageHeight * 0.5f / halfFovTangent
        val horizontalDistance = pageWidth * 0.5f / (halfFovTangent * viewportAspect)
        return max(verticalDistance, horizontalDistance) * FRAME_MARGIN
    }

    fun apparentScale(distance: Float, depth: Float): Float {
        require(distance > 0f && depth < distance)
        return distance / (distance - depth)
    }

    private companion object {
        const val FRAME_MARGIN = 1.01f
    }
}
