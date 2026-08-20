package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.sketchbook.EditCoverDialog
import com.g1.sketchbook.sketchbook.SketchbookTab
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

@Preview(
    name = "19 Edit cover",
    showBackground = true,
    widthDp = 475,
    heightDp = 751,
)
@Composable
private fun EditCoverPreview() {
    DaymoryTheme(mode = ThemeMode.LIGHT) {
        var book by remember { mutableStateOf(PreviewBooks[1]) }
        // 뒤 배경으로 실제 목록 화면을 그대로 두고(길게 눌러 여는 실제 진입 경로와 같은 맥락), 그
        // 위에 표지 수정 시트를 처음부터 펼쳐서 보여준다 — Dialog는 별도 윈도우라 형제로 놓아도
        // 겹쳐 뜬다(OnboardingPreviews.kt의 LoginScreen+NicknameDialog와 같은 패턴).
        SketchbookTab(nickname = "Minjun", myUid = "preview-user", onOpenBook = {}, previewBooks = PreviewBooks)
        EditCoverDialog(
            book = book,
            repo = null,
            onCancel = {},
            onSave = { name, _, _, newColor -> book = book.copy(name = name, coverColor = newColor) },
            onToggleFav = { book = book.copy(fav = !book.fav) },
            onDelete = {},
        )
    }
}
