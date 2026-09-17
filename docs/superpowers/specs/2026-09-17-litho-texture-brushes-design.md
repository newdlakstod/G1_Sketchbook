# 판화 질감 브러시(리소 거친/젖은) 설계

## 배경 및 목적

사용자가 인스타그램에서 본 프로크리에이트용 "판화브러쉬 10종 set"(Grass, Brick, Check,
Dot, Litho rough70/30, Litho wet50/70, Etching, Etching plate)처럼 판화 느낌의 질감
브러시를 앱에 추가하고 싶어 한다. 10종을 한 번에 만들기엔 각각 렌더링 방식이 달라 범위가
크므로, 이번 스펙은 그중 **Litho Rough(거친 질감)와 Litho Wet(젖은 질감) 2종만** 먼저
다룬다 — 나머지 8종(Grass/Brick/Check/Dot/Etching류)은 이번 스펙 범위 밖이며, 이번에
정하는 구조(질감 타일 생성 + `BitmapShader` 스탬핑)가 앞으로 다른 질감 브러시를 추가할 때도
재사용될 기반이 된다.

지금 브러시 엔진(`BrushView.kt`)의 텍스처 브러시(연필/크레파스)는 붓이 지날 때마다 무작위
입자를 새로 뿌리는 방식이라, 매번 다른 느낌이 난다. 이번 두 브러시는 그것과 다르게 **캔버스
위 같은 자리를 두 번 칠하면 항상 같은 무늬가 겹쳐 보이는**, 리소그래피 판(고정된 질감판)에
찍는 듯한 느낌을 요구사항으로 확인했다.

## Rough와 Wet의 차이

- **Rough**: 사포처럼 알갱이가 또렷하게 튀는 고대비 입자 질감(블러 없는 순수 노이즈).
- **Wet**: 알갱이가 덜 또렷하고 뭉쳐서 잉크가 번진 것처럼 부드럽게 얼룩진 톤(같은 노이즈에
  블러를 먹인 버전).

원본 세트의 "70/30", "50/70" 같은 숫자(강도 변형)는 별도 UI를 만들지 않고, **기존
불투명도 슬라이더를 그대로 재사용**한다 — 같은 두 브러시를 불투명도만 다르게 써서 원하는
느낌을 낸다.

## 질감 타일 생성

- 붓마다 정사각형 질감 타일 비트맵을 하나씩 만든다: 크기 256×256px, `Bitmap.Config.ALPHA_8`
  (그레이스케일 1바이트/px라 메모리 가벼움 — 캔버스 전체 크기와 무관한 고정 크기).
- 생성 로직은 Bitmap과 무관한 순수 함수로 분리해 유닛테스트 가능하게 만든다:
  ```kotlin
  fun generateGrainTexture(size: Int, seed: Long, blurRadius: Int): ByteArray
  ```
  - `size × size` 크기의 `ByteArray`를 반환(픽셀당 0~255 밝기 1바이트).
  - `Random(seed)`로 픽셀마다 무작위 밝기를 채운 뒤, `blurRadius > 0`이면 분리형 박스 블러
    (가로 패스 → 세로 패스)를 적용하고, 블러로 낮아진 대비를 최소~최대값 기준으로 다시
    0~255 범위로 늘려 편다(그대로 두면 뭉개져서 거의 안 보이게 됨).
  - Rough: `blurRadius = 0`(블러 없음, 원본 노이즈 그대로).
  - Wet: `blurRadius = 8`(부드럽게 뭉친 얼룩).
  - `seed`는 `BrushView` 인스턴스가 만들어질 때 한 번 무작위로 뽑아 그 세션 동안 고정한다
    (앱을 다시 켜면 다른 무늬가 나와도 무방 — "같은 세션 내 같은 자리는 같은 무늬"가
    요구사항이지, 기기 간·재실행 간 동일함은 요구되지 않음).
- 이 두 타일(Rough용, Wet용)은 `BrushView`가 생성될 때 지연 생성(lazy)해 한 번만 만들고
  재사용한다. 캔버스 크기(`cw`/`ch`)나 페이지 전환과는 무관하므로 `initCanvas()`에서 다시
  만들 필요 없음.

## 그리기 파이프라인

- 현재 선택된 `color`로 타일을 입힌 컬러 버전(256×256, `ARGB_8888`)을 만들어 `BitmapShader`
  (`TileMode.REPEAT`, `TileMode.REPEAT`)로 감싸고, 그 셰이더를 건 `Paint`로 원/선을 그린다
  (`Paint().apply { shader = BitmapShader(coloredTile, REPEAT, REPEAT) }`, 이후
  `content.drawCircle(...)`/`drawLine(...)`처럼 사용). `BrushView`가 그리는 캔버스는 별도
  좌표 변환 없이 원본 픽셀 좌표를 그대로 쓰므로, 셰이더가 항상 캔버스 절대 좌표 기준으로
  타일링돼 "같은 자리 = 같은 무늬"가 자연히 만족된다.
- 컬러 버전은 `color`가 바뀔 때만(또는 브러시 타입이 바뀔 때만) 새로 만들어 캐싱한다 —
  256×256 크기라 매번 새로 만들어도 가볍지만, 굳이 매 스탬프마다 다시 만들 필요는 없음.
- 스탬프 방식은 연필/크레파스처럼 `content`에 직접 그린다(별도 실시간 합성 레이어
  `strokeLayer`/`composite()` 불필요 — 그건 PEN/WATER처럼 매 프레임 다시 그려야 하는
  경우에만 필요).
- 기존 확장 지점을 그대로 따른다:
  - `scaleFor()`: `LITHO_ROUGH -> 2f`, `LITHO_WET -> 2f`(크레파스와 동일 — 두 브러시는
    "같은 스탬프 크기, 질감만 다름"이 가장 단순하고 일관된 기본값).
  - `stampDispatch()`: `LITHO_ROUGH -> stampLithoRough(x, y, r)`,
    `LITHO_WET -> stampLithoWet(x, y, r)` 분기 추가(현재 `else -> {}`인 자리).
  - `strokeStart()`/`strokeMove()`의 `else -> stampDispatch(...)` 분기가 이미 새 타입도
    받아주므로 이 두 함수 자체는 수정 불필요.
  - `spacing`(간격) 계산은 기존 `else -> r * 0.20f`(PEN/PENCIL 기본값)를 그대로 물려받는다
    — 별도 분기 추가 안 함.
- 최근 고친 스트로크별 undo 되돌리기(부분 영역만 저장하는 방식, `markDirty`)에도 자연히
  편입되도록, 새 스탬프 함수 안에서 다른 스탬프들과 같은 패턴으로
  `markDirty(x, y, 알맞은 반경)`을 호출한다.

## 붓 크기 범위

`Dimens.Brush`에 `lithoRoughMinWidth`/`lithoRoughMaxWidth`,
`lithoWetMinWidth`/`lithoWetMaxWidth`를 추가하되, 값은 **크레파스(`crayonMinWidth`/
`crayonMaxWidth`)와 동일하게** 맞춘다(같은 "질감 스탬프 계열" 브러시로 간주, 나중에 실제
써보고 조정 가능).

## UI 통합

- `BrushType`에 `LITHO_ROUGH`, `LITHO_WET` 두 값 추가.
- 붓 고르는 줄(툴바 펼침 상태의 `Row`, 최소화 상태의 `MiniBrushPopup`)이 지금은 고정 폭
  `Row`라 4종+지우개로도 이미 꽉 차 있음 — 이번에 두 자리를 더 추가하면서 앞으로도 계속
  늘어날 걸 감안해 **두 곳 모두 `Modifier.horizontalScroll(rememberScrollState())`를 붙여
  가로 스크롤 가능하게** 바꾼다.
- 아이콘: 기존 4종은 직접 그려진 PNG(`drawable-nodpi/brush_*.png`)라 같은 화풍으로 맞추는
  건 이번 범위 밖. 대신 벡터 드로어블 XML을 새로 작성해 간단한 구분용 아이콘을 넣는다 —
  Rough는 점을 흩뿌린 원, Wet은 부드러운 얼룩(blob) 모양 원처럼 최소한의 형태만 구분되면
  충분(나중에 실제 그림으로 교체 가능). `currentToolIcon()`의 `when {}`과
  `MiniBrushPopup`의 브러시 목록 `listOf(...)`, `brushSizeRange()`, `scaleFor()` 등 기존
  `BrushType` 확장 지점 전부에 새 두 값을 추가한다.
- 라벨(설명 텍스트/contentDescription): 우선 "리소 거친" / "리소 젖은"으로 짧게 넣는다 —
  사용자가 나중에 원하는 이름으로 바꿀 수 있음(지금은 확정하지 않고 이 값으로 진행).

## 에러 처리

- 질감 타일 생성은 순수 메모리 연산이라 실패할 일이 거의 없다 — 다만 `Bitmap.createBitmap`
  실패(극단적 저메모리) 시 해당 브러시로 그리기가 조용히 무시되지 않도록, 타일이 없으면
  일반 단색 채우기(`Paint().apply { color = ... }`, 셰이더 없이)로 자연스럽게 대체한다.
- 색상 캐시는 `color` 값과 함께 저장해두고, 다음 호출 때 `color`가 같으면 재계산을 건너뛴다.

## 이번 스펙에서 다루지 않는 것

- 나머지 8종(Grass, Brick, Check, Dot, Etching, Etching plate) — 각각 다른 렌더링 기법이
  필요해 이후 별도 스펙에서 순차적으로 다룬다.
- 실제 스캔한 질감 사진(텍스처 이미지 에셋) 사용 — 이번엔 전부 코드로 절차적 생성.
- 강도(70/30, 50/70)를 위한 별도 슬라이더 — 기존 불투명도로 대체.
- 브러시 아이콘의 최종 그림 자산 — 이번엔 임시 벡터 아이콘만.
- 질감 타일이 기기 재시작·페이지 간에도 동일하게 유지되는 것 — 세션 내 일관성만 보장.

## 테스트

- `generateGrainTexture(size, seed, blurRadius)`는 Android 의존 없는 순수 함수라 유닛
  테스트 대상: 같은 `seed`로 두 번 호출하면 같은 결과(결정론적), `blurRadius=0`일 때 원본
  노이즈 그대로, `blurRadius>0`일 때 이웃 픽셀 간 분산이 원본보다 줄어드는지(블러 효과)
  확인.
- 나머지(Canvas/BitmapShader 렌더링, Compose UI)는 이 프로젝트 관례상 컴파일 확인 + 수동
  확인.
