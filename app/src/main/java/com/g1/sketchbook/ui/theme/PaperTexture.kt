package com.g1.sketchbook.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Adds a subtle, tileable paper grain *behind* the content — a clean, generated substitute for a
 * stock paper photo (no watermark, no licensing). The noise tile is built once and repeated via a
 * shader, so it costs almost nothing to draw. [strength] scales visibility (1f = full grain).
 */
fun Modifier.paperTexture(strength: Float = 1f): Modifier = composed {
    val brush = remember {
        ShaderBrush(ImageShader(PaperNoise, TileMode.Repeated, TileMode.Repeated))
    }
    drawBehind { drawRect(brush, alpha = strength.coerceIn(0f, 1f)) }
}

private val PaperNoise: ImageBitmap by lazy { buildPaperNoise() }

private fun buildPaperNoise(size: Int = 160): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    for (y in 0 until size) {
        for (x in 0 until size) {
            // Fibrous paper feel: frequent faint light/dark specks over a mostly clear tile.
            val roll = rnd.nextFloat()
            val argb = when {
                roll < 0.10f -> android.graphics.Color.argb(rnd.nextInt(16, 34), 60, 50, 35)     // dark fiber
                roll < 0.24f -> android.graphics.Color.argb(rnd.nextInt(18, 38), 255, 255, 255)  // light fleck
                else -> 0
            }
            bmp.setPixel(x, y, argb)
        }
    }
    return bmp.asImageBitmap()
}
