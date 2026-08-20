package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.diary.CleanCalendarScreen
import com.g1.sketchbook.sketchbook.SketchbookTab
import com.g1.sketchbook.sketchbook.WType
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

private const val PREVIEW_WIDTH = 475
private const val PREVIEW_HEIGHT = 751

@Preview(name = "10 Create personal sketchbook", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun CreatePersonalPreview() = WizardPreview(WType.PERSONAL)

@Preview(name = "11 Create shared sketchbook", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun CreateSharedPreview() = WizardPreview(WType.SHARED_NEW)

@Preview(name = "12 Join shared sketchbook", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun JoinSharedPreview() = WizardPreview(WType.SHARED_JOIN)

@Preview(name = "15 Full calendar", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun FullCalendarPreview() = PreviewTheme {
    CleanCalendarScreen(year = 2026, month = 7, onBack = {}, previewMode = true)
}

@Preview(name = "16 Calendar day detail", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun CalendarDetailPreview() = PreviewTheme {
    CleanCalendarScreen(
        year = 2026,
        month = 7,
        onBack = {},
        previewDetailDate = "2026-08-17",
        previewMode = true,
    )
}

@Composable
private fun WizardPreview(type: WType) = PreviewTheme {
    SketchbookTab(
        nickname = "Minjun",
        myUid = "preview-user",
        onOpenBook = {},
        openWizardAs = type,
        previewBooks = PreviewBooks,
    )
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    DaymoryTheme(mode = ThemeMode.LIGHT, content = content)
}
