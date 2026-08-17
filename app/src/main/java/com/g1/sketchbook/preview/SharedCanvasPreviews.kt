package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.share.SharedBookPreviewScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

private const val PREVIEW_WIDTH = 475
private const val PREVIEW_HEIGHT = 751

@Preview(name = "17 Shared canvas - grid", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SharedGridPreview() = PreviewTheme {
    SharedBookPreviewScreen(startMaximized = false)
}

@Preview(name = "18 Shared canvas - maximize", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SharedMaximizePreview() = PreviewTheme {
    SharedBookPreviewScreen(startMaximized = true)
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    DaymoryTheme(mode = ThemeMode.LIGHT, content = content)
}
