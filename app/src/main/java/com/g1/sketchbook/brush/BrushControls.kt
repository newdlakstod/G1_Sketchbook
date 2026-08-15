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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import com.g1.sketchbook.ui.bounceClick
import kotlin.math.roundToInt

val BrushPalette = listOf(
    0xFF1E2D4CL, 0xFF2B4C9BL, 0xFF4DABF7L, 0xFF4ECDC4L, 0xFF6E9646L,
    0xFFE0A53CL, 0xFFE05454L, 0xFFCE7A7AL, 0xFF9775FAL, 0xFFFFFFFFL,
)

private val HueWheel = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** Single-row floating dock. Optional leading controls (back / pages / rotate) show when provided. */
@Composable
fun BrushControls(
    brush: BrushType, color: Long, sizeDp: Float, opacity: Float, erasing: Boolean,
    onBrush: (BrushType) -> Unit, onColor: (Long) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    onToggleErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit,
    onBack: (() -> Unit)? = null,
    onRotate: (() -> Unit)? = null,
    /** Opens the page list/turn panel — a single dedicated entry point (system back already exits,
     *  so there's no separate "나가기" button here anymore for screens that pass this). */
    onOpenPages: (() -> Unit)? = null,
    favorites: List<Long> = BrushPalette.take(5),
    onEditFavorite: (Int, Long) -> Unit = { _, _ -> },
    eyedropArmed: Boolean = false,
    onToggleEyedrop: () -> Unit = {},
) {
    var colorWheelOpen by remember { mutableStateOf(false) }
    var editFavAt by remember { mutableIntStateOf(-1) } // -1 none, else favourites index being edited
    var confirmClear by remember { mutableStateOf(false) }
    // Which brush's width/opacity panel is open (hoisted here, same pattern as editFavAt above —
    // per-button local state + Popup turned out unreliable, this mirrors the known-good approach).
    var openBrushPanel by remember { mutableStateOf<BrushType?>(null) }
    var openEraserPanel by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("전체 지우기") },
            text = { Text("이 페이지의 그림을 모두 지울까요? 되돌리기로 복구할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text("전체 지우기", color = Color(0xFFE85555))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }

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
            // Page turning/thumbnail list/selection all live behind one button now — see PagePanel.
            onOpenPages?.let { IconBtn(Icons.Filled.Layers, "페이지", onClick = it) }
            onRotate?.let { IconBtn(Icons.Filled.Rotate90DegreesCw, "90° 회전", onClick = it) }
            if (onBack != null || onOpenPages != null || onRotate != null) VDivider()

            // Brush icons: tap to switch; tap the already-selected one again to open ITS OWN
            // width/opacity panel (anchored + labelled per brush, not a single shared control).
            BrushBtnWithPanel(!erasing && brush == BrushType.PEN, "펜", sizeDp, opacity, true, gap,
                panelOpen = openBrushPanel == BrushType.PEN,
                setPanelOpen = { o -> openBrushPanel = if (o) BrushType.PEN else null; if (o) openEraserPanel = false },
                onClick = { onBrush(BrushType.PEN); openBrushPanel = null; openEraserPanel = false },
                onSize = onSize, onOpacity = onOpacity) { t ->
                Image(painterResource(R.drawable.brush_pen), "볼펜", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtnWithPanel(!erasing && brush == BrushType.PENCIL, "연필", sizeDp, opacity, true, gap,
                panelOpen = openBrushPanel == BrushType.PENCIL,
                setPanelOpen = { o -> openBrushPanel = if (o) BrushType.PENCIL else null; if (o) openEraserPanel = false },
                onClick = { onBrush(BrushType.PENCIL); openBrushPanel = null; openEraserPanel = false },
                onSize = onSize, onOpacity = onOpacity) { t ->
                Image(painterResource(R.drawable.brush_pencil), "연필", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtnWithPanel(!erasing && brush == BrushType.CRAYON, "크레파스", sizeDp, opacity, true, gap,
                panelOpen = openBrushPanel == BrushType.CRAYON,
                setPanelOpen = { o -> openBrushPanel = if (o) BrushType.CRAYON else null; if (o) openEraserPanel = false },
                onClick = { onBrush(BrushType.CRAYON); openBrushPanel = null; openEraserPanel = false },
                onSize = onSize, onOpacity = onOpacity) { t ->
                Image(painterResource(R.drawable.brush_crayon), "크레파스", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtnWithPanel(!erasing && brush == BrushType.WATER, "수채화", sizeDp, opacity, true, gap,
                panelOpen = openBrushPanel == BrushType.WATER,
                setPanelOpen = { o -> openBrushPanel = if (o) BrushType.WATER else null; if (o) openEraserPanel = false },
                onClick = { onBrush(BrushType.WATER); openBrushPanel = null; openEraserPanel = false },
                onSize = onSize, onOpacity = onOpacity) { t ->
                Image(painterResource(R.drawable.brush_water), "수채화", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }
            BrushBtnWithPanel(erasing, "지우개", sizeDp, opacity, false, gap,
                panelOpen = openEraserPanel,
                setPanelOpen = { o -> openEraserPanel = o; if (o) openBrushPanel = null },
                onClick = { onToggleErase(); openBrushPanel = null; openEraserPanel = false },
                onSize = onSize, onOpacity = onOpacity) { t ->
                Image(painterResource(R.drawable.brush_eraser), "지우개", colorFilter = ColorFilter.tint(t), modifier = Modifier.size(52.dp))
            }

            VDivider()

            // 5 favourites: tap to pick; tap the already-selected one again to open a colour wheel for it.
            // Visual swatch stays 28dp; the tappable area is expanded to the 48dp accessibility minimum.
            favorites.forEachIndexed { i, c ->
                val on = !erasing && c == color
                Box {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .clickable(onClickLabel = "즐겨찾기 색상 ${i + 1}") { if (on) editFavAt = i else onColor(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape))
                    }
                    if (editFavAt == i) Popup(AboveAnchor(gap), { editFavAt = -1 }, PopupProperties(focusable = true)) {
                        ColorPickerCard(c) { newColor -> onColor(newColor); onEditFavorite(i, newColor) }
                    }
                }
            }
            // Color wheel: opens a hue/saturation/value picker for any custom colour.
            Box {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "사용자 지정 색상 고르기") { colorWheelOpen = !colorWheelOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(HueWheel))
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                }
                if (colorWheelOpen) Popup(AboveAnchor(gap), { colorWheelOpen = false }, PopupProperties(focusable = true)) {
                    ColorPickerCard(color, onColor)
                }
            }
            // Eyedropper: arm it, then the next tap on the canvas picks that colour instead of drawing.
            IconBtn(Icons.Filled.Colorize, "스포이드",
                tint = if (eyedropArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = onToggleEyedrop)

            VDivider()

            IconBtn(Icons.AutoMirrored.Filled.Undo, "되돌리기", onClick = onUndo)
            IconBtn(Icons.AutoMirrored.Filled.Redo, "다시 실행", onClick = onRedo)
            IconBtn(Icons.Filled.Delete, "전체 지우기", tint = Color(0xFFE85555), onClick = { confirmClear = true })
        }
    }
}

/** Floating colour-preview bubble for the eyedropper — sits above the fingertip and follows it while
 *  armed/dragging on the canvas, so it's obvious a colour is being picked (and which one). [xPx]/[yPx]
 *  are raw screen px in the same coordinate space as the BrushView it's overlaid on. */
@Composable
fun EyedropFloatingPreview(colorArgb: Int, xPx: Float, yPx: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val sizePx = 52f * density
    val liftPx = 64f * density
    Box(
        modifier
            .offset { IntOffset((xPx - sizePx / 2f).roundToInt(), (yPx - liftPx - sizePx / 2f).roundToInt()) }
            .size(52.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .border(3.dp, Color.White, CircleShape),
    )
}

/** Width (and, unless erasing, opacity) sliders for ONE specific brush — opened by tapping that
 *  brush's icon again while it's already selected; the brush name heads the panel so it's clear
 *  which brush is being adjusted (each brush keeps its own width/opacity, not a shared value). */
@Composable
private fun SlidersPanel(title: String, showOpacity: Boolean, sizeDp: Float, opacity: Float, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
        Column(Modifier.width(248.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SliderRow("굵기", "${sizeDp.toInt()}", sizeDp, 2f..48f, onSize) { onSize(10f) }
            if (showOpacity) {
                Spacer(Modifier.height(14.dp))
                SliderRow("불투명도", "${opacity.toInt()}%", opacity, 0f..100f, onOpacity) { onOpacity(100f) }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
                      onChange: (Float) -> Unit, onReset: () -> Unit) {
    Column {
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

/** Tap to select; tap again while already selected opens THIS brush's own width/opacity panel,
 *  anchored right above this icon (not a shared control), with the brush's name as the header.
 *  Open/closed state is hoisted by the caller (BrushControls) — mirrors the favourites-edit popup. */
@Composable
private fun BrushBtnWithPanel(
    selected: Boolean, name: String, sizeDp: Float, opacity: Float, showOpacity: Boolean, gap: Int,
    panelOpen: Boolean, setPanelOpen: (Boolean) -> Unit,
    onClick: () -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box {
        // Tap area is the 48dp accessibility minimum; the icon's own 42dp crop window (which frames
        // the enlarged 52dp artwork) stays exactly as before, just centred inside the bigger hitbox.
        Box(Modifier.size(48.dp).bounceClick { if (selected) setPanelOpen(!panelOpen) else onClick() },
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).clipToBounds(), contentAlignment = Alignment.Center) { icon(tint) }
        }
        if (panelOpen) Popup(AboveAnchor(gap), { setPanelOpen(false) }, PopupProperties(focusable = true)) {
            SlidersPanel(name, showOpacity, sizeDp, opacity, onSize, onOpacity)
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).bounceClick { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun VDivider() {
    Spacer(Modifier.width(2.dp))
    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.width(2.dp))
}
