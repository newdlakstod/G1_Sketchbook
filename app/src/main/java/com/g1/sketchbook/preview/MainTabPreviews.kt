package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

private const val PREVIEW_WIDTH = 475
private const val PREVIEW_HEIGHT = 751
// 가로모드(태블릿) 미리보기 — 홈 탭의 2열(읽기모드)/3열(표지리스트) 같은 landscape 전용 레이아웃은
// 세로 프리뷰만으론 아예 안 보였다(2026-08-29). LocalConfiguration.orientation은 프리뷰 툴링이
// widthDp/heightDp로부터 유추해서, 폭>높이면 자동으로 LANDSCAPE로 잡힌다 — 실제 태블릿 가로 화면과
// 비슷한 4:3 비율(1024×768)을 씀.
private const val PREVIEW_LANDSCAPE_WIDTH = 1024
private const val PREVIEW_LANDSCAPE_HEIGHT = 768

@Preview(name = "05 Home", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun HomePreview() = MainTabPreview(tab = 0)

@Preview(name = "05L Home (landscape)", showBackground = true, widthDp = PREVIEW_LANDSCAPE_WIDTH, heightDp = PREVIEW_LANDSCAPE_HEIGHT)
@Composable
private fun HomeLandscapePreview() = MainTabPreview(tab = 0)

@Preview(name = "06 Personal sketchbooks", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun PersonalBooksPreview() = MainTabPreview(tab = 1)

@Preview(name = "06L Personal sketchbooks (landscape)", showBackground = true, widthDp = PREVIEW_LANDSCAPE_WIDTH, heightDp = PREVIEW_LANDSCAPE_HEIGHT)
@Composable
private fun PersonalBooksLandscapePreview() = MainTabPreview(tab = 1)

@Preview(name = "07 Shared sketchbooks", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SharedBooksPreview() = MainTabPreview(tab = 2)

@Preview(name = "07L Shared sketchbooks (landscape)", showBackground = true, widthDp = PREVIEW_LANDSCAPE_WIDTH, heightDp = PREVIEW_LANDSCAPE_HEIGHT)
@Composable
private fun SharedBooksLandscapePreview() = MainTabPreview(tab = 2)

@Preview(name = "08 Diary", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun DiaryTabPreview() = MainTabPreview(tab = 3)

@Preview(name = "08L Diary (landscape)", showBackground = true, widthDp = PREVIEW_LANDSCAPE_WIDTH, heightDp = PREVIEW_LANDSCAPE_HEIGHT)
@Composable
private fun DiaryTabLandscapePreview() = MainTabPreview(tab = 3)

@Preview(name = "09 Settings", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SettingsPreview() = MainTabPreview(tab = 4)

@Preview(name = "09L Settings (landscape)", showBackground = true, widthDp = PREVIEW_LANDSCAPE_WIDTH, heightDp = PREVIEW_LANDSCAPE_HEIGHT)
@Composable
private fun SettingsLandscapePreview() = MainTabPreview(tab = 4)

@Composable
private fun MainTabPreview(tab: Int) {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        MainScreen(
            nickname = "Minjun",
            avatarVersion = 0,
            tab = tab,
            theme = ThemeMode.LIGHT,
            myUid = "preview-user",
            onTab = {},
            onTheme = {},
            onSignOut = {},
            onRename = {},
            onSetAvatarImage = {},
            onOpenBook = {},
            onOpenDiary = {},
            onOpenCalendar = { _, _ -> },
            previewBooks = PreviewBooks,
            previewDiaryDates = PreviewDiaryDates,
        )
    }
}
