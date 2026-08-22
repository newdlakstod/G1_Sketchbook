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
    ): CurlStats {
        require(pageWidth > 0f && pageHeight > 0f)
        val parameters = parameters(drag)
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
            mesh.positions[positionOffset] = positionX
            mesh.positions[positionOffset + 1] = positionY
            mesh.positions[positionOffset + 2] = depth
            mesh.shade[vertex] = vertexShade
            mesh.side[vertex] = backSide
            maxDepth = maxOf(maxDepth, depth)
        }

        mesh.uploadMutableAttributes()
        return CurlStats(flatVertices, curvedVertices, flippedVertices, maxDepth)
    }

    fun updateShadow(
        strip: ShadowStrip,
        parameters: CurlParameters,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        require(pageWidth > 0f && pageHeight > 0f)
        if (parameters.progress <= IDLE_EPSILON) {
            strip.alpha.fill(0f)
            strip.upload()
            return
        }

        val axisX = (parameters.axisPoint.x - 0.5f) * pageWidth
        val axisY = (parameters.axisPoint.y - 0.5f) * pageHeight
        val axisDirection = parameters.axisDirection
        val curlX = axisDirection.y
        val curlY = -axisDirection.x
        val width = lerp(0.025f, 0.11f, smoothStep(parameters.progress)) * pageWidth
        val innerAlpha = minOf(0.52f, 0.14f + parameters.progress * 0.38f)

        for (segment in 0..strip.segments) {
            val amount = segment.toFloat() / strip.segments
            val y = lerp(-pageHeight * 0.5f, pageHeight * 0.5f, amount)
            val along = (y - axisY) / axisDirection.y
            val innerX = clamp(
                axisX + axisDirection.x * along,
                -pageWidth * 0.5f,
                pageWidth * 0.5f,
            )
            val innerY = clamp(y, -pageHeight * 0.5f, pageHeight * 0.5f)
            val outerX = clamp(
                innerX - curlX * width,
                -pageWidth * 0.5f,
                pageWidth * 0.5f,
            )
            val outerY = clamp(
                innerY - curlY * width,
                -pageHeight * 0.5f,
                pageHeight * 0.5f,
            )
            val innerVertex = segment * 2
            val outerVertex = innerVertex + 1
            writeShadowVertex(strip.positions, innerVertex, innerX, innerY)
            writeShadowVertex(strip.positions, outerVertex, outerX, outerY)
            strip.alpha[innerVertex] = innerAlpha
            strip.alpha[outerVertex] = 0f
        }
        strip.upload()
    }

    private fun writeShadowVertex(positions: FloatArray, vertex: Int, x: Float, y: Float) {
        val offset = vertex * 3
        positions[offset] = x
        positions[offset + 1] = y
        positions[offset + 2] = SHADOW_DEPTH
    }

    private companion object {
        const val IDLE_EPSILON = 0.001f
        const val MIN_RADIUS = 0.008f
        const val MAX_RADIUS = 0.12f
        const val RADIUS_PER_PROGRESS = 0.22f
        const val AXIS_TILT_GAIN = 0.6f
        const val MAX_AXIS_TILT = 0.30f
        const val HALF_PI = (PI / 2.0).toFloat()
        const val SHADOW_DEPTH = -0.02f
    }
}
