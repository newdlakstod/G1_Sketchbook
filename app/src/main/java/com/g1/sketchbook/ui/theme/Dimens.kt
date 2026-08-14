package com.g1.sketchbook.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 앱 전반에서 쓰는 치수(dp/sp)를 한 곳에 모은 중앙 설정값.
 *
 * 여기 숫자만 바꾸면 해당 화면에 자동 반영됩니다(앱 다시 빌드하면 적용).
 * 새 치수를 추가하고 싶으면 아래에 값을 만들고, 화면 코드에서 `Dimens.<그룹>.<이름>` 으로 참조하세요.
 */
object Dimens {

    /** 일기달력 탭 (슬라이드 2, DiaryCalendarScreen / AiryCalendar) */
    object Calendar {
        val topSpacer = 110.dp      // 연도 위 상단 여백
        val sideMargin = 24.dp      // 좌우 여백
        val titleGap = 24.dp        // 월 타이틀과 달력 그리드 사이 간격
        val yearSp = 60.sp          // 연도 글자 크기
        val monthSp = 100.sp        // 월(August) 글자 크기
        val weekdaySp = 25.sp       // 요일(Sun..Sat) 글자 크기
        val daySp = 21.sp           // 날짜 숫자 크기
        val arrowIcon = 35.dp       // 이전/다음 달 화살표 아이콘 크기
        val todayDisc = 38.dp       // 오늘 핑크 원 지름
    }

    /** 클린 달력 (슬라이드 3·4, CleanCalendarScreen) — 배경화면 캡처용, 바 없음 */
    object CleanCalendar {
        val sidePadding = 44.dp     // 좌우 여백
        val topPadding = 30.dp      // 상단 여백
        val bottomPadding = 30.dp   // 하단 여백
        val titleGap = 14.dp        // 타이틀과 표 사이 간격
        val yearSp = 30.sp          // 연도 글자 크기
        val monthSp = 70.sp         // 월 글자 크기
    }

    /** 캔버스/드로잉 (BrushView, 캔버스 화면들) */
    object Canvas {
        val outerPadding = 24.dp    // 캔버스 뷰 바깥 여백
        val minZoom = 0.3f          // fit 이하 줌아웃 최소 배율(작게 = 주변 공간 넓어짐)
        val maxZoom = 5f            // 최대 확대 배율
    }

    /** 브러시 종류별 기본 굵기(dp) + 지우개 기본 굵기 */
    object Brush {
        val penWidth = 10f
        val pencilWidth = 12f
        val crayonWidth = 16f
        val waterWidth = 20f
        val eraserWidth = 24f
    }
}
