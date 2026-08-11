package com.g1.sketchbook.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Adds a subtle, tileable paper grain *behind* the content — a clean, generated substitute for a
 * stock paper photo (no watermark, no licensing). The noise tile is built once and repeated via a
 * shader, so it costs almost nothing to draw.
 */
fun Modifier.paperTexture(): Modifier = composed {
    val brush = remember {
        ShaderBrush(
            ImageShader(buildPaperNoise(), TileMode.Repeated, TileMode.Repeated)
        )
    }
    drawBehind { drawRect(brush) }
}

private fun buildPaperNoise(): androidx.compose.ui.graphics.ImageBitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    for (y in 0 until size) {
        for (x in 0 until size) {
            // Mostly transparent; sprinkle very faint light/dark specks for a fibrous paper feel.
            val roll = rnd.nextFloat()
            val argb = when {
                roll < 0.06f -> android.graphics.Color.argb(rnd.nextInt(10, 22), 0, 0, 0)        // dark fleck
                roll < 0.14f -> android.graphics.Color.argb(rnd.nextInt(12, 26), 255, 255, 255)  // light fleck
                else -> 0
            }
            bmp.setPixel(x, y, argb)
        }
    }
    return bmp.asImageBitmap()
}
