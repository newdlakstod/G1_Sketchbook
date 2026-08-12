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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

val BrushPalette = listOf(
    0xFF223150L, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

/** Single-row floating dock; width/opacity open a small slider popup anchored above their button. */
@Composable
fun BrushControls(
    brush: BrushType, color: Long, sizeDp: Float, opacity: Float, erasing: Boolean, zoomLocked: Boolean,
    onBrush: (BrushType) -> Unit, onColor: (Long) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    onToggleErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit, onToggleZoomLock: () -> Unit,
) {
    var panel by remember { mutableIntStateOf(0) } // 0 none, 1 width, 2 opacity
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }

    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Pill("볼펜", !erasing && brush == BrushType.PEN) { onBrush(BrushType.PEN) }
            Pill("연필", !erasing && brush == BrushType.PENCIL) { onBrush(BrushType.PENCIL) }
            Pill("크레파스", !erasing && brush == BrushType.CRAYON) { onBrush(BrushType.CRAYON) }
            Pill("수채화", !erasing && brush == BrushType.WATER) { onBrush(BrushType.WATER) }
            Pill("지우개", erasing) { onToggleErase() }

            VDivider()

            BrushPalette.forEach { c ->
                val on = !erasing && c == color
                Box(Modifier.size(26.dp).background(Color(c), CircleShape)
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .clickable { onColor(c) })
            }

            VDivider()

            // width button + popup above it
            Box {
                Box(Modifier.size(40.dp).clickable { panel = if (panel == 1) 0 else 1 }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size((sizeDp / 2f + 6f).coerceIn(6f, 30f).dp)
                        .background(if (erasing) MaterialTheme.colorScheme.onSurfaceVariant else Color(color), CircleShape))
                }
                if (panel == 1) {
                    Popup(popupPositionProvider = AboveAnchor(gap), onDismissRequest = { panel = 0 },
                        properties = PopupProperties(focusable = true)) {
                        SliderCard("굵기", "${sizeDp.toInt()}", sizeDp, 2f..48f, onSize) { onSize(10f) }
                    }
                }
            }
            // opacity button + popup above it
            Box {
                Box(Modifier.height(40.dp).clickable { panel = if (panel == 2) 0 else 2 }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("${opacity.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (panel == 2) {
                    Popup(popupPositionProvider = AboveAnchor(gap), onDismissRequest = { panel = 0 },
                        properties = PopupProperties(focusable = true)) {
                        SliderCard("불투명도", "${opacity.toInt()}%", opacity, 0f..100f, onOpacity) { onOpacity(100f) }
                    }
                }
            }

            VDivider()

            IconBtn(if (zoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen, "확대 잠금",
                tint = if (zoomLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = onToggleZoomLock)
            IconBtn(Icons.AutoMirrored.Filled.Undo, "되돌리기", onClick = onUndo)
            IconBtn(Icons.AutoMirrored.Filled.Redo, "다시 실행", onClick = onRedo)
            IconBtn(Icons.Filled.Delete, "전체 지우기", tint = Color(0xFFE85555), onClick = onClear)
        }
    }
}

/** Reference-style slider card: label, reset, value chip, thin track. */
@Composable
private fun SliderCard(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
                       onChange: (Float) -> Unit, onReset: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(248.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp).clickable { onReset() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Refresh, "기본값", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Slider(value, onChange, valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.surface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ))
        }
    }
}

private class AboveAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clickable { onClick() },
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun VDivider() {
    Spacer(Modifier.width(3.dp))
    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.width(3.dp))
}
