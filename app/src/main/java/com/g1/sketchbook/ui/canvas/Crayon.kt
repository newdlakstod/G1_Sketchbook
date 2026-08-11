package com.g1.sketchbook.ui.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * A tileable "crayon tooth" grain: white pixels with uneven alpha (some bare gaps, some light,
 * some waxy-solid). Tinting it with a stroke color via SrcIn turns any color into a chalky
 * colored-pencil / crayon texture. Built once and shared by the live canvas (Compose) and the
 * gallery snapshot renderer (native Canvas) so both look identical.
 */
val CrayonGrain: ImageBitmap by lazy { buildCrayonGrain() }

/** Repeating brush over [CrayonGrain] for Compose DrawScope. */
val CrayonBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(CrayonGrain, TileMode.Repeated, TileMode.Repeated))
}

private fun buildCrayonGrain(size: Int = 128): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(11)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val n = rnd.nextFloat()
            val alpha = when {
                n < 0.24f -> 0                    // torn gaps / bare paper (grunge)
                n < 0.46f -> rnd.nextInt(40, 120) // light, patchy coverage
                else -> rnd.nextInt(155, 255)     // waxy, near-solid coverage
            }
            bmp.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
        }
    }
    return bmp.asImageBitmap()
}
