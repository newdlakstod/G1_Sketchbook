package com.gdo.pagecurl

import com.gdo.pagecurl.math.Vec2
import com.gdo.pagecurl.math.clamp

internal data class UnderPageShadow(
    val axisPoint: Vec2,
    val normal: Vec2,
    val width: Float,
    val opacity: Float,
) {
    companion object {
        val Disabled = UnderPageShadow(Vec2(0f, 0f), Vec2(1f, 0f), 0f, 0f)
    }
}

internal fun UnderPageShadow.offsetX(delta: Float): UnderPageShadow =
    copy(axisPoint = Vec2(axisPoint.x + delta, axisPoint.y))

internal fun underPageShadowFor(
    state: CurlState,
    parameters: CurlParameters,
    direction: PageTurnDirection,
    pageWidth: Float,
    pageHeight: Float,
): UnderPageShadow {
    if (state.phase == CurlPhase.Idle ||
        state.phase == CurlPhase.Completed ||
        parameters.progress <= 0f ||
        parameters.radius <= 0f
    ) return UnderPageShadow.Disabled

    val mirrorX = if (direction == PageTurnDirection.Forward) 1f else -1f
    val curlAxisPoint = Vec2(
        x = (parameters.axisPoint.x - 0.5f) * pageWidth * mirrorX,
        y = (parameters.axisPoint.y - 0.5f) * pageHeight,
    )
    val normal = Vec2(
        x = parameters.axisDirection.y * mirrorX,
        y = -parameters.axisDirection.x,
    ).normalized(Vec2(mirrorX, 0f))
    val opacity = MAX_SHADOW_OPACITY * clamp(parameters.progress / SHADOW_FADE_PROGRESS)
    val shadowOrigin = curlAxisPoint + normal * (parameters.radius * pageWidth)

    return UnderPageShadow(
        axisPoint = shadowOrigin,
        normal = normal,
        width = parameters.radius * pageWidth * SHADOW_RADIUS_SCALE,
        opacity = opacity,
    )
}

private const val MAX_SHADOW_OPACITY = 0.22f
private const val SHADOW_FADE_PROGRESS = 0.15f
private const val SHADOW_RADIUS_SCALE = 0.75f
