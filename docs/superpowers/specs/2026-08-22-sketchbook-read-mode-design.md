# 스케치북 읽기모드 + 책장 넘기기(페이지 컬) — 설계

## 배경

개인 스케치북 편집 화면의 "페이지" 패널(`PagePanel.kt`)에 읽기모드 버튼을 추가한다. 읽기모드는
그림 툴바 없이 완성된 스케치북을 실제 종이책처럼 넘겨보는 전체화면 뷰어다. 세로 모드에서는 한
페이지씩, 가로 모드에서는 책을 펼친 것처럼 2페이지 스프레드로 보여준다(표지-1, 2-3, 4-5, ...,
14-15).

책장 넘기는 효과는 사용자가 미리 만들어둔 검증용 프로젝트
`C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo`를 참고·이식한다. 이 프로젝트는 OpenGL ES
3.0(GLSurfaceView + 커스텀 셰이더)로 실제 종이가 원통형으로 말리는 3D 컬 효과를 구현한 독립
검증물이며, 자체 README에 "페이지 스택·그림 캔버스 연결·텍스처 공급은 미리 만들지 않았다"고
명시돼 있어 실제 앱에는 이 세 가지를 새로 붙여야 한다.

## 확정된 결정 (사용자 확인)

- **구현 수준**: PageCurlDemo의 OpenGL 컬 이식(가벼운 Compose 의사-3D 아님) — 시각적으로 데모와
  동일한 실제 종이 컬 효과를 가져간다.
- **가로 스프레드 넘김 방식**: 실제 책처럼 오른쪽 페이지 한 장만 컬 — 예: [2-3]에서 다음으로
  넘기면 오른쪽 페이지(3)만 종이처럼 휘어 넘어가고, 그 자리에 다음 스프레드의 왼쪽 페이지(4)가
  즉시 나타나며 오른쪽엔 5가 나타나 [4-5]가 된다. 왼쪽 페이지는 넘김 애니메이션 없이 바뀐다.
- **시작 페이지**: 편집 중이던 페이지에서 읽기모드 진입(가로모드면 그 페이지가 속한 스프레드로).
- **적용 범위**: 개인 스케치북만. 공유 스케치북은 페이지 개념 자체가 다르므로 제외.
- **엔진 재사용성**: `PageCurlDemo`의 컬 수학(`CurlGeometry`/`PageMesh`/`PageCamera`)은 이미
  페이지 폭·높이를 파라미터로 받는 범용 구조 — 데모의 `PageCurlRenderer`만 데모용으로 고정 상수
  (`page_1.jpg`/`page_2.jpg`, 고정 크기 2×8/3)를 썼을 뿐, 엔진 자체는 세로 1페이지든 가로 스프레드의
  오른쪽 절반이든 그대로 재사용 가능. 새로 만들 것은 렌더러의 텍스처 공급 방식과 스프레드 레이아웃
  뿐이다.

## 아키텍처

### 새 패키지 `com.g1.sketchbook.readmode`

`PageCurlDemo`에서 앱 의존성이 없는 순수 수학/GL 코드를 그대로(또는 거의 그대로) 복사:

- `curl/math/Vec2.kt`, `Vec3.kt`, `MathUtils.kt`
- `curl/CurlGeometry.kt`, `PageCamera.kt`, `PageMesh.kt`, `CurlAnimator.kt`, `CurlState.kt`,
  `ShadowStrip.kt`, `ShaderSources.kt`
- `input/DragInterpreter.kt`

새로 작성/개조:

- **`readmode/ReadModeRenderer.kt`** (`PageCurlRenderer` 개조): 고정 에셋 2장 대신, 외부에서 주입되는
  `PageTextureProvider`(아래)를 통해 현재 넘어가는 페이지(front)·다음 페이지(next)·(가로모드일 때)
  정적 왼쪽 페이지까지 런타임에 텍스처로 업로드. `TextureLoader.loadAsset()`과 같은 방식이지만
  `Bitmap`을 직접 받는 `TextureLoader.loadBitmap(bitmap: Bitmap): Int` 버전 추가. 가로 스프레드일 때는
  왼쪽 페이지용 정적 평면 메시를 하나 더 그린다(컬 없음, `nextPageMesh`와 같은 static GPU 버퍼 패턴
  재사용). "뒷면" 텍스처(컬 도는 낱장의 뒷면)는 이 앱 페이지가 실물 양면 인쇄물이 아니므로, 스케치북
  배경지(`book.bgKey`) 색/텍스처를 그대로 사용.
- **`readmode/ReadModeSurface.kt`** (`PageCurlSurface` 개조): GLSurfaceView + 터치 처리는 거의 동일.
  `DragInterpreter`는 그대로 재사용. 컬이 완료되면(`onDragEnd(complete=true)`) 상위(Compose)에
  "다음 스프레드로 넘어감"을 콜백으로 알려 페이지 인덱스 갱신 + 다음 텍스처 세트를 새로 공급받는다.
- **`readmode/PageTextureProvider.kt`**: `SketchbookRepository.loadPage(bookId, index)`를
  IO 스레드에서 불러와 읽기모드 전용으로 축소(최대 변 1600px — 원본은 최대 3308px라 그대로 텍스처로
  올리면 프레임 드랍 위험)한 `Bitmap`을 반환하는 얇은 래퍼. 다음 스프레드용 페이지를 미리
  1개 앞서 프리페치.
- **`readmode/ReadSpreads.kt`**: 스프레드 계산 순수 함수 —
  - 세로: `spreads[i] = listOf(i)` (페이지 i장씩).
  - 가로: `spreads[0] = listOf(COVER, 0)`, `spreads[n] = listOf(2n-1, 2n)` (n≥1) — "표지-1,
    2-3, ..., 14-15"(1-indexed 표기, 코드는 0-indexed) 매핑. 15페이지 고정이므로 표지 스프레드 1개 +
    7개 스프레드 = 총 8스프레드.
  - `pageIndexToSpread(page, landscape): Int` — 편집 중이던 페이지가 속한 스프레드를 찾아 시작
    스프레드로 사용.
- **`readmode/ReadModeScreen.kt`** (신규 Compose 진입점): 전체화면, 툴바 없음. `LocalConfiguration`로
  세로/가로 판정(기존 `MainTabLayout.kt`의 `landscape` 판정과 동일 패턴). GLES 3.0 미지원 기기 대비:
  `PackageManager.hasSystemFeature(FEATURE_OPENGLES_EXTENSION_PACK)` 류 체크 대신 데모와 동일하게
  `ActivityManager.deviceConfigurationInfo.reqGlEsVersion` 확인 — 미지원이면 애니메이션 없는 단순
  좌우 스와이프 넘김(`HorizontalPager` 등 기존 Compose 컴포저블)으로 대체(크래시 방지, 최소 기능 보장).
  뒤로가기/닫기 버튼으로 나가면 마지막으로 보고 있던 페이지 인덱스를 편집 화면에 돌려준다.

### 기존 코드 연결 지점

- **`sketchbook/PagePanel.kt`**: 하단 취소/완료 버튼 행 위(또는 옆)에 "읽기모드" 버튼 추가,
  `onReadMode: () -> Unit` 콜백 신설.
- **`sketchbook/SketchbookScreens.kt`**(`CanvasScreen`, 942번째 줄 부근): `readModeOpen` 상태 추가,
  `PagePanel(..., onReadMode = { readModeOpen = true })`. `readModeOpen`이면 `ReadModeScreen`을
  전체화면으로 띄우고, 닫히면 그 스프레드에 대응하는 페이지로 `goTo()`.

## 위험 요소 / 미리 밝혀두는 한계

- **실기기 필요**: GLSurfaceView 드래그·셰이더 렌더링은 Compose Preview/에뮬레이터로 의미 있게
  검증할 수 없다. 컴파일 검증까지만 하고 "실기기 확인 필요"로 명확히 남긴다.
- **성능**: 페이지 전환마다 텍스처 재업로드가 필요 — 축소 비트맵(최대 1600px) + 다음 스프레드
  프리페치로 완화하지만, 실기기에서 실제 버벅임 여부는 확인 전까지 모른다.
- **단계적 구현**: 규모가 커서 (1) 순수 수학/GL 코드 이식 + 컴파일 확인 → (2) 세로모드 1페이지 컬
  엔드투엔드 연결 → (3) 가로모드 2페이지 스프레드(오른쪽만 컬) 추가 → (4) GLES3 미지원 폴백 →
  (5) PagePanel 진입점 배선 순서로 진행하고 각 단계마다 `compileDebugKotlin`으로 검증한다.
- **범위 밖**: 공유 스케치북, 페이지 추가/삭제(기존처럼 고정 15페이지 유지), 컬 그림자 강도 등 세부
  튜닝은 1차 구현 이후 실기기 피드백을 보고 조정.
