package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

@Preview(
    name = "Home screen",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun HomeScreenPreview() {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        MainScreen(
            nickname = "Minjun",
            avatar = "",
            tab = 0,
            theme = ThemeMode.LIGHT,
            myUid = "preview",
            onTab = {},
            onTheme = {},
            onSignOut = {},
            onRename = {},
            onSetAvatar = {},
            onOpenBook = {},
            onOpenDiary = {},
            onOpenCalendar = { _, _ -> },
        )
    }
}
