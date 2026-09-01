package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.diary.CleanCalendarScreen
import com.g1.sketchbook.sketchbook.SketchbookTab
import com.g1.sketchbook.sketchbook.WType
import com.g1.sketchbook.ui.main.MainTabLayout
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode
import com.g1.sketchbook.vector.EditablePathObject
import com.g1.sketchbook.vector.PathAppearance
import com.g1.sketchbook.vector.PathGeometry
import com.g1.sketchbook.vector.PathPoint
import com.g1.sketchbook.vector.VectorAppearancePanel
import com.g1.sketchbook.vector.VectorCanvasHost
import com.g1.sketchbook.vector.VectorDocument
import com.g1.sketchbook.vector.VectorEditorLayout
import com.g1.sketchbook.vector.VectorEditorState
import com.g1.sketchbook.vector.projectAppearance

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

// 가로모드 프리뷰는 MainTabLayout으로 감싸야 1열 네비게이션 레일이 같이 보인다 — SketchbookTab을
// 바로 부르면 MainTabPage(2·3열)만 그려지고, 1열은 원래 MainTabLayout이 바깥에서 그리는 부분이라 빠짐.
@Preview(name = "17 Sketchbook list - landscape", showBackground = true, widthDp = PREVIEW_HEIGHT, heightDp = PREVIEW_WIDTH)
@Composable
private fun SketchbookListLandscapePreview() = PreviewTheme {
    MainTabLayout(tab = 1, onTab = {}) {
        SketchbookTab(nickname = "Minjun", myUid = "preview-user", onOpenBook = {}, previewBooks = PreviewBooks)
    }
}

@Preview(name = "18 Vector appearance editor - portrait", showBackground = true, widthDp = 833, heightDp = 1280)
@Composable
private fun VectorEditorPortraitPreview() = PreviewTheme { VectorEditorPreviewSample() }

@Preview(name = "19 Vector appearance editor - landscape", showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun VectorEditorLandscapePreview() = PreviewTheme { VectorEditorPreviewSample() }

/** Deliberately in-memory: previews exercise the production responsive layout without storage or Firebase. */
@Composable
private fun VectorEditorPreviewSample() {
    val state = remember {
        VectorEditorState(
            VectorDocument(objects = listOf(
                EditablePathObject(
                    id = "flower",
                    geometry = PathGeometry(listOf(
                        PathPoint(240f, 100f, 1f), PathPoint(315f, 180f, .8f), PathPoint(390f, 100f, 1f),
                        PathPoint(350f, 245f, .9f), PathPoint(430f, 330f, 1f), PathPoint(315f, 285f, .8f),
                        PathPoint(200f, 330f, 1f), PathPoint(280f, 245f, .9f),
                    ), closed = true),
                    appearance = PathAppearance(),
                ),
            )),
        ).also { it.select(setOf("flower")) }
    }
    val snapshot by state.snapshot.collectAsState()
    VectorEditorLayout(
        title = "벡터 스케치",
        state = state,
        profiles = emptyList(),
        onBack = {},
        onExport = {},
        onImportArt = {},
        onImportPattern = {},
        canvas = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { VectorCanvasHost(it).also { host -> host.bind(state) { emptyMap() } } },
                update = { host -> host.bind(state) { emptyMap() } },
            )
        },
        appearance = {
            VectorAppearancePanel(
                projection = projectAppearance(snapshot),
                onEdit = state::applyAppearance,
                onGestureStart = state::beginAppearanceGesture,
                onGestureCommit = state::commitAppearanceGesture,
                onOpenBrushLibrary = {},
                onToggleClosed = state::toggleSelectedClosed,
            )
        },
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
