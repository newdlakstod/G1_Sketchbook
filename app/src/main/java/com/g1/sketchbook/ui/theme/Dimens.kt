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

    /** 화면 공통 */
    object Screen {
        val bottomMargin = 45.dp    // 하단 네비게이션 바 위 여백 (홈/마법사/리스트/클린달력 공통)
    }

    /** 온보딩 (Splash/Login) */
    object Onboarding {
        val titleSp = 130.sp        // "Daily sketch" 타이틀
        val subtitleSp = 37.sp      // "Draw together, keep the little days" 부제
    }

    /** 홈 탭 */
    object Home {
        val titleSp = 78.sp             // "Draw your time" 탭 타이틀
        val titleToIconGap = 68.dp      // 타이틀 ~ 아이콘 행 사이 간격
        val actionIcon = 56.dp          // 새 노트/공유/참여 원형 아이콘 버튼 지름 (명시 안 됨, 추정치)
        val carouselCenterW = 267.5.dp  // 캐러셀 가운데(포커스) 노트 너비
        val carouselCenterH = 402.5.dp  // 캐러셀 가운데(포커스) 노트 높이
        val carouselSideW = 217.dp      // 캐러셀 옆(비포커스) 노트 너비
        val carouselSideH = 327.dp      // 캐러셀 옆(비포커스) 노트 높이
    }

    /** 새 스케치북 만들기 마법사 */
    object Wizard {
        val titleSp = 78.sp         // 탭 타이틀과 동일 크기
    }

    /** 스케치북 리스트 탭 */
    object SketchbookList {
        val titleSp = 78.sp         // "Sketchbook list" 타이틀
    }

    /** 일기달력 탭 (슬라이드 2, DiaryCalendarScreen / AiryCalendar) */
    object Calendar {
        val topSpacer = 63.5.dp     // 연도 위 상단 여백
        val sideMargin = 71.dp      // 좌우 여백
        val titleGap = 24.dp        // 월 타이틀과 달력 그리드 사이 간격
        val yearSp = 63.sp          // 연도 글자 크기
        val monthSp = 113.sp        // 월(Jaunaly) 글자 크기
        val weekdaySp = 26.sp       // 요일(Sun..Sat) 글자 크기
        val daySp = 21.sp           // 날짜 숫자 크기
        val arrowIcon = 35.dp       // 이전/다음 달 화살표 아이콘 크기
        val editIcon = 35.dp        // 오늘 일기 편집(연필) 아이콘 크기
        val todayDisc = 38.dp       // 오늘 핑크 원 지름
        val bottomMargin = 45.dp    // 하단 네비게이션 바 위 여백
    }

    /** 클린 달력 (슬라이드 3·4, CleanCalendarScreen) — 배경화면 캡처용, 바 없음 */
    object CleanCalendar {
        val sidePadding = 71.dp     // 좌우 여백
        val topPadding = 63.5.dp    // 상단 여백
        val bottomPadding = 45.dp   // 하단 여백
        val titleGap = 14.dp        // 타이틀과 표 사이 간격
        val yearSp = 26.sp          // 연도 글자 크기
        val monthSp = 78.sp         // 월 글자 크기
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
