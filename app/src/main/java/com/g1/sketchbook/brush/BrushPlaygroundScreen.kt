package com.g1.sketchbook.brush

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.R

private val Palette = listOf(
    0xFF223150L, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushPlaygroundScreen() {
    var view by remember { mutableStateOf<BrushView?>(null) }
    var brush by remember { mutableStateOf(BrushType.PEN) }
    var color by remember { mutableStateOf(0xFF2B4C9BL) }
    var sizeDp by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(100f) }
    val density = LocalDensity.current.density

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    // Brushes
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrushChip("볼펜", brush == BrushType.PEN) { brush = BrushType.PEN }
                        BrushChip("연필", brush == BrushType.PENCIL) { brush = BrushType.PENCIL }
                        BrushChip("크레파스", brush == BrushType.CRAYON) { brush = BrushType.CRAYON }
                        BrushChip("수채화", brush == BrushType.WATER) { brush = BrushType.WATER }
                    }
                    Spacer(Modifier.size(8.dp))
                    // Colors
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Palette) { c ->
                            val on = c == color
                            Box(
                                Modifier.size(30.dp).background(Color(c), CircleShape)
                                    .border(if (on) 3.dp else 1.dp,
                                        if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                                    .clickable { color = c }
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    // Size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("굵기", fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(value = sizeDp, onValueChange = { sizeDp = it }, valueRange = 2f..48f, modifier = Modifier.weight(1f))
                        Text("${sizeDp.toInt()}", fontSize = 12.sp, modifier = Modifier.width(32.dp))
                    }
                    // Opacity
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("불투명도", fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                        Text("${opacity.toInt()}%", fontSize = 12.sp, modifier = Modifier.width(38.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { view?.undo() }) { Text("되돌리기") }
                        TextButton(onClick = { view?.clearCanvas() }) { Text("전체 지우기") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("브러시 놀이터", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                factory = { ctx ->
                    BrushView(ctx).also { v ->
                        v.paper = BitmapFactory.decodeResource(ctx.resources, R.drawable.paper_watercolor)
                        view = v
                    }
                },
                update = { v ->
                    v.brush = brush
                    v.color = color.toInt()
                    v.strokeSize = sizeDp * density
                    v.opacity = opacity / 100f
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrushChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
