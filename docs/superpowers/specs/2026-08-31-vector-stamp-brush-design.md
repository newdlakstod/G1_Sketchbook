# 벡터 스탬프/패턴 브러시 설계

## 배경 및 목적

벡터 캔버스에는 이미 "브러시 스와치 패널"(`VectorCanvasScreen.kt`의 `BrushProfile`/`BrushProfiles`)이 뼈대만 만들어져 있다 — 지금은 "기본"(지금 펜) 하나뿐이고, 골라도 아무것도 안 바뀐다. 사용자가 새 브러시(예: 캘리그래픽)를 말로 설명하면 개발자가 코드로 구현해주는 방식을 시도했으나 "정확히 원하는 대로 안 나온다"는 피드백을 받았다. 이 스펙은 사용자가 **직접 만든 SVG 모양**을 임포트해서, 그 모양을 선을 따라 도장 찍듯 반복하는 "스탬프 브러시"로 등록해 쓸 수 있게 한다 — 브러시의 생김새 자체를 사용자가 완전히 통제한다.

이 스펙은 기존 "브러시 스와치 패널" 뼈대를 실제로 채우는 첫 확장이다. 다른 브러시 타입(예: 캘리그래픽 각도 펜)은 이 스펙 범위 밖 — 나중에 같은 `BrushProfiles` 목록에 새 항목으로 추가될 수 있다.

## 데이터 모델

### `VectorStroke`에 필드 하나 추가

```kotlin
data class VectorStroke(
    val color: Long,
    val points: List<VectorPoint>,
    val cap: VectorCap = VectorCap.BUTT,
    val fillEnabled: Boolean = true,
    val strokeColor: Long? = null,
    val strokeWidthPx: Float = 2f,
    /** null이면 지금 펜(리본 모양, cap/fillEnabled/strokeColor 등 그대로 적용). 아니면 그 id의
     *  스탬프 브러시로 그려진 획 — 이때는 cap/fillEnabled/strokeColor/strokeWidthPx는 무시하고
     *  [points]를 중심선 삼아 스탬프를 반복해서 채운다(전부 [color]로 틴트). */
    val brushProfileId: String? = null,
)
```

`points`(중심선)는 그대로 저장 — 라쏘로 선택해서 이동·크기조절하는 기존 기능이 스탬프 획에도 동일하게 작동한다(스탬프는 저장된 좌표가 아니라 매번 렌더링 시점에 중심선을 기준으로 다시 계산해서 그리기 때문). 획 하나는 지금 펜이거나 스탬프거나 둘 중 하나 — 섞이지 않는다.

로컬 JSON 직렬화(`VectorPage.toJson`/`vectorPageFromJson`)에 `brushProfileId`를 옵션 필드로 추가 — 없는(예전) JSON은 `null`(지금 펜)로 읽는다. 참조하는 스탬프 브러시가 나중에 삭제된 경우, 로드 시점에 해당 id를 못 찾으면 지금 펜(리본, 획 자신의 `color`)으로 폴백해서 그린다 — 그림이 사라지지 않는다.

### 스탬프 브러시 프로필

```kotlin
data class StampBrushProfile(
    val id: String,
    val name: String,
    /** 파싱된 스탬프 모양 — sub-shape(원래 SVG의 path/rect/circle/ellipse 하나하나)마다 채워진
     *  다각형 점 목록 하나. 좌표는 스탬프 자체의 로컬 좌표계(중심이 원점, 한 변 길이가 1이 되도록
     *  정규화)로 미리 변환해 둔다 — 찍을 때는 이 정규화 좌표에 [sizePx]만큼 스케일 + 중심선 위
     *  각 지점의 각도만큼 회전 + 그 지점 위치로 평행이동만 하면 된다. */
    val shapes: List<List<Point>>,
    val spacingPx: Float = 24f,
    val sizePx: Float = 32f,
)
```

`BrushProfiles`(지금은 `listOf(BrushProfile("basic", "기본"))` 하드코딩)는 "기본" 고정 항목 + 사용자가 임포트한 `StampBrushProfile` 목록을 합친 형태로 바뀐다.

## SVG 임포트 및 파싱

### 지원 범위

- `<path d="...">` — SVG path mini-language 전체(M/L/H/V/C/S/Q/T/A/Z, 절대/상대 모두). 곡선(C/S/Q/T/A)은 일정 허용오차로 잘게 쪼갠 직선 여러 개로 근사해서, 이 프로젝트가 이미 쓰는 "채워진 다각형" 표현(`Point` 목록)으로 통일한다.
- `<rect x y width height rx ry>`, `<circle cx cy r>`, `<ellipse cx cy rx ry>` — 각각 다각형으로 직접 변환(둥근 모서리/원도 잘게 쪼갠 다각형으로 근사).
- `<g>` 그룹 — 자식 도형들을 하나의 스탬프 프로필로 묶는 용도. `transform` 중 `translate`/`scale`만 지원, `rotate`는 지원하지 않는다(그룹에 회전이 걸려 있으면 그 그룹은 무시하고 나머지만 파싱 — 조용히 틀어지지 않게 에러가 아니라 건너뛰기로 처리).
- 그 외(그라디언트, 텍스트, 클리핑패스, 애니메이션, 다른 `transform` 종류 등)는 전부 무시.
- 색상(`fill`/`stroke` 속성)은 파싱하지 않는다 — 어차피 찍을 때 전부 펜 색으로 틴트하므로 필요 없다.

### 파일 선택

Android Storage Access Framework(`ActivityResultContracts.GetContent()`, MIME `"image/svg+xml"`)로 기기에서 `.svg` 파일을 고른다. 고른 파일의 텍스트를 즉시 파싱 — 실패(지원 안 하는 형식, 손상된 파일 등)하면 토스트로 안내하고 임포트를 취소한다(부분적으로 잘못 파싱된 브러시를 저장하지 않음).

### 정규화

파싱된 모든 다각형의 경계상자를 계산해서, 그 경계상자의 중심이 원점(0,0)에 오고 가장 긴 변이 길이 1이 되도록 좌표를 스케일+평행이동한다. 이렇게 정규화해 두면 찍을 때는 `sizePx`만 곱하면 된다.

## 찍기(스탬핑) 알고리즘

한 획을 그릴 때: 저장된 중심선 `points`를 따라 호 길이 기준으로 `spacingPx`마다 한 번씩 스탬프를 찍는다. 각 지점에서:
1. 그 지점 전후 점으로 진행 방향(접선) 각도를 구한다(첫/끝점은 인접한 한쪽만 사용 — 기존 `strokeOutline`의 방향 계산과 같은 방식).
2. 정규화된 스탬프 다각형들을 그 각도만큼 회전 → `sizePx`만큼 스케일 → 그 지점 좌표로 평행이동.
3. 변환된 다각형들을 전부 `color`(획의 펜 색)로 채워서 그린다.

간격·크기 둘 다 고정값(그리는 속도와 무관) — 브러시 프로필 자체에 저장된 `spacingPx`/`sizePx`를 쓴다. 획 하나 안에서는 모든 스탬프가 같은 크기.

`drawVectorPage`(공용 렌더 함수, 캔버스 화면·미리보기·둘 다 씀)가 `stroke.brushProfileId`를 보고 분기: null이면 지금처럼 `strokeOutline`+채움/테두리, 아니면 위 스탬핑 알고리즘으로 그린다.

## SVG 내보내기

`vectorPageToSvg`도 같은 분기 — 스탬프 획은 찍힌 도장 하나하나를 각각 독립된 `<path>` 요소로 풀어서 쓴다(공용 `<pattern>`/`<use>` 없이, 지금 있는 단순한 문자열 조립 방식 그대로 확장). 내보낸 SVG 파일 자체는 벡터 프로그램에서 열어도 그냥 일반 경로들의 모음으로 보인다.

## 브러시 스와치 패널 — 임포트/관리 UI

기존 스와치 패널(현재 "기본" 하나만 있는 `DropdownMenu`)에:
- **"+"(추가) 항목**: 탭하면 파일 선택기가 뜨고, SVG를 고르면 파싱 → 이름 입력 다이얼로그(기본값 "브러시 N") → 저장.
- **각 스탬프 스와치**: 탭하면 선택(그 브러시로 그리기 시작), 길게 누르면 편집 팝업(이름 바꾸기, 간격·크기 슬라이더, 삭제 버튼) — 새로 만든 이 팝업은 지금 있는 "브러시 설정"(펜 기준 굵기 등)이나 "획" 다이얼로그와 같은 스타일로.
- 스와치 자체의 미리보기는 정규화된 스탬프 모양 하나를 작게 그려서 보여준다(브러시 스와치 미리보기 함수를 "기본"용 손그림 곡선 대신 실제 파싱된 다각형으로 그리도록 확장).

## 저장 및 동기화

### 로컬

`filesDir/vector_brushes/{id}.json`에 프로필 하나당 파일 하나 — `name`, `spacingPx`, `sizePx`, 파싱된 `shapes`(다각형 점 목록)를 JSON으로. 목록(이름/순서)은 `SharedPreferences`에 id 목록만 저장 — 기존 `SketchbookRepository`/`SessionStore`가 쓰는 "로컬 파일 + 목록은 prefs" 패턴 그대로.

### 구글 계정 백업

`RemoteSettings`에 스탬프 브러시 목록 필드 추가(각 항목: id, name, spacingPx, sizePx, 원본 SVG 텍스트 — 파싱은 각 기기에서 받은 뒤 다시 수행, 파싱된 다각형 자체를 큰 배열로 안 올리고 원본 SVG 텍스트만 올려서 페이로드를 가볍게 유지). 기존 설정 동기화(`syncSettingsUp`/`applyRemoteSettings`)와 같은 흐름 — 마지막 수정 시각 기준 last-write-wins은 아니고(목록이라 병합이 필요), 다른 기기에 없는 항목은 추가, 이 기기에서 지운 항목은 다음 동기화 때 원격에서도 지운다(툼스톤 방식, 기존 스케치북 삭제와 같은 패턴).

## 이번 스펙에서 다루지 않는 것

- 캘리그래픽 등 SVG가 아닌 다른 새 브러시 타입.
- 스탬프 색을 SVG 원본 색 그대로 쓰는 옵션(항상 펜 색 틴트).
- 그리는 도중 속도에 따라 스탬프 크기/간격이 변하는 것(항상 고정값).
- 그룹 회전(`transform="rotate(...)"`) 지원.
- 스탬프 브러시로 그린 획을 다시 지금 펜(리본)으로 바꾸는 변환 기능.

## 테스트

- SVG path-data 파서(곡선 근사 포함), rect/circle/ellipse → 다각형 변환, 정규화 계산은 순수 Kotlin이라 유닛 테스트 대상(새 파일, 예: `SvgPathParser.kt`+테스트).
- 스탬핑 알고리즘(중심선 따라 호 길이 간격 계산, 회전 각도 계산)도 순수 Kotlin으로 만들어서 유닛 테스트.
- 파일 선택기 연동, Compose UI, Firebase 동기화는 이 프로젝트 관례상(Android/Firebase 의존) 유닛 테스트 대상이 아님 — 컴파일 확인 + 에뮬레이터 수동 확인.
