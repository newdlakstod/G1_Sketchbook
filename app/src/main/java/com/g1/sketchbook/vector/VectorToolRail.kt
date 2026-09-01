package com.g1.sketchbook.vector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VectorToolRail(selected: VectorTool, compact: Boolean, onSelect: (VectorTool) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        listOf(VectorTool.SELECT to "선택", VectorTool.PEN to "펜", VectorTool.ERASER to "지우개", VectorTool.HAND to "손").forEach { (tool, label) ->
            Text(label, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).background(if (tool == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).clickable { onSelect(tool) }.padding(horizontal = 10.dp, vertical = 14.dp), color = if (tool == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
