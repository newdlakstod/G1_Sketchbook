# 벡터 캔버스 구조 개편 (페이지 → 단일 캔버스) 설계

## 배경 및 목적

현재 벡터 스케치북(`docs/superpowers/specs/2026-08-30-vector-sketchbook-design.md`로 구현·병합됨)은 기존 래스터 스케치북과 동일한 "페이지" 개념(`MAX_PAGES`개 페이지, 화살표로 넘김, 고정 정사각형 1024×1024)을 그대로 재사용하고 있다.

하지만 벡터 캔버스의 원래 목적은 "아이콘처럼 확대해도 깨지지 않는 그림"이다. 여러 페이지를 순서대로 넘기는 다이어리형 구조보다는, 그림 한 장을 자유롭게 확대·이동하며 그리는 구조가 이 목적에 더 맞는다. 이 스펙은 벡터 스케치북을 **"책 한 권 = 캔버스 한 장"**으로 바꾸고, 그 캔버스를 무한 또는 사용자 지정 크기 중 선택할 수 있게 한다.

이 스펙은 원래 스펙의 다음 결정을 **대체(supersede)**한다:
- ~~캔버스: 고정 정사각형 비율, 사이즈 선택 없음~~ → 생성 시 무한/커스텀 선택
- ~~페이지 여러 장, 화살표로 넘김~~ → 페이지 없음, 캔버스 한 장

이번 스펙은 캔버스 구조 자체만 다룬다. 브러시 굵기 슬라이더, 실시간 스무딩, 선 선택 후 굵기 조절, 폐곡선 면칠하기는 이 구조 위에서 동작하는 별도 스펙(다음 브레인스토밍)에서 다룬다 — 이 스펙에서는 다루지 않는다.

## 기존 테스트 데이터

벡터 스케치북은 아직 배포 전(에뮬레이터 테스트만 진행) 상태라, 기존에 만든 테스트용 벡터 스케치북의 페이지 데이터는 마이그레이션하지 않는다. 이 변경 이후 만든 벡터 스케치북부터 새 구조를 따른다.

## 데이터 모델

### `Sketchbook` (`SketchbookRepository.kt`)

- `vectorInfinite: Boolean = false` — 무한 캔버스 여부.
- `vectorCanvasW: Int? = null`, `vectorCanvasH: Int? = null` — 커스텀 크기일 때(=`vectorInfinite == false`)의 논리 좌표 가로·세로. 무한 캔버스면 둘 다 null.
- 기존 `vector: Boolean`은 그대로 유지(벡터 스케치북 여부 판별용).
- `Catalog.sizes`의 `"vector"`(1024×1024 고정) 항목은 제거 — 벡터 책은 더 이상 `sizeKey`로 크기를 표현하지 않는다.

### 페이지 → 단일 캔버스

- `VectorPage`/`vectorPageFromJson`/`toJson`은 그대로 재사용하되(획 목록 직렬화 포맷 자체는 안 바뀜), **책 하나당 인스턴스 하나**로 바뀐다 — 더 이상 페이지 인덱스가 없다.
- `SketchbookRepository`: `loadVectorPage(bookId, index)`/`saveVectorPage(bookId, index, page)` → `loadVectorCanvas(bookId)`/`saveVectorCanvas(bookId, page)`로 이름·시그니처 변경(인덱스 파라미터 제거). 로컬 저장 파일 경로도 페이지 인덱스가 빠진 형태로 바뀐다.
- `vectorPageUpdatedAt`/`setVectorPageUpdatedAt`도 인덱스 없이 책 단위로.
- `MAX_PAGES` 상수는 벡터 책에는 더 이상 적용되지 않음(래스터 책엔 계속 적용).

### Firebase 동기화 (`BackupModels.kt`, `BackupRepository.kt`, `BackupSync.kt`)

- `RemoteSketchbook.vectorPages: Map<Int, Pair<Long, String>>` → `vectorCanvas: Pair<Long, String>?`(updatedAt, strokes json) 단일 필드로 축소.
- `pushVectorPage(...)` → `pushVectorCanvas(uid, bookId, page)`로 이름 변경, 인덱스 파라미터 제거.
- `reconcileSketchbooks`의 벡터 분기도 단일 필드 기준으로 단순화.
- `vectorInfinite`/`vectorCanvasW`/`vectorCanvasH`도 스케치북 메타 동기화 필드에 추가.

## 생성 마법사 (`SketchbookScreens.kt`)

현재 `WType.VECTOR` 단계는 이름만 입력받는 단일 다이얼로그(`"벡터 스케치북 이름"`, `finishVector()`가 `sizeKey="vector"` 고정으로 생성). 여기에 캔버스 타입 선택 단계를 추가한다:

1. 이름 입력 (기존과 동일)
2. 캔버스 타입 선택:
   - **무한**: 버튼 하나로 선택, 추가 입력 없음.
   - **커스텀**: 프리셋 비율 버튼(1:1, 4:3 등 자주 쓰는 것 몇 개) + 가로×세로 직접 입력 필드. 프리셋을 누르면 그 비율의 기본 해상도가 입력란에 채워지고, 사용자가 값을 직접 고칠 수 있다.
3. "만들기" → `finishVector()`가 선택된 타입/크기를 `Sketchbook`에 실어 생성.

프리셋 비율과 그 기본 해상도(예: 1:1 → 1024×1024, 4:3 → 1024×768)는 계획 단계에서 구체적인 목록으로 확정한다.

## 캔버스 뷰 (`VectorBrushView.kt`, `VectorCanvasScreen.kt`)

### 좌표계

- 커스텀 크기 책: 논리 좌표 범위가 `vectorCanvasW × vectorCanvasH`로 고정.
- 무한 책: 논리 좌표에 경계가 없음 — 내부적으로는 충분히 큰 논리 좌표 공간(예: 최초 뷰포트를 중심으로 자유롭게 확장) 위에 그린다.

### 팬/줌

- 한 손가락: 그리기(기존과 동일, 지우개 모드일 때는 탭으로 획 삭제).
- 두 손가락: 핀치로 확대/축소, 패닝으로 이동. `VectorBrushView`에 확대 배율(zoom)과 이동 오프셋(pan) 상태를 추가하고, `onDraw`의 `canvas.scale`/`canvas.translate`와 터치 좌표를 논리 좌표로 변환하는 `scale()` 계산 둘 다 이 상태를 반영하도록 확장한다.
- 최소/최대 확대 배율, 초기 확대 배율(예: 커스텀 캔버스는 전체가 보이도록 fit, 무한은 100%)은 계획 단계에서 확정.

### 페이지 넘김 UI 제거

- `VectorCanvasScreen.kt`의 상단 ◀▶ 페이지 이동 버튼, `page`/`goTo()`/`startPage` 파라미터 전부 제거.
- `saveVectorPageSynced` → `saveVectorCanvasSynced`로 이름 변경(인덱스 제거), 호출부(`onStrokeEnd`, `onBack`) 갱신.
- `MainScreen.kt`에서 벡터 책을 열 때 넘기던 `startPage` 인자도 제거.

## 도구 모드

기존 지우개 토글에 더해, 새 도구 모드 두 개를 추가한다(둘 다 라쏘 기반 선택):

- **내보내기용 라쏘**: 영역을 올가미로 지정하면 그 영역만 SVG로 저장(`saveSvgToGallery` 재사용). "전체 내보내기" 버튼도 별도로 유지 — 무한 캔버스는 그려진 내용 경계상자, 커스텀 캔버스는 캔버스 전체.
- **굵기 조절용 라쏘**: 이번 스펙(캔버스 구조)에는 도구 모드 자리만 마련하고, 실제 동작은 다음 스펙(편집 기능)에서 구현한다.

라쏘 히트테스트는 기존 지우개의 점-다각형 판정(`StrokeGeometry.kt`)과 같은 방식을 확장해서 재사용한다 — 올가미 다각형과 획의 각 점이 겹치는지, 또는 획 전체가 올가미 안에 들어오는지로 판정(정확한 판정 기준은 계획 단계에서 확정).

## 표지(썸네일) 생성

스케치북 목록 카드의 표지 이미지는 사용자가 매번 고르지 않고 자동 생성한다 — **그려진 내용의 경계상자**(모든 획을 감싸는 최소 사각형, 여백 약간 포함)를 기준으로 렌더링. 캔버스 타입(무한/커스텀)과 무관하게 동일한 규칙. 아직 아무것도 안 그린 빈 캔버스는 고정 기본값(예: 빈 사각형 플레이스홀더)으로 폴백.

## 이번 스펙에서 다루지 않는 것

- 브러시 굵기 슬라이더, 실시간 스무딩, 선 선택 후 굵기 조절, 폐곡선 면칠하기 — 다음 스펙.
- 무한 캔버스의 메모리/성능 상한(예: 그림이 매우 커졌을 때의 처리) — 계획 단계에서 필요하면 실용적인 상한을 두되, 이번 스펙 범위 밖.

## 테스트

- `VectorPageTest`, `VectorSvgExportTest`는 페이지 인덱스 제거에 맞춰 시그니처만 조정, 로직은 그대로 유지.
- `decideSyncAction` 등 순수 로직 유닛 테스트는 `vectorCanvas` 단일 필드 기준으로 갱신.
- 팬/줌 좌표 변환처럼 Android View에 의존하는 부분은 이 프로젝트 관례상(로컬 유닛 테스트가 Android 스텁을 못 씀) 유닛 테스트 대상이 아니다 — 에뮬레이터에서 수동 확인.
