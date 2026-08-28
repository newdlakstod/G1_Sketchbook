package com.g1.sketchbook.brush

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// 사용자가 image/icon/*.svg로 넘겨준 원본(Lucide 아이콘셋, 24×24 뷰포트) 패스를 그대로 옮겨왔다
// (2026-08-29) — 그전엔 샘플 스크린샷만 보고 손으로 따라 그렸었는데, 실제 SVG를 받아서 좌표를
// 그대로 포팅했으니 이번엔 정확히 같은 모양이다. Icon()이 tint를 ColorFilter로 덮어씌우므로 stroke
// 색 자체는 아무 값이나 상관없음 — 실제로 보이는 색은 항상 호출부의 tint를 따른다.
private const val LineStrokeWidth = 2f

/** 회전 — Lucide "refresh-cw"(2026-08-29, 사용자가 아이콘 파일을 새 것으로 교체). */
val IconRotateLine: ImageVector by lazy {
    ImageVector.Builder(name = "RotateLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8
            moveTo(21f, 12f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -9f, dy1 = -9f)
            curveToRelative(2.52f, 0f, 4.93f, 1f, 6.74f, 2.74f)
            lineTo(21f, 8f)
            // M21 3v5h-5
            moveTo(21f, 3f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(-5f)
        }
    }.build()
}

/** 읽기모드 — Lucide "book-open". */
val IconBookLine: ImageVector by lazy {
    ImageVector.Builder(name = "BookLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M12 7v14
            moveTo(12f, 7f)
            verticalLineToRelative(14f)
            // M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z
            moveTo(3f, 18f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1f, dy1 = -1f)
            verticalLineTo(4f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1f, dy1 = -1f)
            horizontalLineToRelative(5f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 4f, dy1 = 4f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 4f, dy1 = -4f)
            horizontalLineToRelative(5f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1f, dy1 = 1f)
            verticalLineToRelative(13f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1f, dy1 = 1f)
            horizontalLineToRelative(-6f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -3f, dy1 = 3f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -3f, dy1 = -3f)
            close()
        }
    }.build()
}

/** 레이어 — Lucide "layers". */
val IconLayersLine: ImageVector by lazy {
    ImageVector.Builder(name = "LayersLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z
            moveTo(12.83f, 2.18f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1.66f, dy1 = 0f)
            lineTo(2.6f, 6.08f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0f, dy1 = 1.83f)
            lineToRelative(8.58f, 3.91f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.66f, dy1 = 0f)
            lineToRelative(8.58f, -3.9f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0f, dy1 = -1.83f)
            close()
            // M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12
            moveTo(2f, 12f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.58f, dy1 = 0.91f)
            lineToRelative(8.6f, 3.91f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.65f, dy1 = 0f)
            lineToRelative(8.58f, -3.9f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 22f, y1 = 12f)
            // M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17
            moveTo(2f, 17f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.58f, dy1 = 0.91f)
            lineToRelative(8.6f, 3.91f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.65f, dy1 = 0f)
            lineToRelative(8.58f, -3.9f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 22f, y1 = 17f)
        }
    }.build()
}

/** 라쏘 — Lucide "lasso". */
val IconLassoLine: ImageVector by lazy {
    ImageVector.Builder(name = "LassoLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M3.704 14.467a10 8 0 1 1 3.115 2.375
            moveTo(3.704f, 14.467f)
            arcToRelative(10f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 3.115f, dy1 = 2.375f)
            // M7 22a5 5 0 0 1-2-3.994
            moveTo(7f, 22f)
            arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -3.994f)
            // circle cx=5 cy=16 r=2
            moveTo(7f, 16f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 16f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 7f, y1 = 16f)
            close()
        }
    }.build()
}

/** 전체화면 — Lucide "maximize"(모서리 4개 꺾쇠). "전체화면 종료" 상태는 대응 파일을 안 받아서
 *  Material의 FullscreenExit를 그대로 둔다(2026-08-29). */
val IconFullscreenLine: ImageVector by lazy {
    ImageVector.Builder(name = "FullscreenLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M8 3H5a2 2 0 0 0-2 2v3
            moveTo(8f, 3f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2f, dy1 = 2f)
            verticalLineToRelative(3f)
            // M21 8V5a2 2 0 0 0-2-2h-3
            moveTo(21f, 8f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2f, dy1 = -2f)
            horizontalLineToRelative(-3f)
            // M3 16v3a2 2 0 0 0 2 2h3
            moveTo(3f, 16f)
            verticalLineToRelative(3f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = 2f)
            horizontalLineToRelative(3f)
            // M16 21h3a2 2 0 0 0 2-2v-3
            moveTo(16f, 21f)
            horizontalLineToRelative(3f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = -2f)
            verticalLineToRelative(-3f)
        }
    }.build()
}

/** 선생님모드 켜짐 — Lucide "screen-share"(모니터 + 밖으로 나가는 화살표). */
val IconScreenSharedLine: ImageVector by lazy {
    ImageVector.Builder(name = "ScreenSharedLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3
            moveTo(13f, 3f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2f, dy1 = 2f)
            verticalLineToRelative(10f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = 2f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = -2f)
            verticalLineToRelative(-3f)
            // M8 21h8
            moveTo(8f, 21f)
            horizontalLineToRelative(8f)
            // M12 17v4
            moveTo(12f, 17f)
            verticalLineToRelative(4f)
            // m17 8 5-5
            moveTo(17f, 8f)
            lineToRelative(5f, -5f)
            // M17 3h5v5
            moveTo(17f, 3f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(5f)
        }
    }.build()
}

/** 선생님모드 꺼짐 — Lucide "screen-share-off"(모니터 + 모서리 X 표시). */
val IconScreenUnsharedLine: ImageVector by lazy {
    ImageVector.Builder(name = "ScreenUnsharedLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3
            moveTo(13f, 3f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2f, dy1 = 2f)
            verticalLineToRelative(10f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = 2f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = -2f)
            verticalLineToRelative(-3f)
            // M8 21h8
            moveTo(8f, 21f)
            horizontalLineToRelative(8f)
            // M12 17v4
            moveTo(12f, 17f)
            verticalLineToRelative(4f)
            // m22 3-5 5
            moveTo(22f, 3f)
            lineToRelative(-5f, 5f)
            // m17 3 5 5
            moveTo(17f, 3f)
            lineToRelative(5f, 5f)
        }
    }.build()
}
