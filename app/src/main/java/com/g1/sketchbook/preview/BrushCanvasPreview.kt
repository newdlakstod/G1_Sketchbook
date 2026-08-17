package com.g1.sketchbook.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.brush.BrushControls
import com.g1.sketchbook.brush.BrushPalette
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.BrushView
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

@Preview(
    name = "13 Personal canvas",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun BrushCanvasPreview() {
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableLongStateOf(BrushPalette.first()) }
    var sizeDp by remember { mutableFloatStateOf(20f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    var erasing by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    DaymoryTheme(mode = ThemeMode.LIGHT) {
        Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        BrushView(context).apply {
                            initCanvas(390, 600)
                            drawEnabled = false
                        }
                    },
                    update = { view ->
                        view.brush = brush
                        view.color = color.toInt()
                        view.strokeSize = sizeDp * density
                        view.opacity = opacity / 100f
                        view.erasing = erasing
                    },
                )
            }
            BrushControls(
                brush = brush,
                color = color,
                sizeDp = sizeDp,
                opacity = opacity,
                erasing = erasing,
                onBrush = { brush = it },
                onColor = { color = it },
                onSize = { sizeDp = it },
                onOpacity = { opacity = it },
                onToggleErase = { erasing = !erasing },
                onUndo = {},
                onRedo = {},
                onClear = {},
            )
        }
    }
}
