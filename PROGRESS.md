# G1 Sketchbook — Progress

아날로그 감성의 공유 스케치북 앱 (Android + Compose + Firebase). 브러시 감성이 핵심.
전체 기획은 `plan.md`, 방향 대화로 아래처럼 재정의되어 **클린 재구축** 중.

## Done
- **Phase 0 — 브러시 엔진** (v1.7.x): `brush/BrushView.kt` (bitmap 백엔드 스탬프 엔진), `brush/BrushPlaygroundScreen.kt`.
  - 볼펜(레이어 합성, 불투명도 균일) · 연필(디스크 그레인, 직접 누적) · 크레파스(큰 입자 1.5×, 성김) · 수채화(Tyler-Hobbs 다각형, multiply 대신 레이어 합성).
  - 브러시별 폭 배율: 볼펜1× 연필1.5× 크레파스3× 수채화6× (`scaleFor()`).
  - 거리 누적 스탬프로 속도 무관. 배경 = 실제 수채화 종이(`drawable-nodpi/paper_watercolor.jpg`).
- **Phase 1 — 골격** (v1.8.0): 스플래시→로그인→별명→메인.
  - `ui/RootViewModel` (auth·별명·테마·탭), `ui/SplashScreen`, `ui/NicknameScreen`, `ui/main/MainScreen`(5탭).
  - 5탭: 스케치북·그림일기·홈·일기달력·설정(하이라이트). 홈=대시보드+브러시놀이터. 설정=테마(시스템/라이트/다크)+로그아웃.
  - 다크모드 추가(`ui/theme/Theme.kt` ThemeMode). SessionStore에 nickname·themeMode 저장.

- **캔버스 대개편** (v1.14.0, 2026-08-13):
  - `BrushView.kt` 재작성 → 고정 해상도 캔버스(선택 사이즈 실픽셀, 종이 200dpi, 최장변 1600px 캡) + fit-to-view 표시 매트릭스 + 역행렬 터치 매핑. **한 손가락 전용**(줌·2/3손가락 undo/redo 완전 삭제). `rotate()` 90도 회전. undo 스택 6, paper RGB_565.
  - `BrushControls.kt` 재작성 → zoomLock 제거, 뒤로/페이지(이전·라벨·다음·추가·삭제)/회전 leading 컨트롤 추가, 브러시 아이콘 버튼(선택=진회색/비선택=연회색).
  - `SketchbookCanvasScreen`(공개, 전체화면) → 상단 타이틀·하단 탭 제거, 캔버스가 `size.pxW()/pxH()` 비율. 앱 루트 네비게이션(`RootViewModel.openBookId`/`openBook`/`closeBook`, `MainActivity` 분기).
  - **버그 수정 #8**: 페이지 추가/이동 시 async save가 변경된 `page`를 읽어 엉뚱한 페이지에 저장 → `val pg = page` 캡처 후 저장.
  - `SketchbookRepository.CanvasSize.pxW/pxH`(종이=200dpi 환산), `isPaper` 추가.

- **핀치 줌 + 아이콘** (v1.15.0, 2026-08-13):
  - `BrushView`에 두 손가락 핀치 줌(1~5배)·이동 추가. `userM`(view-space 매트릭스)를 fit 매트릭스 뒤에 `postConcat`, 역행렬로 터치 매핑. 1배 복귀 시 자동 중앙정렬(`clampAndRefresh`). 줌 시작 시 진행 중 획은 `discardStroke()`로 취소(점 안 남김). `rotate()`/`initCanvas`에서 `resetZoom()`.
  - 브러시 아이콘 2배(52dp, 버튼 60dp). 볼펜은 `brush_pen.png`(image/brush_type/ballpoint-pen.png → 112px 알파 실루엣) 사용, ColorFilter.tint(SrcIn) 적용.

- **가로모드 줌 수정 + 아이콘 크롭** (v1.16.0, 2026-08-13):
  - `onSizeChanged`(비초기화)에서 `resetZoom()` → 폴드/회전 후 남은 view-space 변환으로 생기던 사각지대 제거.
  - `ACTION_POINTER_UP`(2→1) 및 `ACTION_DOWN`에서 `pinching` 해제 → 남은 손가락으로 그리기 막히던 문제 해결.
  - `BrushBtn` 버튼 42dp 원복, 아이콘 52dp는 `clipToBounds()`로 크롭.

- **가로화면 종이 자동 회전** (v1.17.0, 2026-08-13):
  - `computeDisplay`: 화면 방향≠종이 방향이면 `autoQ=1`로 종이를 한 번 회전(`q=(rotationQ+autoQ)%4`) → 세로 종이가 가로 화면 폭을 채움. 수동 회전은 그 위에 누적.
  - `SketchbookCanvasScreen`: 종이 비율에 잠긴 `BoxWithConstraints` 제거, AndroidView를 `fillMaxSize`로 → 뷰가 화면 전체를 받아 가로모드 양옆 사각지대 제거(맞춤/레터박스는 BrushView 내부 처리).
  - 원인: 세로 종이가 가로 화면에서 가운데 좁은 띠로 배치돼 양옆이 배경(비드로잉)이었음. 사용자 선택=종이 90° 자동 회전.

- **구식 코드/의존성 정리** (v1.18.0, 2026-08-13):
  - 삭제: `ui/HomeScreen`, `ui/AppViewModel`, `ui/canvas/*`(CanvasScreen/CanvasViewModel/StrokeRender/Crayon), `ui/gallery/*`, `data/RoomRepository`, `data/ArchiveRepository`, `work/DailyArchive`(+ArchiveScheduler), 미참조 `ui/theme/PaperTexture`.
  - `Models.kt` → `SketchbookRef`만 남김(Stroke/Member/RoomMeta/ArchiveEntry 제거). `SketchApp` → 룸/아카이브 저장소·스케줄러·RTDB 영속성 제거.
  - 미사용 의존성 제거: firebase-database, work-runtime.ktx, coil.compose, navigation.compose. (`TaskAwait`는 GoogleAuthClient가 사용 → 유지.) 동작 무변화, 빌드 검증 완료.

- **함께 그리기 · 분할 화면 공유** (v1.19.0, 2026-08-13):
  - `share/ShareRepository`: Firebase RTDB `shareSessions/{CODE}` 2인 세션. 6자리 초대코드(혼동문자 제외), `createSession/joinSession/observeSession(callbackFlow)/pushSnapshot/leaveSession`. 스냅샷=다운스케일(≤700px) JPEG q70 → Base64(무료, Storage 미사용).
  - `share/SharedSessionScreen`: 분할 뷰(세로=위/아래, 가로=좌/우). 내 반쪽=인터랙티브 BrushView(정사각 1280 캔버스, paper_drawing), 획 종료마다 스냅샷 push. 상대 반쪽=최신 스냅샷 Image. 초대코드·상대 상태 표시. 뒤로 시 leaveSession(호스트는 세션 삭제).
  - `RootViewModel`(uid/shareCode/shareIsHost + openShare/closeShare), `MainActivity` 분기, `MainScreen`/`HomeTab`에 "함께 그리기" 카드 + 생성/참여 다이얼로그(`ShareDialog`).
  - firebase-database 의존성 재추가 + `Graph.shareRepository` 배선.
  - 결정: 2인 / 획마다 스냅샷 동기화 / 초대코드 참여 (사용자 선택).

- **함께 그리기 다듬기** (v1.20.0, 2026-08-13):
  - 분할 배치: 내 캔버스=가로 오른쪽/세로 하단(상대=왼쪽/위).
  - 회전 시 내용 소실 수정: 패널을 `movableContentOf<Modifier>`로 감싸 Row↔Column 전환 때 BrushView/비트맵 유지.
  - 공유 캔버스를 A4 규격으로 맞춤(`SHARE_SIZE=Catalog.size("a4")`, bg=drawing) → 저장 왜곡 방지. 상단 저장 버튼: `SketchbookRepository.create(...,"a4","drawing")+savePage` 후 Toast.
  - 홈 브러시 놀이터 제거 + `brush/BrushPlaygroundScreen.kt` 삭제.

- **공유 스케치북 개편** (v1.21.0, 2026-08-13):
  - `Sketchbook`에 `shared`/`code` 추가·영속화. 목록을 "내 스케치북"/"함께 그린 스케치북"으로 그룹화(LazyVerticalGrid span 헤더).
  - 단계별 생성 팝업 `CreateWizard`(SketchbookScreens): 유형(개인/공유생성/공유참여) → 개인은 이름·크기·배경, 공유생성은 이름만(A4·수채화 고정+RTDB host 세션), 참여는 코드. 기존 one-shot `CreateSketchbookScreen` 제거.
  - `share/SharedBookScreen`: 공유책 = 15페이지 분할뷰(내 캔버스 오른쪽/하단, 상대 왼쪽/위). 로컬 페이지 저장(SketchbookRepository) + 획/페이지전환마다 스냅샷 push. `movableContentOf`로 회전 유지. A4·수채화.
  - 라우팅: `SketchbookCanvasScreen(bookId,myUid,myName,onBack)`가 `book.shared`면 SharedBookScreen로 분기. `SketchbookTab(nickname,myUid,onOpenBook)`.
  - 옛 진입점 제거: Home "함께 그리기" 카드+ShareDialog, 단일페이지 `SharedSessionScreen`, RootViewModel shareCode/openShare 등.

- **색상 휠** (v1.22.0, 2026-08-13):
  - `BrushControls`에 색상 휠 버튼(sweepGradient 원) + 팝업 `ColorPickerCard`: SV 사각형(pointerInput awaitPointerEvent) + 색조 바 + 현재색/HEX. android.graphics.Color HSV 변환. 기존 10색 팔레트는 인라인 유지. → Phase 4 "색상휠+팔레트 UI" 완료.

- **화면 디테일 다듬기 (진행 중)**:
  - (1/N, v1.23.0) 홈: 최근 스케치북 커버 → 해당 책 바로 열기(`onOpenBook`), 공유책 🤝 배지, 시작 카드 문구/화살표, "전체 보기", 빈 상태 카드.
  - (2/N, v1.24.0) 스케치북 목록: 삭제 확인 AlertDialog(`pendingDelete`), 커버에 페이지 수 + 공유책 🤝·코드 표시.
  - 다음: 설정 → 그림일기/달력.

- **공유 스케치북 보기 모드** (v1.26.0, 2026-08-13):
  - `SharedBookScreen`에 보기 모드 선택: EQUAL(균등분할)/LARGE(한쪽 크게+반대쪽 상단·측면 축소)/SOLO(하나만). `나/상대` focus 토글(LARGE·SOLO에서). 상단 세그먼트 컨트롤(SegGroup/SegChip). SOLO+상대일 때 내 BrushView는 1dp로 살려 상태 유지. `maxWidth/maxHeight`는 layout-scope 밖으로 캡처(mw/mh).

- **배치 수정** (v1.27.0, 2026-08-13):
  - (#1) `BrushView` 연필/크레파스 입자를 `grainPx()=(1/fitScale).coerceIn(1,5)`로 스케일 → 큰 A4 캔버스가 작은 분할 뷰에 표시될 때도 보임(볼펜만 되던 문제 해결). 입자 수 상한(900/800).
  - (#3) `BrushControls` 전체 지우기 확인 AlertDialog(모든 드로잉 화면 공통).
  - (#4) `DiaryScreen` A4 세로 캔버스(`Catalog.size("a4")` initCanvas) + 상단 TopAppBar 제거(전체 캔버스).
  - (#5) 달력 셀·`DiaryPanel` 미리보기 `aspectRatio(A4_RATIO=210/297)`.
  - (#6) `DiaryCalendarScreen` 세로모드=달력만(패널 숨김).
  - (#7) 그림일기 상단바 제거. (스케치북/공유는 이미 전체화면. 그림일기는 탭이라 하단 네비게이션은 유지됨.)
  - **미완**: (#2) 제스처 동작 설정(두손가락 드래그 4방향/탭/더블탭 → undo/redo/투명도/굵기/펜/색상/지우개 매핑). 기존 핀치줌과 충돌·설정 UI·저장 필요 → 다음 버전.

- **공유 브러시 회전 후 먹통 수정** (v1.28.0, 2026-08-13):
  - 원인: `SharedBookScreen`이 패널을 `movableContent`로 감싸는데, 회전/보기모드 전환으로 패널이 이동하면 `AndroidView.update()`가 snapshot 상태를 더 이상 관찰하지 못해 브러시/색/불투명도/지우개 선택이 뷰에 반영 안 됨(개인 캔버스는 movableContent 미사용이라 정상).
  - 수정: 브러시 설정+onStrokeEnd를 `update` 대신 최상위 `LaunchedEffect(view,brush,color,sizeDp,opacity,erasing)`로 적용 → 패널 이동과 무관하게 항상 재동기화. (v1.27.1의 추정성 cap 1280은 원복.)
  - 남은 요청: 그림일기 탭을 달력에 통합해 스케치북처럼 전체화면으로 열기(미착수). 제스처(#2) 보류(사용자 재검토).

- **고화질 종이 + 풀 200dpi 캔버스** (v1.29.0, 2026-08-13):
  - 종이 배경을 원본 PNG(1672×941, `image/background/*`)로 교체(기존 1200×675 JPG 제거). BrushView가 `paperBmp`(RGB_565 사전렌더) 대신 원본을 매 렌더 `drawPaper`로 필터링(`FILTER_BITMAP`) → 화질 보존.
  - `initCanvas` cap 1600→3308: 종이 실제 200dpi 픽셀 사용(A3 2339×3307/A4 1654×2339/A5 1165×1654). 메모리: paperBmp 제거, undo 6→4, 매니페스트 `largeHeap=true`.

- **그림일기 달력 통합 + 전체화면 편집** (v1.30.0, 2026-08-13):
  - `그림일기` 탭 제거 → 하단 탭 4개(스케치북/일기/홈/설정). 일기는 앱루트 전체화면 `DiaryEditorScreen(date,onBack)`(A4, 뒤로 버튼)로 열림(`RootViewModel.openDiaryDate`/`openDiary`/`closeDiary`, MainActivity 분기, MainScreen `onOpenDiary`).
  - 달력 `CalendarTable` 7×7(요일 헤더 + 6주, 모든 달 수용). 일칸 썸네일 `ContentScale.Crop`. 오늘 칸/‘오늘 일기’ 버튼→편집 열기, 다른 날→선택. 세로=표만, 가로=표+선택일 A4 미리보기(`DiarySidePanel`). 영어 월 이름.

- **세로 달력 상세 보기** (v1.31.0, 2026-08-13):
  - 세로모드: 달력 표 먼저 → 날짜 탭 시 `DiaryDetailView`(월/연도 중앙 + 요일 + 날짜 서수, 액자 프레임 이미지). `detailDate` 상태 + `BackHandler`. 달력 제목 중앙정렬, 날짜 숫자 우상단(`TopEnd`), 헤더에 오늘편집 아이콘+월 화살표. `CalendarTable(selected: String?, onDayClick)`로 단순화. `ordinal()`, `FullWeekdays` 추가.
- **Cavorting 폰트** (v1.32.0): `support/font/Cavorting/Cavorting.ttf` → `app/src/main/res/font/cavorting.ttf`. `ui/theme/Fonts.kt`의 `Cavorting` FontFamily를 달력 제목/요일/날짜숫자 + 일기 상세 헤더에 적용. (라이선스는 desktop용 — 배포 시 앱 임베딩 라이선스 확인 필요.)

- **플로팅 탭바 + 탭 순서** (v1.33.0): `MainScreen.FloatingNavBar` — 떠 있는 알약형(Surface RoundedCornerShape) + 선택 항목이 흰 원(CircleShape, shadowElevation, `offset(y=-18)`)으로 위로 돌출, 아이콘 primary 틴트. 라벨 제거. Material `NavigationBar`/`NavItem` 제거. 탭 순서 홈(0)/스케치북(1)/일기(2)/설정(3), 기본탭 0. `HomeTab.onGoSketchbooks`→onTab(1).

- **앱 컬러톤 통일** (v1.34.0): 팔레트 = 세이지 `#ACBDAA`/네이비 `#1E2D4C`/그레이 `#858585`/토프 `#CEC0BB`. `theme/Theme.kt` 라이트·다크 스킴 전면 교체(navy=primary/ink, grey=onSurfaceVariant, sage=secondary, taupe=tertiary). 온보딩 Splash/Login 로열블루→세이지 배경+네이비. CoverColors(MainScreen·SketchbookScreens), 기본 그리기 색·BrushView 기본색·BrushPalette 첫 색 = 네이비.

- **컬러 조정 + 버튼 바운스** (v1.35.0): 주조색=밝은 베이지(bg `#F4F0E7`, surface `#FFFDF8`, surfaceVariant `#EBE5D7`), 세이지=보조(secondary)로 강등. `Ivory #F6F1E6`=onPrimary/미색. 온보딩 Splash/Login 배경=토프 `#CEC0BB`. 네비 선택 원=primary(navy), 아이콘=onPrimary(ivory). `ui/Interactions.kt`의 `Modifier.bounceClick`(스케일 스프링, 리플 없음) → 탭슬롯/홈카드/MiniCover/CoverCard/BrushBtn/IconBtn/색스와치에 적용. (Material Button은 기본 리플 유지 — 필요시 확장.)

- **달력 6×7 + 상세 정렬 + 마법사 아이콘화** (v1.36.0):
  - `CalendarTable` 6주×7열 그리드 + 요일 헤더를 테두리 밖(위)으로. `DiaryDetailView`: 헤더(64dp, 중앙 제목+뒤로/액션 아이콘) → 요일/일(그림 좌우 끝 정렬) → 이미지가 `weight(1f).fillMaxWidth()`로 달력표 footprint 채움(Fit). 여백 horizontal 20/vertical 14.
  - 생성 마법사 `WizardChoice(icon,...)` 아이콘화: 개인=Book, 공유생성=Groups, 참여=Login(원형 secondaryContainer).

- **어댑티브 내비게이션** (v1.38.0): `MainScreen`에서 `LocalConfiguration.orientation`으로 분기. 세로=하단 `FloatingNavBar`(Scaffold bottomBar), 가로=좌측 `SideNavRail`(세로 pill, 선택 원이 `offset(x=18)`로 우측 돌출, primary/onPrimary) + 콘텐츠 `Box(weight1).systemBarsPadding()`. `content` 람다로 탭 분기 공유.
- (진행중) 감성 리디자인 협업: 손그림 테두리/종이질감/손글씨 폰트/여백. 사용자가 화면별 A안 제공 예정.

- **A안 디자인 반영** (v1.39.0, 2026-08-14):
  - 온보딩: `duck_walk.png`(image/UI design/ONBOARDING.png, 투명 라인아트). Splash/Login = 세이지(#ACBDAA) 배경 + "G1 SKETCH"(Cavorting 64sp) + 오리 + 검정 "continue" 알약(bounceClick, Login만).
  - 슬라이드2 `DiaryCalendarScreen(onOpenDiary,onOpenCalendar)`: 월 타이틀 대형(56sp), 펜=오늘편집, 좌우 화살표. `AiryCalendar`(테두리없음, 오늘=핑크원 `TodayPink`, 일기날=아래 점 `DiaryDot`, `datesWithDiary`). 그리드 탭→`onOpenCalendar(y,m)`.
  - 슬라이드3·4 `CleanCalendarScreen(year,month,onBack)`(앱루트, 바 없음): `CleanGrid`(테두리 6×7, 썸네일 Crop)→일자 탭→`CleanDetail`(손그림 테두리 `Modifier.sketchBorder` drawBehind 지터 Path + 스케치 Crop). BackHandler로 4→3→탭. `RootViewModel.cleanCalendar`/`openCleanCalendar`/`closeCleanCalendar`, MainActivity 분기, MainScreen `onOpenCalendar`.
  - 구 `CalendarTable`/`DiaryDetailView`/`DiarySidePanel` 제거.

- **A안 타이포·여백 정밀 반영** (v1.40.0~1.42.0, 2026-08-14): 사용자 비교 이미지 기반. 폰트/여백 2배 확대, 손그림 테두리 강도 완화(`sketchBorder` step 46f, jitter ×0.7). 슬라이드2 탭 = 월 100sp·연도 60sp·일 21sp·요일 25sp·상단 여백 110dp·화살표 35dp. 슬라이드3·4 완전 동일 = 월(August) 70sp, 상/하 여백 30dp, 좌우 44dp. 오늘 핑크원 아래 그림자(`shadow(4dp,CircleShape)`).

- **캔버스 여백 + 페이지 유실 수정 + 화살표 35dp** (v1.43.0, 2026-08-14):
  - 월 이동 화살표 35dp로 축소.
  - 캔버스 바깥 여백 확대(Sketchbook/Diary padding 8→24, Shared 8→16) — 귀퉁이 드로잉 곤란 해소.
  - **페이지 유실 버그(#3) 수정**: 페이지 전환 시 async `saveCurrent`가 `loadPage` 읽기와 경쟁 → 동기 저장-후-로드로 변경. 저장 단위를 획 전용(`BrushView.exportContent()`, 종이 미포함)으로 바꿔 이중 종이 누적도 함께 해결. `goTo()`가 저장 완료 후 `loadContent`. onStrokeEnd도 exportContent 사용.

- **펜별 굵기·투명도 + 색상 즐겨찾기 5개 편집** (v1.44.0, 2026-08-14):
  - 브러시 종류별(볼펜/연필/크레파스/수채화) 굵기·투명도를 독립 저장(`mutableStateMapOf<BrushType,Float>` + 지우개 전용 `eraserSize`). 활성 sizeDp/opacity는 `erasing`이면 지우개값, 아니면 현재 브러시값. `onSize`/`onOpacity`가 활성 대상만 갱신. Sketchbook·Shared·Diary 3개 화면 모두 적용.
  - 색상 즐겨찾기 10개→5개로 축소, 개인 편집 가능. `SessionStore.favoriteColors`(SharedPrefs `fav_colors`, 5개 검증, `DefaultFavorites`=네이비/세이지/레드/앰버/그린)에 영속. `BrushControls`에 `favorites`/`onEditFavorite` 파라미터 + `combinedClickable`(탭=선택, 롱프레스=현재색으로 덮어쓰기).

- **개발용 미리보기 + 캔버스 팬 여백 + 일기탭 스펙 주석** (v1.45.0, 2026-08-14):
  - Pretendard 폰트 추가(`res/font/pretendard_{regular,medium,bold}.otf`, `theme/Fonts.kt`의 `Pretendard` FontFamily). 원본은 `support/font/Pretendard`.
  - 설정탭 "개발자 > 폰트·사이즈 미리보기"(`DevPreviewScreen`, BackHandler): Pretendard·Cavorting를 10~120sp 5단위로, 정사각형을 10~200dp 10단위로 나열해 실제 크기 확인.
  - **캔버스 팬 여백**(`BrushView.clampAndRefresh`): 화면 절반까지 여백 허용 → 줌 상태에서 캔버스 귀퉁이를 화면 중앙까지 끌어와 가장자리 드로잉 가능. 캔버스 픽셀 크기는 불변.
  - **임시 스펙 주석**(`dev/DevAnno.kt`): `DevAnno.SHOW`(현재 true) 하나로 일괄 on/off. `Modifier.devBounds(key)`로 요소 위치 수집 → `DevAnnoOverlay`가 Pretendard 라벨 + 지시선을 그림. 일기달력 탭(slide 2)에 연도60sp·월100sp·화살표35dp·요일25sp·일21sp·오늘원38dp·좌우여백24dp·오늘일기 아이콘 표시. 배포 전 `DevAnno.SHOW=false`(또는 파일+호출부 삭제)로 제거.

- **가로 팬 여백 + 사각형 샘플 가로배치** (v1.46.0, 2026-08-14):
  - `BrushView.clampAndRefresh`를 축 구분 없이 통일 — 캔버스가 화면을 채우든 레터박스든 좌/우도 상/하와 동일하게 화면 절반까지 여백 허용. (레터박스 축을 중앙으로 스냅하던 `else` 제거; fit 리센터는 scale≤1의 `resetZoom`이 담당.)
  - `DevPreviewScreen` 정사각형 10~200dp 샘플을 세로 목록 → 가로 스크롤 Row로 변경(각 사각형 아래 dp 라벨).

- **팬 여백 버그픽스: 드로잉 유실 방지 + 좌우 여백** (v1.47.0, 2026-08-14):
  - 증상(v1.46.0): 캔버스가 세로로 꽉 차고 가로가 레터박스인 경우, "edge-to-center" 클램프가 좁은 축(가로)의 캔버스를 화면 절반 밖까지 밀어내 화면 대부분이 배경이 됨 → 그 위에 그리면 스트로크가 비트맵 밖으로 매핑돼 안 보임("드로잉 안됨").
  - 수정: `clampAndRefresh`를 `axisAdjust(lo,hi,view,margin)`로 통일. 축별로 **배경 노출 예산 `m=0.35*min(w,h)`** 까지만 여백을 허용하고, **화면 중앙은 항상 캔버스가 덮도록** 보장 → 중앙은 언제나 드로잉 가능. 좌우도 상하와 동일한 예산으로 여백 노출. (캔버스보다 좁은 축은 중앙 정렬.) fit 리센터는 scale≤1의 `resetZoom` 유지.

- **서명 스킴 명시 + 재릴리스(설치 실패 대응)** (v1.48.0, 2026-08-14):
  - 일부 기기에서 "앱이 설치되지 않았습니다" 보고. 디버그 APK는 유효(안드로이드 디버그 키, V2 서명)하며 minSdk 24라 V2만으로 모든 대상 기기에 설치 가능 — 서명이 원인은 아님(V1은 AGP가 minSdk24+에서 자동 생략).
  - `signingConfigs.debug`에 `enableV1/V2/V3Signing` 명시(V3 추가, 호환성 최대화). 깨끗한 새 APK로 재릴리스 → 모바일 대용량 다운로드 손상 케이스 대응.
  - 코드 변경 없음(v1.47.0 로직 동일). 실패 지속 시 실제 오류 문구 확인 필요(다운로드 손상 / Play Protect / 기존 앱 충돌 등).

- **fit 여백(캔버스 항상 화면 안쪽에 띄우기) + 버전명 APK** (v1.49.0, 2026-08-14):
  - 증상: 세로모드에서 A4 세로 페이지가 화면 가로폭을 꽉 채워 좌우 여백이 없고 좌우 가장자리를 그리기 어려움("좌우 드로잉 안됨"). 가로모드는 캔버스가 높이를 채우고 좌우가 레터박스라 자연 여백이 있어 잘 됐음.
  - 원인: 팬(pan) 방식 여백은 확대해야만 드러나 fit(기본) 상태에선 여백이 없음.
  - 수정: `computeDisplay`가 `FIT_MARGIN_FRACTION=0.08 × min(w,h)` 만큼 상하좌우를 인셋한 뒤 fit 계산 → 캔버스가 **기본 상태에서도 항상 네 변 모두 화면 안쪽에 떠서** 가장자리 드로잉 가능. 캔버스 픽셀 크기는 불변(표시 스케일만 축소). pan 여백(axisAdjust)은 확대 시 보조로 유지.
  - 릴리스 자산 파일명을 `G1-Sketchbook-vX.Y.Z.apk`로 변경(다운로드 폴더의 이전 `app-debug.apk` 캐시 충돌로 인한 설치 실패 방지).

- **fit 이하 줌아웃(PPT식 작업공간)** (v1.50.0, 2026-08-14):
  - 사용자 요청: "PPT 줌아웃처럼 캔버스를 작게 만들고 주변에 여유를 많이" (레터박스/캔버스 축소 방식 아님, 자유 줌아웃).
  - v1.49.0의 fit 여백(캔버스 표시 축소) 되돌림.
  - 핀치 최소 배율을 1x(fit)→`MIN_SCALE=0.3`으로 낮춰 **fit 이하로 줌아웃 가능** → 캔버스가 작아지고 주변에 넓은 작업공간이 생김. `userScale≤1` 리셋 제거(줌아웃 상태 유지).
  - `clampAndRefresh`/`axisAdjust` 2분기: 줌아웃(캔버스<화면)=화면 안에 완전히 보이도록 유지하며 자유 이동, 줌인(캔버스>화면)=화면 중앙은 항상 캔버스가 덮도록(드로잉 유실 방지, 한 변당 최대 절반 여백). 회전/리사이즈 시 `resetZoom`으로 fit 복귀.

- **페이지 경계선(드로잉 영역 명확화)** (v1.51.0, 2026-08-14):
  - 증상: 가로 화면에서 A4 세로 페이지가 자동회전해 높이에 맞춰지고 폰이 더 넓어 좌우 레터박스(배경) 발생 → 배경은 종이 밖이라 터치해도 안 그려지는데 앱 배경색과 종이색이 비슷해 "좌우 드로잉 인식 안됨"으로 느껴짐.
  - 수정: `onDraw`에서 페이지 rect를 화면 좌표로 매핑해 **은은한 헤일로 + 얇은 테두리**를 그림 → 그릴 수 있는 종이 영역이 주변 작업공간과 명확히 구분됨(회전 q는 90° 배수라 mapRect 정확). 터치 로직 변경 없음.

- **치수 중앙 설정값 파일** (v1.52.0, 2026-08-14):
  - 사용자 요청: "앱에 쓰는 치수들 한 파일에 모아서 내가 수정하면 자동 반영."
  - `ui/theme/Dimens.kt` 신설 — `object Dimens`에 그룹별(Calendar/CleanCalendar/Canvas/Brush) dp·sp·배율 값 정리, 각 줄 한글 주석. 숫자만 고치고 리빌드하면 반영.
  - 연결: 일기달력 탭(topSpacer/sideMargin/titleGap/yearSp/monthSp/weekdaySp/daySp/arrowIcon/todayDisc), 클린달력(sidePadding/top/bottom/titleGap/yearSp/monthSp), 캔버스(outerPadding, BrushView min/maxZoom), 브러시 기본 굵기(pen/pencil/crayon/water/eraser) — Sketchbook·Shared·Diary 3개 화면 + BrushView.
  - 미연결(추후 확장 가능): Shared 분할뷰 padding(16dp, 좁게 유지), 기타 화면별 세부 dp. 새 값은 Dimens에 추가 후 `Dimens.<그룹>.<이름>` 참조.

- **배경 방향 자동 회전(잘림 최소화)** (v1.53.0, 2026-08-14):
  - 증상: paper 배경이 가로형(1672×941)이라 세로 캔버스(예: 모바일 390×844)에 커버-핏하면 좌우가 과도하게 잘림.
  - 수정: `drawPaper`가 배경과 페이지의 방향(가로/세로)이 다르면 배경을 90° 회전 후 커버-핏 → 배경 긴 변이 페이지 긴 변을 따라가 훨씬 많이 쓰이고 잘림 최소화. 매트릭스(`paperM`)로 회전·스케일·중앙배치 통합, onDraw/exportBitmap 공통 적용(캔버스 픽셀 좌표 기준).

- **온보딩 오리 GIF 애니메이션** (v1.54.0, 2026-08-14):
  - 온보딩(Splash/Login) 정적 오리(duck_walk.png)를 걷는 GIF(`res/raw/duck_walk.gif`, image/source/duck-walk.gif)로 교체.
  - GIF는 `painterResource`로 애니 안 됨 → Coil(coil-compose+coil-gif 2.7.0) 추가, 공용 `ui/DuckWalk.kt`가 GIF 디코더(API28+ ImageDecoderDecoder / 이하 GifDecoder) 포함 ImageLoader로 `AsyncImage` 재생. Splash·Login 둘 다 사용.

- **임시 치수 주석 제거 + 스크린샷 정리** (v1.55.0, 2026-08-14):
  - 달력 탭의 임시 스펙 주석(DevAnno) 완전 삭제: `dev/DevAnno.kt` 삭제, DiaryCalendarScreen/AiryCalendar의 marks·devBounds·DevAnnoOverlay·DiaryDevNotes·관련 import 제거(치수는 Dimens 참조 유지).
  - 실수로 커밋됐던 폰 스크린샷(`image/UI design/Screenshot_*.jpg`) git 제거.

- **홈 캐러셀 · 마법사 라이브 미리보기 · 리스트 필터/그리드 · 치수 재기입 · A안 온보딩 카피** (v1.56.0, 2026-08-14). 사용자가 dp/sp 주석이 달린 7장의 시안 이미지를 제공, 항목별로 반영:
  1. **홈 캐러셀**: "최근 스케치북" 가로 리스트 → `HorizontalPager` 캐러셀로 교체(`HomeCarousel`). 센터 노트가 크게(`Dimens.Home.carouselCenterW/H`=267.5/402.5dp), 옆 노트는 작고 흐리게(`carouselSideW/H`=217/327dp, distance 기반 lerp+alpha). 좌우 peek 여백은 `BoxWithConstraints`로 화면폭에서 동적 계산(센터가 항상 스펙 폭 확보).
  2. **홈 액션 버튼**: 상단 "Draw your time"(Cavorting 78sp) 타이틀 아래 68dp 간격에 새 노트(+)/공유 만들기(Share)/참여(Login) 원형 아이콘 3개 추가. 탭하면 스케치북 탭으로 이동 + 마법사가 해당 타입으로 바로 열림(`WType` 공개, `CreateWizard(initialType=...)`가 TYPE 스텝 건너뜀, `SketchbookTab(openWizardAs, onWizardOpened)` / `MainScreen`의 `pendingWizardType`로 배선).
  3. **마법사 배경 라이브 미리보기**: WStep.BG에 선택된 배경을 즉시 보여주는 미리보기 이미지(110dp) 추가 — 스와치 탭 시 바로 갱신. (파란 아이콘 = 캔버스 사이즈/배경 선택지라는 기존 구조는 유지, 마법사 카드 자체를 시안처럼 통짜 카드로 재설계하진 않음 — AlertDialog 구조 유지.)
  4. **리스트 개인/공유 필터**: "내 스케치북"/"함께 그린" 섹션 동시 표시 → 사람/그룹 아이콘 토글(`FilterIconBtn`)로 하나씩만 표시. 타이틀도 Cavorting "Sketchbook list" 78sp로 교체.
  5. **리스트 그리드**: `GridCells.Adaptive(150dp)` → `GridCells.Fixed(3)` 고정 3열.
  6. **달력 탭 치수 재기입**: `Dimens.Calendar` 갱신 — topSpacer 110→63.5dp, sideMargin 24→71dp, yearSp 60→63sp, monthSp 100→113sp, weekdaySp 25→26sp(daySp 21 유지), 신규 `editIcon`=35dp(편집 연필 아이콘에 적용), 신규 `bottomMargin`=45dp(그리드 아래 여백). `Dimens.CleanCalendar`도 갱신 — sidePadding 44→71dp, topPadding 30→63.5dp, bottomPadding 30→45dp, yearSp 30→26sp, monthSp 70→78sp.
  7. **Dimens.kt 전체 반영**: 신규 그룹 `Screen`(bottomMargin 45dp 공용), `Onboarding`(titleSp 130sp/subtitleSp 37sp), `Home`, `Wizard`, `SketchbookList` 추가. 온보딩(Splash/Login) 타이틀 "G1 SKETCH"→"Daily sketch"(130sp) + 부제 "Draw together, keep the little days"(37sp) 신설(시안 이미지에 포함된 A안 온보딩 카피 반영).
  - **스코프 메모**: 홈 액션 아이콘 크기(56dp)는 시안에 명시 안 돼 추정치. "스케치북 클릭 시 화면"(시안 5번째 이미지)은 홈/마법사 스펙과 중복이라 별도 화면으로 만들지 않음. 마법사 카드의 전체 비주얼(둥근 카드, 생성/취소 배치 등)은 이번엔 손대지 않음 — 필요하면 다음 버전에서 커스텀 다이얼로그로 재설계 가능.

- **온보딩 enter 게이트 + 탭 타이틀 통일** (v1.57.0, 2026-08-14):
  - **온보딩**: 스플래시가 1.2초 후 자동으로 넘어가던 것을 제거, `SplashScreen(onEnter)`에 "enter" 알약 버튼 추가 — 눌러야 다음(로그인)으로 진입. `MainActivity`의 `LaunchedEffect(Unit){delay(1200)...}` 삭제.
  - **탭 타이틀 통일**: `Dimens.Screen`(신규) = `topMargin`63.5dp/`bottomMargin`45dp/`titleSp`78sp를 홈·스케치북 리스트·설정 3탭이 공유. 설정 탭 타이틀 "설정"→"Setting"(Cavorting, 78sp, 가운데 정렬, onSurface — 기존 24sp 기본폰트/좌측정렬에서 통일). 스케치북 리스트 타이틀도 가운데 정렬로 변경.
  - **정렬 버그 수정**: `SketchbookListScreen`이 자체 `Scaffold`를 갖고 있어 바깥 탭 Scaffold의 상태바 인셋과 이중으로 겹쳐 타이틀이 다른 탭보다 아래로 밀려 보이던 문제 → `contentWindowInsets = WindowInsets(0)`로 이중 인셋 제거.
  - `Dimens.Calendar.topSpacer`/`bottomMargin`을 `Screen.topMargin`/`bottomMargin` 참조로 변경(매직넘버 중복 제거, 항상 같은 값 보장). `Dimens.Home.titleSp`/`Dimens.SketchbookList.titleSp` 제거하고 `Screen.titleSp`로 통합.

- **스케치북 리스트탭 노트 추가 버튼 제거** (v1.58.0, 2026-08-14):
  - `SketchbookListScreen`의 `FloatingActionButton`("+") 삭제 — 새 스케치북 생성은 홈 탭의 새 노트 아이콘(v1.56.0)으로 일원화. `onCreate` 파라미터/콜백도 함께 제거(`SketchbookTab`의 `creating=true` 트리거 배선 삭제).
  - 빈 리스트 안내문구 "+ 로 만들어보세요." → "홈 화면에서 만들어보세요."로 수정(더 이상 이 화면에 + 버튼이 없으므로).
  - 부수 정리: 이제 안 쓰는 `FloatingActionButton`/`Icons.Filled.Add` import 제거.

- **온보딩 타이틀 줄바꿈 · 달력 탭 레이아웃 재적용 · 새 스케치북 카드 통합 + 배경 라이브 프리뷰** (v1.59.0, 2026-08-14), 시안 2장 반영:
  1. **온보딩 타이틀 자동 줄바꿈**: `OnboardingTitle`(신규 공용 컴포저블, `ui/OnboardingTitle.kt`) 추가 — `rememberTextMeasurer()`로 "Daily sketch" 한 줄 폭을 실측해 화면 폭보다 크면(기기 비율상 타이틀이 화면보다 클 때) "Daily"/"sketch" 두 줄로 자동 전환. Splash/Login 두 화면 모두 이 컴포저블로 교체.
  2. **달력 탭 레이아웃**: 다른 탭과 동일한 위치·크기의 탭 타이틀 "A piece of today"(`Dimens.Screen.titleSp`=78sp) 신설 — 연도/월 위에 추가(다른 탭 타이틀과 정렬·색 통일). 이전/다음 달 화살표를 정사각 35dp → 10×20dp(`Dimens.Calendar.arrowIconW/H`)로 재조정. 신규 `Dimens.Calendar.topTitleGap`=16dp(타이틀~연도 간격).
  3. **새 스케치북(개인) 화면 통합**: 기존 이름→사이즈→배경 3단계 `AlertDialog` 순차 전환을 없애고, 이름 입력/종이/디스플레이/배경 선택을 카드 한 화면에 전부 배치(`PersonalCreateCard`, 신규). 디스플레이 아이콘 순서를 시안대로 데스크톱/모바일/태블릿으로 재정렬. 하단에 취소(좌)/생성(우) 버튼. 공유 만들기(이름만)·참여(코드만) 흐름은 기존 단일 단계 그대로 유지.
  4. **배경 선택 시 팝업 뒷배경 실시간 프리뷰**: `PersonalCreateCard`를 `AlertDialog` 대신 커스텀 `Dialog`(`usePlatformDefaultWidth=false`)로 구현 — 스와치를 탭하면 선택한 종이 재질이 팝업 카드 뒤 전체화면 배경(어둡게 스크림 처리)에 즉시 적용돼 실제 캔버스 배경 느낌을 바로 확인할 수 있음. 기존 카드 내부 110dp 미리보기 이미지는 제거(뒷배경 프리뷰로 대체).
  - `Dimens.Wizard`에 `cardWidth`=425dp, `cardRadius`=28dp 추가.

- **캔버스 배경 크롭 · 색상/브러시 재탭 설정 · 제스처 단축키 · 되돌리기 20회 · 표지 그림자** (v1.60.0, 2026-08-15):
  1. **배경 이미지 크롭**: `BrushView.onDraw`에서 페이퍼를 그리기 전에 `clipRect(0,0,cw,ch)` 적용 — cover-fit 배경이 캔버스 경계 밖으로 삐져나와 줌아웃 여백(워크스페이스)까지 덮던 문제 수정. 이제 배경은 항상 캔버스 크기에 맞게 잘림.
  2. **색상 즐겨찾기 재탭 → 색상휠**: 즐겨찾기 5개 중 이미 선택된(활성) 색을 한 번 더 탭하면 그 자리에 색상휠 팝업이 뜸 — 드래그로 고르면 즉시 활성 색 + 그 즐겨찾기 슬롯에 반영. 기존 롱프레스(현재색으로 덮어쓰기) 방식은 제거(`combinedClickable`→`clickable`), `onEditFavorite` 콜백이 `(index)`에서 `(index, 새색상)`으로 변경.
  3. **브러시 재탭 → 굵기/불투명도 설정**: 툴바의 별도 굵기 원/불투명도 % 컨트롤을 없애고, 이미 선택된 브러시(펜/연필/크레파스/수채화/지우개) 아이콘을 한 번 더 탭하면 굵기+불투명도 슬라이더 패널이 뜨도록 변경(`BrushBtn`에 `onReclick` 추가, `SliderCard`→`SlidersPanel`+`SliderRow`로 리팩터). 지우개는 굵기만 표시.
  4. **제스처 단축키 설정 추가**: 설정 탭에 "제스처" 섹션 신설 — 두 손가락 탭 / 세 손가락 탭 / 화면 길게 누르기 각각에 없음·뒤로가기(되돌리기)·앞으로가기(다시실행)·색상 스포이드 중 매핑 가능(`GestureAction` enum, `SessionStore`에 영속). `BrushView`에 실제 제스처 인식 로직 구현(2/3손가락 탭 판정, 롱프레스 타이머+슬롭 판정, 스트로크 시작 취소) + 화면에 실제 보이는 색(스트로크 우선, 없으면 배경 재질)을 집는 스포이드(`pickColorAt`). 기본값은 전부 "없음"이라 기존 동작은 그대로 유지됨.
  5. **되돌리기 20회로 확장**: undo 스택 캡을 4 → 19로 확장(`MAX_UNDO`) — 기존에 4~5번만 되던 되돌리기가 20번까지 가능.
  6. **스케치북 표지 그림자**: `SketchbookListScreen`의 `CoverCard`에 `Modifier.shadow(6.dp, coverShape, clip=false)` 추가 — 그리드의 표지 카드 뒤로 은은한 그림자.

- **온보딩 반응형 타이틀/오리 비율, 브러시별 설정 패널, 되돌리기 버그 수정, 캔버스 그림자, 스포이드, 캐러셀 플링 제한** (v1.61.0, 2026-08-15). 시안 1장 + 9개 지적사항 반영:
  1. **온보딩 줄바꿈 겹침 수정**: `OnboardingTitle`에 `lineHeight = fontSize*1.15`를 명시해 "Daily"/"sketch" 두 줄로 나뉠 때 글자가 겹치던 문제 해결.
  2. **타이틀 크기 반응형 + 오리 비율 고정**: 타이틀을 130sp 고정이 아니라 화면 폭에 맞춰 최대 130sp에서 필요한 만큼(4sp 단위) 줄어들도록 변경 — 대부분 기기에서 한 줄로 표시되고, 정말 안 들어갈 때만 두 줄로 전환. 대신 오리(DuckWalk)는 `Dimens.Onboarding.duckW/duckH`(765×510dp) 비율로 고정(`aspectRatio` + `widthIn(max=765dp)`), 화면 폭에 맞춰 비율 유지한 채 축소.
  3. **브러시별 굵기/불투명도 패널 재설계**: 재탭으로 여는 설정 패널이 브러시 아이콘 그룹 전체에 하나만 떠서 "정확히 어떤 브러시인지" 불명확했던 문제 → 각 브러시 아이콘(`BrushBtnWithPanel`)이 자기 위치에 자기 이름("펜"/"연필"/"크레파스"/"수채화"/"지우개")을 헤더로 단 패널을 직접 띄우도록 변경(굵기/불투명도 값 자체는 처음부터 브러시별로 독립 저장돼 있었음 — 표시만 모호했던 것).
  4. **표지 그림자 강화**: `CoverCard`(리스트) 그림자 6dp→12dp + 완전 불투명 검정 ambient/spot 색으로 눈에 띄게. 홈 캐러셀의 표지 박스에도 동일한 그림자를 새로 추가(이전엔 그림자 없었음).
  5. **새 스케치북 카드 시안 정합**: 이름 입력창을 완전 pill 모양(`RoundedCornerShape(50)`)으로, 디스플레이~배경 섹션 사이에 구분선(`HorizontalDivider`) 추가, 모바일 사이즈 아이콘의 상단 표시를 줄→점(카메라 노치)으로 수정.
  6. **스포이드 기능**: 툴바에 스포이드 아이콘 버튼 신설(`eyedropArmed` 상태) — 누르면 다음 캔버스 탭이 그리기 대신 그 지점의 색(스트로크 우선, 없으면 배경 재질)을 집어 활성 색으로 반영. 제스처로 매핑하는 기존 스포이드와 별개로 항상 접근 가능.
  7. **되돌리기(제스처) 버그 수정**: `pushUndo()`가 매 터치다운(ACTION_DOWN)마다 즉시 `redo.clear()`를 호출해서, 손가락을 대는 순간 리두 스택이 비워져 있었음 — 2/3손가락 탭이나 롱프레스로 "앞으로가기"를 매핑해도 항상 무동작이었던 근본 원인. `redo.clear()`를 스트로크가 실제로 커밋되는 `endStroke()`(+`clearCanvas()`)로 이동 — 그림을 그린 뒤에만 리두 이력이 사라지도록 수정(툴바의 되돌리기/다시하기 버튼과 완전히 동일하게 동작).
  8. **캔버스 라인 경계 → 그림자**: `BrushView`의 페이지 외곽 halo+border 라인을 없애고, 페이지 뒤에 옅은 레이어드 사각 그림자(`drawPageShadow`, BlurMaskFilter 없이 3겹 반투명 라운드렉트로 구현, 좌상단에서 비추는 느낌의 오프셋)로 대체.
  9. **홈 캐러셀 플링 제한**: `HorizontalPager`에 `flingBehavior = PagerDefaults.flingBehavior(pagerSnapDistance = PagerSnapDistance.atMost(1))` 적용 — 세게 스와이프해도 한 번에 최대 한 장만 넘어가도록 제한(빠른 플릭에 여러 장이 주루룩 넘어가던 문제).

- **브러시 패널 재작동 수정, 스포이드 드래그 프리뷰, 되돌리기 화면흔들림 수정, 그림자 값 통일, 리스트 열수 설정, 온보딩 버튼 아웃라인화** (v1.62.0, 2026-08-15):
  1. **브러시 굵기/불투명도 패널이 안 뜨던 문제 수정**: v1.61.0에서 각 브러시 버튼이 로컬 `remember` 상태로 자기 패널을 열고 닫게 했던 구조를 버리고, 즐겨찾기 편집과 동일하게 `BrushControls` 상위에서 어떤 브러시 패널이 열려있는지 호이스팅해서 관리하도록 재작성(`openBrushPanel`/`openEraserPanel`). 정확한 원인은 특정하지 못했지만, 이미 안정적으로 동작하는 즐겨찾기 팝업과 동일한 패턴으로 통일해 재발 가능성을 없앰.
  2. **새 스케치북 카드 배경 선택 가운데 정렬**: 배경 스와치 `LazyRow`에 `Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)` 적용.
  3. **스포이드 재설계 + 실제 동작 확인**: 탭 한 번에 즉시 픽업하던 방식 대신, 무장(armed) 상태에서 캔버스를 누르고 있는 동안 손가락 위치의 색을 계속 샘플링해 손가락 위쪽에 뜨는 플로팅 원(`EyedropFloatingPreview`)으로 실시간 미리보기, 손을 뗄 때 그 색으로 확정 적용하도록 변경(`BrushView`에 `onEyedropPreview`/`onEyedropCancel` 콜백 추가, 터치 처리를 무장 상태일 때 완전히 분리된 경로로 재작성). 3개 캔버스 화면(그림일기/스케치북/공유) 전부에 플로팅 프리뷰 연결.
  4. **되돌리기(제스처)로 화면이 살짝 움직이던 문제 수정**: 2/3손가락 탭 도중 손가락을 떼기 전까지의 미세한 떨림에도 핀치 확대/축소·이동 변환이 즉시 적용되고 있었음 — 이동량이 tap-slop을 넘어서 "진짜 핀치"로 확정되기 전까지는 변환을 아예 적용하지 않도록 변경. 탭(되돌리기/앞으로가기 제스처)에서는 화면이 전혀 움직이지 않고, 실제 핀치는 기존과 동일하게 동작.
  5. **캔버스 그림자 값을 표지 그림자와 통일**: `BrushView`의 `drawPageShadow`를 dp 단위(density 반영)로 재작성하고 강도를 표지 그림자(12dp, 완전 불투명 검정)에 맞춰 상향.
  6. **스케치북 리스트 열 수 설정**: 리스트 상단에 햄버거 아이콘 추가 → 3/4/5열 중 선택하는 드롭다운(`SessionStore.gridColumns`에 저장, 즉시 반영).
  7. **온보딩 버튼 아웃라인화**: Splash의 "enter"·Login의 "continue" 버튼을 검정 솔리드 채움에서 검정 2dp 테두리 + 투명 배경(선형) 스타일로 변경.

- **UI/UX Pro Max 스킬 검토 반영** (v1.63.0, 2026-08-15): `ui-ux-pro-max` 스킬(터치 타겟/접근성/명도대비 DB)로 앱 전반을 점검, 발견된 5건 전부 수정.
  1. **설정 > 제스처 줄 가로 넘침 수정**: `GestureActionRow`의 4개 FilterChip("없음"/"뒤로가기"/"앞으로가기"/"색상 스포이드")이 스크롤 없는 `Row`에 있어 좁은 화면에서 마지막 칩이 잘리던 문제 — `horizontalScroll` 추가.
  2. **표지 텍스트 명도대비 수정**: `CoverCard`의 크림색 텍스트가 일부 커버색(연한 모브 `0xFFB79A94` 등) 위에서 대비율 약 2.2:1(WCAG AA 4.5:1 미달)였던 문제 — 텍스트 뒤에 하단 그라디언트 스크림 추가, 즐겨찾기·삭제 아이콘에도 반투명 원형 배경 추가해 배경색과 무관하게 항상 읽히도록 수정.
  3. **브러시 툴바 터치 타겟 확대**: 즐겨찾기 색상(28dp)·색상휠(28dp)·브러시 아이콘(42dp)·일반 아이콘 버튼(40dp)이 전부 Android 권장 최소 48dp 미만이었던 문제 — 시각 크기는 그대로 두고 탭 가능 영역만 48dp로 확대(`BrushControls.kt`).
  4. **온보딩 다크모드 지원**: Splash/Login이 `MaterialTheme` 대신 하드코딩된 세이지그린 배경/잉크 텍스트를 항상 써서 다크모드 사용자도 매번 밝은 화면을 먼저 보던 문제 — `onboardingPalette()`(`OnboardingTitle.kt`)로 라이트/다크 각각의 브랜드 톤(라이트: 기존 세이지그린, 다크: 어두운 올리브 + 밝은 잉크)을 분기.
  5. **색상 스와치 접근성 라벨 추가**: 즐겨찾기 색상·색상휠 스와치에 스크린리더용 `onClickLabel`("즐겨찾기 색상 N", "사용자 지정 색상 고르기") 추가(브러시 아이콘엔 이미 있던 패턴을 색상 스와치에도 동일 적용).

- **브러시 설정 패널 진짜 원인 발견·수정** (v1.63.1, 2026-08-15): v1.61.0·v1.62.0에서 두 차례 다르게 고쳤는데도 "브러쉬 눌러서 설정값 조절하는 기능 전혀 안됨" 재보고 — 이번엔 `ui/Interactions.kt`의 공용 `bounceClick` 모디파이어 자체에 있던 stale-closure 버그가 근본 원인으로 확인됨. `Modifier.pointerInput(enabled) { detectTapGestures(onTap = { onClick() }) }`에서 `enabled`(항상 `true`)가 안 바뀌면 코루틴이 재시작되지 않아, `onTap`이 **최초 컴포지션 시점의 `onClick` 클로저를 영원히 재사용**함. `BrushBtnWithPanel`의 `onClick = { if (selected) setPanelOpen(!panelOpen) else onClick() }`처럼 매 리컴포지션마다 값이 바뀌는 `panelOpen`(`State` 아닌 평범한 Boolean 파라미터)을 캡처하는 콜백은 항상 "최초 값"만 보고 토글해서 사실상 안 열림 — 반면 `view?.undo()`처럼 `State`(`by remember`) 객체만 참조하는 콜백은 클로저가 stale해도 매번 최신 값을 읽어서 우연히 정상 동작했던 것. `rememberUpdatedState(onClick)`로 감싸 매 리컴포지션의 최신 콜백을 항상 참조하도록 수정 — `bounceClick`을 쓰는 모든 곳(브러시 패널 포함)에 공통 적용되는 근본 수정.

- **캔버스 그림자 계단현상 수정 + 롱프레스 스포이드 피드백 수정** (v1.63.2, 2026-08-15):
  1. **그림자가 그라데이션이 아니라 3단으로 보이던 문제**: `drawPageShadow`가 3겹의 평평한 반투명 라운드렉트만 쌓아서 경계가 뚜렷한 "계단"으로 보였음 — 18겹 + 2차함수 알파 감쇠 곡선으로 재작성해 사실상 매끄러운 그라데이션으로 보이도록 수정(레이어 수만 늘린 저비용 근사, BlurMaskFilter/오프스크린 비트맵 캐싱 없이 동일 프레임 비용대).
  2. **"길게 누르기 = 스포이드" 제스처가 안 먹는 것처럼 보이던 문제**: 실제로는 색은 바뀌고 있었지만 롱프레스 경로가 툴바 스포이드처럼 플로팅 프리뷰 없이 "조용히" 색만 바꾸고 끝나서, 사용자 입장에서 아무 반응이 없는 것처럼 보였음 — 롱프레스가 감지되면(500ms) 그 자리에서 바로 색을 확정하는 대신, 툴바 스포이드와 동일한 "누른 채 드래그하면 플로팅 원으로 미리보기 → 손 뗄 때 확정" 흐름으로 넘겨주도록 수정. 이제 두 진입 경로(툴바 버튼/제스처)가 동일한, 눈에 보이는 인터랙션을 공유.

- **스케치북 고정 15페이지 + 페이지 패널 + 페이지 넘기기 애니메이션** (v1.64.0, 2026-08-15):
  1. **생성 시 15페이지 전부 자동 추가**: `SketchbookRepository.create()`가 `pageCount=1` 대신 `pageCount=MAX_PAGES(15)`로 시작 — 실제 물리 스케치북처럼 처음부터 고정 15페이지. 빈 페이지는 그림을 그리기 전까지 파일이 생성되지 않는 기존 지연 저장 방식 그대로라 저장공간은 그대로.
  2. **페이지 버튼 신설(첨부 아이콘 = 레이어 스택) + 버튼바 정리**: `BrushControls`의 페이지 관련 인라인 클러스터(이전/다음 화살표+쪽수+추가+삭제 5개 아이콘)를 없애고, 새 "페이지" 아이콘(`Icons.Filled.Layers`) 하나로 통합. 누르면 `PagePanel`(신규, `sketchbook/PagePanel.kt`)이 열려 상단에 이전/다음+쪽수, 아래에 15페이지 썸네일이 세로 리스트로 나열되어 바로 원하는 페이지로 이동 가능. 페이지가 이제 고정 개수라 추가/삭제 개념 자체가 없어짐. 툴바의 "나가기" 아이콘도 제거(시스템 뒤로가기로 이미 나가짐 — `BackHandler` 그대로 유지).
  3. **페이지 넘기기 애니메이션**: `BrushView.captureScreenBitmap()`(신규) — 현재 화면에 실제로 보이는 그대로(확대/이동/회전 상태 포함)를 뷰 픽셀 크기로 캡처. 페이지 전환 시 이 스냅샷을 살짝 페이드시키며 넘어가는 방향으로 슬라이드시켜 밀어내고, 그 아래에서 이미 갱신된 새 페이지가 드러나는 방식(`PageTurnOverlay`)으로 종이 넘기는 느낌 구현 — BlurMaskFilter나 실제 3D 컬(curl) 없이 가벼운 근사.
  - 개인 스케치북(`SketchbookCanvasScreen`)·공유 스케치북(`SharedBookScreen`) 양쪽 모두 동일하게 적용.

- **캔버스 그림자 연하게 + 색상 버튼 사각 리플 수정** (v1.64.1, 2026-08-15):
  1. `drawPageShadow`의 최대 알파를 70→36으로 낮춰 그림자를 더 옅게(약 절반 강도) 조정.
  2. 즐겨찾기 색상·색상휠 스와치가 접근성 터치 영역 확대(v1.63.0) 이후, 클릭 시 리플이 그 사각형 48dp 히트박스 전체에 네모나게 번지던 문제 — 해당 박스에 `.clip(CircleShape)` 추가해 리플이 원형으로만 번지도록 수정(시각 크기는 그대로, 터치 영역도 그대로 48dp 유지).

- **캔버스 테두리 얇은 선으로 교체, 리플 정확한 크기로, 아날로그 페이지 넘기기, 세손가락 드래그 인터랙션** (v1.65.0, 2026-08-15):
  1. **캔버스 그림자 제거 → 얇고 연한 선**: 레이어드 그림자(`drawPageShadow`) 삭제하고 `pageEdge` 1dp 스트로크(반투명 검정 18% 정도)로 교체 — 페이지 경계만 은은하게 표시.
  2. **리플이 버튼 크기에 정확히 맞도록 수정**: 이전엔 48dp 터치 영역 전체에 리플이 번졌음(원형이긴 했지만 스와치보다 큼). 이제 `MutableInteractionSource`를 공유해서, 터치 판정은 48dp 박스가 하되 리플 자체는 실제 28dp 스와치 크기의 원에만 그려지도록 분리(`Modifier.indication(interactionSource, LocalIndication.current)`를 안쪽 28dp 박스에 적용).
  3. **아날로그 감성의 페이지 넘기기 애니메이션**: 평면 슬라이드 대신, `graphicsLayer`의 `rotationY`+`cameraDistance`로 페이지가 넘어가는 모서리를 축으로 3D 회전하며 넘어가듯 표현, 회전량에 비례해 어두워지는 스크림을 더해 종이가 빛에서 멀어지는 느낌 추가(`PageTurnOverlay`).
  4. **세 손가락 좌우 드래그로 페이지 넘기기(인터랙티브)**: `BrushView`에 3손가락 수평 드래그 감지 추가(기존 3손가락 탭 제스처와는 별도, 이동량이 슬롭을 넘으면 드래그로 확정되고 탭은 취소됨). 손가락을 움직이는 동안 실시간으로 페이지가 손가락을 따라 회전하며 미리보기되고(`onPageDragProgress`), 손을 뗄 때 25% 이상 넘겼으면 페이지 전환 완료, 아니면 제자리로 스프링백(`onPageDragEnd`). 개인/공유 스케치북 화면 모두 적용.

- **페이지 패널 바둑판 그리드로 변경** (v1.65.1, 2026-08-15): `PagePanel`의 세로 리스트(썸네일+"N쪽"+체크아이콘 한 줄)를 3열×5행 그리드(`LazyVerticalGrid`)로 교체 — 고정 15페이지라 3열이 딱 맞아떨어짐. 각 칸은 썸네일 아래 페이지 숫자만 표기(선택된 페이지는 굵게+강조색, 테두리도 두껍게).

- **홈 캐러셀 입체감, 표지 비율 통일, 달력 화살표·연월 표기 수정** (v1.66.0, 2026-08-15):
  1. **홈 캐러셀 입체감 강화**: 가운데 노트 267.5×402dp / 옆 노트 217×327dp 크기는 그대로 정확히 맞추고, 그림자 강도를 근접도에 따라 보간(가운데 18dp → 옆 4dp)해서 가운데가 "튀어나오고" 옆은 "가라앉는" 느낌 추가. 페이드도 살짝 강화(거리비례 최대 45%→50% 흐려짐).
  2. **스케치북 리스트 썸네일 비율 통일**: `Dimens.Home.coverRatio`(= 267.5/402, 홈 캐러셀 가운데 노트 비율)를 새로 정의하고 `CoverCard`가 기존 하드코딩 0.78 대신 이 값을 쓰도록 변경 — 모든 화면에서 표지 비율이 항상 동일.
  3. **달력 화살표 정정**: 이전엔 Material 아이콘(정사각형 뷰박스)을 10×20dp 비정사각 박스에 강제로 넣어서 실제로는 짧은 변(10dp)에 맞춰 축소되어 더 작아 보였음 — 직접 그리는 화살표(Canvas, 두 개의 대각선 스트로크)로 교체해 10×20dp 박스를 정확히 채우도록 수정.
  4. **연·월 표기 방식 변경**: "2026" / "Jaunaly"(월 이름, 두 줄) 대신 시안처럼 "2026.01" 한 줄로 통합, 52sp로 표기(`Dimens.Calendar.yearMonthSp`).

- **하단 네비게이션 바 여백 확보** (v1.67.0, 2026-08-15): `FloatingNavBar`(홈/스케치북/일기/설정 하단 알약형 탭바)가 시스템 네비게이션 바 바로 위에 `bottom = 10.dp`로만 떠 있어 여백이 거의 없어 보였음 — 다른 화면들과 동일한 `Dimens.Screen.bottomMargin`(45dp)으로 통일.

- **전체화면, 화면 잠금, 페이지 패널 소형 팝업화** (v1.68.0, 2026-08-15):
  1. **전체화면 버튼**: `BrushControls` 툴바에 확대(⛶) 버튼 추가 — 탭하면 시스템 상태바/네비게이션 바를 숨겨(스와이프로 다시 잠깐 꺼낼 수 있는 transient 모드) 캔버스가 화면 전체를 씀. 새 `ui/ImmersiveMode.kt`(`ImmersiveModeEffect`)로 구현, `WindowCompat.getInsetsController` 사용. 화면을 벗어나면 항상 시스템 바를 복원(`DisposableEffect`)해 다른 화면에 잔류하지 않도록 함. 전체화면 중 뒤로가기는 먼저 전체화면을 끄고, 한 번 더 누르면 실제로 나가짐.
  2. **화면 잠금 버튼**: `BrushView`에 `locked` 플래그 추가 — 켜면 핀치 줌/팬 트랜스폼과 90° 회전 버튼이 무시됨(그리기 자체는 그대로 동작). 실수로 그리다가 확대/축소·회전이 걸리는 걸 방지. 툴바에 자물쇠 아이콘으로 토글, 잠금 중엔 회전 버튼도 흐리게 표시해 왜 안 먹히는지 알 수 있게 함.
  3. **페이지 패널 소형 팝업화**: 기존엔 화면 하단 78% 높이를 차지하는 바텀시트였음 — 화면 중앙의 작은 카드(폭 292dp, 그리드는 최대 320dp까지만 차지하고 넘치면 자체 스크롤)로 교체해 캔버스가 더 많이 보이도록 함.
  - 개인 스케치북(`SketchbookCanvasScreen`)·공유 스케치북(`SharedBookScreen`) 양쪽 모두 동일하게 적용.

- **아날로그 페이지 벼륍(절반폭 기준 접힘), 페이지 넘기기 모드, 세손가락 드래그 게스처 설정화** (v1.69.0, 2026-08-15):
  1. **절반 폭 기준 접히는 페이지 넘기기**: `PageTurnOverlay`를 단일 강체 회전(가장자리 기준 door-swing)에서, 캔버스 가로 폭의 정중앙 크리즈를 기준으로 두 조각이 순차적으로 접히는 방식으로 재작성. 책등에서 먼 쪽(FAR) 조각이 먼저 접히며 사라지고(0→50%), 이어서 책등쪽(NEAR) 조각이 그 크리즈를 축으로 마저 접힘(50→100%) — FAR 조각은 NEAR 박스의 자식으로 중첩시켜 회전이 누적되도록 함(책등에서 멀수록 더 많이 휘는 실제 종이 물성과 유사).
  2. **페이지 넘기기 모드**: 캔버스가 확대/축소된 상태에서 페이지를 넘기면 핀치줌 상태와 뒤섞여 다음 페이지 위치가 어긋나던 문제 — 그리기·확대축소·팬과 페이지 넘기기를 아예 상호 배타적으로 분리. 툴바에 새 토글 버튼(📖) 추가, 켜면 `BrushView`가 한손가락 좌우 스와이프만 처리(그 외 모든 입력 무시)하며 살아있게 페이지가 넘어가는 미리보기(#1 효과)를 따라감.
  3. **세손가락 드래그 게스처 설정화**: 기존엔 3손가락 드래그가 항상 페이지 넘기기로 고정돼 있었음 — 이제 설정 > 제스처에 "세 손가락 드래그" 행 추가, 두손가락 탭/세손가락 탭/길게 누르기와 동일하게 없음·뒤로가기·앞으로가기·색상 스포이드·페이지 넘기기 중 선택 가능(기본값은 기존 동작 유지를 위해 "페이지 넘기기"). 3손가락 드래그는 이제 실시간 미리보기 없이 놓는 순간 한 번에 넘어가는 방식(라이브 드래그는 페이지 넘기기 모드 전용).
  - `GestureAction`에 `PAGE_TURN` 값 추가, `SessionStore.threeFingerDragAction` 추가. 개인/공유 스케치북 화면 모두 동일 적용.

## Next (Phase 2~4)
- **Phase 2 — 스케치북**: 생성(이름→사이즈→배경)·멀티페이지(≤15)·자동저장·공유 실시간. 캔버스에 BrushView 연결.
  - 사이즈 6종: A5/A4/A3/데스크톱1920×1080/모바일390×844/태블릿810×1080. 배경 5종(image/background/*).
- **Phase 3 — 그림일기 + 달력**: 개인 전용, 사용자당 1개, 날짜별 1장, 자정 잠금(이미지화). 달력(가로 4:3 / 세로 상단달력+하단일기), 이미지 저장.
- **Phase 4 — 마감**: 홈 대시보드 실기능(새 스케치북/참여/즐겨찾기), 계정(아바타). (색상휠+팔레트 UI = v1.22.0 완료.)
  - 남은 후보: 화면 디테일 다듬기, 공유 페이지 동기화 옵션(같은 페이지 함께 넘기기), 저장/내보내기 등.

## Decisions
- 브러시 = PNG/스탬프 감성 최우선. 연해/중심선/각짐 문제는 "웹 놀이터와 동일 코드(디스크+직접 누적)"로 해결.
- 백엔드: 지금은 무료(RTDB+Base64), Repository로 감싸 후에 이전(A안).
- 그림일기: 개인 전용·사용자당 1개.
- 라이트/다크 지원. 캔버스 90도 회전은 버튼으로 제공(+화면 방향에 맞춰 자동 회전). 제스처 회전은 없음.
- 핀치 줌(1~5배)·이동 재도입 완료(v1.15~1.17). 한 손가락 그리기 / 두 손가락 줌.
- 버전 매 업로드마다 bump + 새 태그(vX.Y.Z), 덮어쓰기 금지.

## Open / Blockers
- **빌드 잠금**: VS Code Java/Kotlin 언어서버가 `app/build/.../R.jar`를 잠가 CLI 빌드가 IOException. 대응: 빌드 전 해당 java 프로세스(kotlinLanguageServer/redhat.java/.vscode\extensions) kill 후 R.jar 삭제(사용자가 권한 허용함). `.vscode/settings.json`에 Java 자동빌드/Gradle import off + build 감시 제외 넣음.
- 빌드 JDK: standalone 없음 → Android Studio JBR. `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.
- (해결됨 v1.18.0) 구식 룸 기반 UI/데이터층 제거 완료. → v1.19.0에서 공유는 `share/*`로 새로 설계·구현(RTDB 재도입).
- **RTDB 보안 규칙 (실행 전제)**: 공유 세션이 동작하려면 로그인 사용자에게 `/shareSessions` 읽기·쓰기 허용 규칙이 배포돼 있어야 함. 규칙이 잠겨 있으면 세션 생성/참여 실패. 프로젝트=`g1-sketchbook-default-rtdb`. 운영 배포 전 적절히 제한할 것.
