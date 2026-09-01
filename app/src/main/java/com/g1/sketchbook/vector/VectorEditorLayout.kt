package com.g1.sketchbook.vector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.min

enum class PanelPlacement { RIGHT, BOTTOM }
fun choosePanelPlacement(widthDp: Float, heightDp: Float): PanelPlacement = if (widthDp >= 720f && widthDp > heightDp) PanelPlacement.RIGHT else PanelPlacement.BOTTOM
fun rightInspectorWidthDp(widthDp: Float): Float = min(336f, widthDp * .32f)
fun bottomPanelInitialFraction(): Float = .42f

@Composable
fun VectorTopBar(title: String, canUndo: Boolean, canRedo: Boolean, onBack: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("뒤로", Modifier.clickableText(onBack)); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text("되돌리기", Modifier.clickableText(onUndo, canUndo)); Text("다시", Modifier.clickableText(onRedo, canRedo)); HorizontalDivider(Modifier.width(1.dp).height(24.dp)); Text("내보내기", Modifier.clickableText(onExport))
    }
}

private fun Modifier.clickableText(action: () -> Unit, enabled: Boolean = true): Modifier = clickable(enabled = enabled, onClick = action)

/** Responsive shell only. Its explicit canvas and appearance slots let Task 13 keep one shared
 * editor state and one Android pointer owner through orientation recompositions. */
@Composable
fun VectorEditorLayout(
    title: String, state: VectorEditorState, profiles: List<VectorBrushProfile>, onBack: () -> Unit, onExport: () -> Unit,
    onImportArt: () -> Unit, onImportPattern: () -> Unit, modifier: Modifier = Modifier,
    canvas: @Composable BoxScope.() -> Unit, appearance: @Composable () -> Unit,
) {
    val snapshot = state.snapshot.value
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VectorTopBar(title, snapshot.canUndo, snapshot.canRedo, onBack, state::undo, state::redo, onExport)
        BoxWithConstraints(Modifier.weight(1f)) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val density = LocalDensity.current
            var bottomPanelFraction by remember { mutableFloatStateOf(bottomPanelInitialFraction()) }
            val placement = choosePanelPlacement(availableWidth.value, availableHeight.value)
            if (placement == PanelPlacement.RIGHT) Row(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxHeight().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { VectorToolRail(snapshot.tool, false, state::setTool) }
                Box(Modifier.weight(1f).fillMaxHeight(), content = canvas)
                Column(Modifier.width(rightInspectorWidthDp(availableWidth.value).dp).fillMaxHeight().verticalScroll(rememberScrollState())) { appearance() }
            } else Box(Modifier.fillMaxSize()) {
                Row(Modifier.align(Alignment.CenterStart).padding(8.dp)) { VectorToolRail(snapshot.tool, true, state::setTool) }
                Box(Modifier.fillMaxSize(), content = canvas)
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(availableHeight * bottomPanelFraction)
                        .draggable(
                            state = rememberDraggableState { delta ->
                                val totalPx = with(density) { availableHeight.toPx() }.coerceAtLeast(1f)
                                bottomPanelFraction = (bottomPanelFraction - delta / totalPx).coerceIn(.16f, .86f)
                            }, orientation = Orientation.Vertical,
                        ).background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()),
                ) {
                    Text("모양", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
                    appearance()
                }
            }
        }
    }
}
