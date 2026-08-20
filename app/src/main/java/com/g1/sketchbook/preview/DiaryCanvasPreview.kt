package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.diary.DiaryEditorScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

@Preview(
    name = "14 Diary editor",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun DiaryEditorPreview() {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        DiaryEditorScreen(date = "2026-08-17", onBack = {}, previewMode = true)
    }
}
