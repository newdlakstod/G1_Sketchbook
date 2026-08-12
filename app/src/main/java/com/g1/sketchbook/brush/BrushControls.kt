package com.g1.sketchbook.brush

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrushPalette = listOf(
    0xFF223150L, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

/** Shared brush toolbar: brushes + eraser, palette, width, opacity, undo/redo/clear, zoom lock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushControls(
    brush: BrushType, color: Long, sizeDp: Float, opacity: Float, erasing: Boolean, zoomLocked: Boolean,
    onBrush: (BrushType) -> Unit, onColor: (Long) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    onToggleErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit, onToggleZoomLock: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("볼펜", !erasing && brush == BrushType.PEN) { onBrush(BrushType.PEN) }
                Chip("연필", !erasing && brush == BrushType.PENCIL) { onBrush(BrushType.PENCIL) }
                Chip("크레파스", !erasing && brush == BrushType.CRAYON) { onBrush(BrushType.CRAYON) }
                Chip("수채화", !erasing && brush == BrushType.WATER) { onBrush(BrushType.WATER) }
                Chip("지우개", erasing) { onToggleErase() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BrushPalette) { c ->
                        val on = !erasing && c == color
                        Box(Modifier.size(28.dp).background(Color(c), CircleShape)
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable { onColor(c) })
                    }
                }
                IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "되돌리기") }
                IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
                IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, "전체 지우기", tint = Color(0xFFE85555)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("굵기", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Slider(sizeDp, onSize, valueRange = 2f..48f, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleZoomLock) {
                    Icon(if (zoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        "확대 잠금", tint = if (zoomLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("불투명도", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Slider(opacity, onOpacity, valueRange = 0f..100f, modifier = Modifier.weight(1f))
                Text("${opacity.toInt()}%", fontSize = 12.sp, modifier = Modifier.width(38.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.small,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
}
