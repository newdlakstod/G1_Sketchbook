# 벡터(SVG) 스케치북 — 설계

## 배경

지금 앱은 전부 래스터(픽셀) 기반이다. `BrushView`가 `Canvas`/`Bitmap`에 직접 그리고, 수채화·연필·크레용 브러시는 질감·입자 효과가 전부 래스터 스탬프라 벡터로 자연스럽게 옮겨지지 않는다. 저장은 PNG 파일, 백업 동기화도 base64 PNG/JPEG 스냅샷이다.

사용자가 "아이콘처럼 확대해도 안 깨지는 그림"을 원해서(다른 프로그램 편집·출력용이 아니라 해상도 독립성이 목적), 기존 그림을 SVG로 내보내는 게 아니라 **처음부터 벡터로 그리는 새 스케치북 타입**을 추가한다.

## 확정된 결정 (사용자 확인)

- **용도**: 확대해도 안 깨지는 아이콘용 그림. 다른 프로그램 편집이나 출력(레이저 커팅 등) 목적 아님.
- **브러시**: 펜 하나만, 그리는 속도에 따라 굵기가 변한다(기존 래스터 펜의 속도-굵기 로직과 같은 느낌). 수채화·연필·크레용 같은 질감 브러시, 도형/채우기 도구는 없음.
- **위치**: 완전히 새 화면이 아니라 기존 `SketchbookRepository`의 새 타입으로 — 목록/페이지 넘기기/백업 동기화 틀을 그대로 재사용.
- **동기화**: 기존 개인 스케치북과 동일하게 구글 계정으로 기기 간 자동 동기화.
- **색상**: 기존 브러시 색상 팔레트·즐겨찾기 그대로 재사용.
- **내보내기**: 완성된 그림을 `.svg` 파일로 갤러리(공유 시트)에 저장 — 기존 "이미지로 저장" 메뉴에 옵션 추가.
- **지우개**: 픽셀 단위가 아니라 획(스트로크) 단위 — 탭하면 그 획 전체가 삭제된다.
- **캔버스 비율**: 정사각 고정(사이즈 선택 단계 없음).

## 아키텍처

기존 `BrushView`(Bitmap 기반)와 완전히 분리된 새 커스텀 View `VectorBrushView`를 만든다. `com.g1.sketchbook.vector` 패키지에 둔다.

`Sketchbook` 데이터 클래스에 `shared`와 같은 층위의 새 필드 `vector: Boolean = false`를 추가한다. `vector`와 `shared`를 동시에 켜는 조합(벡터+공유 협업)은 이번 스코프에 없음 — 생성 마법사에서 애초에 그 조합을 만들 수 없게 한다.

목록 화면(`SketchbookListScreen`)에서 벡터 스케치북은 기존 "개인" 목록에 함께 섞여 보인다(공유처럼 별도 상단 토글을 새로 만들지 않음) — 표지에 작은 배지(예: 펜 아이콘)로 구분한다. 새로 만들기 마법사(`CreateWizard`)에 `WType.VECTOR`를 추가: 이름만 입력하는 화면 하나로 끝(사이즈·배경 선택 단계 없음, 이번 세션에 이미 통일한 `PersonalCreateCard` 스타일의 다이얼로그 사용). 캔버스 크기는 `Catalog.sizes`에 정사각 항목(`CanvasSize("vector", "벡터", 1024, 1024)`)을 추가해 `vector` 타입 생성 시 `sizeKey`를 이 값으로 고정한다.

**읽기모드(페이지 넘기기 GL 애니메이션) 미지원** — 종이 넘김 시뮬레이션은 아이콘 그림과 안 맞는다. 벡터 스케치북은 3열 페이지 목록/편집화면 전환만 지원하고, `onReadMode` 진입점 자체를 안 띄운다.

## 데이터 모델 & 저장

페이지 하나 = 획(stroke) 목록의 JSON. 획 하나 = `{ color: Long, points: [{x, y, w}, ...] }` (`w`는 그 지점의 선 굵기, 그릴 때 속도로 계산해 점마다 같이 저장). 예:

```json
{
  "strokes": [
    { "color": -13421773, "points": [{"x":10.2,"y":30.1,"w":3.0}, {"x":12.5,"y":31.0,"w":3.4}, ...] }
  ]
}
```

`SketchbookRepository`에 벡터 전용 페이지 read/write를 추가한다 — 같은 `sketchbooks/{id}/` 폴더 안에 `page_{i}.png` 대신 `page_{i}.json`으로 저장해서 기존 파일 관리(`applyPageOrder` 등) 구조를 그대로 쓴다:

- `loadVectorPage(id, index): VectorPage?` / `saveVectorPage(id, index, page: VectorPage)`
- `vectorPageUpdatedAt(id, index)` / `setVectorPageUpdatedAt(...)` — 기존 `pageUpdatedAt`와 동일하게 파일 mtime 사용(백업 동기화 last-write-wins 비교용).

표지(커스텀 사진/색상)는 기존 `coverFile`/`saveCover` 메커니즘을 그대로 쓴다 — 벡터냐 아니냐와 무관한 독립 기능이라 변경 없음.

## 그리기 & 렌더링

한 획은 "중심선 굵기가 변하는 리본" 모양의 채워진 `android.graphics.Path`로 그린다 — 각 점에서 진행 방향에 수직으로 굵기/2만큼 오프셋한 두 경계선(위/아래)을 이어 닫힌 다각형을 만드는 방식(획 하나 = 채워진 다각형 하나, `stroke-width` 아님). 그리는 동안은 이 Path를 `VectorBrushView`가 실시간으로 Canvas에 그린다.

**썸네일**은 같은 Path 데이터로 작은 비트맵 하나를 렌더링해서 쓴다 — SVG를 파싱해서 그리는 게 아니라, 저장된 JSON(점 목록)에서 바로 Path를 복원해 그리므로 SVG 파서가 전혀 필요 없다.

**SVG 내보내기**는 "이미지로 저장" 메뉴에서 누른 시점에만, 그 순간의 획 목록을 순회하며 각 획을 `<path d="..." fill="#색상"/>` 하나로 직렬화해 SVG 텍스트를 만든다(획 하나 = path 하나, 겹치는 획끼리 z-order는 그린 순서를 그대로 따름).

**지우개(획 단위 삭제)**: 탭 좌표가 어떤 획의 채워진 다각형 내부에 들어가는지 `android.graphics.Region.setPath(path, clip)` + `Region.contains(x, y)`로 판정한다(`BrushView`의 라소 선택 판정과 같은 방식) — 여러 획이 겹치면 가장 나중에 그린(위에 있는) 획을 지운다.

**되돌리기(undo)**: 마지막 획을 스택에서 pop — 페이지당 획 목록 전체를 스냅샷하는 기존 라소보드 undo 방식보다 가벼워서, 획 단위 스택으로 충분하다.

## 백업 동기화

기존 스케치북 페이지 동기화(`BackupRepository.pushSketchbookPage`/`pullAll`)는 Bitmap 전용이다. 벡터 페이지는 이미 텍스트(JSON)라 base64 인코딩 없이 그대로 문자열로 올린다 — 이미지보다 훨씬 가볍다.

- `pushVectorPage(uid, bookId, index, strokesJson: String, updatedAt)` — RTDB에 `sketchbooks/{id}/vectorPages/{index}: { updatedAt, strokes: "<json 문자열>" }`로 저장(기존 `pages/{index}`와 분리된 별도 키 — 스케치북 하나가 `vector=true`면 항상 `vectorPages`만 쓰고 `pages`는 비어 있음).
- `pullAll`이 읽어오는 `RemoteSketchbook`에 `vectorPages: Map<Int, Pair<Long, String>>` 필드를 추가.
- `BackupSync.reconcileSketchbooks`의 페이지 동기화 루프에서 `book.vector` 여부로 분기해 `pages`/`vectorPages` 중 하나만 처리한다.

메타(`sketchbooks/{id}/meta`)에도 `vector: Boolean` 필드를 추가해서 다른 기기가 pull할 때 `Sketchbook.vector`를 정확히 복원한다.

## 에러 처리

- JSON 파싱 실패(손상된 파일, 다른 기기의 이전 포맷과 안 맞는 경우 등)는 빈 페이지로 처리하고 조용히 넘어간다 — 기존 코드가 손상된 PNG를 만나도 그냥 `null` 반환하고 빈 캔버스로 시작하는 것과 같은 태도.
- SVG 내보내기 자체는 실패할 일이 거의 없다(단순 문자열 조립) — 실패하면 기존 PNG 저장과 같은 Toast 에러 문구.

## 테스트

- 점 목록 → 리본 Path(외곽선 좌표 계산), Path → SVG `<path d>` 직렬화, 획 hit-test(Region 판정) 세 가지는 Android/Compose 의존 없는 순수 함수로 뽑아서 유닛 테스트 가능하게 만든다.
- 실제 그리기 손맛(속도-굵기 반응, 겹친 획 지우기 판정)은 에뮬레이터/실기기 확인이 필요하다.

## 스코프 밖 (이번에 안 함)

- 벡터 + 공유(실시간 협업) 조합.
- 읽기모드(페이지 넘김 애니메이션).
- 펜 외 다른 벡터 브러시(도형, 채우기, 텍스트 등).
- 기존 래스터 스케치북을 벡터로 변환하거나 그 반대.
- SVG 가져오기(외부 SVG 파일을 불러와 편집).
