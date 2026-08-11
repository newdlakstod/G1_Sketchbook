package com.g1.sketchbook.ui.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * A tileable graphite "pencil tooth" grain: white pixels with fine, mostly-dense alpha and only
 * occasional light gaps. Tinting it with a stroke color via SrcIn gives a stable pencil texture
 * (like Samsung Notes) — the grain is anchored in canvas space so it does NOT shimmer while drawing.
 * Built once and shared by the live canvas (Compose) and the gallery snapshot renderer (native).
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
                n < 0.06f -> 0                     // rare bare speck (paper tooth)
                n < 0.30f -> rnd.nextInt(120, 190) // fine light graphite grain
                else -> rnd.nextInt(195, 255)      // dense pencil coverage
            }
            bmp.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
        }
    }
    return bmp.asImageBitmap()
}
