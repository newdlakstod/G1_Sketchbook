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
  - 대기: **Cavorting 폰트** 적용 요청됨 — 폰트 파일(.ttf/.otf)이 프로젝트에 없어 사용자 제공 필요. 받으면 `app/src/main/res/font/cavorting.ttf`로 넣고 `FontFamily`로 달력·일기 헤더에 적용 예정.

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
