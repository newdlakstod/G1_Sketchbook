package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

private const val PREVIEW_WIDTH = 475
private const val PREVIEW_HEIGHT = 751

@Preview(name = "05 Home", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun HomePreview() = MainTabPreview(tab = 0)

@Preview(name = "06 Personal sketchbooks", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun PersonalBooksPreview() = MainTabPreview(tab = 1)

@Preview(name = "07 Shared sketchbooks", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SharedBooksPreview() = MainTabPreview(tab = 2)

@Preview(name = "08 Diary", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun DiaryTabPreview() = MainTabPreview(tab = 3)

@Preview(name = "09 Settings", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SettingsPreview() = MainTabPreview(tab = 4)

@Composable
private fun MainTabPreview(tab: Int) {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        MainScreen(
            nickname = "Minjun",
            avatar = "🦆",
            tab = tab,
            theme = ThemeMode.LIGHT,
            myUid = "preview-user",
            onTab = {},
            onTheme = {},
            onSignOut = {},
            onRename = {},
            onSetAvatar = {},
            onOpenBook = {},
            onOpenDiary = {},
            onOpenCalendar = { _, _ -> },
            previewBooks = PreviewBooks,
            previewDiaryDates = PreviewDiaryDates,
        )
    }
}
