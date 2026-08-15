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

    /** 탭 화면 공통 — 홈/스케치북 리스트/설정 세 탭이 이 값을 그대로 공유해서 타이틀 위치·크기가 항상 동일함.
     *  (일기달력 탭은 타이틀 자리에 동적인 연도/월이 들어가서 자체 Calendar 그룹 값을 쓰지만, 상하 여백은 같은 값.) */
    object Screen {
        val topMargin = 63.5.dp     // 탭 타이틀 위 상단 여백
        val bottomMargin = 45.dp    // 하단 네비게이션 바 위 여백
        val titleSp = 78.sp         // 탭 타이틀 글자 크기 (Cavorting, 가운데 정렬)
    }

    /** 온보딩 (Splash/Login) */
    object Onboarding {
        val titleSp = 130.sp        // "Daily sketch" 타이틀 — 화면 폭에 맞춰 줄어들 수 있는 최대값(고정 아님)
        val subtitleSp = 37.sp      // "Draw together, keep the little days" 부제
        val duckW = 765.dp          // 오리 GIF 비율 고정용 — 실제 크기는 화면 폭에 맞춰 이 비율로 축소됨
        val duckH = 510.dp
    }

    /** 홈 탭 (타이틀 크기·위치는 Screen 공용값 사용) */
    object Home {
        val titleToIconGap = 68.dp      // 타이틀 ~ 아이콘 행 사이 간격
        val actionIcon = 56.dp          // 새 노트/공유/참여 원형 아이콘 버튼 지름 (명시 안 됨, 추정치)
        val carouselCenterW = 267.5.dp  // 캐러셀 가운데(포커스) 노트 너비
        val carouselCenterH = 402.dp    // 캐러셀 가운데(포커스) 노트 높이
        val carouselSideW = 217.dp      // 캐러셀 옆(비포커스) 노트 너비
        val carouselSideH = 327.dp      // 캐러셀 옆(비포커스) 노트 높이
        /** 표지 가로세로 비율(가운데 노트 기준) — 스케치북 리스트 썸네일도 이 비율을 그대로 씀(고정 비율). */
        val coverRatio = (carouselCenterW / carouselCenterH)
    }

    /** 새 스케치북 만들기 화면 (팝업 카드 — 이름/사이즈/배경을 한 화면에서 선택) */
    object Wizard {
        val titleSp = 78.sp
        val cardWidth = 425.dp      // 카드 최대 너비
        val cardRadius = 28.dp      // 카드 모서리 둥글기
    }

    /** 일기달력 탭 (슬라이드 2, DiaryCalendarScreen / AiryCalendar) */
    object Calendar {
        val topSpacer = Screen.topMargin   // 탭 타이틀 위 상단 여백 — 다른 탭과 동일
        val bottomMargin = Screen.bottomMargin  // 하단 네비게이션 바 위 여백 — 다른 탭과 동일
        val sideMargin = 71.dp      // 좌우 여백
        val topTitleGap = 16.dp     // "A piece of today" 탭 타이틀 ~ 연월 사이 간격
        val titleGap = 24.dp        // 연월 타이틀과 달력 그리드 사이 간격
        val yearMonthSp = 52.sp     // "2026.01" 형식 연월 표기 글자 크기(연도/월 이름 분리 표기 대신 한 줄로 통합)
        val weekdaySp = 26.sp       // 요일(Sun..Sat) 글자 크기
        val daySp = 21.sp           // 날짜 숫자 크기
        val arrowIconW = 10.dp      // 이전/다음 달 화살표 너비 — Icon 크기가 아니라 직접 그리는 화살표(Canvas)의 크기
        val arrowIconH = 20.dp      // 이전/다음 달 화살표 높이
        val editIcon = 35.dp        // 오늘 일기 편집(연필) 아이콘 크기
        val todayDisc = 38.dp       // 오늘 핑크 원 지름
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
