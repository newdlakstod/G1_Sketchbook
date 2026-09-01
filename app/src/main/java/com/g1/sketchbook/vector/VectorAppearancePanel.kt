package com.g1.sketchbook.vector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

data class ProjectedValue<T>(val value: T, val mixed: Boolean = false)
data class AppearanceProjection(
    val title: String,
    val fillEnabled: ProjectedValue<Boolean>,
    val fillColor: ProjectedValue<Long>,
    val strokeEnabled: ProjectedValue<Boolean>,
    val strokeColor: ProjectedValue<Long>,
    val strokeWidth: ProjectedValue<Float>,
    val cap: ProjectedValue<VectorCap>,
    val join: ProjectedValue<VectorJoin>,
    val brush: ProjectedValue<BrushStyle>,
    val closed: ProjectedValue<Boolean>,
    val canToggleClosed: Boolean,
    val closeTarget: Boolean,
    val hasLegacySelection: Boolean,
    val missingProfile: Boolean,
    val previewBrush: BrushStyle,
)

private fun VectorObject.effectiveAppearance(): PathAppearance = when (this) {
    is EditablePathObject -> appearance
    is LegacyStrokeObject -> convertLegacyObject(this).appearance
}

private fun VectorObject.effectiveClosed(): Boolean = (this as? EditablePathObject)?.geometry?.closed ?: false

private fun <T> projected(values: List<T>, fallback: T): ProjectedValue<T> =
    if (values.isEmpty()) ProjectedValue(fallback) else ProjectedValue(values.first(), values.any { it != values.first() })

/** Pure panel model. Reading legacy values goes through an ephemeral conversion only, so merely
 * opening the inspector never changes document object types. */
fun projectAppearance(snapshot: VectorEditorSnapshot, profiles: Map<String, VectorBrushProfile> = emptyMap()): AppearanceProjection {
    val selected = snapshot.document.objects.filter { it.id in snapshot.selectedIds }
    val appearances = selected.map(VectorObject::effectiveAppearance).ifEmpty { listOf(snapshot.defaultAppearance) }
    val brush = projected(appearances.map(PathAppearance::brush), snapshot.defaultAppearance.brush)
    val missing = brush.value.kind != VectorBrushKind.BASIC && !brush.value.profileId.isNullOrBlank() && profiles[brush.value.profileId] == null
    val closedValues = selected.map(VectorObject::effectiveClosed)
    val closed = projected(closedValues, false)
    return AppearanceProjection(
        title = if (selected.isEmpty()) "새 패스 스타일" else "패스",
        fillEnabled = projected(appearances.map { it.fill.enabled }, snapshot.defaultAppearance.fill.enabled),
        fillColor = projected(appearances.map { it.fill.color }, snapshot.defaultAppearance.fill.color),
        strokeEnabled = projected(appearances.map { it.stroke.enabled }, snapshot.defaultAppearance.stroke.enabled),
        strokeColor = projected(appearances.map { it.stroke.color }, snapshot.defaultAppearance.stroke.color),
        strokeWidth = projected(appearances.map { it.stroke.width }, snapshot.defaultAppearance.stroke.width),
        cap = projected(appearances.map { it.stroke.cap }, snapshot.defaultAppearance.stroke.cap),
        join = projected(appearances.map { it.stroke.join }, snapshot.defaultAppearance.stroke.join),
        brush = brush,
        closed = closed,
        canToggleClosed = selected.isNotEmpty(),
        closeTarget = selected.isNotEmpty() && closedValues.any { !it },
        hasLegacySelection = selected.any { it is LegacyStrokeObject },
        missingProfile = missing,
        previewBrush = if (missing) BrushStyle() else brush.value,
    )
}

@Composable
fun VectorAppearancePanel(
    projection: AppearanceProjection,
    onEdit: (AppearanceEdit) -> Unit,
    onGestureStart: () -> Unit,
    onGestureCommit: () -> Unit,
    onOpenBrushLibrary: () -> Unit,
    onToggleClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val basic = projection.previewBrush.kind == VectorBrushKind.BASIC
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(projection.title, style = MaterialTheme.typography.titleMedium); Text(if (projection.title == "패스") "선택한 패스의 모양" else "다음에 그릴 패스의 모양", style = MaterialTheme.typography.bodySmall) }
            BrushCurvePreview(projection.strokeColor.value)
        }
        if (projection.missingProfile) Text("브러시를 찾을 수 없어 기본 미리보기로 표시합니다.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(projection.fillColor.value, "채움", projection.fillEnabled.value) { onEdit(AppearanceEdit.FillEnabled(!projection.fillEnabled.value)) }
            Spacer(Modifier.width((-10).dp))
            Swatch(projection.strokeColor.value, "획", projection.strokeEnabled.value) { onEdit(AppearanceEdit.StrokeEnabled(!projection.strokeEnabled.value)) }
            Spacer(Modifier.width(16.dp)); Text("채움 / 획")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("채움", modifier = Modifier.weight(1f)); Switch(projection.fillEnabled.value, { onEdit(AppearanceEdit.FillEnabled(it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("획", modifier = Modifier.weight(1f)); Switch(projection.strokeEnabled.value, { onEdit(AppearanceEdit.StrokeEnabled(it)) })
        }
        Text("굵기 ${if (projection.strokeWidth.mixed) "혼합" else projection.strokeWidth.value.toInt()}")
        Slider(
            value = projection.strokeWidth.value.coerceIn(0f, 200f), valueRange = 0f..200f,
            onValueChange = { onGestureStart(); onEdit(AppearanceEdit.StrokeWidth(it)) }, onValueChangeFinished = onGestureCommit,
        )
        Text("브러시")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VectorBrushKind.entries.forEach { kind ->
                val selected = projection.brush.value.kind == kind
                Box(Modifier.background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).clickable { onEdit(AppearanceEdit.Brush(BrushStyle(kind, if (kind == projection.brush.value.kind) projection.brush.value.profileId else null))) }.padding(horizontal = 12.dp, vertical = 8.dp)) { Text(kind.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VectorCap.entries.forEach { cap -> SegmentedOption(cap.name, cap == projection.cap.value, basic) { onEdit(AppearanceEdit.Cap(cap)) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VectorJoin.entries.forEach { join -> SegmentedOption(join.name, join == projection.join.value, basic) { onEdit(AppearanceEdit.Join(join)) } }
        }
        Button(onClick = onOpenBrushLibrary) { Text("브러시 라이브러리") }
        if (projection.canToggleClosed) Button(onClick = onToggleClosed) { Text(if (projection.closeTarget) "패스 닫기" else "패스 열기") }
    }
}

@Composable private fun Swatch(color: Long, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).background(Color(color), RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun SegmentedOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (selected && enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)) { Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun BrushCurvePreview(color: Long) = Canvas(Modifier.size(86.dp, 36.dp)) {
    drawLine(Color(color), Offset(size.width * .12f, size.height * .72f), Offset(size.width * .88f, size.height * .28f), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
}
