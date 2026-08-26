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
        val topMargin = 60.dp       // 탭 타이틀 위 상단 여백 (00_탭화면 레이아웃 시안)
        val bottomMargin = 45.dp    // 하단 네비게이션 바 위 여백
        val headerHeight = 25.dp    // 상단 daymory 브랜드 영역 높이
        val titleSp = 61.sp         // 탭 타이틀 글자 크기 (Cavorting, 가운데 정렬)
        val titleGap = 40.dp        // 브랜드 영역 ~ 타이틀 영역 사이 여백
        val titleAreaHeight = 80.dp // 모든 탭의 타이틀이 같은 좌표를 쓰는 고정 영역
        val actionAreaHeight = 60.dp // 타이틀 아래 우측 버튼 영역(시안의 빨간 점 위치)
        val actionButtonSize = 32.dp // 액션영역 IconButton 터치 영역 크기(기본 48dp 대신 통일 적용)
        val sideMargin = 45.dp      // 본문 좌우 여백 (탭 화면 공통)
        val navItemSize = 60.dp     // 하단 네비게이션 아이템 크기
        // 네비게이션 바 영역이 화면 하단 끝부터 총 60dp가 되도록(아이콘 24dp+간격 3dp+라벨 1줄
        // 기준) 상하 내부 여백을 역산한 값.
        val navBarPadding = 10.dp   // 하단 네비게이션 바 내부 여백(상하)
    }

    /** 가로모드 3열 레이아웃(1열 네비게이션 · 2열 본문 · 3열 서브패널) 폭 설정 — `MainTabLayout`/
     *  `MainTabPage`가 이 값들을 그대로 씀. 1열은 고정 폭(dp), 2·3열은 남은 공간을 나눠 갖는 비율
     *  (weight)이라 절대 폭이 아니라 "2열:3열 = contentWeight:sidePanelWeight" 비율로 읽는다. */
    object Landscape {
        val navWidth = 80.dp        // 1열(SideNavRail) 고정 폭
        val contentWeight = 2f    // 2열(본문) 비율
        val sidePanelWeight = 1f    // 3열(서브패널) 비율 — 이 값이 0이 되진 않게(레이아웃이 사라짐)
        val dividerWidth = 0.5.dp     // 1↔2열, 2↔3열 사이 얇은 구분선 두께
        val sidePanelTintAlpha = 0.2f // 3열 배경에 얹는 미세한 명암차(surfaceVariant 위에 이 알파로 덧칠)
    }

    /** 온보딩 (Splash/Login) */
    object Onboarding {
        val titleSp = 120.sp         // "daymory" 타이틀 — 화면 폭에 맞춰 줄어들 수 있는 최대값(고정 아님)
        val subtitleSp = 27.sp      // "Draw together, keep the little days" 부제
        val ctaSp = 21.sp           // "enter" / "Google 계정으로 로그인" 버튼 글자 크기
        val duckMaxWidth = 275.dp   // 이 값 하나만 바꾸면 오리가 정비율로 커지거나 작아짐
        const val duckAspectRatio = 275f / 400f
    }

    /** 홈 탭 (타이틀 크기·위치는 Screen 공용값 사용) */
    object Home {
        val carouselCenterW = 182.dp    // 캐러셀 가운데(포커스) 노트 너비
        val carouselCenterH = 275.dp    // 캐러셀 가운데(포커스) 노트 높이
        val carouselSideW = 145.dp      // 캐러셀 옆(비포커스) 노트 너비
        val carouselSideH = 218.dp      // 캐러셀 옆(비포커스) 노트 높이
        /** 표지 가로세로 비율(가운데 노트 기준) — 스케치북 리스트 썸네일도 이 비율을 그대로 씀(고정 비율). */
        val coverRatio = (carouselCenterW / carouselCenterH)
        // 캐러셀 아래 가운데 표지 하나만 보여주는 타이틀 — 큰 제목(이름)/작은 부제(생성일·사이즈·배경명).
        val carouselTitleSp = 18.sp
        val carouselSubtitleSp = 12.sp
        val editCoverCardWidth = 350.dp  // 표지 수정 시트 최대 폭(Wizard.cardWidth와 별개 — 내용이 더 많음)
    }

    /** 새 스케치북 만들기 화면 (팝업 카드 — 이름/사이즈/배경을 한 화면에서 선택) */
    object Wizard {
        val cardWidth = 280.dp      // 카드 최대 너비
        val cardRadius = 28.dp      // 카드 모서리 둥글기
    }

    /** 일기달력 탭 (슬라이드 2, DiaryCalendarScreen / AiryCalendar) */
    object Calendar {
        val titleGap = 24.dp        // 연월 타이틀과 달력 그리드 사이 간격
        val yearMonthSp = 35.sp     // "2026.01" 형식 연월 표기 글자 크기(연도/월 이름 분리 표기 대신 한 줄로 통합)
        val weekdaySp = 26.sp       // 요일(Sun..Sat) 글자 크기
        val daySp = 21.sp           // 날짜 숫자 크기
        val arrowIconW = 10.dp      // 이전/다음 달 화살표 너비 — Icon 크기가 아니라 직접 그리는 화살표(Canvas)의 크기
        val arrowIconH = 20.dp      // 이전/다음 달 화살표 높이
        val editIcon = 24.dp        // 오늘 일기 편집(연필) 아이콘 크기 — 액션영역 아이콘 통일 기준(리스트 탭과 동일)
        val todayDisc = 36.dp       // 오늘 핑크 원 지름
        val summarySp = 16.sp       // 연월 타이틀 아래 "이번 달 N일 기록" 요약 글자 크기
    }

    /** 클린 달력 (슬라이드 3·4, CleanCalendarScreen) — 좌측 썸네일 달력 열(화면 폭의 42%)에 쓰인다.
     *  좌우/하단은 좁은 열 안에서 표가 최대한 잘 보이도록 최소화했고(2026-08-26, 이전엔 전체화면
     *  포스터용으로 71/63.5/45dp였음), 연/월 글자도 그만큼 줄였다. 상단은 그대로 둠. */
    object CleanCalendar {
        val sidePadding = 12.dp     // 좌우 여백
        val topPadding = 63.5.dp    // 상단 여백
        val bottomPadding = 12.dp   // 하단 여백
        val titleGap = 14.dp        // 타이틀과 표 사이 간격
        val yearSp = 14.sp          // 연도 글자 크기
        val monthSp = 20.sp         // 월 글자 크기
    }

    /** 캔버스/드로잉 (BrushView, 캔버스 화면들) */
    object Canvas {
        val outerPadding = 24.dp    // 캔버스 뷰 바깥 여백
        val minZoom = 0.3f          // fit 이하 줌아웃 최소 배율(작게 = 주변 공간 넓어짐)
        val maxZoom = 5f            // 최대 확대 배율
        val lassoStrokeWidthDp = 0.5f // 라쏘 선택 점선 테두리 두께(density 곱해서 px로 사용)
    }

    /** 브러시 종류별 기본 굵기(dp) + 지우개 기본 굵기, 그리고 각 굵기 슬라이더가 오갈 수 있는
     *  최소/최대값(2026-08-23 추가 — 예전엔 모든 브러시가 BrushControls.MinBrushSize~MaxBrushSize(4~96)
     *  하나를 공유했는데, 펜처럼 가는 도구와 수채화처럼 굵은 도구가 같은 범위를 쓰면 세밀한 조절이 어려워서
     *  브러시 기본값 기준으로 나눔). */
    object Brush {
        // 캔버스 px 기준 굵기(2026-08-17, BrushControls.SizeRange=4~96과 같은 스케일로 2배 상향 —
        // strokeSize가 화면 밀도/fitScale과 무관해지면서 예전 값 그대로면 캔버스에서 너무 얇았음).
        val penWidth = 4f
        val penMinWidth = 0.5f
        val penMaxWidth = 30f
        val pencilWidth = 8f
        val pencilMinWidth = 1f
        val pencilMaxWidth = 60f
        val crayonWidth = 8f
        val crayonMinWidth = 1.5f
        val crayonMaxWidth = 60f
        val waterWidth = 5f
        val waterMinWidth = 1f
        val waterMaxWidth = 95f
        val eraserWidth = 5f
        val eraserMinWidth = 1f
        val eraserMaxWidth = 95f
    }
}
