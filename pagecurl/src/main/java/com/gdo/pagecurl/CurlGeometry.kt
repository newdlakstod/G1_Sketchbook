package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import com.gdo.pagecurl.math.clamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class CurlRegion {
    Flat,
    Curved,
    Flipped,
}

internal data class CurlParameters(
    val axisPoint: Vec2,
    val axisDirection: Vec2,
    val radius: Float,
    val progress: Float,
)

internal data class CurlStats(
    val flatVertices: Int,
    val curvedVertices: Int,
    val flippedVertices: Int,
    val maxDepth: Float,
)

internal class CurlGeometry {
    fun parameters(drag: Vec2, grabAnchorY: Float = drag.y): CurlParameters {
        val progress = clamp(1f - drag.x)
        val radius = if (progress <= IDLE_EPSILON) {
            0f
        } else {
            minOf(MAX_RADIUS, maxOf(MIN_RADIUS, progress * RADIUS_PER_PROGRESS))
        }
        val halfCircumference = PI.toFloat() * radius
        val axisX = (1f + drag.x - halfCircumference) * 0.5f
        val tilt = clamp(
            value = (0.5f - grabAnchorY) * AXIS_TILT_GAIN,
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
        direction: PageTurnDirection = PageTurnDirection.Forward,
        grabAnchorY: Float = drag.y,
    ): CurlStats {
        require(pageWidth > 0f && pageHeight > 0f)
        val mirrorX = if (direction == PageTurnDirection.Forward) 1f else -1f
        val parameters = parameters(drag, grabAnchorY)
        if (parameters.progress <= IDLE_EPSILON || parameters.radius == 0f) {
            mesh.resetFlat(pageWidth, pageHeight)
            return CurlStats(mesh.vertexCount, 0, 0, 0f)
        }

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
            val originalX = (u - 0.5f) * pageWidth
            val workingOriginalX = originalX * mirrorX
            val originalY = (v - 0.5f) * pageHeight
            val relativeX = workingOriginalX - axisPointX
            val relativeY = originalY - axisPointY
            val alongDistance = relativeX * axisDirectionX + relativeY * axisDirectionY
            val signedDistance = relativeX * curlDirectionX + relativeY * curlDirectionY
            val axisFootX = axisPointX + axisDirectionX * alongDistance
            val axisFootY = axisPointY + axisDirectionY * alongDistance

            val positionX: Float
            val positionY: Float
            val depth: Float
            when {
                signedDistance <= 0f -> {
                    positionX = workingOriginalX
                    positionY = originalY
                    depth = 0f
                    flatVertices++
                }

                signedDistance < halfCircumference -> {
                    val angle = signedDistance / radius
                    val curledDistance = radius * sin(angle)
                    positionX = axisFootX + curlDirectionX * curledDistance
                    positionY = axisFootY + curlDirectionY * curledDistance
                    depth = radius * (1f - cos(angle))
                    curvedVertices++
                }

                else -> {
                    val flippedDistance = signedDistance - halfCircumference
                    positionX = axisFootX - curlDirectionX * flippedDistance
                    positionY = axisFootY - curlDirectionY * flippedDistance
                    depth = radius * 2f
                    flippedVertices++
                }
            }

            val positionOffset = vertex * 3
            mesh.positions[positionOffset] = positionX * mirrorX
            mesh.positions[positionOffset + 1] = positionY
            mesh.positions[positionOffset + 2] = depth
            maxDepth = maxOf(maxDepth, depth)
        }

        mesh.uploadPositions()
        return CurlStats(flatVertices, curvedVertices, flippedVertices, maxDepth)
    }

    private companion object {
        const val IDLE_EPSILON = 0.001f
        const val MIN_RADIUS = 0.008f
        const val MAX_RADIUS = 0.12f
        const val RADIUS_PER_PROGRESS = 0.22f
        const val AXIS_TILT_GAIN = 0.6f
        const val MAX_AXIS_TILT = 0.30f
    }
}
