# G1 Sketchbook — Progress

아날로그 감성의 공유 스케치북 앱 (Android + Compose + Firebase). 브러시 감성이 핵심.
전체 기획은 `plan.md`, 방향 대화로 아래처럼 재정의되어 **클린 재구축** 중.

## Done
- **v2.9.16 배포** (2026-08-29, Claude): 굵기/불투명도 그립 슬라이더 재설계 — 활성화(드래그) 시
  크기는 그대로 두고 색상만 강조색으로 바뀌게, 버튼바-그립 레인 간격 좁힘, 비활성 그립 알파 70%로.
  버튼바를 가로모드에서 세로 가장자리로 바로 드래그 못 하던 도킹 버그(`nearestDock` 중심 기준으로
  재계산) 수정, 터치 배치(historical point) 처리로 빠른 스트로크 선 품질 개선. 버튼바가 TOP 도킹
  + 펼침 상태일 때 우측 상단 ScreenControls(설정 버튼)와 겹치던 문제를 버튼바 폭을 줄여 해결.
  홈 캐러셀 표지 그림자 잘림 완화(shadowSlack 16→28dp). 공유그리기 "선생님모드"가 게스트 화면에
  반영 안 되던 문제 수정 — 게스트가 host보다 다른 페이지를 보고 있으면 오버레이 조건이 계속
  거짓이었던 게 원인이라, 선생님모드 켜진 동안 게스트가 host 페이지를 자동으로 따라가게 함. 가로
  모드 홈 화면을 2열(선택한 표지를 그 자리에서 읽는 읽기모드)+3열(표지 목록, 탭하면 2열 대상 변경,
  그리기는 별도 버튼)로 재구성(세로모드는 기존 캐러셀 유지). 그리는 중 선이 버튼바 밑을 지나가면
  버튼바가 잠깐 숨어서 선이 가려지지 않게 함(`BrushView.onToolbarObstructed`). versionCode 120,
  로컬 빌드 후 `gh release create`로 배포.
- **v2.9.15 배포** (2026-08-28, Claude): 공유그리기 "선생님모드" 추가 — host가 켜면 다른 참가자
  캔버스에 host의 현재 페이지가 가이드로 겹쳐 보임(같은 페이지를 보고 있을 때만, 투명도는 보는
  사람이 직접 조절). `ShareRepository`에 `host`/`teacherMode` 필드와 `setTeacherMode()` 추가,
  `BrushView`에 `teacherOverlay`/`teacherOverlayOpacity` 오버레이 렌더링 추가. 또한 브러시별로
  따로 있던 굵기·불투명도 팝업을 버튼바 상단에 항상 떠 있는 슬라이더 바(`ActiveToolSlidersBar`)
  하나로 통합(최소화 상태에서도 계속 보임) — 브러시별 마지막 값 기억은 그대로 유지, 지우개 전용
  경계 블러만 기존처럼 별도 팝업으로 남김. versionCode 119, 로컬 빌드 후 `gh release create`로 배포.
- **v2.9.14 배포** (2026-08-26, Claude): 색상 피커 즐겨찾기 미리보기 제거 + RGB/HSL 슬라이더 트랙에
  실제 색상 반영, 붓 종류 묶음 접힘 상태에서 탭=붓 선택 팝업/길게누름=설정 패널(툴바 전체 최소화와
  동일 동작), 일기 달력 좌우 분할 뷰는 가로모드 전용(세로는 원래 1단계 방식 복원), 달력 날짜 원
  36dp→30dp. versionCode 118, 로컬 빌드 후 `gh release create`로 배포.
- **v2.9.13 배포** (2026-08-26, Claude): 색상 피커 원형 휠 재설계(+RGB/HSL 별도 탭, 무채색 슬라이더,
  즐겨찾기 20→21개 폭 맞춤 배치) · 공유 스케치북 참여 계정 동기화 · 일기 해칭 렉 수정 · 붓 종류
  묶음 접기/펼치기 · 올가미 삭제 버튼 팝업화 · 읽기모드 가장자리 뒤로가기 스와이프 충돌 수정
  (`View.systemGestureExclusionRects`) · 표지 편집창 즐겨찾기/삭제 버튼 헤더 이동 · 일기 달력
  초록점→원 통일 + 좌측 썸네일/우측 스케치 상시 분할 뷰. versionCode 117, 로컬 빌드 후
  `gh release create`로 배포(자동 CI는 여전히 pagecurl 이슈로 실패).
- **v2.9.12 배포: master 병합 + 색상 피커 RGB/HSL·즐겨찾기 20개 + 동기화 검정 배경 수정**
  (2026-08-26, Claude): `codex/release-v2.9.9-pagecurl` 브랜치(fast-forward 가능, 히스토리 분기
  없었음)를 master로 병합. 이번에 새로 추가된 것:
  - 색상 피커에 R/G/B, H/S/L 숫자 입력칸 추가(SV사각형+Hue바 아래, 항상 표시, 서로 실시간 연동).
  - 즐겨찾기 색상 5개 → 20개(`SessionStore.FavoritesCount`). 툴바엔 여전히 5개만 인라인, 나머지는
    새 팔레트 버튼으로 여는 4x5 그리드 팝업에서. 백업 동기화의 `favoriteColors.size` 검증도 20으로 맞춤.
  - **태블릿↔폰 전환 시 스케치 페이지 배경이 검게 바뀌던 버그 수정**: 스케치북 페이지는 로컬에
    종이 없이 필기만 투명 PNG로 저장되는데(`exportContent()`), `BackupRepository.encode()`가
    항상 JPEG(알파 없음)로 압축해서 올려 투명한 곳이 검게 눌러 붙었다. `pushSketchbookPage`만
    PNG(`preserveAlpha=true`)로 바꿈 — 표지/일기/아바타는 원래 불투명이라 JPEG 유지.
    **주의**: 이미 검게 오염되어 동기화된 기기의 로컬 페이지 파일은 자동 복구 안 됨 — 원본을 그린
    기기가 그 페이지를 다시 저장(재동기화)해야 정상본으로 덮어써짐.
  versionCode 116, APK `daymory-v2.9.12-116.apk`, SHA-256
  `776349A491CEE4853762228614F0771EAD6FC5409701FD09354D5690AE05DA3C`, 로컬 빌드 후
  `gh release create`로 수동 배포(자동 CI는 여전히 pagecurl 이슈로 실패 — 아래 항목 참고).
- **v2.9.11 배포: 스케치 붓질 렉 수정** (2026-08-25, Claude): 구글 계정 백업/동기화 기능을 붙이며
  `SketchbookSync.savePageSynced`가 로컬 페이지 저장(`repo.savePage`, PNG 인코딩+디스크 쓰기)을
  `Dispatchers.IO`로 안 감싸고 호출 스레드(=`onStrokeEnd`가 불리는 메인 스레드)에서 그대로
  실행하는 회귀가 있었다 — 백업 기능 붙이기 전엔 항상 IO 스레드에서 돌았음. 매 붓질마다
  발생해 드로잉 렉으로 체감됨. 로컬 저장+백업 업로드를 하나의 IO 코루틴으로 다시 묶어서 고쳤다
  (다이어리 쪽 `saveCurrent`는 원래도 올바르게 감싸져 있어서 이 버그 없었음). versionCode 115,
  APK `daymory-v2.9.11-115.apk`, SHA-256 `3E86372ED1E7EB20681A7540B904A08033E67720D8EFD3B210F89463CC573FDB`,
  GitHub Release로 수동 배포(아래 CI 이슈 때문에 자동 워크플로 대신 로컬 빌드 후 `gh release create`).
  구글ID 백업/동기화 기능 자체(12개 태스크 + 전체검토 9건 수정)도 이번에 master에 완료·배포(v2.9.8).
- **CI 릴리스 워크플로(`release.yml`)가 pagecurl 도입 이후 항상 실패함** (2026-08-25 발견, Claude):
  `:app`이 `:pagecurl` 프로젝트에 의존하는데, 그 경로가 커밋 안 되는 `local.properties`의
  `pagecurl.dir`(로컬 머신 전용 절대경로)로만 지정돼 있어 GitHub Actions의 fresh checkout에는
  `:pagecurl` 프로젝트 자체가 없다 ("No matching variant of project :pagecurl was found. No
  variants exist."). v2.9.9/2.9.10/2.9.11 모두 CI 빌드는 실패했고, 실제 배포는 로컬에서
  `assembleDebug`로 만든 APK를 `gh release create`로 수동 업로드해서 이뤄짐 — 태그 푸시가 트리거하는
  CI 실행 자체는 매번 실패로 남지만 릴리스 자산엔 영향 없음. 근본 수정(예: pagecurl을 git
  submodule로 바꾸거나 CI에서 별도 checkout 스텝 추가)은 아직 안 함 — pagecurl 모듈 배치를 담당한
  세션과 상의 필요.
- **빌드 잠금 원인 재확인**: 기존 노트는 "Java 언어서버(redhat.java)"만 지목했는데, 실제로 R.jar를
  물고 있던 건 VS Code의 **Kotlin** 언어서버(`fwcd.kotlin`, `org.javacs.kt.MainKt`, Android Studio
  JBR의 java.exe로 구동됨)였다. redhat.java만 죽이면 안 풀리고, 이 프로세스를 찾아 죽여야 풀림.
- **신규 다이어리 투명 필기 PNG 보조 저장 추가** (2026-08-25): 기존의 종이+필기 합성
  `yyyy-MM-dd.png`와 클라우드 백업 흐름은 그대로 유지하고, 새로 작성되는 다이어리에만 배경 없는
  `yyyy-MM-dd_content.png`를 로컬에 함께 저장한다. 편집기를 다시 열 때는 보조 파일이 있으면 필기
  레이어로 사용하며, 이미지 저장 창에 네 번째 `투명 배경 PNG` 옵션을 추가했다. 기존 다이어리는
  보조 파일을 만들지 않고 해당 옵션 선택 시 지원하지 않는다는 안내만 표시한다. 보조 PNG가 백업
  날짜로 오인되지 않도록 파일명 필터 회귀 테스트를 추가했다. 앱 단위 테스트 15개와 debug APK
  assembly가 통과했으며, 사용자 요청대로 에뮬레이터 검증은 생략했다.
- **v2.9.10 배포 완료** (2026-08-25): 읽기모드에서 투명 필기 PNG의 배경이 검게
  표시되던 문제를 수정한 APK를 versionName `2.9.10`, versionCode `114`로 빌드했다. 배포 자산은
  `daymory-v2.9.10-114.apk`, SHA-256은
  `A2E6B472E1AFBE5723F4A6A427E695BEAA0FAB06B9BEED206528D9C7D6176D4F`다. 공용 PageCurl 84개와 앱
  13개 테스트를 캐시 없이 다시 실행해 모두 통과했고 APK 버전 및 V2/V3 서명을 확인했다. GitHub
  Release `v2.9.10`에 공개한 자산을 다시 내려받아 같은 SHA-256인지 확인했다.
- **읽기모드 작성 페이지 검정 표시 수정** (2026-08-25): 편집기는 페이지를 종이 없이 투명한
  필기 PNG로 저장하는데, 새 PageCurl 셰이더가 투명 픽셀의 RGB(0,0,0)를 불투명하게 출력해 작성된
  페이지 배경이 검게 보였다. 앱 경계인 `SketchbookPageSource`에서 선택한 `bgKey` 종이 재질을
  편집 캔버스와 같은 cover-fit 방식으로 먼저 그리고 필기 PNG를 그 위에 합성한 불투명 Bitmap을
  PageCurl에 공급하도록 수정했다. 빈 페이지도 흰 임시 Bitmap 대신 같은 종이 재질을 사용한다.
  레이어 순서 회귀 테스트를 추가했고, 공용 PageCurl 전체 테스트 + 앱 전체 테스트 + debug APK
  assembly가 통과했다. 연결된 에뮬레이터가 없어 시각 검증은 남아 있다.
- **v2.9.9 배포 완료** (2026-08-24): 공용 PageCurl 신버전이 적용된 읽기모드 APK를
  versionName `2.9.9`, versionCode `113`으로 빌드해 GitHub Release `v2.9.9`에
  `daymory-v2.9.9-113.apk`로 공개했다. SHA-256은
  `9210859334343CBF3D38345B6C8777A67FEFEC2E3D380B5F5E9C4148951C81D5`다. 기존 v2.9.8
  릴리스와 자산은 덮어쓰지 않았다.
- **읽기모드 구형 컬 엔진 제거 + 공용 PageCurl 신버전 이식** (2026-08-24): 앱 내부의
  `ReadModeRenderer`/`ReadModeSurface`/`readmode/curl`/`readmode/input`과 전용 테스트를 삭제하고,
  공용 `:pagecurl` 모듈의 controlled `PageCurl`을 `ReadModeScreen`에 연결했다. 앱에는 저장된 페이지
  PNG를 요청 크기로 공급하는 `SketchbookPageSource`와 닫기/현재 페이지 상태만 남겼다. A4뿐 아니라
  모바일·데스크톱 등 기존 모든 캔버스 비율을 보존하도록 공용 source의 `pageAspectRatio`를 사용한다.
  공유 모듈 위치는 커밋되지 않는 `local.properties`의 `pagecurl.dir`로 지정하고 저장소 기본값은
  `../pagecurl`이다. 분리 빌드 경로에서 module 84/84 tests, app 11/11 tests, portability 검사와 debug
  APK assembly가 통과했다. APK SHA-256은
  `2FDF0D01E8D694393A5327A153127A24F8FCEBA20592CA1C3D9708AF38A1CAFF`이다.
- **구글 계정 백업/동기화 — 전체 브랜치 코드리뷰 지적사항 최종 수정 웨이브 완료**
  (2026-08-24, 커밋 `d36bc21` / `ce1ac1c` / `effc8b9`): 리뷰에서 나온 Critical/Important 9건 중
  앞선 세션이 Fix 1~5(`be24678`, `ecdaf3f`, `6597017`, `2b750e5`)를 끝냈고, 이번 세션이 남은 3건을 마무리.
  1. **Fix 9 — `BackupRepository.root`를 lazy로** (`d36bc21`): `BackupRepository()`가 Compose
     Preview에서도 무조건 생성되는데 `FirebaseDatabase.getInstance()`가 생성 시점에 터졌음.
     `by lazy`로 미뤄서 실제로 DB를 쓰는 호출에서만 예외가 나도록 함(한 줄).
  2. **Fix 7 — `signOut()`이 `uid`도 비우도록** (`ce1ac1c`): `uid`가 남아 있어서 로그아웃 뒤에도
     로컬 편집이 남의 uid 경로로 push될 수 있었음(모든 `*Synced`/`syncNow`/`flushSettings`가
     `uid ?: return`로 게이트됨).
  3. **Fix 6 — 백그라운드 동기화 후 UI 갱신** (`effc8b9`, 4파일): `reconcileBackup`이 파일/
     SharedPreferences를 직접 써서 Compose가 알 방법이 없었음 — 다른 기기에서 당겨온 페이지/표지/
     스케치북이 무관한 편집으로 `refresh++`가 될 때까지 안 보였음. `RootState.syncGeneration`
     카운터를 신설해 `syncNow` 성공마다 올리고, `MainActivity` → `MainScreen` → `HomeTab`/
     `SketchbookTab`(자체 `refresh` 카운터를 쓰는 두 탭)까지 내려서 `LaunchedEffect(syncGeneration)`
     로 다시 읽게 함. `syncNow`는 덤으로 (a) `SessionStore`에 직접 쓰인 theme/nickname을 다시
     `RootState`에 반영(생성 시 1회만 시드됐었음), (b) Activity context 대신 applicationContext를
     캡처(코루틴이 Activity보다 오래 살 수 있음), (c) 실패 시 `Log.w`만 남기고 에러 UI는 안 띄움.
  - `compileDebugKotlin` BUILD SUCCESSFUL(에러 0, 기존 deprecation 경고 2건만),
    `testDebugUnitTest` 10개 스위트 46테스트 전부 통과(`BackupModelsTest` 8/8 포함).
    이번엔 `R.jar` 파일 잠금 이슈 없었음. 상세 리포트:
    `.superpowers/sdd/2026-08-24-google-account-backup-sync/final-fix-wave-continuation-report.md`
  - **남은 것(스코프 밖, 후속 후보)**: 일기 탭(`DiaryCalendarScreen`)과 열려 있는 캔버스
    (`SketchbookCanvasScreen`)는 `syncGeneration`을 안 받아서 여전히 재진입해야 갱신됨.
    `MainScreen` 호출부에 값이 이미 있으니 붙이는 건 작은 작업.
- **가로모드 3열 썸네일 화질 개선 + 공유모드 화질 개선 + 현재 페이지 표기 + 공유 상대방 페이지 선택**
  (2026-08-23, 버전 미상향, 아직 커밋 안 함): 사용자가 4가지 요청.
  1. **가로모드 List/Share 탭 3열 썸네일 화질**: `PageThumbnailCell`(`SketchbookScreens.kt`)이
     `loadPageThumb` 기본값(`reqPx=160`)을 그대로 썼는데, 3열 셀이 그보다 훨씬 넓게 그려져서 확대되며
     흐릿했음 — 이 자리 전용으로 `reqPx=480`을 명시.
  2. **공유모드 상대방 화질**: `SharedBookScreen.kt`의 `encodeSnapshot`이 `maxSide=700`/JPEG quality 70
     이었는데, A4 캔버스 원본 장변이 ~2300px라 1/3 이하로 줄어 흐릿했음 — `maxSide=1400`/quality 85로
     상향(스케치는 단색 배경이 많아 JPEG 압축이 잘 먹어서 페이로드 증가는 제한적).
  3. **개인 스케치북 화면에 현재 페이지 표기**: `SketchbookCanvasScreen`의 캔버스 우측 하단에
     `"${page+1} / $pageCount"` 배지 추가(다른 화면들과 같은 반투명 검정+흰글자 스타일).
  4. **공유화면에서 상대방의 다른 페이지도 선택해서 보기** — 가장 큰 변경. 기존엔 `Slot.snapshot`이
     "상대가 지금 그리고 있는 페이지 1장"의 스냅샷만 실시간으로 받는 구조라, 상대의 다른 14장은 내
     기기에 데이터 자체가 없었음(AskUserQuestion으로 확인 — 사용자가 "내 페이지처럼 상대도 15장 다
     볼 수 있게 서버구조 바꿔줘"를 선택). **Firebase 스키마를 깨는 변경**:
     - `ShareRepository.Slot.snapshot: String?` → `snapshots: Map<Int, String>`(페이지별) +
       `currentPage: Int`(상대가 지금 그리고 있는 "라이브" 페이지) 신설.
     - `pushSnapshot(code, uid, base64)` → `pushSnapshot(code, uid, page, base64)`로 시그니처 변경,
       `updateChildren`으로 `currentPage`/`snapshots/{page}`/`updatedAt`을 한 번에 갱신.
     - `SharedBookScreen.kt`의 `pushMine()`/`onStrokeEnd`가 현재 `page`를 실어 보내도록 수정.
     - `OtherPane`에 페이지 배지(우하단, 탭하면 `PartnerPagePicker` 오픈) 추가 — 로컬 상태
       `viewedPage: Int?`(null=라이브, 상대의 `currentPage`를 계속 따라감 / 값 있으면 그 페이지에
       고정). `PartnerPagePicker`는 `PagePanel`과 같은 스타일(3열 그리드 다이얼로그)로 15칸을
       보여주되, 아직 상대가 안 그린 페이지는 빈 칸(플레이스홀더)으로 표시. 다이얼로그용 썸네일은
       `decodeSnapshotThumb`(신규, `loadPageThumb`와 같은 `inSampleSize` 다운샘플 기법)로 디코드해
       15장을 한 번에 열어도 무겁지 않게 함.
     - **하위호환 없음** — 기존 세션(구버전 앱)이 쓰던 `slots/{uid}/snapshot` 필드는 더 이상 안 읽음.
       사용자가 직접 요청한 구조 변경이라 별도 마이그레이션은 만들지 않음(기존 진행 중이던 공유
       세션이 있다면 새 버전 배포 후 깨질 수 있음 — 알고 있어야 함).
  - `compileDebugKotlin` BUILD SUCCESSFUL. 실기기 확인 안 됨 — 특히 4번은 Firebase 실시간 동기화라
    로컬 컴파일만으론 동작 보장 안 됨(두 기기로 실제 공유 세션 열어서 서로 다른 페이지 넘기며 확인
    필요).
- **v2.9.3 배포분: 브러시별 굵기 최소/최대 범위 + 액자 다운로드 연도 표기 + 보기모드 워터마크 색상**
  (2026-08-23): 세 가지 추가 수정을 이전 미커밋 작업들과 함께 이번 릴리스로 묶음.
  1. **브러시별 굵기 최소/최대**: 바로 아래 항목(전역 `MinBrushSize`/`MaxBrushSize` 분리)에서 한 단계
     더 나가 `Dimens.Brush`에 브러시별(`pen`/`pencil`/`crayon`/`water`/`eraser`) `*MinWidth`/`*MaxWidth`를
     추가하고, `BrushControls.kt`의 `SlidersPanel`/`BrushBtnWithPanel`에 `sizeRange` 파라미터를 뚫어
     각 브러시 버튼이 자기 브러시의 범위를 쓰도록 배선(`brushSizeRange(BrushType)`/`EraserSizeRange`
     헬퍼). 1~30단계 표시(`sizeLevel`)도 브러시별 range 기준으로 재계산하도록 수정. 이후 사용자가
     Dimens.kt 값 자체(펜 5~30, 크레용 7~45, 물감 10~80, 지우개 5~80 등)를 직접 조정함 — 그 값을
     그대로 유지.
  2. **액자 구성 다운로드에 연도 누락**: `diary/DiaryScreens.kt`의 `renderFramedDiaryBitmap`이 헤더에
     요일(좌)+날짜서수(우, 예 "23rd")만 그리고 연도가 아예 없었음 — `dayLabel`을 `"23rd, 2026"`
     형식으로 연도를 붙임(월은 요청 범위 밖이라 그대로 둠).
  3. **보기모드 워터마크 날짜가 흰 글씨라 안 보임**: `CleanDetailBody`의 날짜 워터마크 `Text` 색을
     흰색(그림자 검정)에서 검정(그림자 흰색)으로 반전.
  - `compileDebugKotlin` BUILD SUCCESSFUL. 로컬 `testDebugUnitTest`는 Android Studio가 같은
    `app/build` 산출물 디렉터리를 동시에 잡고 있어 `R.jar` 파일 잠금 충돌로 실패(코드 문제 아님) —
    GitHub Actions 클린 러너에서의 `assembleDebug` 결과로 대체 검증.
- **브러시 굵기 최소/최대값 상수 분리 + 일기 다운로드 옵션 아이콘 전용화** (2026-08-23, v2.9.3에
  포함되어 커밋됨): 사용자가 2가지 요청.
  1. **브러시 굵기 최소/최대값**: "최소사이즈 값은 있는데 최대값이 안 보인다"는 지적 — 실제로는
     코드 어디에도 최소/최대 숫자 표시 UI가 없어서(AskUserQuestion으로 재확인), 진짜 요청은 "코드에서
     직접 최소·최대 굵기를 설정하고 싶은데 그 코드를 못 찾겠다"였음. `brush/BrushControls.kt`에서
     기존 `private val SizeRange = 4f..96f`(한 줄에 두 값이 묶여 있어 찾기 어려웠음) 대신
     `internal const val MinBrushSize = 4f` / `internal const val MaxBrushSize = 96f`로 분리하고
     `SizeRange = MinBrushSize..MaxBrushSize`로 구성 — 이제 두 값이 각각 이름 붙은 채로 눈에 띄고,
     직접 숫자만 바꾸면 굵기 슬라이더의 최소/최대가 바뀐다.
  2. **일기 다운로드 옵션 다이얼로그 아이콘 전용화**: 오늘일기 저장 시 뜨는 "이미지로 저장" 시트가
     아이콘+텍스트 행 3줄(`DownloadChoiceRow`)이었던 걸, 텍스트 없이 원형 아이콘 3개를 가로로 나란히
     보여주는 형태(`DownloadChoiceIcon`)로 교체. 액자 구성 아이콘은 `Icons.Filled.Crop`(자르기
     아이콘, 의미가 안 맞음)이었는데 머티리얼 아이콘 세트엔 "폴라로이드" 모양이 없어서(소스 jar 직접
     검색해 확인) `Canvas`로 직접 그린 `PolaroidIcon`(테두리 카드 + 위쪽 사진 영역 + 아래쪽 여백)으로
     교체. 접근성을 위해 각 아이콘에 `Modifier.semantics { contentDescription = ... }` 부여, 클릭은
     기존 관례대로 `bounceClick` 재사용. 안 쓰게 된 `Icons.Filled.Crop`/`ImageVector` import 삭제.
  - `compileDebugKotlin` BUILD SUCCESSFUL. 실기기/에뮬레이터 확인 안 함 — 특히 폴라로이드 아이콘의
    실제 크기감, 아이콘 3개 가로 배치가 다이얼로그 폭에서 자연스러운지 확인 필요.
- **표지 두께 색상 버그 + 3손가락 페이지넘김 캔버스 점프 + 라쏘 5종 개선** (2026-08-23, v2.9.3에
  포함되어 커밋됨): 사용자가 5가지를 한 번에 요청.
  1. **표지가 사진일 때 "두께감" 스택이 여전히 이전 색상**: `ui/main/MainScreen.kt`의 홈 캐러셀에서
     표지 뒤 어긋난 종이 스택 2겹(`stackColor`)이 `book.coverColor`를 그대로 썼는데, 사진 표지로
     바꿔도 `book.coverColor` 필드 자체는 안 지워지고 남아있어 두께 부분에서만 옛 색이 비쳐 보였다.
     `SketchbookCover.kt`의 책등(spine) 자체는 원래부터 항상 검정 오버레이라 문제 없었음 — 버그는
     캐러셀의 두께 스택 쪽. `cover != null`(사진 로드됨)이면 `stackColor = Color.Black`으로 고정.
  2. **3손가락 페이지 넘기기 후 캔버스가 튐**: `brush/BrushView.kt`의 `spacing`/`midX`/`midY`가
     항상 포인터 인덱스 0/1만 읽는데, 3손가락 중 하나가 떨어져 2손가락으로 줄어들면 안드로이드가
     남은 손가락들의 인덱스를 재배정한다 — 그 순간 `prevDist`/`prevMidX`/`prevMidY`는 옛 인덱스
     조합 기준 값이라 다음 MOVE에서 완전히 다른 두 점 사이 델타로 계산되어 갑자기 팬/줌이 튀었다.
     `resyncPinchBaseline` 플래그를 신설 — 손가락이 줄어들어도 2개 이상 남으면(`ACTION_POINTER_UP`)
     이 플래그를 세우고, 다음 MOVE에서 diff 대신 그 프레임의 위치를 새 기준점으로 재설정한다(점프 없이
     자연스럽게 이어짐). 페이지 전환 자체(줌/팬 유지)는 원래도 `loadContent`가 `resetZoom()`을 안 불러
     보존되고 있었음 — 확인만 함.
  3. **라쏘 점선 테두리 두께를 Dimens로**: `BrushView.kt`에 하드코딩돼 있던
     `1.5f * resources.displayMetrics.density`를 `Dimens.Canvas.lassoStrokeWidthDp`로 추출.
  4. **라쏘 선택 크기조절+회전 신규**: 기존엔 선택 이동(평행이동)만 가능했다(`moveDx`/`moveDy` 두
     값뿐). 화면좌표계 델타를 통째로 누적하는 `Matrix selectionTransform`으로 교체 — 손가락 1개면
     이동만, 선택을 옮기는 도중 손가락이 하나 더 닿으면(`ACTION_POINTER_DOWN`, pointerCount==2)
     두 손가락 사이 거리 변화(확대/축소)·각도 변화(회전)·중점 이동(이동)을 매 프레임
     `postScale`→`postRotate`→`postTranslate`로 누적 적용(사진 앱들의 표준 2손가락 트랜스폼과 동일
     패턴). 미리보기(`onDraw`)와 최종 합성(`commitMove`) 둘 다 정확히 같은 행렬
     (`disp → selectionTransform → inv`)을 써서 한 픽셀도 안 어긋나게 함. 손가락 하나가 떨어져
     2→1이 되는 순간도 캔버스 3손가락 케이스와 같은 이유로 재기준점을 잡아 안 튀게 처리.
     최소/최대 배율 clamp는 아직 없음(의도적 스코프 축소 — 필요시 추후).
  5. **라쏘 모드에서 캔버스 바깥 탭하면 원래 브러시로 복귀**: 새 콜백 `onLassoTapOutside`를
     `BrushView`에 추가 — 라쏘 모드의 `ACTION_DOWN`이 캔버스 범위(`[0,cw]×[0,ch]`) 밖으로 매핑되면
     그 콜백만 부르고 아무 선택 동작도 안 함. Compose 쪽(`SketchbookScreens.kt`,
     `diary/DiaryScreens.kt` 둘 다)에 `preLassoErasing`/`preLassoFillActive` 스냅샷을 추가해서,
     라쏘를 켜기 직전 지우개/채우기 상태를 기억해뒀다가 바깥 탭 시 그대로 복원.
  - `compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` 전부 BUILD SUCCESSFUL. **실기기
    확인 안 됨** — 특히 4번(2손가락 트랜스폼 손맛, pivot이 손가락 중점이라 "제자리 회전"이 아니라
    "손가락 사이 축 중심 회전"으로 느껴질 수 있음)과 5번(캔버스 바깥 경계가 줌아웃 여백까지 포함하는
    체감)은 직접 조작해봐야 확인 가능.
- **읽기모드 버튼 위치 이동 + 페이지-커얼 이펙트 4가지 수정** (2026-08-23, 버전 미상향, 아직 커밋 안 함):
  사용자가 실기기 테스트 전 "읽기모드 버튼이 안 보인다"고 지적 → 알고 보니 페이지 설정(`PagePanel`)
  다이얼로그 안이 아니라 화면 우측 상단 확장 버튼(`ScreenControls`, 페이지/회전/잠금/전체화면이 모인
  곳) 안에 넣어달라는 요청이었음 — `PagePanel`에서 버튼을 빼고 `ScreenControls`에 `onReadMode`
  파라미터+아이콘 신설(`BrushControls.kt`, `SketchbookScreens.kt`, `PagePanel.kt`).
  이어서 사용자가 "책장넘기기 효과가 예전 코드"라며 4가지 구체적 문제를 지적, 참고 프로젝트
  (`CODEX/GDO_DAILY SKETCH/PageCurlDemo`)를 다시 조사시킴 — **중요 발견**: 참고 프로젝트도 실제로는
  같은 문제를 겪고 있었고, 그림자 제거·양방향 넘기기는 설계 문서(spec/plan)만 있고 실제 코드는
  없었음(`PageBookState.kt` 자체가 없음), 가로모드 제본선 개념은 참고 프로젝트에 아예 존재하지
  않음(우리 앱의 독자 설계). 즉 "최신 코드를 그대로 포팅"이 아니라 설계 문서를 청사진 삼아
  새로 구현한 작업.
  - **계단현상 그림자 완전 삭제**: `ShadowStrip`(저해상도 세그먼트 메쉬 기반 그림자 스트립),
    전용 `shadowProgram`/`SHADOW_VERTEX`/`SHADOW_FRAGMENT`, `CurlGeometry.updateShadow` 전부
    삭제. 기존 셰이더의 곡률 음영(`vShade`)만으로 입체감 유지 — 참고 프로젝트의 설계 문서가
    명시한 해법과 동일.
  - **넘어가는 다음 장에 입체감 그림자 신설**: 저해상도 메쉬가 아니라 페이지 셰이더에 프래그먼트
    단위 그라디언트를 추가하는 방식으로 새로 설계(`ShaderSources.kt`의 `uFoldX`/
    `uFoldShadowWidth`/`uFoldShadowStrength`/`uFoldShadowSign` 유니폼 + `CurlGeometry.foldShadow()`).
    정점 메쉬가 아니라 픽셀 단위 연속 함수라 계단현상이 날 수 없음. 참고 프로젝트 설계는 오히려
    "그림자를 아예 없앤다"는 결론이라 그대로 가져올 코드가 없었고, 사용자 요구(입체감용 그림자는
    유지하되 다음 장에)에 맞춰 새로 설계.
  - **가로모드 제본선 중심 넘기기 — 근본 원인 특정 및 수정**: 카메라/메쉬 배치(`recomputeCamera`의
    `rightMvp`/`leftMvp`)는 원래부터 제본선(월드 x=0) 중심으로 정확히 짜여 있었음 — 진짜 버그는
    `DragInterpreter.normalized()`가 터치를 항상 "전체 화면 너비" 기준으로 정규화해서, 가로모드의
    오른쪽 페이지(화면 절반)를 다루는 컬 수학이 잘못된 범위의 값을 받고 있던 것(이전 세션에서
    "landscape drag-coordinate mismatch"로 알려진 채 보류됐던 이슈). `DragInterpreter`에
    `landscape` 플래그를 받는 `directionForStart`/`toWorkingPosition`을 추가해 화면 절반→오른쪽
    페이지 0..1 범위로 재매핑.
  - **왼쪽→오른쪽(뒤로) 페이지 넘기기 신설**: 참고 프로젝트의 미구현 설계 문서(좌표 미러링 방식,
    `workingX = 1 - touchX`)를 청사진으로 새로 구현. `CurlDirection` enum, `CurlGeometry.deform`에
    `direction` 파라미터(메쉬 x축을 작업공간에서 미러링 후 되돌림), `DragInterpreter`의 양방향
    가장자리 판정, `ReadModeSurface`(방향 잠금 + `setTurnAvailability`), `ReadModeScreen`(spread
    앞뒤 모두 로드하는 `previousRight` 텍스처, `onTurnCompleted(direction)`으로 `spreadIndex++`/`--`).
    거울 메쉬는 삼각형 와인딩이 뒤집혀 `gl_FrontFacing` 기반 앞/뒷면 판정이 깨지므로, 뒤로 넘기기
    드로우콜에서만 `glFrontFace(GL_CW)`로 보정.
    **의도적 스코프 제한**: 세로모드(페이지 1장)는 완전 지원, **가로모드는 앞으로 넘기기만** —
    가로모드의 왼쪽 페이지는 애초에 변형되는 메쉬가 없는 구조(오른쪽 페이지만 커얼)라, 왼쪽에서
    시작하는 뒤로 넘기기를 제대로 만들려면 별도의 거울 메쉬가 필요함. 실기기 검증 없이 무리하게
    확장하기보다 명시적으로 범위를 좁힘(`ReadModeScreen`에서 `canBackward = spreadIndex > 0 &&
    !landscape`로 강제).
  - `ShadowStrip.kt`/`CurlShadowTest.kt` 삭제, `CurlGeometryTest.kt`/`DragInterpreterTest.kt`에
    새 동작(양방향 미러링 대칭성, foldShadow 경계값, 가로모드 재매핑) 테스트 추가.
  - `compileDebugKotlin` + `testDebugUnitTest`(35개, 0 실패) + `assembleDebug` 전부
    BUILD SUCCESSFUL. **실기기 확인 전혀 안 됨** — 특히 뒤로 넘기기의 앞/뒷면 텍스처 스왑
    정합성(`glFrontFace` 보정), 새 그림자의 실제 체감 강도, 가로모드 재매핑 후 손가락-화면
    일치감은 시뮬레이션으로 검증 불가능한 영역.
- **읽기모드 전체 브랜치 최종 리뷰 수정 (머지 직전 마지막 패스)** (2026-08-22, 버전 미상향):
  - **페이지 비율 하드코딩 제거**: `ReadModeRenderer`가 데모에서 물려받은 고정 3:4(`PORTRAIT_HEIGHT
    = 8f/3f`)로 모든 책을 그리던 문제 — 태블릿 사이즈 외 전 사이즈가 늘어나/눌려 보였다
    (모바일 ~62%, 데스크톱 ~2.4배 왜곡). `setSpread(textures, landscape, pageAspect)`로 실제
    `book.size.pxW()/pxH()`를 받아 `pageHeight = PORTRAIT_WIDTH / pageAspect`로 계산.
    `PORTRAIT_WIDTH = 2f`는 좌표계의 기준 단위라 그대로 유지.
  - **마지막 스프레드에서 한 번 더 넘기면 백지에 갇히던 버그**: 마지막 스프레드는 `spreadIndex`가
    더 못 올라가 `setSpread`가 다시 안 불렸고, 렌더러가 `CurlPhase.Completed`에 머물러 넘어가는
    페이지를 안 그린 채 빈 `nextRight`(백지)만 남았다. `reloadTick` 상태를 추가해 마지막에서
    완료되면 같은 스프레드를 다시 밀어넣어 컬 상태를 리셋한다.
  - **페이지 턴 핫패스의 비트맵 낭비 감소**: `PageTextureProvider`가 `loadPage`(전체 해상도 디코드,
    A3면 ~31MB) 후 축소하던 것을 `loadPageThumb`(디코드 중 `inSampleSize` 다운샘플)로 교체.
    단 `loadPageThumb`의 `reqPx`는 **가로 폭 하한**이라 세로 페이지에 그대로 1600을 넘기면
    샘플이 1로 고정돼 아무 효과가 없다 — `decodeRequestWidth(maxEdge, pageAspect)`로 종횡비를
    곱해 넘긴다. 중간 비트맵은 정확한 목표 크기로 리스케일 후 `recycle()`.
    백지 텍스처(`blankPage()`)도 전체 페이지 해상도 → 4×4로 축소(GL이 어차피 리샘플).
  - **닫기 버튼 신설**: 설계서의 "뒤로가기/닫기 버튼으로 나가면" 요구대로 좌상단 원형 닫기 버튼을
    GL 경로와 GLES3 미지원 폴백 경로 **양쪽 모두**에 추가(폴백엔 그동안 상호작용 요소가 전무했다).
  - `compileDebugKotlin` / `testDebugUnitTest` 모두 BUILD SUCCESSFUL (33 tests, 0 failures).
  - **여전히 실기기 확인 필요**: 가로모드 드래그 좌표가 화면 정규화라 페이지와 잘 안 겹치는 문제는
    실기기 손맛 튜닝이 필요해 이번 라운드에서 의도적으로 보류.
- **읽기모드("페이지 넘기기") 진입점 연결 — SDD 플랜 Task 10/10 완료** (2026-08-22, 버전 미상향):
  `.superpowers/sdd/2026-08-22-sketchbook-read-mode/` 플랜의 마지막 태스크. Task 1~9(별도 세션들)가
  `com.g1.sketchbook.readmode` 패키지 전체(GLSurfaceView 기반 페이지-커얼 엔진, 스프레드 페어링,
  다운샘플링, GLES3 미지원 폴백, `ReadModeScreen` 컴포저블)를 이미 완성해뒀고, 이번 태스크는 기존
  화면에 진입 버튼만 연결하는 배선 작업.
  - `sketchbook/PagePanel.kt`: 새 파라미터 `onReadMode: (() -> Unit)? = null`을 `onReorder`와 `onDismiss`
    사이에 추가. 페이지 그리드 아래·취소/완료 버튼 위에 "읽기모드" 버튼(`Icons.Filled.AutoStories`,
    `secondaryContainer` 톤) 신설.
  - `sketchbook/SketchbookScreens.kt`: `readModeOpen` 상태 신설, `PagePanel`의 `onReadMode`에서
    `saveCurrent()`(방금 그리던 페이지를 읽기모드가 보기 전에 저장) → `pagesOpen=false` →
    `readModeOpen=true`로 전환. `readModeOpen`이면 `ReadModeScreen(repo, book, startPage=page,
    onClose={ lastPage -> readModeOpen=false; goTo(lastPage) })`을 띄워 닫을 때 마지막으로 보던
    페이지로 편집기를 동기화.
  - **플랜 브리핑에 없던 추가 발견 → 리뷰에서 지적받아 근본 수정**: `share/SharedBookScreen.kt:392`도
    `PagePanel(...)`을 이름 있는 인자로 호출하고 있어 새 필수 파라미터 때문에 컴파일이 깨짐 —
    처음엔 `onReadMode = {}`(no-op)만 추가해 컴파일만 통과시켰는데, 그러면 공유 스케치북의 페이지
    패널에도 "읽기모드" 버튼이 똑같이 나타나지만 눌러도 반응 없는 죽은 버튼이 되는 문제가 있었음
    (task-10 리뷰에서 Important로 지적, 플랜의 스코프 분석이 이 콜사이트를 놓친 탓이지 태스크
    실행 오류는 아니라고 확인됨). **최종 수정**: `onReadMode`를 `(() -> Unit)? = null`로 nullable+
    기본값화하고, 버튼 블록 전체를 `if (onReadMode != null) { ... }`로 감싸 핸들러가 없으면
    버튼 자체가 컴포즈되지 않도록 함. 그 결과 `SharedBookScreen.kt`의 콜사이트는 원래대로
    되돌려(이 태스크가 아예 손대지 않은 것과 동일한 상태) `onReadMode` 인자 없이 호출 — 공유
    스케치북 페이지 패널엔 이제 "읽기모드" 버튼이 아예 뜨지 않는다. `SketchbookScreens.kt` 쪽은
    실제 람다를 넘기므로 변경 없이 그대로 정상 동작.
    `preview/BrushCanvasPreview.kt`는 실제 `PagePanel`이 아니라 저장소 없이 흉내만 내는
    `MockPagePanel`(별도 로컬 컴포저블)을 쓰고 있어 이번 변경과 무관, 수정 불필요.
  - `compileDebugKotlin` 및 `:app:compileDebugKotlin --rerun-tasks` 둘 다 BUILD SUCCESSFUL.
  - **실기기 확인 필요**(이 플랜의 GLSurfaceView 관련 태스크 전부와 동일한 관례): 읽기모드 버튼
    누른 후 실제 페이지 넘기기 드래그 손맛, 실제 페이지 전환 프레임레이트, 읽기모드 중 가로 회전,
    GLES3 미지원 기기에서의 즉시 전환 폴백 경로 — 이 중 어느 것도 `compileDebugKotlin`이나 Compose
    Preview만으로는 검증되지 않는다.
- **달력 오버레이 5차 개편 — 손잡이 50%/70%, 다이얼로그는 길게 눌러 진입, 오늘 강조 원 on/off,
  배경 on/off(+색·불투명도)** (2026-08-22, 버전 미상향): `diary/DiaryScreens.kt`. 5가지 수정:
  1. **손잡이 크기 50%·불투명도 70%**: 필(pill) 손잡이 크기를 6×32dp/32×6dp → 3×16dp/16×3dp로,
     배경 흰색 알파를 0.92→0.7로, 테두리도 더 옅게. 그림자(`shadow`)도 제거(너무 작아져서 불필요).
  2. **다이얼로그는 달력을 길게 눌러 진입**: 상단 바의 톱니(Tune) 버튼 삭제. 대신 스티커 영역에
     `detectDragGestures`(이동)와 별도로 `detectTapGestures(onLongPress = ...)`를 같은 Box에 얹어
     길게 누르면 설정 다이얼로그가 뜨게 함 — 손을 떼지 않고 움직이면(드래그) 롱프레스가 자동
     취소되므로 이동과 충돌하지 않음.
  3. **"오늘 날짜 강조 원" on/off 스위치**: `OverlayPlacement.showTodayCircle`(기본 true) 추가,
     `CalendarSettingsDialog`에 `Switch` 토글 신설. 꺼지면 `MiniCalendarSticker`와
     `renderCalendarOverlayDiaryBitmap` 양쪽에서 분홍 원을 안 그림.
  4. **"달력 배경" on/off 스위치**: 지난 세션에 완전히 지웠던 반투명 카드 배경을 다시 넣되 이번엔
     기본 꺼짐(`backgroundEnabled = false`)인 옵션으로. 켜면 `MiniCalendarSticker`가 둥근 사각형
     배경(`RoundedCornerShape(12.dp)`)을 깔고 안쪽에 살짝 여백을 준다.
  5. **배경 켜면 색·불투명도 조절**: `backgroundColorArgb`(기본 흰색)·`backgroundOpacity`(기본
     0.75, 0~1 슬라이더, 브러시 굵기와 같은 무채색 슬라이더 디자인 재사용) 추가, 배경 스위치가
     켜져 있을 때만 이 두 컨트롤(불투명도 슬라이더 + `ColorPickerCard`)이 나타남.
  - `OverlayPlacement`에 `showTodayCircle`/`backgroundEnabled`/`backgroundColorArgb`/
    `backgroundOpacity` 4개 필드 추가, `MiniCalendarSticker`·`renderCalendarOverlayDiaryBitmap`
    양쪽에 배선. 다이얼로그 내용이 늘어나서 `Column`에 `verticalScroll` 추가(작은 화면 대응).
  - `compileDebugKotlin` 검증 완료. 실기기 확인은 아직 안 함 — 특히 롱프레스 vs 드래그 제스처
    공존이 실제 손가락으로 자연스러운지 확인 필요.
- **달력 오버레이 4차 개편 — 필(pill) 손잡이, 다이얼로그 가운데 정렬, Y/M/D 문자 버튼, 슬라이더 썸
  탭으로 pt 입력, 글자크기/색상 통합 표시, 다이얼로그 내부 전체 가운데 정렬** (2026-08-22, 버전
  미상향): `diary/DiaryScreens.kt` + `brush/BrushControls.kt`. 5가지 수정:
  1. **손잡이 = 얇은 필 모양**: 가로/세로 크기 손잡이를 원형 배경 없는 아이콘에서, `RoundedCornerShape(50)`
     로 만든 얇은 흰색 필 막대로 교체(가로 손잡이=세로로 긴 필 6×32dp, 세로 손잡이=가로로 긴 필
     32×6dp — 필의 방향 자체가 어느 쪽으로 드래그하는지 암시).
  2. **다이얼로그 가운데 정렬**: `CalendarSettingsDialog`를 `DialogProperties(usePlatformDefaultWidth =
     false)` + `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`로 감싸 명시적으로
     화면 중앙에 뜨도록 보장(플랫폼 기본 동작에 기대지 않음).
  3. **연/월/일 버튼을 Y/M/D 문자로**: 아이콘(DateRange/CalendarMonth/Today)이 서로 구분이 잘 안
     간다는 피드백 — 아이콘 대신 굵은 글자 "Y"/"M"/"D"를 원형 토글 버튼에 표시(`OverlayToggleText`,
     아이콘 버전 `OverlayToggleIcon`은 이제 안 써서 삭제).
  4. **글자 크기 직접 입력칸 삭제 → 슬라이더 썸 탭으로 입력**: 상시 노출돼 있던 pt `OutlinedTextField`를
     없애고, 대신 슬라이더의 동그란 손잡이(썸)를 탭하면 그 자리가 숫자 입력 모드로 바뀐다(포커스 자동
     이동, 완료 버튼 또는 키보드 확인으로 슬라이더로 복귀). 이를 위해 `brush/BrushControls.kt`의
     `RingSliderThumb`/`IconSliderRow`에 `onThumbClick: (() -> Unit)? = null` 파라미터 추가(기본 null이라
     기존 브러시 쪽엔 영향 없음) — 썸 안쪽 원에 `clickable`을 얹어 탭과 드래그(슬라이더 자체 제스처)가
     공존하게 함.
  5. **글자크기/색상을 버튼으로 나누지 않음**: 이전엔 아이콘 버튼 2개로 토글해서 둘 중 하나만
     보였는데, 이제 글자 크기 슬라이더와 색상휠(`ColorPickerCard`)을 항상 같이(세로로 나란히) 보여줌
     — `OverlayProperty` enum과 토글 버튼 완전히 삭제.
  6. **다이얼로그 내부 전체 가운데 정렬**: 바깥 `Column`에 `horizontalAlignment =
     Alignment.CenterHorizontally` 추가, "완료" 버튼의 `Modifier.align(Alignment.End)`도 제거해 다른
     요소들과 같이 가운데로.
  - `compileDebugKotlin` 검증 완료. 실기기 확인은 아직 안 함.
- **달력 오버레이 3차 개편 — 이동은 드래그 전용(버튼 삭제), 손잡이 배경 삭제, "달력 설정" 다이얼로그로
  통합(연/월/일 아이콘 선택 + 상단 실시간 미리보기 + 글자크기·색상 아이콘 전환 + pt 직접입력 + 서체
  칩 실제 폰트 렌더링 + 브러시 굵기와 동일한 무채색 슬라이더)** (2026-08-22, 버전 미상향):
  `diary/DiaryScreens.kt` + `brush/BrushControls.kt`.
  1. **이동 버튼 삭제**: 왼쪽 위 이동 손잡이(`OpenWith` 아이콘) 완전히 삭제. 대신 스티커 본체(연/월/
     일/요일/날짜 전부를 담은 영역)를 감싼 `Box`에 직접 `detectDragGestures`를 달아 어디를 눌러
     드래그해도 바로 위치가 옮겨진다. 스티커 안에 있던 개별 탭 콜백(`onTapYear`/`onTapMonth`/
     `onTapDay`)도 함께 제거 — 이제 스티커는 순수 미리보기이고, 편집은 전부 아래 4번 다이얼로그로.
  2. **손잡이 배경 삭제**: 남은 가로/세로 크기 손잡이 2개에서 `Color(0xCC1E2D4C)` 원형 배경 삭제,
     아이콘만 남기고 흰색 + 그림자로 배경 없이도 어느 정도 보이게 함.
  3. **기타 수정을 전부 "달력 설정" 다이얼로그로**: 상단 바에 톱니(`Icons.Filled.Tune`) 버튼 신설,
     누르면 `CalendarSettingsDialog`가 뜬다.
  4. **다이얼로그 구성**(`CalendarSettingsDialog`):
     - 상단: 지금 편집 중인 값이 실시간 반영되는 `MiniCalendarSticker` 미리보기(160×190dp).
     - 연도/월/일자 아이콘 3개(`DateRange`/`CalendarMonth`/`Today`) — 어느 요소를 편집할지 선택.
     - 글자 크기(`FormatSize` 아이콘) / 색상(현재 색이 채워진 원형 스와치, 선택되면 테두리 강조)
       아이콘 2개 — 아래에 보여줄 편집 패널을 전환.
     - **글자 크기 패널**(`FontSizeEditor`): 브러시 굵기 슬라이더와 완전히 같은 트랙/썸 디자인이지만
       강조색만 무채색(`OverlayAccentColor` = `Color(0xFF8A8A8A)`)으로. 이 재사용을 위해
       `brush/BrushControls.kt`의 `IconSliderRow`/`GradientSliderTrack`/`RingSliderThumb`를
       `private`→`internal`로 열고 `accentColor: Color = SliderAccentColor` 파라미터를 추가(디폴트라
       기존 브러시 쪽 호출부는 색 변화 없음). 슬라이더 옆에 pt 직접 입력 `OutlinedTextField`도 추가 —
       타이핑 중간값("12"를 치는 도중의 "1")이 슬라이더가 되받아쓰기 때문에 끊기지 않도록, 텍스트
       상태는 `selectedElement`가 바뀔 때만 다시 초기화하고 슬라이더 변경 시엔 텍스트 쪽에서
       직접(명령형으로) 갱신하는 방식으로 짬(리컴포지션 키로 엮지 않음 — 안 그러면 타이핑 중 값이
       범위 하한으로 클램프되면서 방금 친 숫자가 날아가는 버그가 생김).
     - **색상 패널**: 기존 `ColorPickerCard` 그대로.
     - **서체 칩 3개**: 각 칩의 라벨 텍스트에 `fontFamily = f.family`를 적용해 "손글씨"/"고딕"/"세리프"
       글자 자체가 그 서체로 보이도록(이전엔 전부 기본 서체로 표시돼 있었음).
  - `OverlayPlacement`/`renderCalendarOverlayDiaryBitmap`(저장용 Canvas 렌더)은 값 구조가 안 바뀌어서
    그대로 재사용.
  - `compileDebugKotlin` 검증 완료. 실기기 확인은 아직 안 함 — 배경 없앤 손잡이가 실제로 잡기 충분히
    쉬운지, pt 입력 필드 동작이 매끄러운지 확인 필요.
- **달력 오버레이 배치 화면 전면 재설계 — 연/월/일 개별 색상·글자크기, 드래그 핸들 크기조절, 요소별
  편집 팝업** (2026-08-22, 버전 미상향): `diary/DiaryScreens.kt`. 직전 세션의 하단 슬라이더 패널(크기 1개
  + 글자크기 1개 + 폰트 공용) 방식을 사용자가 다시 뒤집어서 요청 — 4가지:
  1. **연/월/일 각각 독립 색상**: 스티커에 연도 줄을 새로 추가(이전엔 월 이름만 있고 연도가 없었음)해서
     연/월/일 세 요소로 나누고, 각각 `OverlayElementStyle(colorArgb, fontSp, fontRes)`로 색상·글자
     크기·폰트를 완전히 독립적으로 가짐.
  2. **연/월/일 각각 독립 글자 크기**: 위와 같은 구조로 해결(`fontSp`가 요소별로 다름).
  3. **크기 조절을 슬라이더→드래그 핸들로, 표 비율만 바뀌고 폰트 폭은 고정**: 스티커 왼쪽 위에 이동
     손잡이(`Icons.Filled.OpenWith`), 오른쪽 가운데에 가로 크기 손잡이, 아래 가운데에 세로 크기
     손잡이(둘 다 `Icons.Filled.DragHandle`, 가로쪽은 90도 회전) 3개를 원형 드래그 핸들로 배치.
     가로/세로 손잡이는 `gridWidthFraction`/`gridHeightFraction`(스케치 폭/높이 대비 비율)을 각각
     독립적으로 바꾼다 — `MiniCalendarSticker`를 `widthDp×heightDp` 고정 크기의 `Column`으로 만들고
     연/월/요일 줄은 자기 글자 크기만큼만 차지, 날짜 그리드가 `weight(1f)`로 나머지를 채우는 구조라
     표 크기를 조절해도 글자 크기 자체(`OverlayElementStyle.fontSp`)는 전혀 안 바뀜.
  4. **하단 상시 패널 삭제 → 요소 선택 시 작은 Dialog 팝업**: 연도/월/일자 텍스트(또는 날짜 그리드
     전체)를 탭하면 `OverlayElementEditDialog`(글자 크기 슬라이더 + 브러시 쪽 기존
     `ColorPickerCard`(색상휠) 재사용 + 폰트 칩 3개)가 `Dialog`로 뜬다. 하단 고정 패널·슬라이더 2개·
     전역 폰트 칩 행은 전부 삭제.
  - `OverlayPlacement`가 `(fracX, fracY, sizeFraction, fontScale, fontRes)` 5개 값 → `(fracX, fracY,
    gridWidthFraction, gridHeightFraction, year: OverlayElementStyle, month: ..., day: ...)`로 재설계.
    `renderCalendarOverlayDiaryBitmap`도 동일 구조로 다시 씀 — 연/월/요일 줄 높이를 각 줄 글자
    크기(`fontSp × 화면 밀도`)에서 유도해 Compose 쪽과 같은 규칙을 저장 결과에도 반영.
  - 새 `OverlayHandle`(재사용 가능한 원형 드래그 손잡이), `OverlayElement` enum(연도/월/일자 구분),
    `fontFamilyFor(fontRes)` 헬퍼(Int 리소스 id ↔ Compose FontFamily 매핑) 추가.
  - `compileDebugKotlin` 검증 완료. 실기기 확인은 아직 안 함 — 손잡이 드래그 히트영역(28dp 원)이
    실제 손가락으로 조작하기 충분히 큰지, 팝업 위치/레이아웃이 실제로 괜찮은지 확인 필요.
- **일자 상세화면 전체화면화(워터마크 날짜) + 달력 오버레이 배경 제거 + 크기/글자크기/폰트 조절**
  (2026-08-22, 버전 미상향): `diary/DiaryScreens.kt`. 사용자가 6가지로 정리해 요청 — 1(썸네일 달력
  진입)·3(좌우 스와이프 일자 변경)·4(다운로드 옵션 선택)은 이미 구현돼 있어 확인만 함(4는 "스케치만"
  까지 3개 옵션인데 사용자는 2개만 언급 — "스케치만"을 빼란 뜻인지 불명확해 일단 그대로 둠, 필요하면
  말씀해달라고 안내). 실제로 바뀐 건 2·5·6:
  - **2(전체화면+워터마크)**: `CleanDetailBody`에서 날짜를 별도 헤더 줄(레이아웃 공간 차지)로 안 그리고,
    이미지 위에 겹치는 반투명 텍스트(흰 60% 알파 + 검은 그림자)로 바꿈. `CleanCalendarScreen`도
    함께 손봄 — 그리드 보기일 때만 연/월 타이틀+사이드 패딩을 두르고, 상세 보기일 때는
    `CleanDetailBody(..., Modifier.fillMaxSize())`로 패딩·헤더 없이 화면 전체를 그대로 씀. 그림 없는
    날은 중앙에 날짜+안내문구를 세로로 표시.
  - **5(달력 스티커 배경 제거)**: `MiniCalendarSticker`(Compose 미리보기)의
    `.background(Color(0x99FFFFFF), ...)` 삭제, `renderCalendarOverlayDiaryBitmap`(저장용 Canvas)의
    `canvas.drawRoundRect(...)` 흰 카드 그리기 삭제 — 이제 텍스트/원만 스케치 위에 직접 떠 있음.
  - **6(크기·글자크기·폰트 조절)**: `CalendarOverlayPlacementScreen` 하단에 반투명 컨트롤 패널 추가 —
    크기 슬라이더(`sizeFraction`, 0.18~0.7), 글자 크기 슬라이더(`fontScale`, 0.5~2, 크기와 독립적으로
    글자만 키움/줄임), 폰트 선택 칩 3개(손글씨=`Cavorting`/고딕=`Pretendard`/세리프=`BodoniMTBlack`,
    새 `OverlayFont` enum). 위치·크기·글자크기·폰트를 하나로 묶은 `OverlayPlacement` 데이터 클래스를
    신설해 배치 화면과 저장 렌더(`renderCalendarOverlayDiaryBitmap`)가 값을 그대로 공유(이전엔 위치
    두 값만 넘겼음). 드래그 스티커의 중심 정렬은 근사치(스티커 폭 절반)였던 걸
    `onGloballyPositioned`로 실제 측정 크기를 받아 정확히 계산하도록 개선.
    **코드 위치**(전부 `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`): 슬라이더 UI·상태 =
    `CalendarOverlayPlacementScreen`(647번째 줄) + `OverlaySliderRow`(724번째 줄), 값 묶음 정의 =
    `OverlayPlacement`(630번째 줄)/`OverlayFont`(634번째 줄), 배치 화면 미리보기 스티커 =
    `MiniCalendarSticker`(735번째 줄), 최종 저장용 합성 =
    `renderCalendarOverlayDiaryBitmap`(773번째 줄).
  - `preview/DiaryOverlayPreview.kt`: `onSave` 시그니처가 `(Float, Float) -> Unit`→
    `(OverlayPlacement) -> Unit`로 바뀐 것에 맞춰 갱신.
  - `compileDebugKotlin` 검증 완료. 실기기 확인은 아직 안 함(배경 없앤 텍스트의 실제 가독성, 슬라이더
    체감은 확인 필요).
- **일자 상세화면/달력 오버레이 배치 화면 Preview 신규 추가** (2026-08-22, 버전 미상향): 사용자가
  "Preview에 오늘 날짜 샘플이 없다"고 지적 — 확인해보니 `CleanCalendarScreen`/`CleanDetailBody`는
  `previewMode`일 때 `repo`가 항상 `null`이라 `bmp`가 항상 `null`이 되고, 그림이 있을 때만 뜨는
  저장 버튼·다운로드 다이얼로그(3번째 "달력 오버레이" 옵션 포함) 자체를 Preview에서 켤 방법이
  없었다(기존 "16 Calendar day detail" Preview도 사실 항상 빈 상태만 보여주고 있었음).
  - `diary/DiaryScreens.kt`: `CleanCalendarScreen`/`CleanDetailBody`에 `previewBitmap: Bitmap? = null`
    파라미터 추가 — `bmp = previewBitmap ?: repo?.load(date)`로, 지정하면 repo 없이도 실제 그림이
    있는 것처럼 동작(기존 `previewMode`/`previewDetailDate`와 같은 패턴). `CalendarOverlayPlacementScreen`
    가시성을 `private`→`internal`로 변경(다른 다이얼로그들처럼 Preview 패키지에서 직접 호출 가능하게).
  - 신규 `preview/DiaryOverlayPreview.kt`: "20 Diary day detail - sample sketch"(오늘 날짜 +
    `sampleDiarySketch()`로 생성한 간단한 언덕/해 낙서 비트맵을 주입한 `CleanCalendarScreen` — 저장
    버튼→다운로드 다이얼로그→"달력 오버레이로 다운로드"까지 Interactive Preview로 실제 탭해서 확인
    가능), "21 Calendar overlay placement"(배치 화면을 곧바로 열어둔 상태로, 스티커 드래그 자체만
    빠르게 확인).
  - `compileDebugKotlin` 검증 완료.
- **일자 상세화면 다운로드 3번째 옵션 "달력 오버레이" 러프 구현** (2026-08-22, 버전 미상향): 사용자
  확정 사양([memory] project-diary-detail-download-versions 참고, AskUserQuestion으로 세부 재확인) =
  전용 배치 화면에서 스티커를 드래그만(크기 고정) 가능, 스티커 내용은 해당 월 전체 그리드 + 지금
  보고 있는 날짜만 분홍 원 강조(다른 날 기록 여부 점 표시는 생략), 배경은 반투명 오버레이.
  `diary/DiaryScreens.kt`:
  - `DownloadOptionsDialog`에 세 번째 `DownloadChoiceRow`("달력 오버레이로 다운로드") 추가,
    `onOverlay` 콜백 신설.
  - `CleanDetailBody`: `showOverlayPlacement` 상태 추가, 기존 `Column(modifier)`를
    `Box(modifier) { Column(...) { ... } }`로 감싸 그 위에 전용 배치 화면을 조건부로 얹는 구조로 변경.
    시스템 뒤로가기(`BackHandler`)로도 배치 화면을 닫을 수 있게 배선(앱 전반의 기존 관례).
  - 신규 `CalendarOverlayPlacementScreen`: 스케치를 원본 비율(`aspectRatio`) 그대로 크게 보여주고,
    그 위에 `MiniCalendarSticker`를 `detectDragGestures`로 드래그(위치만, `fracX`/`fracY` 0~1 비율로
    저장) — 상단에 취소(X)/저장(체크) 원형 버튼.
  - 신규 `MiniCalendarSticker`(Compose 미리보기용): 반투명 흰 카드 위에 요일 헤더+6주 그리드,
    보고 있는 날짜만 `TodayPink` 원으로 강조.
  - 신규 `renderCalendarOverlayDiaryBitmap()`(저장용 Canvas 렌더): 배치 화면에서 고른 `fracX`/`fracY`
    위치에 스케치 원본 해상도 기준으로 같은 스타일(폭 42%, 반투명 흰 카드)의 달력을 다시 그려 합성 —
    `renderFramedDiaryBitmap()`과 같은 "Compose 미리보기와 저장용 Canvas 렌더는 완전히 같은 수치식일
    필요 없다"는 이 프로젝트의 기존 관례를 그대로 따름.
  - **의도적으로 미룬 디테일**(사용자가 "러프하게 먼저, 디테일은 나중에"로 명시): 스티커 크기 조절
    불가(고정 42%), 다른 날 기록 여부 점 표시 없음, Compose 미리보기와 Canvas 렌더 사이 픽셀 단위
    정합성 없음(비율만 맞춤) — 실기기로 실제 보이는 결과 확인 후 다듬을 예정.
  - `compileDebugKotlin` 검증 완료. 에뮬레이터/실기기 확인은 아직 안 함(드래그 체감·최종 합성 결과
    실제로 어떻게 보이는지는 실기기에서 확인 필요).
- **v2.8.1 릴리스 준비** (2026-08-21): 메인탭 고정 레이아웃 정렬, 화면 버튼 그림자·분할 아이콘
  수정, 페인트통 제스처 정리, Android Studio Preview 복구, 표지 색상 확인 단계와 갤러리 이미지
  반영 오류 수정 및 회귀 테스트를 묶어 `versionCode 103` / `versionName 2.8.1`로 상향했다.
- **표지 수정 색상 확인 단계 + 갤러리 이미지 반영 오류 수정** (2026-08-21, 버전 미상향):
  `sketchbook/SketchbookScreens.kt`. 색상휠 조작값을 `CoverEditSelection.pendingColor`에만 보관하고
  팝업의 `확인`을 눌러야 실제 표지 색과 미리보기에 반영되도록 변경했다. `취소`·바깥 터치는 원래
  색을 유지하며, 색상을 확정하면 기존 이미지 표지는 제거 대상으로 전환한다. 갤러리 URI 디코딩은
  메인 스레드의 `BitmapFactory` 단독 처리에서 IO 스레드 처리로 옮기고, Android 9 이상은 EXIF 방향과
  최신 이미지 형식을 처리하는 `ImageDecoder`, 이전 버전은 기존 다운샘플 디코더를 사용한다. 성공하면
  크롭 화면→표지 미리보기→최종 `완료` 저장으로 연결하고, 실패하면 조용히 무시하지 않고 오류 문구를
  표시한다. `CoverEditSelectionTest` 2개를 RED→GREEN으로 추가했으며
  `assembleDebug compileReleaseKotlin testDebugUnitTest lintDebug` 검증 완료.
- **분할/최대화 아이콘 전환 표시 + 그림자 잘림 3곳 수정 + 페인트통 크레파스 삭제·제스처 버그 수정**
  (2026-08-20, 버전 미상향):
  1. `share/SharedBookScreen.kt`(`ModeToggleButton`): 아이콘이 "지금 모드"가 아니라 "탭하면 바뀔
     모드"를 보여주도록 뒤집음(그리드 모드일 땐 최대화 아이콘, 최대화 모드일 땐 그리드 아이콘 —
     기존 설명 문구와 실제로 맞게).
  2. **그림자 잘림**(페이지 버튼/분할모드 버튼/홈화면 표지 공통 원인): `Modifier.alpha()`로 감싼
     구성요소는 별도 오프스크린 그래픽 레이어가 생기는데, 그 레이어는 감싼 요소 자신의 레이아웃
     크기로 딱 잘려서 그려진다 — 그림자는 원래 요소 경계 밖으로 번져야 하는데 그 레이어 밖으로는
     못 나가 잘렸다.
     - `brush/BrushControls.kt`의 `ScreenControls`(페이지 버튼): `Modifier.alpha(0.5f)` 대신
       `Surface(color = ...surface.copy(alpha = 0.5f))`로 — 레이어 없이 색 자체에 알파를 줘서
       그림자에 전혀 영향 없음. `share/SharedBookScreen.kt`의 `ModeToggleButton`(분할모드 버튼)도
       동일하게 수정.
     - `ui/main/MainScreen.kt`(`HomeCarousel`): 표지 스케일/페이드는 alpha() 유지가 불가피해서
       대신 그 레이어 자체의 레이아웃 크기에 `shadowSlack`(16dp) 여유를 추가하고, 원래 스택-겹침
       비주얼은 그 안에 가운데 정렬로 유지. elevation 상한도 18dp→12dp로 낮춰 안전 마진 확보.
  3. `brush/BrushView.kt`: 페인트통 크레파스 질감 옵션(`fillCrayonStyle`/`crayonFillPixel`) 전체
     삭제 — 항상 단색 채우기만. `brush/BrushControls.kt`의 스타일 전환 아이콘도 함께 삭제. 3개
     실제 화면 + 프리뷰의 관련 상태·배선도 모두 제거.
     **제스처 반응 안 함 버그**: `onTouchEvent`에서 `fillMode`가 켜져 있으면 무조건 `return true`로
     모든 터치를 가로채고 있었음 — 핀치줌·멀티핑거 탭 제스처가 페인트통 선택 중엔 전혀 안 먹혔던
     원인. 이 하이재킹 분기를 삭제하고, 일반 드로잉과 같은 흐름(`beginStroke`/`strokeMove`/
     `endStroke`)을 타되 그 안에서만 fillMode를 다르게 처리하도록 재구성 — `beginStroke`는 fillMode면
     아무것도 안 그리고 대기만, `strokeMove`도 무시, 두 번째 손가락이 닿으면(핀치 시작) 기존
     `discardStroke()`가 그대로 타서 안전하게 취소되고 핀치가 정상 작동, 손을 뗄 때(`endStroke`)
     비로소 `floodFillAt()` 한 번 실행. 이제 핀치줌·3손가락 스와이프 등이 페인트통 선택 중에도 그대로
     작동한다.
  - `compileDebugKotlin` 검증 완료. 그림자·제스처 체감은 실기기 확인 권장.
- **홈 캐러셀: 표지 크기 고정(자기참조 버그 수정) + scale로만 가운데 확대** (2026-08-20, 버전
  미상향): `ui/main/MainScreen.kt`(`HomeCarousel`). 지난 LazyRow 전환 때 표지 레이아웃 폭(`w`/`h`)
  자체를 `distance`(화면 중심에서 거리)로 lerp했는데, `distance`는 `LazyListItemInfo.size`(그
  아이템이 실제로 측정된 폭)에서 역산하는 값이라 — 폭이 distance를 정하고 distance가 다시 폭을
  정하는 자기참조 루프가 생겨 크기가 원래 스펙(`Dimens.Home.carouselCenterW/H`)대로 안정적으로
  안 나오고, 가운데가 뚜렷하게 커 보이지도 않았음. 참고 프로젝트(G1_BOOKLOG_rev1)를 다시 보니 거긴
  레이아웃 폭을 고정해두고 `Modifier.scale()`(렌더링 시각 변환, 레이아웃엔 영향 없음)만 distance로
  바꾸고 있었음 — 같은 방식으로 고쳐서 레이아웃 폭은 항상 고정 스펙 사이즈, 가운데↔옆 크기 차이는
  `scale(lerp(1f, sideW/centerW, distance))` 하나로만 낸다(스크롤 중 레이아웃이 안 흔들려 스냅
  계산도 같이 안정됨). `compileDebugKotlin` 검증 완료.
- **v2.8.0 릴리스**: 이번 세션 작업 전체(화면버튼 재구성, 올가미/페인트통, 공유 캔버스 재구성,
  표지 수정 시트 전면 개편, 홈 캐러셀 관성 스크롤, 갤러리 크롭 버그수정, 350dp 폭 등)를 커밋
  `ed92121`로 묶어 `versionCode 102`/`versionName 2.8.0`으로 올림. 로컬 `assembleDebug` 성공 확인
  후 태그 `v2.8.0` 푸시 → GitHub Actions(`release.yml`)가 APK 빌드 후 릴리스 자동 첨부.
- **표지 수정 시트 폭 280dp→350dp** (2026-08-20, 버전 미상향): `sketchbook/SketchbookScreens.kt`+
  `ui/theme/Dimens.kt`. 이전엔 마법사 팝업과 같은 `Dimens.Wizard.cardWidth`(280dp)를 재사용하고
  있었는데, 그 값을 바꾸면 마법사 카드도 같이 바뀌므로 전용 상수 `Dimens.Home.editCoverCardWidth`
  =350dp를 새로 만들어 분리했다. `compileDebugKotlin` 검증 완료.
- **표지 수정: 갤러리에서 사진 골라도 적용 안 되던 버그 수정** (2026-08-20, 버전 미상향):
  `sketchbook/SketchbookScreens.kt`. 증상 = 갤러리 피커는 뜨는데 사진을 골라도 아무 반응이 없음.
  원인으로 추정되는 지점: 사진을 고르면 크롭 범위 선택 화면(`CoverImageCropDialog`)을 별도의 새
  `Dialog`로 띄웠는데, 갤러리(다른 앱)에 다녀온 직후 시점에 Dialog 창을 하나 더 여는 조합이 일부
  기기에서 창이 제대로 붙지 않아 조용히 실패했던 것으로 보임. `EditCoverDialog`가 이미 열어 둔
  Dialog 창을 새로 하나 더 만들지 않고, 그 안에서 내용만 바꿔치기(`CoverImageCropDialog`→
  `CoverImageCropContent`, Dialog 래퍼 제거)하도록 재구성해 Dialog 창이 하나만 존재하게 했다.
  `compileDebugKotlin` 검증 완료 — 실제로 이게 근본 원인이었는지는 실기기 확인 권장(증상 재현이
  안 되면 알려주시면 계속 추적).
- **공유 스케치북 분할: 여백·간격 완전 제거** (2026-08-20, 버전 미상향): "정확히 2/4분할"이 가용
  영역 전부를 캔버스로 쓰라는 뜻이었다는 피드백 — `share/SharedBookScreen.kt`/
  `preview/SharedBookPreviewScreen.kt`의 GRID 모드에서 바깥 `padding(8.dp)`와 칸 사이
  `Arrangement.spacedBy(8.dp)`를 모두 제거. 칸 사이 구분은 각 `PaneFrame`이 이미 그리던 테두리
  선(1~2dp) 하나로만 — 인접한 두 칸의 테두리가 맞닿아 자연스럽게 구분선처럼 보인다. MAXIMIZE
  모드(큰 화면+작은 팝업)의 자체 오버레이 여백은 분할 레이아웃이 아니라서 그대로 둠.
  `compileDebugKotlin` 검증 완료.
- **페인트통 기본값 단색으로 변경 + 공유 스케치북 화면 전면 재구성** (2026-08-20, 버전 미상향):
  1. `brush/BrushView.kt` + 3개 화면 + 프리뷰: `fillCrayonStyle` 기본값 `true`→`false`(단색이 기본,
     크레파스는 옵션으로 유지).
  2. `share/SharedBookScreen.kt` 대폭 재구성 — 캔버스에 화면을 최대한 내주는 방향:
     - **뒤로가기 버튼·헤더 바 삭제**: 나가기는 시스템 뒤로가기(`BackHandler`, 기존 로직 그대로)로만.
       `BoxWithConstraints` 바깥 여백도 16dp→8dp로 축소.
     - **스케치북 이름**: 헤더 줄 없이, 화면 맨 위에 참가자 캔버스 위로 겹치는 작은 반투명 라벨
       하나로(대기 인원/코드 안내는 이미 각 참가자 칸(`OtherPane`)이 담당해 중복 없이 제거).
     - **참가자 별명**: `PaneFrame`을 다시 구성해 캔버스 위 별도 줄(높이를 차지)이 아니라 각자
       캔버스 좌측 상단에 겹치는 작은 반투명 배지로.
     - **분할/최대화 토글**: 텍스트 세그먼트(`SegGroup`/`SegChip`, 삭제) → 아이콘 하나(그리드/펼침
       아이콘, 화면버튼과 같은 반투명 원형 스타일)로, 우측 상단 화면버튼(`ScreenControls`) 바로
       왼쪽에 한 줄로 배치(신규 `ModeToggleButton`, `internal`로 노출해 Preview에서도 재사용).
     - **2/4분할 규칙 확인**: 기존 로직이 이미 "참가자 2인 이하=정확히 2분할, 3~4인=2x2(4분할)"을
       구현하고 있어(요청과 일치) 이 부분은 코드 변경 없음.
     - `preview/SharedBookPreviewScreen.kt`도 동일 구조로 갱신(헤더 삭제, 오버랩 라벨, 아이콘 토글).
  - `compileDebugKotlin` 검증 완료.
- **페인트통 단색/크레파스 선택 + 올가미 아이콘 교체 + 일기 그리기 버튼 아이콘 교체**
  (2026-08-20, 버전 미상향):
  1. `brush/BrushView.kt`: `fillCrayonStyle: Boolean`(기본 true) 추가 — 꺼지면 `crayonFillPixel`이
     입자감 없이 매끈한 단색(`blendOver` 그대로)으로 채운다. `brush/BrushControls.kt`: 페인트통이
     켜져 있을 때만 스타일 전환 아이콘(크레파스=`Icons.Filled.Texture`/단색=`Icons.Filled.Circle`,
     탭할 때마다 서로 전환) 추가. 3개 실제 화면(`SketchbookScreens.kt`/`DiaryScreens.kt`/
     `SharedBookScreen.kt`) + `preview/BrushCanvasPreview.kt`에 `fillCrayonStyle` 상태 배선.
  2. `brush/BrushControls.kt`: 올가미 아이콘을 네모난 `Icons.Filled.HighlightAlt`(마퀴 선택처럼
     보여 어색하다는 피드백) → 자유형 곡선 느낌의 `Icons.Filled.Gesture`로 교체.
  3. `diary/DiaryScreens.kt`: 일기달력 탭 "오늘 일기 그리기" 아이콘을 기본 연필(`Icons.Filled.Edit`)
     에서 사용자가 제공한 `image/icon/paint-palette-1.png`(팔레트+붓)로 교체 —
     `app/src/main/res/drawable-nodpi/paint_palette_1.png`로 복사(안드로이드 리소스명은 하이픈
     불가라 언더스코어로), 기존 브러시 아이콘들과 같은 방식(`Image`+`ColorFilter.tint`)으로 표시.
  - `compileDebugKotlin` 검증 완료(리소스 처리 포함).
- **페인트통 채우기에 크레파스 질감** (2026-08-20, 버전 미상향): `brush/BrushView.kt`. 사용자가 크레파스
  스와치 시안 이미지를 제시 — 단색 flat fill이던 페인트통을 픽셀 단위로 흔들어 왁스 입자감을 내도록
  바꿨다. `scanlineFill()`이 채울 때 고정된 `replacement` 색 하나 대신 새 `crayonFillPixel()`을
  픽셀마다 호출: 10% 확률로 아예 안 칠해서(원래 색이 비치는 자잘한 흰 틈) 크레파스 특유의 거친 느낌을
  내고, 나머지 90%는 불투명도를 72~100% 사이에서 흔들어(`blendOver` 재사용) 매끈한 디지털 단색과
  다르게 보이도록 함. 대상 영역을 찾는 매칭 자체는 그대로(터치 지점과 정확히 같은 색인 영역), 칠하는
  방식만 바뀜. `compileDebugKotlin` 검증 완료 — 질감 체감은 실기기 확인 권장.
- **홈 캐러셀: 관성 스크롤 매끄럽게 + 점 인디케이터 + 가운데 표지 전용 타이틀 블록**
  (2026-08-20, 버전 미상향): `ui/main/MainScreen.kt`(`HomeCarousel`). 사용자가 참고 프로젝트
  (`G1_BOOKLOG_rev1`의 `HomeScreen.kt` `ReadingPagerCarousel`, 155~268줄)를 제시 — 같은 조합을
  그대로 이식.
  - **관성 스크롤 끊김**: `HorizontalPager`(페이지 단위 스냅)를 `LazyRow` + `rememberSnapFlingBehavior`
    조합으로 교체 — 손을 떼기 전까지는 일반 스크롤처럼 관성이 자연스럽게 이어지다가, 멈추는 순간에만
    가장 가까운 표지로 스냅한다(페이지 경계마다 관성이 끊기던 Pager 특유의 느낌 해소).
  - **점 인디케이터**: 화면 중앙에 가장 가까운 표지 인덱스(`centeredIndex`, `LazyListState.layoutInfo`
    기반 `derivedStateOf`)를 계산해 캐러셀 아래 점으로 표시(선택=진하고 큰 점, 나머지=흐리고 작은 점).
  - **가운데 표지 전용 타이틀**: 기존엔 카드마다 자기 이름·날짜를 작게 표시했는데, 이제는 카드 안 텍스트를
    없애고 캐러셀 아래에 큰 타이틀(스케치북 이름) + 작은 부제(생성일 · 캔버스 사이즈 · 배경명, 예:
    "2026.08.20 · A4 · 수채화용지") 하나만 두어 가운데 표지가 바뀔 때마다 이 블록만 갱신되게 했다.
    배경명은 `Catalog.backgrounds`에서, 사이즈 라벨은 `Sketchbook.size.label`에서 가져옴.
  - `ui/theme/Dimens.kt`: 카드별 제목/날짜 sp(`coverTitleCenterSp` 등 4개, 더 이상 안 씀) 삭제,
    캐러셀 공용 타이틀/부제 sp(`carouselTitleSp`=18sp, `carouselSubtitleSp`=12sp) 신설.
  - `compileDebugKotlin` 검증 완료. 실제 스크롤 체감(관성이 매끄러운지)은 에뮬레이터/실기기 확인 권장.
- **올가미(라소) 선택 + 페인트통(채우기) 도구 추가** (2026-08-20, 버전 미상향): 사용자 확인 범위 =
  올가미는 "지우기 + 이동(드래그)"까지(복사는 없음). 홈 캐러셀 롱프레스 표지수정은 확인해보니
  지난 세션(2026-08-20 앞선 항목)에 이미 구현돼 있어 이번엔 손 안 댐.
  - `brush/BrushView.kt`: `lassoMode`/`fillMode` 두 boolean 추가(브러시 종류와 별개 축, `erasing`과
    같은 패턴). `lassoMode`는 커스텀 setter로 꺼지는 순간 선택을 자동 해제.
    - 올가미: `ACTION_DOWN`이 기존 선택 영역(`android.graphics.Region.contains`) 안쪽이면 그 영역을
      `liftSelection()`으로 별도 비트맵에 떼어내고 원본에서는 지운 뒤 드래그를 그 비트맵 오프셋으로
      실시간 미리보기, 손을 떼면 `commitMove()`가 화면 오프셋을 `Matrix.mapVectors()`로 캔버스 픽셀
      좌표로 역산해 실제 위치에 합성하고 선택 영역도 같이 옮겨서 계속 선택 상태 유지. 바깥을 누르면
      기존 선택 해제 후 새 라소 그리기 시작, 손을 떼면 `Region.setPath()`로 선택 확정("marching ants"
      점선 테두리로 표시, 화면 좌표로 매 프레임 변환해 줌·회전과 무관하게 항상 같은 두께).
      `deleteLassoSelection()`(툴바 "선택 지우기" 버튼)은 선택 영역만 클립해서 지움.
    - 페인트통: `floodFillAt()` — 스캔라인(가로 구간 단위) flood fill로 터치 지점과 정확히 같은 색
      영역을 찾아 현재 색·불투명도를 표준 source-over 알파합성(`blendOver()`)으로 채움. 허용오차
      없이 정확히 같은 색만(가장 단순한 버전). A3 크기(최대 3308×3308px) 캔버스에서도 감당 가능한
      속도(픽셀 단위가 아니라 구간 단위 처리).
    - `initCanvas`/`loadContent`/`clearCanvas`에서 선택 자동 해제(페이지가 바뀌면 이전 선택은
      의미가 없으므로).
  - `brush/BrushControls.kt`: 지우개 버튼 뒤에 올가미(`Icons.Filled.HighlightAlt`)·페인트통
    (`Icons.Filled.FormatColorFill`) 토글 아이콘 추가(스포이드와 같은 톤 규칙 — 비활성 흐린 회색,
    활성 강조색), 선택이 있을 때만 나타나는 "선택 지우기"(`Icons.Filled.Delete`) 아이콘 추가.
  - `sketchbook/SketchbookScreens.kt`, `diary/DiaryScreens.kt`, `share/SharedBookScreen.kt`,
    `preview/BrushCanvasPreview.kt`: `lassoActive`/`fillActive`/`hasLassoSelection` 상태 추가,
    브러시 선택·지우개 토글이 서로(그리고 올가미·페인트통과) 상호배타적이 되도록 각 콜백에서
    나머지를 꺼줌. `SharedBookScreen.kt`는 `movableContentOf` 때문에 `update` 대신 키가 있는
    `LaunchedEffect`로 브러시 상태를 재동기화하는 기존 패턴이라, 키 목록에 `lassoActive`/`fillActive`
    추가도 빠뜨리지 않음.
  - `compileDebugKotlin` 검증 완료. 에뮬레이터 실제 확인은 아직 안 함(플러드필 성능·라소 드래그
    체감은 실기기에서 확인 권장).
- **표지 수정 시트 더 압축: 섹션 타이틀 제거+가운데정렬, 카드 폭 축소, 하단 버튼 pill→텍스트만**
  (2026-08-20, 버전 미상향): `sketchbook/SketchbookScreens.kt`(`EditCoverDialog`).
  - "이름 변경"/"표지 변경" 굵은 섹션 타이틀 삭제(더 이상 안 쓰는 `SectionLabel` 헬퍼도 삭제),
    표지 변경·즐겨찾기/삭제 아이콘 행을 `Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)`로
    가운데 정렬.
  - 카드가 `fillMaxSize()`(거의 전체화면)였던 걸 `widthIn(max = Dimens.Wizard.cardWidth)`(=280dp,
    다른 팝업 카드와 동일 토큰)로 폭만 좁힘(높이는 그대로 — 요청이 폭만 언급).
  - 하단 취소/완료를 `Button`/`OutlinedButton`(알약 배경) → `TextButton`(배경 없이 글자만),
    `Arrangement.SpaceBetween`으로 양끝 배치(예전 소형 팝업 버전과 동일 스타일로 회귀).
  - `compileDebugKotlin` 검증 완료.
- **표지 수정 시트: 텍스트 행 → 아이콘 버튼 2개씩 + 이미지 범위 선택 크롭 추가** (2026-08-20, 버전
  미상향): `sketchbook/SketchbookScreens.kt`(`EditCoverDialog`).
  1. **표지 변경**: 파스텔 8색 스와치 그리드 + 갤러리 타일(캡션 포함) → 아이콘 버튼 2개(색상휠 🎨,
     갤러리 🖼)로 압축. `CoverPalette`(8색 스와치 목록)는 더 이상 안 써서 삭제. 색상 버튼은
     기존에 브러시 쪽에서 쓰던 `com.g1.sketchbook.brush.ColorPickerCard`(허용 어떤 색이든)를
     버튼 바로 아래 `Popup`(신규 `BelowCenterAnchor`)으로 띄운다. 사진이 있을 때만 "사진 빼기"
     아이콘이 옆에 추가로 나타남.
  2. **즐겨찾기·삭제**: 스위치+설명문 행, 화살표+설명문 행 → 아이콘 버튼 2개(★/🗑)로 압축, 텍스트
     완전 제거(`contentDescription`만 유지). 공용 `CoverActionIcon`(원형 배경+아이콘, 텍스트 없음)
     신설 — 표지 변경 두 버튼과 즐겨찾기·삭제 두 버튼이 전부 이걸 공유.
  3. **이미지 적용 범위 선택**(신규 기능): 갤러리에서 사진을 고르면 예전처럼 즉시 가운데 기준
     자동 크롭하지 않고, 새 `CoverImageCropDialog`가 표지 비율 그대로의 창 안에 원본을 띄워
     핀치 확대·드래그 이동(`detectTransformGestures`, 다이어리 상세 보기와 동일 제스처 패턴)으로
     사용자가 직접 보이는 범위를 고르게 한 뒤 "적용"을 누르면 그 화면 그대로가 잘린다.
     `cropSelectedRegion()`이 화면의 scale/translate를 원본 픽셀 좌표로 역산해 `Bitmap.createBitmap`
     으로 정확히 잘라낸다(ContentScale.Crop의 baseScale × 사용자 확대배율 = 총 배율로 역산).
     기존 자동 중앙크롭 헬퍼 `cropToAspect()`는 더 이상 안 써서 삭제.
  - `compileDebugKotlin` 검증 완료. 에뮬레이터 실제 확인은 아직 안 함(핀치 제스처·크롭 결과는
    실기기에서 확인 권장).
- **표지 수정 시트 Preview 추가** (2026-08-20, 버전 미상향): `preview/EditCoverPreview.kt` 신규 —
  "19 Edit cover". `EditCoverDialog`가 `internal`이라 미리보기 패키지에서 바로 호출 가능한 걸 활용,
  뒤 배경엔 `SketchbookTab(previewBooks=PreviewBooks)`(실제 목록 화면), 그 위에 `EditCoverDialog`를
  처음부터 펼친 상태로 얹었다(Dialog는 별도 윈도우라 형제로 나란히 호출해도 겹쳐 뜸 —
  `OnboardingPreviews.kt`의 LoginScreen+NicknameDialog와 같은 패턴). `repo=null`(로컬 저장소 미접근),
  이름/색/즐겨찾기는 로컬 state로만 반영해 실제 상호작용(Interactive Preview)도 확인 가능.
  `compileDebugKotlin` 검증 완료.
- **화면버튼 50% 불투명도 + 표지 수정 시트 전면 재구성 + Preview 페이지 팝업 버그 수정**
  (2026-08-20, 버전 미상향 — 컴파일 검증만):
  1. `brush/BrushControls.kt`: `ScreenControls`의 닫힌 상태 원형 버튼에 `Modifier.alpha(0.5f)` 적용
     — 캔버스 위에 항상 떠 있어도 그림을 덜 가리도록(펼쳐진 팝업 내용은 완전 불투명 유지).
  2. `sketchbook/SketchbookScreens.kt`(`EditCoverDialog`): 사용자가 제공한 시안 이미지 기준으로
     작은 팝업 카드 → 거의 전체화면 시트로 전면 재구성. 상단 "스케치북 표지 변경" 타이틀+닫기(X),
     큰 표지 미리보기(200dp), "이름 변경"(글자수 카운터), "표지 색상 변경"(파스텔 8색 스와치,
     `CoverPalette` 신설 — 기존 `BrushPalette`는 브러시용 원색이라 별개), "표지 이미지로 변경"(갤러리
     선택 타일, "사진 빼기"는 사진이 있을 때만 라벨 옆에 노출), "즐겨찾기"(설명 문구 + 스위치, 스위치는
     `onCheckedChange=null`로 시각 표시만 하고 바깥 Row의 `bounceClick` 하나로만 토글 — 안 그러면
     스위치를 직접 탭했을 때 두 번 토글되는 문제가 생김), "삭제"(빨간 텍스트+설명+화살표) 순으로 구성,
     하단 취소/완료 버튼 고정. 세로 스크롤(`verticalScroll`)로 내용이 화면보다 길어도 대응.
     - 스코프 결정: 시안의 "미리보기 배경 변경" 버튼과 프리셋 표지 이미지 갤러리(구름/들판/밤하늘 등)는
       사용자 확인 후 이번엔 생략 — 프리셋 이미지는 실제 에셋 파일이 프로젝트에 아직 없고, 미리보기
       배경 버튼은 정확한 동작이 미정이라 다음에 구체화되면 추가. 이름 글자수 제한도 시안엔 30으로
       보이지만 마법사 등 앱 전체가 20자 기준이라 그대로 유지(표시만 카운터로, 임의로 30 상향 안 함).
  3. **Preview 페이지 팝업 버그 확인 결과**: 실제 코드 버그가 아니라 `preview/BrushCanvasPreview.kt`의
     `ScreenControls(onOpenPages = {})`가 처음부터 빈 no-op이었던 것 — 실제 화면(`SketchbookScreens.kt`)
     은 `onOpenPages = { pagesOpen = true }`로 정상 배선되어 있어 진짜 `PagePanel`이 뜬다. 다만 진짜
     `PagePanel`은 `SketchbookRepository`가 있어야 썸네일을 읽어오는데, Preview는 로컬 저장소를 건드리지
     않는다는 프로젝트 규칙(Decisions 항목)이라 그대로 못 가져다 씀 — 대신 저장소 없이 같은 화면 얼개
     (헤더+3열 그리드+취소/완료)만 흉내 낸 `MockPagePanel`을 이 파일 안에 추가해 배선.
  - `compileDebugKotlin` 검증 완료. 에뮬레이터 실제 확인은 아직 안 함.
- **화면버튼(ScreenControls) 우측 상단 고정 확장 버튼으로 전면 개편** (2026-08-20, 버전 미상향 —
  컴파일 검증만): 페이지/회전/잠금/전체화면을 담던 독립 드래그 바를, 항상 화면 우측 상단(가로/세로
  공통)에 고정된 작은 원형 버튼 하나로 교체. 평소엔 닫혀 있다가 탭하면 아래로 펼쳐지고, 기능을
  하나 고르거나 팝업 바깥을 탭하면 자동으로 다시 닫힌다(펼침 상태는 어디에도 저장되지 않음 —
  매번 새로 열고 닫는 팝업).
  - `brush/BrushControls.kt`: `ScreenControls`에서 `dock`/`collapsed`/`onToggleCollapsed`/
    `onDragBar`/`onDragBarEnd` 파라미터를 전부 제거하고 `Popup`(`BelowAnchor`) 기반 펼침으로
    재작성. 최소화 토글 아이콘(UnfoldLess/More)과 이동 손잡이(`DragHandle`) 호출 삭제. 닫힌 상태
    아이콘은 `Icons.Filled.Tune`.
  - `sketchbook/SketchbookScreens.kt`, `diary/DiaryScreens.kt`, `share/SharedBookScreen.kt`,
    `preview/BrushCanvasPreview.kt`, `share/SharedBookPreviewScreen.kt`: `screenBarDock`/
    `screenBarCollapsed`/`screenBarDragPx` 상태 전부 제거, `ScreenControls` 호출을
    `modifier = Modifier.align(Alignment.TopEnd)`로 단순화. 두/세손가락 제스처의
    `onToggleToolbars`는 이제 브러시바(`BrushControls`)의 최소화만 토글(화면버튼은 더 이상 지속
    되는 접힘 상태가 없으므로).
  - `share/SharedBookScreen.kt`/`SharedBookPreviewScreen.kt`: 최대화 모드의 참가자 선택
    아이콘+스위치 줄이 우측 상단에서 화면버튼과 겹치던 것을 top padding 64dp로 내려서 회피.
  - `ui/main/MainScreen.kt`: 제스처 설정 라벨 "버튼바 최소화/펼치기" → "브러시바 최소화/펼치기"로
    수정(이제 화면버튼이 아니라 브러시바에만 해당).
  - 브러시바(`BrushControls`, 그림 도구용)는 이번 변경과 무관 — dock/드래그/최소화 전부 그대로 유지.
  - `compileDebugKotlin` 검증 완료. 에뮬레이터 실제 확인은 아직 안 함.
- **Interactive Preview 드로잉 안 되던 버그 수정 + 일기장 Preview 전용 파일 분리** (2026-08-20,
  버전 미상향 — 컴파일 검증만):
  - `preview/BrushCanvasPreview.kt`: `BrushView.apply { drawEnabled = false }`가 남아있어서
    Android Studio Interactive Preview에서 펜을 켜도 전혀 그려지지 않았음(`BrushView.onTouchEvent`가
    `if (!drawEnabled) return false`로 터치 자체를 씹어버림 — 드로잉뿐 아니라 핀치줌·스포이드·세손가락
    스와이프까지 전부 죽어있었음). `drawEnabled` 오버라이드 제거로 해결.
  - 같은 파일에 undo/redo/clear가 `{}`(no-op)였던 것과 스포이드 버튼이 실제 `BrushView`에 안
    묶여있던 것도 실제 화면(`SketchbookScreens.kt`) 배선과 동일하게 맞춤 — `view` 참조 저장 후
    `onUndo=view?.undo()`/`onRedo=view?.redo()`/`onClear=view?.clearCanvas()`/`onRotate=view?.rotate()`,
    `eyedropArmed`/`onEyedropPreview`/`onEyedrop`/`onEyedropCancel` 배선 + `EyedropFloatingPreview` 추가.
  - `preview/FlowPreviews.kt`에 마법사·달력 미리보기와 섞여 있던 "14 Diary editor"(실제
    `DiaryEditorScreen(previewMode=true)` 호출)를 새 `preview/DiaryCanvasPreview.kt`로 분리 —
    "13 Personal canvas"(`BrushCanvasPreview.kt`)/"17-18 Shared canvas"(`SharedCanvasPreviews.kt`)와
    같은 파일당-화면 구조로 통일해 Android Studio Preview 목록에서 찾기 쉽게 함(동작 변경 없음,
    실제 `DiaryEditorScreen`을 그대로 호출하므로 previewMode=true에서도 이미 드로잉 가능했음).
  - `compileDebugKotlin` 검증 완료(VS Code Java/Kotlin 언어서버가 잠근 `R.jar` 프로세스 종료 후 재시도).
- **화면버튼 분리 + 자유 드래그 통일 + 다이어리 툴바 통합 + 홈 표지 편집 + 스포이드 톤 정리**
  (2026-08-20, 버전 미상향 — 컴파일 검증만, 업로드는 아직 요청 안 됨):
  - `brush/BrushControls.kt`: 페이지/회전/화면잠금/전체화면 4개를 `BrushControls`에서 떼어내
    새 `ScreenControls` 컴포저블로 분리 — 자기만의 dock/드래그/최소화 상태를 가진 독립 플로팅
    서피스. `BrushControls`엔 `onBack`만 남음(다이어리 전용).
  - `ToolbarDock`/`nearestDock` 공용 헬퍼 추가 — 드래그로 가장 가까운 가장자리로 재도킹하는 로직을
    한 곳으로 모음. 최소화 상태 전용이던 1축 슬라이드(`toolbarCollapsedOffsetPx`)를 없애고, 펼친
    상태와 동일한 자유 2D 드래그+재도킹으로 통일(브러시바·화면버튼바 둘 다).
  - `brush/BrushView.kt`: `GestureAction.TOGGLE_TOOLBARS` 추가 + `onToggleToolbars` 콜백 — 2/3
    손가락 탭·롱프레스 제스처에 매핑하면 브러시바+화면버튼바를 함께 최소화/펼침 토글.
    `ui/main/MainScreen.kt` 설정 화면 라벨/아이콘도 추가.
  - `sketchbook/SketchbookScreens.kt`, `share/SharedBookScreen.kt`: `ScreenControls` 배선(기본
    dock=TOP, 브러시바=BOTTOM과 겹치지 않게), `onToggleToolbars` 연결.
  - `diary/DiaryScreens.kt`(`DiaryEditorScreen`): 캔버스 아래 고정 바 하나뿐이던 구조를 스케치북과
    동일한 `BoxWithConstraints` 오버레이+`BrushControls`+`ScreenControls`+잠금+전체화면+최소화로
    통합(다이어리는 하루 단위라 페이지 버튼만 없음).
  - `ui/main/MainScreen.kt`: 홈 캐러셀 표지에 롱프레스→`EditCoverDialog`(목록탭과 동일 컴포넌트,
    `internal`로 가시성 변경) 연결 — 이름/표지/즐겨찾기/삭제 모두 홈에서 바로 가능.
  - `brush/BrushControls.kt`: 스포이드 버튼 톤을 브러시 버튼과 통일(비무장=흐린 회색, 무장=강조색).
  - `preview/BrushCanvasPreview.kt`, `share/SharedBookPreviewScreen.kt`: 위 변경 전부 동일하게 배선.
  - `compileDebugKotlin --rerun-tasks` 성공. 에뮬레이터 실행 검증은 아직 안 함.
- **지우개 불투명도·블러 + 브러시 두께 재보정** (v2.7.0, 2026-08-17):
  - `brush/BrushView.kt`: 지우개 페인트(`eraseFill`/`eraseStroke`)를 `PorterDuff.CLEAR`에서
    `DST_OUT`으로 바꿔 `paint.alpha`가 실제로 반영되게 함(CLEAR는 알파 무시하고 항상 완전히 지움).
    `eraserBlur` 필드 추가, 0보다 크면 `BlurMaskFilter`로 지우개 경계를 부드럽게.
  - `brush/BrushControls.kt`: 지우개 패널에 불투명도 슬라이더를 켜고, "경계 블러" 슬라이더를
    세 번째 줄로 추가(`BlurOn` 아이콘, 0~32 범위, 기존과 동일한 30단계 슬라이더).
  - `SizeRange`(2~48→4~96)·`BlurRange`(0~16→0~32)·`Dimens.Brush.*` 기본값을 2배로 상향 — 지난
    세션에서 획 굵기를 캔버스 픽셀 고정값으로 바꾸며 화면 밀도·zoom 증폭이 빠져, 예전 범위 그대로면
    캔버스에서 거의 안 보일 만큼 얇았음.
  - `data/SessionStore.kt`: `eraserOpacity`/`eraserBlur` 영구 저장 추가(`eraserSize`와 동일 패턴).
  - `SketchbookScreens.kt`/`SharedBookScreen.kt`/`DiaryScreens.kt`: 지우개 전용 불투명도 상태를
    신설(기존엔 지우개일 때 무조건 `100f` 고정이라 사실상 죽어있던 값이었음) — 실제로 조절되게
    연결하고, 블러 값도 매 프레임 `BrushView`에 전달.
  - `preview/BrushCanvasPreview.kt`, `share/SharedBookPreviewScreen.kt`: 지우개 전용 불투명도·블러
    상태를 추가해 Preview에서도 실제 화면과 동일하게 동작하도록 배선.
  - 에뮬레이터에 실제 설치해 검증: 최대 굵기 펜 획, 블러 지우개 경계, 지우개→브러시 전환 모두
    정상 동작 확인(원격 코드 리딩만으로는 재현 안 되던 버그 리포트였음 — 사용자가 확인했던 건
    이번 수정 전 예전 빌드였을 가능성이 높음).
- **최소화 버튼바 위치 조절 + Preview 도킹/드래그 배선** (v2.6.1, 2026-08-17):
  - `SketchbookScreens.kt`/`SharedBookScreen.kt`: 최소화 상태일 땐 가로 도킹(상/하)이어도 폭을 안
    채우고(버튼 개수만큼만 감싸도록 함), 화면 중앙에 고정되던 것을 그립 드래그로 도킹된 가장자리를
    따라 미끄러지게(가로 도킹=좌우, 세로 도킹=상하) 바꿨다 — `toolbarCollapsedOffsetPx` 신설, 펼친
    상태의 기존 "드래그해서 가장 가까운 가장자리로 재도킹" 동작은 그대로 유지.
  - `preview/BrushCanvasPreview.kt`, `share/SharedBookPreviewScreen.kt`: 잠금·전체화면·최소화·드래그
    손잡이 콜백을 아예 안 넘기고 있어서 Preview에 해당 버튼/그립이 안 보이던 문제 수정 — 두 Preview
    모두 실제 화면과 같은 `BoxWithConstraints` 오버레이 구조로 바꿔 dock 전환과 드래그가 실제로
    동작하도록 배선(Interactive Preview에서 확인 가능).
  - `--rerun-tasks` 캐시 없는 재컴파일로 검증.
- **브러시 툴바 슬라이더 재디자인 + 터치영역/간격 정리** (v2.6.0, 2026-08-17): `brush/BrushControls.kt`.
  - 굵기·투명도 슬라이더를 커스텀 트랙(빨강→연빨강 그라데이션 채움 + 회색 미채움)과 원형 흰 썸으로
    재디자인, 30단계(`steps=28`)로 설정. 썸 안에는 색 점 대신 실제 수치를 표기.
  - `SlidersPanel`을 아이콘화(굵기=LineWeight, 불투명도=Opacity) + 두 줄(아이콘-슬라이더) 레이아웃으로
    압축, 브러시 이름 타이틀·리셋 버튼 제거. 굵기 표시는 실제 dp가 아니라 1~30 단계 번호로 통일.
  - 최소화(축소) 모드에서도 브러시 아이콘을 길게 눌러 같은 `SlidersPanel`을 열 수 있게 추가.
  - `SlidersPanel` 팝업은 전용 `sizePopupAnchor`(`AboveAnchor`/`BelowAnchor`/`SideAnchor`에
    `edgeMarginPx` 추가)로 화면 가장자리에서 최소 20dp 띄워서 뜨도록 분리.
  - 버튼 터치 영역을 접근성 최소치 48dp에서 `ButtonTapSize`(30dp) 상수로 통일, 브러시 버튼의
    이중 크롭 박스를 단순화.
  - 버튼바를 구분선 기준 "그룹"으로 재구성 — 그룹 내부 버튼 간격(15dp)과 그룹-구분선 간격(8dp)을
    바깥/안쪽 `Arrangement.spacedBy`로 분리해 구분선 쪽만 더 좁게 만들 수 있게 함(공유 spacedBy
    하나로는 안 되고, `Modifier.height/width`에 음수 dp도 0으로 클램프돼 안 먹힘 — 그룹 분리로 해결).
  - `image/brush_type_rev1/`의 여백 없는 새 브러시 아이콘(펜/연필/크레파스/수채화/지우개)으로 교체,
    표시 크기 30dp로 통일(기존 아이콘은 112px 캔버스에 그림이 절반 이하만 차지해 여백이 커 보였음).
  - `--rerun-tasks` 캐시 없는 재컴파일로 매 단계 검증.
- **메인 탭 타이틀·액션 고정 배치** (2026-08-17): `MainTabPage`를 시안의 고정 구역
  `상단 60dp → daymory 25dp → 여백 40dp → 타이틀 80dp → 액션 60dp`로 재구성했다.
  화면별 액션 유무나 크기가 헤더 높이를 바꾸지 않으므로 Home/List/share/Diary/Other 타이틀의
  Y 좌표가 동일하다. 홈 토글, 공유 생성·참여, 오늘 일기 편집 버튼은 타이틀 아래 액션 영역의
  오른쪽 아래(시안 빨간 점 위치)로 통일했고 월 이동 등 본문 컨트롤은 유지했다.
  `assembleDebug lintDebug` 성공.
- **불필요 코드·리소스 정리** (2026-08-17): 호출되지 않던 구 SessionStore 방/스케치북 목록,
  `SketchbookRef`, `DuckWalk`, 미사용 Repository API·싱글턴·홈 콜백·헤더 아바타 분기·앱 내부
  개발자 미리보기를 제거했다. Android Studio Preview 6개와 공유 캔버스 Preview 구현은
  `app/src/debug`로 이동해 릴리스 소스에서 제외했다. 미사용 오리 GIF/PNG 3개, Coil·Navigation
  Compose·Firebase Storage·WorkManager 카탈로그 항목, 미사용 import 47개와 상수·Manifest 중복을
  정리했다. `assembleDebug testDebugUnitTest lintDebug` 성공(`testDebugUnitTest`는 테스트 소스가 없어
  `NO-SOURCE`), Lint는 58건에서 버전/런처 아이콘 관련 41건으로 감소했다.
- **Shared sketchbook cover component** (2026-08-17): Added `SketchbookCover.kt` and routed both the Home carousel and sketchbook list through it. All existing/new covers now default to `#FFBF2A`; solid-color covers receive a 20% black spine overlay, optional image covers receive a 70% black spine overlay, and the cover duck plus `Theme.kt` rotating `CoverColors` palette were removed. Added Korean tuning comments and `scripts/verify_sketchbook_cover.ps1`.
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

- **페이지 넘기기 모드 동작 수정: 전체화면 전환 + 툴바 숨김 + 확인 버튼, 세손가락 넘기기 완전 제거** (v1.70.0, 2026-08-15):
  1. **버튼이 안 먹히던 원인**: v1.69.0에서 추가한 "페이지 넘기기 모드" 토글은 내부 플래그만 바뀔 뿐 화면상 아무 변화가 없어서(툴바도 그대로, 전체화면도 안 됨) 탭해도 반응이 없는 것처럼 보였음. 실제로는 그 상태에서 그리기/줌만 막혀 있었을 뿐.
  2. **수정**: 페이지 넘기기 모드 버튼을 누르면 이제 (1) 전체화면으로 전환되고 (2) 버튼 바(BrushControls)가 완전히 사라짐(공유 화면은 상단 헤더까지 함께 숨김) — 한손가락 좌우 스와이프로만 페이지가 넘어감. 우측 상단에 새 "확인" 버튼(`PageTurnConfirmButton`)이 떠서, 원하는 페이지에 도착한 뒤 이걸 누르면 전체화면·페이지 넘기기 모드가 한 번에 꺼지고 원래 드로잉 화면으로 복귀. 뒤로가기 버튼도 동일하게 동작(모드→전체화면→화면 나가기 순으로 단계적 탈출).
  3. **세손가락 드래그로 페이지 넘기기 완전 제거**: v1.69.0에서 설정 가능한 제스처로 남겨뒀던 것까지 포함해 전부 삭제 — `BrushView`의 3손가락 드래그 추적 코드, `GestureAction.PAGE_TURN`, `SessionStore.threeFingerDragAction`, 설정 화면의 "세 손가락 드래그" 항목 모두 제거. 이제 페이지를 인터랙티브하게 넘기는 방법은 페이지 넘기기 모드의 한손가락 스와이프 하나뿐.
  - 개인/공유 스케치북 화면 모두 동일 적용.

- **페이지 넘기기 애니메이션이 화면에 아예 안 보이던 문제 대응** (v1.71.0, 2026-08-16):
  - v1.69.0에서 `PageTurnOverlay`를 절반폭 기준 2단 벼륍 효과로 재작성할 때, FAR(먼 쪽) 조각을 NEAR(책등쪽) 조각의 **자식으로 중첩**시키고 `.offset()`으로 부모의 실제 레이아웃 폭 바깥까지 밀어내 그리는 방식을 썼는데, 실기기에서 애니메이션이 전혀 안 보인다는 리포트가 들어옴 — 정확한 원인은 기기에서 직접 확인 못 했지만, 부모 경계 밖으로 밀어낸 자식이 그려지는지 여부와 `AndroidView`(캔버스) 위에 Compose 오버레이가 확실히 위에 그려지는지가 가장 유력한 용의선이었음.
  - **대응**: FAR/NEAR 두 조각을 부모-자식 중첩이 아니라 **형제(sibling)** 로 바꿔 각자 `BoxWithConstraints` 안에서 `.align()`으로 독립적으로 배치(자식을 부모 폭 밖으로 밀어내는 트릭 완전히 제거) — 시각적으로는 동일한 결과(FAR가 사라질 때쯤 NEAR가 움직이기 시작해서 이어붙는 지점이 안 보임). 여기에 `Modifier.zIndex(10f)`을 추가해 이 오버레이가 캔버스(AndroidView)보다 항상 위에 그려지도록 명시적으로 고정.
  - 이 변경만으로 100% 확정 원인 해결이라고 장담하긴 어려움 — 실기기 확인 후 여전히 안 보이면 추가로 다른 원인을 찾아야 함.

- **daymory 리브랜딩 + 폰 기준 레이아웃/네비게이션 재설계** (v2.0.0, 2026-08-16): 사용자가 PPT로 만든
  접은 화면(폰 비율) 시안 세트를 바탕으로 한 대규모 개편. 3~4인 공유 화면 재설계는 이번엔 제외(별도
  세션으로 미룸), 오리 마스코트는 기존 걷는 GIF 그대로 비율만 조정.
  1. **리브랜딩**: 앱 이름 "G1 Sketchbook" → **"daymory"** — `strings.xml`(app_name), `themes.xml`
     (`Theme.G1Sketchbook`→`Theme.Daymory`), `Theme.kt`(`G1Theme`→`DaymoryTheme`), 설정 탭 정보
     카드, 로그인 화면 콘텐츠 설명, 스플래시/로그인 타이틀("Daily sketch"→"daymory", 단어라
     2줄 폴백 로직 제거) 전부 갱신. `applicationId`/런처 아이콘은 미변경(리소스 재구성 리스크).
  2. **`Dimens.kt` 폰 기준 재설정**: `Screen.topMargin` 63.5→60dp, `titleSp` 78→61sp, 신규
     `titleGap`(40dp)/`contentGap`(60dp)/`sideMargin`(45dp, 화면마다 0/16/20/71dp로 제각각이던
     좌우 여백 통일)/`navItemSize`(60dp)/`navBarPadding`(25dp) 추가. `Calendar.yearMonthSp`
     52→78sp(달력 숫자가 이제 탭 타이틀보다 큼), `Calendar.sideMargin`은 `Screen.sideMargin` 참조로
     변경(일기 탭도 공통 템플릿 적용). `Onboarding.titleSp` 130→87sp, `subtitleSp` 37→24sp,
     `duckW/H` 765×510→275×400dp, 신규 `ctaSp`(21sp). `Wizard.cardWidth` 425→280dp.
  2. **네비게이션 5탭 재설계**: 알약형 배경+원형 팝업 아이콘 방식을 버리고 아이콘+라벨 텍스트가
     있는 평평한 Row로 교체(선택 탭은 `primary` 색상만, 배경/그림자 없음). **"공유" 탭 신설**
     (Home/List/**share**/Diary/Other) — 새 화면을 만들지 않고 기존 `SketchbookTab`을
     `initialShowShared=true`로 재사용(위저드·그리드·생성/참여 로직 100% 재사용, 코드 중복 없음).
  3. **탭 헤더**: 왼쪽 아바타 + 가운데 "daymory" 워드마크(기존에 있었지만 미사용이던 `Pretendard`
     폰트 재사용, 새 폰트 에셋 불필요) + 오른쪽 화면별 액션 슬롯을 5개 탭 전부에 추가. 기존
     태그라인/버튼은 그대로 두고 그 위에 추가하는 방식이라 기존 동작 영향 없음.
  4. **커버 색상 팔레트**: `MainScreen.kt`/`SketchbookScreens.kt`에 중복돼 있던 `CoverColors`를
     `Theme.kt`의 공유 상수 하나로 합치면서 시안 7색(오렌지/연청록/진청록/핑크/옐로우/레드/크림)으로
     교체.
  5. **일기 상세보기**: `CleanDetailBody`의 정적 이미지에 핀치 확대/축소+팬 추가(1x~5x, 그림 자체는
     안 바뀌는 보기 전용이라 표준 Compose 제스처로 구현, `BrushView` 안 씀). 존재하지만 호출부가
     없던 `saveToGallery()`를 다운로드 아이콘 버튼에 연결(결과는 Toast로 표시).
  - 실기기 시각 확인 불가 상태로 진행 — 특히 네비게이션 바 세부 여백, 헤더 오른쪽 아이콘 매핑,
    커버 색상 근사값은 실기기 확인 후 조정이 필요할 수 있음.

- **네비게이션 바 하단 여백 제거 + 공유 화면 3~4인 지원** (v2.1.0, 2026-08-16):
  1. **네비게이션 바**: v2.0.0에서 새로 만든 평평한 5탭 바에 남아있던 45dp 하단 여백(`Dimens.Screen.bottomMargin`)을 제거해 화면 최하단에 완전히 붙도록 수정(`navigationBarsPadding()`과 25dp 내부 패딩은 유지).
  2. **공유 화면 3~4인 지원**: v2.0.0에서 미룬 항목. `ShareRepository.joinSession()`의 하드코딩된 2인 정원(`>= 2`)을 `MAX_SLOTS = 4`로 상향. `SharedBookScreen.kt`를 "나+상대 1명" 고정 구조에서 N명 구조로 재작성:
     - `partner: Slot?`(단일) → `others: List<Slot>`, `partnerBmp: Bitmap?`(단일) → 참가자별 디코딩 컴포저블(`ParticipantBitmap`)로 일반화.
     - `ViewMode{EQUAL,LARGE,SOLO}` + `Focus{MINE,THEIRS}`(이분법) → `ViewMode{GRID,MAXIMIZE}`로 교체.
     - **GRID**: 실제 참가자 수에 따라 자동 전환 — 2인 이하는 기존과 동일한 반반 분할(세로면 상대 위/나 아래), 3~4인은 2x2 그리드(나는 항상 우하단 고정, 3인이면 남은 한 칸은 빈칸).
     - **MAXIMIZE**(신규): 큰 화면 하나 + 모서리에 뜨는 작은 팝업, "..." 버튼으로 팝업에 넣을 참가자 선택, 스위치로 큰 화면↔팝업 맞바꿈(시안의 "내 화면 최대화"/"참가자 최대화" 두 상태).
     - 내 캔버스(`mine`)는 여전히 `movableContentOf` 하나로 유지(상태 보존 필요), 상대방 pane들은 정적 이미지라 매번 재구성해도 무방하므로 일반 컴포저블로 단순화. `mine`이 한 컴포지션에 중복 배치되지 않도록(특히 참가자가 0명인 상태로 MAXIMIZE 진입 시) 방어 로직 추가.
  - 실기기 2대 이상으로 실제 공유 세션 테스트는 못 해봤음 — 확인 후 조정 필요할 수 있음.

- **온보딩 정적 이미지 교체 + 그리드 시안 반영 + 공유화면 각진 코너 + 브랜드 컬러/폰트** (v2.2.0, 2026-08-16):
  1. **온보딩 오리**: 색상 재염색이 어려운 애니메이션 GIF(`duck_walk.gif`) 대신, 사용자가 제공한 정적 이미지(`image/source/ONBOARDING2.png`, 이미 틸 색상으로 그려진 베레모 쓴 오리)를 `drawable-nodpi/onboarding_duck2.png`로 추가해 스플래시/로그인 화면에 적용.
  2. **브랜드 틸 컬러(#008484) 도입**: `Theme.kt`에 `DaymoryTeal` 상수 추가, 온보딩 타이틀/부제/버튼과 5개 탭의 태그라인("Draw your time" 등) 전체에 적용(기존 테마 적응형 색 대신 고정 브랜드 색).
  3. **Bodoni MT Black 폰트**: 사용자가 제공한 폰트 파일(`support/font/bodoni MT Black/BOD_BLAR.TTF`)을 `res/font/bodoni_mt_black.ttf`로 등록, 탭 헤더의 "daymory" 워드마크 전용으로 적용(시안 "Font: Bodoni MT Black" 주석 반영).
  4. **그리드 시안 반영(레이아웃 이해 수정)**: 네비게이션 바 총 높이가 화면 하단부터 60dp가 되도록 내부 여백값 보정(25dp→10dp). 홈 캐러셀 노트 아이콘 크기를 시안 수치로 축소(가운데 267.5×402→182×275dp, 옆 217×327→145×218dp). 캐러셀 표지에 제목+날짜 2줄 텍스트 추가(가운데 14/12sp, 옆 12/10sp — `Sketchbook.dateLabel` 신규 헬퍼), 책처럼 두께감 있는 그림자(뒤로 살짝 어긋난 종이 스택 2겹) 추가. 홈 탭 헤더 우측에 개인/공유 토글 아이콘 추가해 캐러셀 필터링(스케치북 리스트 탭의 개인/공유받음 필터와 동일 개념).
  5. **공유 화면 각진 코너**: 지난번 3~4인 지원 작업에서 `PaneFrame`/`GridCell`/최대화 팝업에 남아있던 둥근 모서리(`MaterialTheme.shapes.medium`)를 전부 `RectangleShape`로 교체 — 시안(분할 화면 mockup)이 각진 사각형이라 그대로 맞춤.

- **Android Studio Canvas Preview** (2026-08-17): Added
  `app/src/main/java/com/g1/sketchbook/preview/BrushCanvasPreview.kt`. It renders
  `BrushView` with `BrushControls` through Compose `@Preview`; the existing
  `design-tool/mockup.html` is unchanged. Verified with `compileDebugKotlin`.
- **Android Studio 전체 화면 Preview 카탈로그** (2026-08-17): 기존 단일 홈 Preview를
  `OnboardingPreviews.kt`, `MainTabPreviews.kt`, `FlowPreviews.kt`,
  `SharedCanvasPreviews.kt`, `BrushCanvasPreview.kt`로 확장. Splash/Login/Nickname, 홈과 5개 탭,
  개인·공유 생성/참여, 개인 캔버스, 일기 편집기, 전체 달력/날짜 상세, 공유 캔버스 분할/최대화까지
  총 18개 상태를 Android Studio Design/Split에서 볼 수 있다. 실제 화면 Composable에
  preview 전용 샘플 데이터/모드만 주입하며, 런타임 기본값과 Firebase/로컬 저장 동작은 유지했다.
  `design-tool/mockup.html`은 변경하지 않았다. `compileDebugKotlin`과
  `compileReleaseKotlin`으로 검증.
- **온보딩 오리 단일 크기 조절값** (2026-08-17): `Dimens.Onboarding.duckW/duckH`를
  `duckMaxWidth`와 고정 `duckAspectRatio(275:400)`로 분리. 이제 `duckMaxWidth` 하나만 바꾸면
  Splash/Login 오리가 정비율로 함께 확대·축소된다. `widthIn`을 `fillMaxWidth`보다 바깥에
  배치해 최대 너비가 실제로 적용되도록 수정했다. `compileDebugKotlin` 검증 완료.
- **Google 로그인 버튼 스타일 통일** (2026-08-17): Login의 outlined 버튼을 Splash의 enter와
  동일한 `DaymoryTeal` 채움+흰 글씨 pill로 변경. 고정 너비 없이 기존 좌우 46dp content padding을
  유지해 문구 길이에 따라 가로 길이가 자동 결정된다. 로그인/로딩/오류 동작은 변경하지 않았다.
- **온보딩 네 상태 위치 통일** (2026-08-17): Splash/Login/Loading/Error가 새 공통
  `OnboardingLayout`을 사용하도록 중복 레이아웃을 제거했다. CTA 56dp·오류 40dp 고정 슬롯으로
  로딩/오류 유무와 관계없이 daymory·오리·부제·버튼 좌표가 동일하다. `OnboardingTitle`은
  `titleSp` 입력값을 먼저 적용하고 화면 폭을 넘을 때만 55% 한도까지 축소한다. Loading/Error
  Preview도 각각 분리했다.
- **온보딩 라이트·다크 전체 반전** (2026-08-17): 온보딩 배경을 MainScreen과 동일한
  `colorScheme.background`로 통일하고, 제목·부제·오리 tint·로딩·버튼 배경은
  `onBackground`, 버튼 글자는 `background`를 사용하도록 변경했다. 기존 세이지 전용 팔레트는
  제거했고 오류 문구는 테마 `error`를 유지한다. 라이트/다크 Splash·Login Preview 추가.
- **온보딩 브랜드 색상 복원 + 오리 위치 조절값** (2026-08-17): 배경은 MainScreen과 동일한
  `colorScheme.background`를 유지하되 제목·부제·오리 tint·로딩·버튼 배경은 고정 브랜드 색상
  `DaymoryTeal`, 버튼 글자는 흰색으로 복원했다. 공용 `OnboardingLayout`의 오리 Modifier에
  `.offset(x = 0.dp, y = 0.dp)`와 한국어 조절 주석을 추가했다. x 양수는 오른쪽, y 양수는
  아래쪽이며 Splash/Login/Loading/Error 네 화면에 함께 적용된다.

- **온보딩 전용 라이트·다크 색상 반전** (2026-08-17): 전역 `Theme.kt`는 바꾸지 않고 공용
  `OnboardingLayout` 안에서만 색상을 선택하도록 수정했다. 라이트는 Ivory 배경과
  `DaymoryTeal` 요소, 다크는 `DaymoryTeal` 배경과 Ivory 요소를 사용한다. 제목·부제·오리
  tint·로딩·오류·버튼 배경이 같은 전경색을 쓰고 버튼 글자는 배경색을 사용한다. 수정 위치에
  한국어 주석을 추가했고 `compileDebugKotlin testDebugUnitTest`로 검증했다.
- **메인 탭 공용 레이아웃 분리** (2026-08-17): 새 `ui/main/MainTabLayout.kt`에 세로 화면 하단
  탭바, 가로 화면 사이드 탭바, `daymory` 헤더, 화면 제목과 공통 여백을 모았다. Home,
  개인·공유 Sketchbook, Diary, Settings는 `MainTabPage`에 고유 본문과 액션만 전달한다.
  Sketchbook의 중첩 `Scaffold`를 제거했고 기존 Preview 5개와 화면별 데이터/콜백은 유지했다.
  공통 위치 조절부에 한국어 주석을 추가하고 전체 디버그 Kotlin 빌드로 검증했다.

- **닉네임 전체 화면을 팝업으로 교체** (2026-08-17): 로그인 후 표시되던
  `NicknameScreen.kt`를 삭제하고 로그인 온보딩 위에 `NicknameDialog`를 띄우도록 변경했다.
  입력창은 투명한 pill 형태이며 `별명을 입력해주세요.` 안내문만 연한 회색으로 표시한다.
  팝업 폭은 `NicknameDialog.kt`의 `Modifier.width(280.dp)` 한 줄에서 조절한다.
  입력은 16자로 제한하고 우측 하단에 취소·확인 버튼을 배치했다. 취소·바깥 터치·뒤로가기는
  로그아웃 후 로그인 화면으로 돌아가고, 확인은 기존 `saveNickname`으로 저장한다. Preview 호출과
  `compileDebugKotlin testDebugUnitTest`를 검증했다.

- **Android Studio Preview 카탈로그 표시 복구** (2026-08-17): 정리 과정에서
  `app/src/debug/java`로 옮겼던 Preview 6개와 공유 화면 Preview 렌더러를
  `app/src/main/java`로 복구했다. Android Studio의 일반 Project/Android 뷰에서 파일을 바로
  열 수 있고, `ui-tooling-preview`도 main 컴파일에서 참조할 수 있도록 `implementation`으로
  되돌렸다. `@Preview` 함수는 앱 실행 경로에서 호출되지 않으므로 실제 화면 동작에는 영향이 없다.

- **액션영역 버튼 통일 · 홈 개인/공유 아이콘화 · 페이지 넘기기 모션 삭제 · 표지 편집 이동 · 내비 아이콘**
  (v2.3.0, 2026-08-17):
  1. **액션영역 아이콘 버튼 통일**: 다이어리 연필 아이콘 35dp→24dp, `MainTabLayout`의 `actions`
     Row에 `LocalMinimumInteractiveComponentSize`를 32dp로 덮어써 터치 영역도 한 곳에서 통일
     (`Dimens.Screen.actionButtonSize`).
  2. **홈 탭 개인/공유 전환**: 우상단 `Switch`를 Person/Groups 아이콘 버튼 2개로 교체(선택 쪽만
     primary 틴트).
  3. **페이지 넘기기 기능 완전 삭제**: 툴바의 "페이지 넘기기 모드" 버튼, `BrushView`의 스와이프
     드래그 턴 코드, 책장 넘김 플립 애니메이션(`PageTurnOverlay`/`playPageTurn`)을 개인·공유
     캔버스 양쪽에서 전부 제거 — 페이지 전환은 이제 애니메이션 없이 즉시 반영된다. 대신
     `PagePanel`(페이지 다이얼로그)에 페이지 번호를 직접 입력해 이동하는 필드를 추가했다.
  4. **표지 길게 눌러 수정**: `CoverCard` 상단의 즐겨찾기·삭제 아이콘을 없애고, 길게 누르면 뜨는
     `EditCoverDialog`(이름+배경, `SketchbookRepository.updateNameAndBg`)로 즐겨찾기 토글·삭제까지
     옮겼다. `bounceClick`에 `onLongClick` 파라미터 추가.
  5. **제스처 설정 아이콘화**: 두 손가락 탭/세 손가락 탭/길게 누르기 칩의 텍스트 라벨을 아이콘
     (Block/Undo/Redo/Colorize)으로 교체, 접근성 라벨은 유지.
  6. **하단 내비 share 탭 아이콘 교체**: `Groups`(사람 그룹, 다른 탭 대비 시각적으로 무거움) →
     `Share`. 내비 아이콘 크기 24dp→26dp(주석: "버튼아이콘 사이즈").
  - `compileDebugKotlin` 검증 완료.

- **표지 이미지 갤러리 선택 · 스케치북 캔버스 8종 개선 · 버튼바 도킹** (v2.4.0, 2026-08-17):
  1. **표지 = 갤러리 사진**: v2.3.0에서 "표지 수정"의 "배경"을 종이 재질(bgKey) 스와치로 잘못
     구현했던 것을 재작업 — 표지 디자인은 그리기용 종이 재질과 무관하고 갤러리 사진으로 바꾸는
     것이라는 피드백을 받아 `ActivityResultContracts.PickVisualMedia`로 갤러리에서 사진을 골라
     표지 이미지로 저장(`SketchbookRepository.saveCover/loadCover/loadCoverThumb/removeCover`,
     book 폴더에 `cover.jpg` 한 장). 이름 변경은 `rename()`으로 분리. `CoverCard`와 홈 캐러셀
     둘 다 저장된 표지 이미지를 불러와 보여준다(`SketchbookCover`의 기존 `coverImage` 파라미터 활용).
  2. **화면 잠금 중 두 손가락 이동 허용**: `BrushView`의 잠금이 확대/축소(scale)만 막고 이동
     (translate)은 그대로 적용하도록 분리.
  3. **브러시 색상/굵기/투명도 저장**: `SessionStore`에 저장해 앱을 다시 켜도 이어서 쓸 수 있게
     (브러시 종류·지우개 여부는 저장 안 함, 매번 펜으로 시작). 개인·공유 캔버스가 같은 키를 공유.
  4. **페이지 번호 표기 단순화**: 페이지 다이얼로그의 "1/15" → "1"만(직접 입력으로 이동하는 필드는
     유지).
  5. **페이지 순서 드래그 이동**: `PagePanel` 그리드에서 길게 눌러 드래그하면 실시간으로 자리가
     바뀌고, 손을 떼면 실제 파일이 재배치된다(`SketchbookRepository.applyPageOrder` — 충돌 없이
     바꾸려고 전부 임시파일로 옮긴 뒤 최종 위치에 다시 씀).
  6. **세손가락 페이지 넘기기 재도입(애니메이션 없이)**: `BrushView`에 3손가락 수평 스와이프
     감지(`onThreeFingerSwipe`)를 추가 — 두 손가락 핀치/팬과 완전히 분리된 제스처라 서로 안
     겹친다. 페이지 전환 자체는 애니메이션 없이 즉시 반영(책장 넘김 효과는 v2.3.0에서 이미 삭제).
  7. **버튼바 최소화**: 현재 브러시(탭→4개 미니 팝업)·색상(탭→기존 색상휠)만 남는 축소 모드
     추가(`BrushControls`의 `collapsed`/`onToggleCollapsed`).
  8. **버튼바 드래그 도킹 + 세로 모드**: 왼쪽 끝 손잡이(⋮⋮ 아이콘)를 길게 눌러 드래그하면 놓은
     위치에서 가장 가까운 가장자리(상/하/좌/우)로 붙는다(`ToolbarDock`, 캔버스 위에 뜨는 오버레이라
     캔버스 크기는 안 바뀜). 좌/우로 붙으면 내부 배치가 자동으로 세로(Column+verticalScroll)로
     전환되고, 브러시 패널·색상휠·즐겨찾기 편집 팝업도 화면 밖으로 안 나가는 방향으로 열린다
     (`AboveAnchor`/`BelowAnchor`/`SideAnchor`).
  - 개인·공유 스케치북 캔버스 화면 둘 다 적용(그림일기 탭은 페이지 개념이 없어 제외).
  - `compileDebugKotlin` 검증 완료.

- **표지 편집 팝업 고정 · 색상 선택 · 표지 크롭 버그 수정 · 목록 텍스트 개편 · 페이지 편집창 정리**
  (v2.5.0, 2026-08-17):
  1. **표지 편집 팝업이 더 이상 배경을 안 바꿈**: `EditCoverDialog`가 화면 전체를 표지색/사진으로
     바꾸던 걸 없애고 고정 스크림 위에 작은 팝업 카드만 뜨도록 변경, 카드 안에 작은 표지 모양
     미리보기(64dp)를 추가했다.
  2. **표지 색상 선택 추가**: `Sketchbook.coverColor` 필드 신설, "색상 선택" 버튼으로 브러시 색상휠
     (`ColorPickerCard`, `internal`로 노출)을 인라인으로 열어 고른다. "갤러리에서 선택"과 별개로
     동작(하나가 다른 하나를 안 지움 — 사진이 있으면 사진이 화면에서 색보다 우선 표시됨).
  3. **"갤러리에서 골라도 표지가 안 바뀌던" 버그 수정**: 원인은 캐시 무효화 누락 — book id가 그대로라
     저장 후에도 목록·홈이 새 이미지를 다시 안 읽어왔다. `Sketchbook.coverVersion`을 추가해 저장/삭제
     때마다 올리고 `LaunchedEffect` 키에 넣어 확실히 다시 로드하게 했다. 고른 사진은 표지 비율
     (`Dimens.Home.coverRatio`)에 맞춰 가운데 기준으로 크롭한 뒤 저장한다.
  4. **목록 썸네일 텍스트 개편**: "N쪽" 표기를 생성일(`dateLabel`)로 교체, 하단 그라데이션 스크림
     삭제(텍스트는 그대로 표지 하단에 표기).
  5. **페이지 편집창 취소/완료 버튼**: 배경(스크림) 탭으로 닫히던 걸 없애고 명시적 취소/완료
     버튼으로만 닫히게 변경 — 드래그로 순서 바꾸다 실수로 튕겨나가던 문제 방지. 페이지 탭 선택도
     더 이상 자동으로 창을 안 닫는다.
  6. **드래그 중 하이라이트 범위 수정**: 그림자 효과가 썸네일 박스에만 걸리도록 수정(페이지 번호
     텍스트는 더 이상 같이 하이라이트되지 않음).
  7. **홈 캐러셀 플링 복원**: 한 장씩만 넘어가게 막았던 `pagerSnapDistance` 제한을 없애 빠르게
     스와이프하면 계속 스크롤되다 손가락으로 다시 누르면 멈추는 기본 동작으로 되돌렸다.
  - 일기달력 탭 화살표 크기/두께 위치를 찾기 쉽게 주석 추가(`Dimens.Calendar.arrowIconW/H`,
    `ChevronArrow`의 `Stroke(width = w * 0.15f, ...)` 배율 — 사용자가 0.5f에서 직접 조정함).
  - `compileDebugKotlin` 검증 완료.

- **앱 아이콘 교체** (v2.5.1, 2026-08-17): 사용자가 제공한 `image/앱아이콘.png`(1254×1254, 투명
  배경 로고)로 런처 아이콘을 전부 교체했다. PowerShell(System.Drawing, ImageMagick 등 외부 툴
  없이)로 밀도별 PNG를 생성: 레거시 `ic_launcher.png`(mdpi 48~xxxhdpi 192, 원본 그대로 리사이즈),
  `ic_launcher_round.png`(같은 리사이즈 후 원형 클립), 어댑티브 아이콘용 `ic_launcher_foreground.png`
  (mdpi 108~xxxhdpi 432, 로고를 캔버스의 62%로 축소·중앙 배치해 원형/스퀴클 마스크에 안 잘리게
  세이프존 확보). 기존 자리표시자 벡터(빨간 원+펜 스트로크: `drawable/ic_launcher_foreground.xml`,
  `mipmap/ic_launcher(_round).xml`)는 삭제. `ic_launcher_background` 색상을 시안 빨강(#FF6B6B)에서
  흰색(#FFFFFF)으로 변경(로고가 검정 위주라 대비 확보). `assembleDebug` 빌드 검증 완료.

## Next (Phase 2~4)
- 읽기모드 신버전을 에뮬레이터/실기기에서 세로 왕복, 가로 두 페이지 왕복, 각 캔버스 비율별로 시각 확인한다.
- **Phase 2 — 스케치북**: 생성(이름→사이즈→배경)·멀티페이지(≤15)·자동저장·공유 실시간. 캔버스에 BrushView 연결.
  - 사이즈 6종: A5/A4/A3/데스크톱1920×1080/모바일390×844/태블릿810×1080. 배경 5종(image/background/*).
- **Phase 3 — 그림일기 + 달력**: 개인 전용, 사용자당 1개, 날짜별 1장, 자정 잠금(이미지화). 달력(가로 4:3 / 세로 상단달력+하단일기), 이미지 저장.
- **Phase 4 — 마감**: 홈 대시보드 실기능(새 스케치북/참여/즐겨찾기), 계정(아바타). (색상휠+팔레트 UI = v1.22.0 완료.)
  - 남은 후보: 화면 디테일 다듬기, 공유 페이지 동기화 옵션(같은 페이지 함께 넘기기), 저장/내보내기 등.

## Decisions
- 다이어리 투명 필기는 기존 합성 이미지나 Firebase 백업 스키마를 변경하지 않고 로컬 companion
  PNG(`_content.png`)로만 추가 보관한다. 기존 항목은 역변환하지 않는다.
- 읽기모드는 앱 내부에 GLES/curl 구현을 복제하지 않고 공용 `:pagecurl`만 사용한다. 앱은
  `SketchbookPageSource`로 페이지 Bitmap과 실제 `width / height` 비율만 공급한다. 저장된 페이지는
  투명 필기 레이어이므로 source가 스케치북의 종이 재질과 합성한 최종 불투명 Bitmap으로 변환한다.
- 메인 탭의 브랜드·타이틀·액션 영역은 콘텐츠 크기에 따라 측정하지 않고 `Dimens.Screen`의
  고정 높이를 사용한다. 화면별 액션은 `MainTabPage.actions`로만 전달해 타이틀 아래 우측에 둔다.
- Android Studio Preview 카탈로그는 Android Studio에서 항상 발견할 수 있도록
  `app/src/main/java/com/g1/sketchbook/preview`에 둔다. Preview는 실제 화면 Composable을
  호출하되 앱 실행 경로에서는 호출하지 않으며, 샘플 데이터는 Preview 안에서만 사용한다.
- Sketchbook cover rendering belongs to the shared `SketchbookCover` composable, not `Theme.kt`. A black overlay derives the spine from any selected color, while image covers use a fixed 70% black spine for consistent contrast.
- Android Studio Preview는 별도 디자인 복사본이 아니라 실제 앱 Composable을 렌더링한다.
  디자인 수정은 각 실제 화면 `kt`에서 하고, `preview/*Previews.kt`는 표시할 화면 상태와 샘플
  데이터 선택에만 사용한다. Preview 중에는 로컬 저장소/Firebase 접속을 하지 않는다.
- 브러시 = PNG/스탬프 감성 최우선. 연해/중심선/각짐 문제는 "웹 놀이터와 동일 코드(디스크+직접 누적)"로 해결.
- 백엔드: 지금은 무료(RTDB+Base64), Repository로 감싸 후에 이전(A안).
- 그림일기: 개인 전용·사용자당 1개.
- 라이트/다크 지원. 캔버스 90도 회전은 버튼으로 제공(+화면 방향에 맞춰 자동 회전). 제스처 회전은 없음.
- 핀치 줌(1~5배)·이동 재도입 완료(v1.15~1.17). 한 손가락 그리기 / 두 손가락 줌.
- 버전 매 업로드마다 bump + 새 태그(vX.Y.Z), 덮어쓰기 금지.

## Open / Blockers
- (해결됨 v2.3.0) `app/debug.keystore`를 의도적으로 Git에 추적 — 표준 디버그 키(storePassword
  "android")라 민감정보 아님, 모든 빌드 환경이 동일 키로 서명해 설치/업데이트 충돌을 막는다.
  릴리스 키는 여전히 저장소에 넣지 않는다.
- **빌드 잠금**: VS Code Java/Kotlin 언어서버가 `app/build/.../R.jar`를 잠가 CLI 빌드가 IOException. 대응: 빌드 전 해당 java 프로세스(kotlinLanguageServer/redhat.java/.vscode\extensions) kill 후 R.jar 삭제(사용자가 권한 허용함). `.vscode/settings.json`에 Java 자동빌드/Gradle import off + build 감시 제외 넣음.
- 빌드 JDK: standalone 없음 → Android Studio JBR. `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.
- (해결됨 v1.18.0) 구식 룸 기반 UI/데이터층 제거 완료. → v1.19.0에서 공유는 `share/*`로 새로 설계·구현(RTDB 재도입).
- **RTDB 보안 규칙 (실행 전제)**: 공유 세션이 동작하려면 로그인 사용자에게 `/shareSessions` 읽기·쓰기 허용 규칙이 배포돼 있어야 함. 규칙이 잠겨 있으면 세션 생성/참여 실패. 프로젝트=`g1-sketchbook-default-rtdb`. 운영 배포 전 적절히 제한할 것.
