package com.g1.sketchbook.vector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VectorBrushLibrary(
    profiles: List<VectorBrushProfile>, selected: BrushStyle, onSelect: (BrushStyle) -> Unit,
    onImportArt: () -> Unit, onImportPattern: () -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("브러시 라이브러리", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onClose) { Text("닫기") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onImportArt) { Text("Art 가져오기") }; Button(onClick = onImportPattern) { Text("Pattern 가져오기") } }
        profiles.forEach { profile ->
            val kind = if (profile is ArtBrushProfile) VectorBrushKind.ART else VectorBrushKind.PATTERN
            Row(Modifier.fillMaxWidth().clickable { onSelect(BrushStyle(kind, profile.id)) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(profile.name + if (selected.profileId == profile.id) "  ✓" else "")
                Row { Text("이름", Modifier.clickable { onRename(profile.id, profile.name) }.padding(4.dp)); Text("삭제", Modifier.clickable { onDelete(profile.id) }.padding(4.dp)) }
            }
        }
    }
}
