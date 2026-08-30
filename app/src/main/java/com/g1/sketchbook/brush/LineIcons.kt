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

/** 이미지로 저장 — Lucide "image-down"(사진 프레임 + 우하단 다운로드 화살표). 페이지/올가미 선택
 *  영역/일기 다운로드 등 "이미지로 저장" 계열 버튼에 공통으로 쓴다(2026-08-30, 기존 Material
 *  Save 아이콘을 사용자가 넘겨준 실제 SVG로 교체). */
val IconImageSaveLine: ImageVector by lazy {
    ImageVector.Builder(name = "ImageSaveLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M10.3 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10l-3.1-3.1a2 2 0 0 0-2.814.014L6 21
            moveTo(10.3f, 21f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
            horizontalLineToRelative(14f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
            verticalLineToRelative(10f)
            lineToRelative(-3.1f, -3.1f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2.814f, dy1 = 0.014f)
            lineTo(6f, 21f)
            // m14 19 3 3v-5.5
            moveTo(14f, 19f)
            lineToRelative(3f, 3f)
            verticalLineToRelative(-5.5f)
            // m17 22 3-3
            moveTo(17f, 22f)
            lineToRelative(3f, -3f)
            // circle cx=9 cy=9 r=2
            moveTo(11f, 9f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 7f, y1 = 9f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11f, y1 = 9f)
            close()
        }
    }.build()
}

/** 개인(홈 탭 상단) — Lucide "user"(원 머리 + 어깨선), 2026-08-30 사용자가 넘겨준 SVG로 기존
 *  Material Person 아이콘을 교체. */
val IconUserLine: ImageVector by lazy {
    ImageVector.Builder(name = "UserLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // circle cx=12 cy=8 r=5
            moveTo(17f, 8f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 7f, y1 = 8f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17f, y1 = 8f)
            close()
            // M20 21a8 8 0 0 0-16 0
            moveTo(20f, 21f)
            arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -16f, dy1 = 0f)
        }
    }.build()
}

/** 공유(홈 탭 상단) — Lucide "users"(사람 둘), 2026-08-30 사용자가 넘겨준 SVG로 기존 Material
 *  Groups 아이콘을 교체. */
val IconPeopleLine: ImageVector by lazy {
    ImageVector.Builder(name = "PeopleLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M18 21a8 8 0 0 0-16 0
            moveTo(18f, 21f)
            arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -16f, dy1 = 0f)
            // circle cx=10 cy=8 r=5
            moveTo(15f, 8f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 5f, y1 = 8f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 15f, y1 = 8f)
            close()
            // M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3
            moveTo(22f, 20f)
            curveToRelative(0f, -3.37f, -2f, -6.5f, -4f, -8f)
            arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -0.45f, dy1 = -8.3f)
        }
    }.build()
}

/** 공유노트 새로 만들기 — Lucide "file-plus-2"(문서 + 우하단 + 표시), 2026-08-30 사용자가 넘겨준
 *  SVG로 기존 Material GroupAdd 아이콘을 교체. */
val IconNewNoteLine: ImageVector by lazy {
    ImageVector.Builder(name = "NewNoteLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M11.35 22H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.706.706l3.588 3.588A2.4 2.4 0 0 1 20 8v5.35
            moveTo(11.35f, 22f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
            verticalLineTo(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
            horizontalLineToRelative(8f)
            arcToRelative(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1.706f, dy1 = 0.706f)
            lineToRelative(3.588f, 3.588f)
            arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 8f)
            verticalLineToRelative(5.35f)
            // M14 2v5a1 1 0 0 0 1 1h5
            moveTo(14f, 2f)
            verticalLineToRelative(5f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = 1f)
            horizontalLineToRelative(5f)
            // M14 19h6
            moveTo(14f, 19f)
            horizontalLineToRelative(6f)
            // M17 16v6
            moveTo(17f, 16f)
            verticalLineToRelative(6f)
        }
    }.build()
}

/** 초대 코드로 참가 — Lucide "key-round", 2026-08-30 사용자가 넘겨준 SVG로 기존 Material Key
 *  아이콘을 교체. 열쇠 머리의 작은 구멍(circle)만 원본대로 채워서 그린다. */
val IconKeyLine: ImageVector by lazy {
    ImageVector.Builder(name = "KeyLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z
            moveTo(2.586f, 17.414f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 2f, y1 = 18.828f)
            verticalLineTo(21f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = 1f)
            horizontalLineToRelative(3f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = -1f)
            verticalLineToRelative(-1f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1f, dy1 = -1f)
            horizontalLineToRelative(1f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = -1f)
            verticalLineToRelative(-1f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 1f, dy1 = -1f)
            horizontalLineToRelative(0.172f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.414f, dy1 = -0.586f)
            lineToRelative(0.814f, -0.814f)
            arcToRelative(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -4f, dy1 = -4f)
            close()
        }
        path(
            fill = SolidColor(Color.Black), stroke = null,
        ) {
            // circle cx=16.5 cy=7.5 r=.5 (열쇠 머리 구멍 — 원본대로 채움)
            moveTo(17f, 7.5f)
            arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 16f, y1 = 7.5f)
            arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17f, y1 = 7.5f)
            close()
        }
    }.build()
}

/** 그리기(오늘 일기 탭) — Lucide "pen-line", 2026-08-30 사용자가 넘겨준 SVG로 기존 Material Edit
 *  아이콘을 교체. */
val IconDrawLine: ImageVector by lazy {
    ImageVector.Builder(name = "DrawLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z
            moveTo(21.174f, 6.812f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -3.986f, dy1 = -3.987f)
            lineTo(3.842f, 16.174f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -0.5f, dy1 = 0.83f)
            lineToRelative(-1.321f, 4.352f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.623f, dy1 = 0.622f)
            lineToRelative(4.353f, -1.32f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.83f, dy1 = -0.497f)
            close()
            // m15 5 4 4
            moveTo(15f, 5f)
            lineToRelative(4f, 4f)
        }
    }.build()
}

/** 벡터 스케치북 만들기 — Lucide "spline-pointer"(펜/포인터 + 곡선 + 앵커점 2개), 2026-08-30
 *  사용자가 넘겨준 image/icon/vector.svg로 기존 IconImageSaveLine 임시 사용을 교체. */
val IconVectorLine: ImageVector by lazy {
    ImageVector.Builder(name = "VectorLine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LineStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            // M12.034 12.681a.498.498 0 0 1 .647-.647l9 3.5a.5.5 0 0 1-.033.943l-3.444 1.068a1 1 0 0 0-.66.66l-1.067 3.443a.5.5 0 0 1-.943.033z
            moveTo(12.034f, 12.681f)
            arcToRelative(0.498f, 0.498f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0.647f, dy1 = -0.647f)
            lineToRelative(9f, 3.5f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.033f, dy1 = 0.943f)
            lineToRelative(-3.444f, 1.068f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -0.66f, dy1 = 0.66f)
            lineToRelative(-1.067f, 3.443f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -0.943f, dy1 = 0.033f)
            close()
            // M5 17A12 12 0 0 1 17 5
            moveTo(5f, 17f)
            arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17f, y1 = 5f)
            // circle cx=19 cy=5 r=2
            moveTo(21f, 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17f, y1 = 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 5f)
            close()
            // circle cx=5 cy=19 r=2
            moveTo(7f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 7f, y1 = 19f)
            close()
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
