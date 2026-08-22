package com.g1.sketchbook.readmode.curl

import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp
import com.g1.sketchbook.readmode.curl.math.lerp
import com.g1.sketchbook.readmode.curl.math.smoothStep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class CurlRegion {
    Flat,
    Curved,
    Flipped,
}

/** Which edge a turn started from. [Forward] curls the current page away from its right edge to
 *  reveal the next one (the original, only supported direction). [Backward] mirrors that: geometry
 *  is deformed in x-flipped "working space" (see [CurlGeometry.deform]) so the same math curls the
 *  page away from its *left* edge instead, revealing the previous one. */
enum class CurlDirection {
    Forward,
    Backward,
}

data class CurlParameters(
    val axisPoint: Vec2,
    val axisDirection: Vec2,
    val radius: Float,
    val progress: Float,
)

data class CurlStats(
    val flatVertices: Int,
    val curvedVertices: Int,
    val flippedVertices: Int,
    val maxDepth: Float,
)

class CurlGeometry {
    fun parameters(drag: Vec2): CurlParameters {
        val progress = clamp(1f - drag.x)
        val radius = if (progress <= IDLE_EPSILON) {
            0f
        } else {
            minOf(MAX_RADIUS, maxOf(MIN_RADIUS, progress * RADIUS_PER_PROGRESS))
        }
        val halfCircumference = PI.toFloat() * radius
        val axisX = (1f + drag.x - halfCircumference) * 0.5f
        val tilt = clamp(
            value = (drag.y - 0.5f) * AXIS_TILT_GAIN,
            minimum = -MAX_AXIS_TILT,
            maximum = MAX_AXIS_TILT,
        )
        return CurlParameters(
            axisPoint = Vec2(axisX, clamp(drag.y)),
            axisDirection = Vec2(tilt, 1f).normalized(Vec2(0f, 1f)),
            radius = radius,
            progress = progress,
        )
    }

    fun deform(
        mesh: PageMesh,
        drag: Vec2,
        pageWidth: Float,
        pageHeight: Float,
        direction: CurlDirection = CurlDirection.Forward,
    ): CurlStats {
        require(pageWidth > 0f && pageHeight > 0f)
        val parameters = parameters(drag)
        if (parameters.progress <= IDLE_EPSILON || parameters.radius == 0f) {
            mesh.resetFlat(pageWidth, pageHeight)
            return CurlStats(mesh.vertexCount, 0, 0, 0f)
        }

        val mirrorX = if (direction == CurlDirection.Forward) 1f else -1f
        val axisPointX = (parameters.axisPoint.x - 0.5f) * pageWidth
        val axisPointY = (parameters.axisPoint.y - 0.5f) * pageHeight
        val axisDirectionX = parameters.axisDirection.x
        val axisDirectionY = parameters.axisDirection.y
        val curlDirectionX = axisDirectionY
        val curlDirectionY = -axisDirectionX
        val radius = parameters.radius * pageWidth
        val halfCircumference = PI.toFloat() * radius

        var flatVertices = 0
        var curvedVertices = 0
        var flippedVertices = 0
        var maxDepth = 0f

        for (vertex in 0 until mesh.vertexCount) {
            val uvOffset = vertex * 2
            val u = mesh.uvs[uvOffset]
            val v = 1f - mesh.uvs[uvOffset + 1]
            val originalX = (u - 0.5f) * pageWidth * mirrorX
            val originalY = (v - 0.5f) * pageHeight
            val relativeX = originalX - axisPointX
            val relativeY = originalY - axisPointY
            val alongDistance = relativeX * axisDirectionX + relativeY * axisDirectionY
            val signedDistance = relativeX * curlDirectionX + relativeY * curlDirectionY
            val axisFootX = axisPointX + axisDirectionX * alongDistance
            val axisFootY = axisPointY + axisDirectionY * alongDistance

            val positionX: Float
            val positionY: Float
            val depth: Float
            val vertexShade: Float
            val backSide: Float

            when {
                signedDistance <= 0f -> {
                    positionX = originalX
                    positionY = originalY
                    depth = 0f
                    vertexShade = 1f
                    backSide = 0f
                    flatVertices++
                }

                signedDistance < halfCircumference -> {
                    val angle = signedDistance / radius
                    val curledDistance = radius * sin(angle)
                    positionX = axisFootX + curlDirectionX * curledDistance
                    positionY = axisFootY + curlDirectionY * curledDistance
                    depth = radius * (1f - cos(angle))
                    vertexShade = lerp(0.48f, 1.07f, abs(cos(angle)))
                    backSide = if (angle > HALF_PI) 1f else 0f
                    curvedVertices++
                }

                else -> {
                    val flippedDistance = signedDistance - halfCircumference
                    positionX = axisFootX - curlDirectionX * flippedDistance
                    positionY = axisFootY - curlDirectionY * flippedDistance
                    depth = radius * 2f
                    vertexShade = lerp(0.78f, 0.88f, smoothStep(parameters.progress))
                    backSide = 1f
                    flippedVertices++
                }
            }

            val positionOffset = vertex * 3
            mesh.positions[positionOffset] = positionX * mirrorX
            mesh.positions[positionOffset + 1] = positionY
            mesh.positions[positionOffset + 2] = depth
            mesh.shade[vertex] = vertexShade
            mesh.side[vertex] = backSide
            maxDepth = maxOf(maxDepth, depth)
        }

        mesh.uploadMutableAttributes()
        return CurlStats(flatVertices, curvedVertices, flippedVertices, maxDepth)
    }

    /** Depth cue cast onto the *underlying* (revealed) page, not the turning page itself — unlike
     *  the old vertex-strip shadow this replaced, it's just three numbers consumed by a per-fragment
     *  gradient in [ShaderSources.PAGE_FRAGMENT], so it can't alias into a visible staircase the way
     *  a low-poly mesh strip did. [foldX] and [width] are in the same working-space units as
     *  [deform]'s `pageWidth` (i.e. *not* yet mirrored for [CurlDirection.Backward] — the caller
     *  applies that, since only it knows which real-space side is "revealed"). [width] of `0f` means
     *  "no shadow" (idle). */
    data class FoldShadow(val foldX: Float, val width: Float, val strength: Float)

    fun foldShadow(parameters: CurlParameters, pageWidth: Float): FoldShadow {
        if (parameters.progress <= IDLE_EPSILON) return FoldShadow(0f, 0f, 0f)
        val foldX = (parameters.axisPoint.x - 0.5f) * pageWidth
        val width = lerp(0.05f, 0.22f, smoothStep(parameters.progress)) * pageWidth
        val strength = minOf(0.45f, 0.12f + parameters.progress * 0.33f)
        return FoldShadow(foldX, width, strength)
    }

    private companion object {
        const val IDLE_EPSILON = 0.001f
        const val MIN_RADIUS = 0.008f
        const val MAX_RADIUS = 0.12f
        const val RADIUS_PER_PROGRESS = 0.22f
        const val AXIS_TILT_GAIN = 0.6f
        const val MAX_AXIS_TILT = 0.30f
        const val HALF_PI = (PI / 2.0).toFloat()
    }
}
