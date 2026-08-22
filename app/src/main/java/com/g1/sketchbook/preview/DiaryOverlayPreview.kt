package com.g1.sketchbook.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.diary.CalendarOverlayPlacementScreen
import com.g1.sketchbook.diary.CleanCalendarScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode
import java.util.Calendar

/** 미리보기 전용 — 실제 기기/저장소 없이도 "일기 상세" 화면과 3번째 다운로드 옵션(달력 오버레이
 *  배치 화면)을 볼 수 있도록, repo가 없어도 쓸 수 있는 오늘 날짜 샘플 스케치 비트맵을 만든다.
 *  CleanCalendarScreen/CleanDetailBody는 previewMode일 때 항상 repo=null이라 실제 그림이 없으면
 *  "이 날의 일기가 없어요"만 뜨는데, 그래서는 다운로드 옵션(저장 버튼 자체가 그림이 있을 때만
 *  나타남)을 Preview에서 확인할 방법이 없었다. */
private fun todayDateString(): String {
    val cal = Calendar.getInstance()
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

private fun sampleDiarySketch(): Bitmap {
    val size = 900
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.parseColor("#FFFBF3"))

    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3A3A"); style = Paint.Style.STROKE
        strokeWidth = 10f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2C14E"); style = Paint.Style.FILL }
    val hill1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A9C99B"); style = Paint.Style.FILL }
    val hill2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8FB07E"); style = Paint.Style.FILL }

    canvas.drawCircle(size * 0.76f, size * 0.22f, size * 0.09f, sun)

    val back = Path().apply {
        moveTo(0f, size * 0.62f)
        quadTo(size * 0.3f, size * 0.48f, size * 0.55f, size * 0.6f)
        quadTo(size * 0.8f, size * 0.7f, size.toFloat(), size * 0.56f)
        lineTo(size.toFloat(), size.toFloat()); lineTo(0f, size.toFloat()); close()
    }
    canvas.drawPath(back, hill1)

    val front = Path().apply {
        moveTo(0f, size * 0.78f)
        quadTo(size * 0.25f, size * 0.68f, size * 0.5f, size * 0.8f)
        quadTo(size * 0.75f, size * 0.92f, size.toFloat(), size * 0.76f)
        lineTo(size.toFloat(), size.toFloat()); lineTo(0f, size.toFloat()); close()
    }
    canvas.drawPath(front, hill2)

    // A little wobbly freehand line across the sky, like a bird or a scribble.
    val scribble = Path().apply {
        moveTo(size * 0.12f, size * 0.32f)
        quadTo(size * 0.2f, size * 0.26f, size * 0.28f, size * 0.32f)
        quadTo(size * 0.36f, size * 0.38f, size * 0.44f, size * 0.3f)
    }
    canvas.drawPath(scribble, ink)

    return bmp
}

@Preview(name = "20 Diary day detail - sample sketch", showBackground = true, widthDp = 475, heightDp = 751)
@Composable
private fun DiaryDetailWithSketchPreview() = DaymoryTheme(mode = ThemeMode.LIGHT) {
    val today = remember { todayDateString() }
    val sketch = remember { sampleDiarySketch() }
    val cal = remember { Calendar.getInstance() }
    CleanCalendarScreen(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH),
        onBack = {},
        previewDetailDate = today,
        previewMode = true,
        previewBitmap = sketch,
    )
}

@Preview(name = "21 Calendar overlay placement", showBackground = true, widthDp = 475, heightDp = 751)
@Composable
private fun CalendarOverlayPlacementPreview() = DaymoryTheme(mode = ThemeMode.LIGHT) {
    val today = remember { todayDateString() }
    val sketch = remember { sampleDiarySketch() }
    CalendarOverlayPlacementScreen(bmp = sketch, date = today, onCancel = {}, onSave = {})
}
