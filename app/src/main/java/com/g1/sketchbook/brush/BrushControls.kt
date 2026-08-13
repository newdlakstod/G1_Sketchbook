package com.g1.sketchbook.brush

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.g1.sketchbook.R

val BrushPalette = listOf(
    0xFF223150L, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

private val HueWheel = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** Single-row floating dock. Optional leading controls (back / page nav / rotate) show when provided. */
@Composable
fun BrushControls(
    brush: BrushType, color: Long, sizeDp: Float, opacity: Float, erasing: Boolean,
    onBrush: (BrushType) -> Unit, onColor: (Long) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    onToggleErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit,
    onBack: (() -> Unit)? = null,
    onRotate: (() -> Unit)? = null,
    pageLabel: String? = null,
    onPrevPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
    onAddPage: (() -> Unit)? = null,
    onDeletePage: (() -> Unit)? = null,
) {
    var panel by remember { mutableIntStateOf(0) } // 0 none, 1 width, 2 opacity
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }

    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            onBack?.let { IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", onClick = it) }
            if (pageLabel != null) {
                IconBtn(Icons.Filled.ChevronLeft, "이전 페이지") { onPrevPage?.invoke() }
                Text(pageLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IconBtn(Icons.Filled.ChevronRight, "다음 페이지") { onNextPage?.invoke() }
                IconBtn(Icons.Filled.Add, "페이지 추가") { onAddPage?.invoke() }
                IconBtn(Icons.Filled.Remove, "페이지 삭제") { onDeletePage?.invoke() }
            }
            onRotate?.let { IconBtn(Icons.Filled.Rotate90DegreesCw, "90° 회전", onClick = it) }
            if (onBack != null || pageLabel != null || onRotate != null) VDivider()

            BrushBtn(!erasing && brush == BrushType.PEN, { onBrush(BrushType.PEN) }) { t ->
                Image(painterResource(R.drawable.brush_pen), "볼펜", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtn(!erasing && brush == BrushType.PENCIL, { onBrush(BrushType.PENCIL) }) { t ->
                Image(painterResource(R.drawable.brush_pencil), "연필", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtn(!erasing && brush == BrushType.CRAYON, { onBrush(BrushType.CRAYON) }) { t ->
                Image(painterResource(R.drawable.brush_crayon), "크레파스", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtn(!erasing && brush == BrushType.WATER, { onBrush(BrushType.WATER) }) { t ->
                Image(painterResource(R.drawable.brush_water), "수채화", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtn(erasing, { onToggleErase() }) { t ->
                Image(painterResource(R.drawable.brush_eraser), "지우개", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }

            VDivider()

            BrushPalette.forEach { c ->
                val on = !erasing && c == color
                Box(Modifier.size(26.dp).background(Color(c), CircleShape)
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .clickable { onColor(c) })
            }
            // Color wheel: opens a hue/saturation/value picker for any custom colour.
            Box {
                Box(Modifier.size(28.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(HueWheel))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { panel = if (panel == 3) 0 else 3 })
                if (panel == 3) Popup(AboveAnchor(gap), { panel = 0 }, PopupProperties(focusable = true)) {
                    ColorPickerCard(color, onColor)
                }
            }

            VDivider()

            Box {
                Box(Modifier.size(40.dp).clickable { panel = if (panel == 1) 0 else 1 }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size((sizeDp / 2f + 6f).coerceIn(6f, 30f).dp)
                        .background(if (erasing) MaterialTheme.colorScheme.onSurfaceVariant else Color(color), CircleShape))
                }
                if (panel == 1) Popup(AboveAnchor(gap), { panel = 0 }, PopupProperties(focusable = true)) {
                    SliderCard("굵기", "${sizeDp.toInt()}", sizeDp, 2f..48f, onSize) { onSize(10f) }
                }
            }
            Box {
                Box(Modifier.height(40.dp).clickable { panel = if (panel == 2) 0 else 2 }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("${opacity.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (panel == 2) Popup(AboveAnchor(gap), { panel = 0 }, PopupProperties(focusable = true)) {
                    SliderCard("불투명도", "${opacity.toInt()}%", opacity, 0f..100f, onOpacity) { onOpacity(100f) }
                }
            }

            VDivider()

            IconBtn(Icons.AutoMirrored.Filled.Undo, "되돌리기", onClick = onUndo)
            IconBtn(Icons.AutoMirrored.Filled.Redo, "다시 실행", onClick = onRedo)
            IconBtn(Icons.Filled.Delete, "전체 지우기", tint = Color(0xFFE85555), onClick = onClear)
        }
    }
}

@Composable
private fun SliderCard(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
                       onChange: (Float) -> Unit, onReset: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(248.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp).clickable { onReset() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Rotate90DegreesCw, "기본값", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Hue/saturation/value picker: SV square + hue bar + preview, for choosing any custom colour. */
@Composable
private fun ColorPickerCard(color: Long, onColor: (Long) -> Unit) {
    val init = remember { FloatArray(3).also { AndroidColor.colorToHSV((color and 0xFFFFFFFF).toInt(), it) } }
    var hue by remember { mutableFloatStateOf(init[0]) }
    var sat by remember { mutableFloatStateOf(init[1]) }
    var value by remember { mutableFloatStateOf(init[2]) }
    fun emit() { onColor((AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFF) or 0xFF000000L) }
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(248.dp).padding(16.dp)) {
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val p = awaitPointerEvent().changes.first().position
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = (1f - p.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.White, 7.dp.toPx(), c, style = Stroke(2.5f.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.5f), 7.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.horizontalGradient(HueWheel))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val x = awaitPointerEvent().changes.first().position.x
                                hue = (x / size.width * 360f).coerceIn(0f, 360f)
                                emit()
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.height / 2f
                    val x = ((hue / 360f) * size.width).coerceIn(r, size.width - r)
                    drawCircle(Color.White, r - 1.dp.toPx(), Offset(x, r), style = Stroke(2.5f.dp.toPx()))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(current)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("#%06X".format(0xFFFFFF and AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
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
private fun BrushBtn(selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    // Button footprint stays at the original 42dp; the enlarged icon is cropped to fit inside it.
    Box(Modifier.size(42.dp).clipToBounds().clickable { onClick() }, contentAlignment = Alignment.Center) { icon(tint) }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun VDivider() {
    Spacer(Modifier.width(2.dp))
    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.width(2.dp))
}
