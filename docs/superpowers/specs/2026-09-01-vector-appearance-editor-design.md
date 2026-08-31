# 벡터 패스 Appearance 편집기 재설계

## 배경

현재 벡터 모드는 손가락 한 번의 입력을 `VectorStroke` 하나로 저장하지만 실제 작업 모델은
Illustrator의 패스와 다르다. 굵기가 점마다 구워진 리본 폴리곤을 그리고, `fillEnabled`는 패스의
일반적인 Fill이 아니라 자기교차로 생긴 영역을 자동 채우며, 임포트 브러시는 SVG 모양을 경로에
반복하는 스탬프 방식만 지원한다. 선택 도구도 객체의 Appearance를 다시 편집하기보다 라쏘 이동과
내보내기에 맞춰져 있다. 그 결과 Fill, Stroke, Brush가 한 객체의 속성처럼 연결되지 않고 여러
팝업에 흩어져 보인다.

이 설계는 사용자가 승인한 세로·가로 시안을 기준으로 벡터 모드를 **패스 형상 + Appearance**
편집기로 재구성한다. 목표는 Adobe Illustrator의 핵심 작업 흐름을 태블릿과 S펜에 맞게 단순화하는
것이지, 데스크톱 Illustrator 전체를 복제하는 것이 아니다.

## 확정된 사용자 결정

- 선택 도구로 기존 패스를 탭한 뒤 Fill, Stroke, 굵기, Cap, Join, Brush를 실시간 수정한다.
- 선택된 패스가 없으면 같은 Appearance 패널이 새로 그릴 패스의 기본값을 설정한다.
- 브러시는 `Basic`, `Art`, `Pattern` 세 종류로 분리한다.
  - Art Brush는 SVG 원본 모양 하나를 패스 전체 길이에 맞춰 휘고 늘인다.
  - Pattern Brush는 현재 스탬프 브러시처럼 모양을 경로를 따라 반복한다.
- 열린 패스에도 Fill을 적용한다. Fill은 양 끝을 가상의 직선으로 연결하지만 Stroke는 실제 경로만
  따라간다. Stroke까지 닫는 동작은 별도의 `패스 닫기` 명령이다.
- 이번 단계에서는 객체 선택, Appearance 편집, 이동·회전·크기 조절까지 구현한다. 앵커 포인트와
  베지어 핸들의 직접 편집은 후속 범위다.
- 승인 시안처럼 세로 화면은 하단 Appearance 패널, 가로 화면은 오른쪽 고정 Appearance 패널을 쓴다.

## 목표

1. 패스를 탭해 선택하고 하나의 Appearance 패널에서 모든 시각 속성을 편집한다.
2. 새 패스와 기존 패스가 동일한 렌더링 규칙을 사용한다.
3. 실제 Art Brush 변형과 기존 Pattern Brush 반복을 서로 다른 렌더러로 제공한다.
4. 그리기, 스타일 변경, 변형, 삭제를 모두 Undo/Redo할 수 있게 한다.
5. 기존 벡터 그림을 모습 그대로 읽고 보존하며 새 형식 전환 과정에서 원본을 덮어쓰지 않는다.
6. 승인 시안의 따뜻한 아이보리·네이비 시각 언어와 세로/가로 적응형 배치를 구현한다.

## 범위 밖

- 앵커 포인트·베지어 핸들 직접 편집
- 그룹, 복합 패스, 클리핑 마스크, 그라디언트, 텍스트
- 벡터 실시간 협업
- 래스터 그림을 벡터로 자동 변환
- Adobe 전용 AI 파일 읽기·쓰기

## 접근법

현재 렌더러 위에 시안 UI만 씌우는 방식은 제외한다. 기존 리본·자기교차 Fill 의미가 남아 사용자가
원하는 편집 모델과 다시 충돌하기 때문이다. 반대로 베지어 엔진과 그룹까지 한 번에 만드는 것도
이번 범위를 넘는다.

선택한 방식은 **호환성을 유지하는 패스 객체 모델 재구축**이다. 기존 데이터는 호환 객체로 읽어
기존 렌더러로 동일하게 표시하고, 새로 그린 패스부터 새 Appearance 모델을 쓴다. 기존 객체의
Appearance를 처음 변경할 때만 편집 가능한 새 패스로 변환한다.

## 화면 구조

### 공통

- 상단 바: 뒤로가기, 스케치 이름, Undo, Redo, 구분선, 내보내기
- 왼쪽 플로팅 도구 레일: 선택, 펜, 지우개, 손 도구
- 중앙: 캔버스와 선택 오버레이
- Appearance 패널: 패스 미리보기, Fill, Stroke, Cap/Join, Brush 라이브러리 진입점
- 선택 도구가 활성화되면 네이비 배경과 흰색 아이콘·라벨로 강조한다. 나머지는 아이보리 표면 위
  네이비 라인 아이콘을 쓴다.
- 순백·순검정 대신 기존 앱의 따뜻한 아이보리 표면과 짙은 네이비를 사용한다. 그림자는 한 방향의
  낮은 명도 그림자만 쓰고 장식용 그라디언트는 쓰지 않는다.

### 가로 화면

- `maxWidth >= 720.dp`이고 가로가 세로보다 넓으면 오른쪽에 300~336dp 고정 패널을 둔다.
- 캔버스는 도구 레일과 패널 사이의 남은 영역을 사용한다.
- 패널은 화면 높이를 넘으면 내부만 스크롤한다. 캔버스 크기는 패널 스크롤에 영향받지 않는다.

### 세로 화면

- Appearance는 하단 시트로 표시한다. 기본 높이는 화면의 약 42%이며 상단 드래그 핸들로 접거나
  확장할 수 있다.
- 접힌 상태에서도 Fill/Stroke 겹침 스와치와 현재 브러시 미리보기는 남긴다.
- 시트가 캔버스를 덮는 동안 선택 객체가 가려지지 않도록 필요하면 뷰포트만 위로 이동한다. 객체
  좌표 자체는 변경하지 않는다.

### 작은 휴대전화

- 도구 레일은 아이콘만 표시하고 라벨은 접근성 설명으로 유지한다.
- Appearance는 전체 폭 하단 시트로 쓰며 Cap/Join과 Brush 세부 항목은 접이식 섹션으로 표시한다.

## 편집 상호작용

### 선택

- 패스를 탭하면 터치 지점과 겹치는 객체 중 가장 위에 있는 하나를 선택한다.
- 빈 캔버스를 탭하면 선택을 해제한다.
- 선택된 객체에는 실제 렌더링 경계를 감싸는 사각형, 모서리·변 중앙 핸들 8개, 상단 회전 핸들을
  표시한다.
- 사각형 내부 드래그는 이동, 모서리 핸들은 비율 유지 크기 조절, 변 중앙 핸들은 한 축 크기 조절,
  회전 핸들은 중심 기준 회전이다.
- 선택 도구로 빈 공간을 드래그하면 기존 라쏘 다중 선택을 시작한다. 다중 선택에서는 공통으로
  적용 가능한 Appearance 값만 표시하고 서로 다른 값은 혼합 상태로 표시한다.
- 두 손가락 팬·줌은 모든 도구에서 캔버스 뷰포트에 적용한다. 손 도구에서는 한 손가락도 팬으로 쓴다.

### 펜·지우개

- 펜은 현재 기본 Appearance를 복사해 새 패스를 만든다. 이후 기본값이 바뀌어도 기존 패스는 바뀌지
  않는다.
- 지우개는 픽셀이 아니라 경로 객체를 지운다. 드래그 중 같은 경로를 여러 번 만나도 한 번만 지우고
  한 번의 Undo로 해당 제스처 전체를 복구한다.

### Appearance

- 선택이 있으면 패널 제목은 `패스`, 없으면 `새 패스 스타일`이다.
- Fill과 Stroke는 겹친 두 사각형으로 현재 색과 전면 항목을 표시한다.
- Fill/Stroke 토글, 색상 스와치, Stroke 굵기, Cap, Join 변경은 캔버스에 즉시 반영한다.
- 슬라이더를 움직이는 동안은 라이브 미리보기를 제공하지만 손을 뗄 때 하나의 Undo 명령으로 묶는다.
- Art/Pattern 브러시에서 의미가 없는 Basic 전용 옵션은 값을 삭제하지 않고 비활성화하며 짧은 설명을
  표시한다. Basic으로 돌아오면 이전 값이 복원된다.
- `패스 닫기`는 기하의 `closed` 값만 바꾼다. Fill은 `closed=false`여도 가상 닫힘으로 보이지만,
  Stroke는 `closed=true`일 때만 마지막 점에서 첫 점까지 이어진다.

## 문서와 상태 모델

새 저장 형식은 명시적인 `version: 2`를 갖는다.

```text
VectorDocumentV2
  version = 2
  objects: List<VectorObject>

VectorObject
  id: String
  geometry: PathGeometry
  appearance: PathAppearance
  transform: ObjectTransform

PathGeometry
  points: List<PathPoint(x, y, widthFactor)>
  closed: Boolean

PathAppearance
  fill: FillStyle(enabled, color)
  stroke: StrokeStyle(enabled, color, width, cap, join)
  brush: Basic | Art(profileId) | Pattern(profileId)
```

- `id`는 객체 선택과 Undo/Redo가 리스트 인덱스 변화에 영향받지 않게 하는 안정 식별자다.
- `widthFactor`는 0~1 범위의 속도·압력 변화값이고 절대 굵기는 `StrokeStyle.width`에 둔다. 따라서
  Stroke 굵기를 바꾸면 손맛의 굵기 변화 비율을 유지한 채 전체가 함께 커진다.
- 이동·회전·크기 조절 결과는 편집 중 `ObjectTransform`으로 미리보기하고 제스처 종료 시 점 좌표에
  합성한다. 저장 파일에는 누적 변환 행렬을 계속 쌓지 않는다.

`VectorEditorState`가 문서, 선택 id 집합, 현재 도구, 뷰포트, 새 패스 기본 Appearance, Undo/Redo
스택의 단일 소유자가 된다. Compose 패널과 `VectorBrushView`가 각각 별도 복사본을 갖지 않는다.
`VectorBrushView`는 렌더링과 포인터 입력을 전달하는 호스트로 축소한다.

## 렌더링

### Fill

- 점이 3개 이상이고 Fill이 켜져 있으면 중심선 점 목록을 다각형으로 사용한다.
- 열린 패스도 마지막 점에서 첫 점까지 가상의 직선을 추가해 `EVEN_ODD` 규칙으로 채운다.
- 가상 닫힘 선은 Fill 경계일 뿐 Stroke로 그리지 않는다.

### Basic Stroke

- 기존 `strokeOutline`의 가변 폭 리본 방식을 재사용하되 절대 `w` 대신
  `appearance.stroke.width * point.widthFactor`를 사용한다.
- Cap과 Join은 실제 외곽선 생성에 반영한다. 기존 Cap 구현에 Join 지오메트리를 추가한다.

### Art Brush

- Art Brush용 SVG는 가로 방향 원본 모양으로 파싱하고 전체 경계를 `u=0..1`, 중심선을 `v=0`으로
  정규화한다.
- 원본 다각형의 각 점 `(u, v)`에서 `u`를 대상 패스의 누적 길이 위치로 변환한다.
- 해당 위치의 접선과 법선을 구해 `v`를 법선 방향으로 배치한다. 법선 오프셋에는 Stroke 굵기와
  보간된 `widthFactor`를 곱한다.
- 이 매핑으로 원본 모양 하나가 패스 전체를 따라 휘고 늘어난다. 속도에 따른 손그림 굵기 변화도
  유지된다.
- SVG 원본의 색은 사용하지 않고 현재 Stroke 색으로 틴트한다.

### Pattern Brush

- 기존 `StampBrushProfile`과 호 길이 기반 반복 알고리즘을 유지한다.
- 기존 스탬프 브러시는 마이그레이션 없이 전부 Pattern 탭에 나타난다.
- 크기·간격 설정은 Pattern 프로파일 속성으로 유지한다.

### 캐시

- 객체별 `geometryRevision + appearanceRevision + profileRevision` 키로 렌더 경로와 히트 영역을
  캐시한다.
- 스타일 변경 시 선택 객체 캐시만 무효화하고, 뷰포트 팬·줌은 기하를 재계산하지 않는다.

## 브러시 라이브러리

- `Basic`, `Art`, `Pattern` 탭을 제공한다.
- Basic은 프로파일 없는 기본 가변 폭 펜이다.
- Art 탭에서 SVG를 가져오면 Art 정규화 규칙으로 파싱해 별도 Art 프로파일로 저장한다.
- Pattern 탭은 기존 스탬프 프로파일과 기존 가져오기 흐름을 사용한다.
- 프로파일 카드에는 실제 렌더링과 같은 곡선 미리보기를 사용한다. 장식용 가짜 곡선을 쓰지 않는다.
- 프로파일을 찾을 수 없으면 화면에서 Basic으로 안전하게 렌더링하되 저장된 `profileId`는 지우지
  않는다. 패널에는 `브러시를 찾을 수 없음` 상태를 표시해 재동기화 후 원래 모양이 돌아올 수 있게
  한다.
- 원격 브러시 모델에 `type = ART | PATTERN`을 추가한다. 기존 원격 항목은 type이 없으므로
  `PATTERN`으로 읽는다.

## 기존 데이터 보존과 마이그레이션

현재 파일 `vector_canvas.json`과 Firebase `vectorCanvas` 문자열은 이미 배포된 사용자 데이터다.
자동 변환으로 로컬·원격 원본을 덮어쓰지 않는다.

1. 로컬에 `vector_canvas_v2.json`이 있으면 이를 먼저 읽는다.
2. v2 파일이 없으면 기존 `vector_canvas.json`을 기존 파서로 읽어 `LegacyStrokeObject` 목록으로
   메모리에 올린다. 이 단계에서는 어떤 파일도 쓰지 않는다.
3. Legacy 객체는 기존 렌더러로 표시해 현재 모습과 자기교차 Fill을 그대로 보존한다.
4. 이동·회전·크기 조절은 Legacy 객체에도 가능하며 원본 획 속성을 유지한다.
5. Legacy 객체의 Appearance를 처음 변경하면 해당 객체만 새 `PathGeometry + PathAppearance`로
   변환한다. 기존 절대 굵기의 최댓값을 Stroke 굵기로 삼고 각 점의 `w / maxW`를
   `widthFactor`로 만들어 시각 차이를 최소화한다.
6. 첫 변경 저장 시 새 문서 전체를 `vector_canvas_v2.json`에 원자적으로 기록한다. 기존
   `vector_canvas.json`은 삭제·수정하지 않는다.
7. 저장은 임시 파일에 쓴 뒤 파싱 검증에 성공한 경우에만 최종 파일명으로 이동한다.
8. Firebase에는 새 형식을 별도 형제 노드 `vectorCanvasV2`에 올린다. 기존 `vectorCanvas`는 수정·
   삭제하지 않는다. 새 앱은 유효한 v2가 있으면 이를 우선하고, 없거나 손상됐으면 v1을 읽는다.
9. 구버전 앱이 이후 기존 `vectorCanvas`를 갱신해도 v2와 섞이지 않는다. 새 앱에서 v2 편집이 시작된
   뒤에는 v2 노드의 타임스탬프만 동기화 기준으로 사용한다.
10. v2 파싱이 실패하면 원격이나 로컬을 덮어쓰지 않고 보존된 v1 파일로 폴백한다.

v1 원본 정리는 자동으로 하지 않는다. 사용자가 별도 삭제 기능을 요청하기 전까지 복구용으로 남긴다.

## Undo/Redo

모든 변경은 역연산을 가진 `EditorCommand`로 처리한다.

- `AddObject`
- `DeleteObjects`
- `TransformObjects`
- `ChangeAppearance`
- `ChangeClosedState`

그리기 한 획, 지우개 한 제스처, 변형 한 제스처, 슬라이더 한 드래그가 각각 Undo 한 단계다. 새 명령이
실행되면 Redo 스택은 비운다. 자동저장은 명령 완료 후 실행하며 미리보기 프레임마다 저장하지 않는다.

## 컴포넌트 경계

- `VectorEditorState`: 문서·선택·도구·명령·기본 Appearance
- `VectorDocumentCodec`: v1 읽기, v2 읽기/쓰기, 검증
- `VectorSelectionEngine`: 히트테스트, 라쏘 선택, 선택 경계
- `VectorTransformEngine`: 이동·축척·회전과 점 좌표 합성
- `VectorRenderer`: Fill과 Basic/Art/Pattern 공통 렌더 진입점
- `ArtBrushMapper`: SVG 원본 다각형을 패스에 휘어 매핑하는 순수 기하 함수
- `VectorCanvasHost`: Android 포인터 이벤트와 렌더 호출
- `VectorEditorScreen`: 적응형 배치만 담당
- `VectorToolRail`, `VectorAppearancePanel`, `VectorBrushLibrary`: 독립 Compose UI

현재 558줄의 `VectorCanvasScreen`에 저장·브러시 임포트·팝업·캔버스 상태를 계속 추가하지 않는다.
각 컴포넌트는 위 경계를 통해 상태와 명령만 주고받는다.

## 오류 처리

- 손상된 v2 문서는 원본과 v1 파일을 덮어쓰지 않고 오류 메시지와 함께 v1 폴백 또는 빈 읽기 전용
  캔버스를 제공한다.
- Art SVG가 지원되지 않는 요소만 포함하면 프로파일을 저장하지 않고 이유를 안내한다.
- 동기화 중 프로파일보다 문서가 먼저 도착해도 Basic 폴백으로 그림이 사라지지 않게 한다.
- 저장 실패 시 메모리 문서는 유지하고 재시도할 수 있게 하며 성공으로 표시하지 않는다.

## 테스트

### 순수 단위 테스트

- v1 문서 읽기와 시각 호환 객체 생성
- v2 직렬화 왕복, 손상된 v2의 v1 폴백, v1 파일 불변
- Legacy 객체를 새 패스로 변환할 때 굵기 비율 보존
- 열린 패스 Fill 가상 닫힘과 Stroke 비닫힘
- `closed=true`의 Fill/Stroke 닫힘
- Basic Cap/Join 외곽선
- Art Brush 직선·곡선·역방향 매핑과 widthFactor 보간
- Pattern Brush 기존 반복 회귀 테스트
- 최상단 객체 히트테스트와 라쏘 다중 선택
- 이동·한 축/비율 축척·회전
- Add/Delete/Transform/Appearance 명령의 Undo/Redo
- 슬라이더 변경 명령 병합

### 빌드·정적 검사

- 전체 `testDebugUnitTest`
- `assembleDebug`
- `lintDebug` 오류 0

### 수동 검증

- 태블릿 세로: 하단 패널 접기·확장, 선택 객체 가림 방지
- 태블릿 가로: 오른쪽 고정 패널, 패널 내부 스크롤
- 휴대전화 세로/가로의 도구 레일과 터치 목표 크기
- S펜으로 그리기, 손가락 두 개로 팬·줌, 손 도구 한 손가락 팬
- 선택 핸들 이동·축척·회전과 스타일 실시간 변경
- 기존 벡터 책을 열기만 했을 때 파일 hash와 mtime 불변
- 기존 벡터 책 편집 후 v1 파일 보존과 v2 파일 생성
- Firebase v1/v2 왕복 및 누락 프로파일 폴백

## 완료 기준

- 승인 시안과 동일하게 세로는 하단, 가로는 오른쪽 Appearance 패널을 사용한다.
- 패스 선택 전후로 패널이 기본 스타일/선택 스타일 모드로 정확히 전환된다.
- Fill, Stroke, 굵기, Cap, Join, Basic/Art/Pattern 변경이 선택 패스에 즉시 반영된다.
- Art Brush가 원본 SVG 하나를 패스 전체에 실제로 휘고 늘이며 Pattern과 시각적으로 구분된다.
- 이동·회전·크기 조절과 모든 Appearance 변경이 Undo/Redo된다.
- 기존 벡터 문서는 열기만 해서는 어떤 로컬·원격 데이터도 변경하지 않는다.
- 기존 로컬 `vector_canvas.json`과 원격 `vectorCanvas`를 남긴 상태로 별도 v2 문서를 저장하고,
  전체 자동 테스트·빌드·lint를 통과한다.
