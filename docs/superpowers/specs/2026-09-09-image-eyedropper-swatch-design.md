# 이미지 스포이드로 라이브러리 색 채우기 설계

## 배경 및 목적

지금 스포이드(eyedropper) 기능은 `BrushView`가 그리는 캔버스 위에서만 색을 뽑을 수 있다
(`eyedropArmed`/`pickColorAt`/`onEyedrop`). 사용자는 참고 사진 같은 외부 이미지에서 색을 뽑아
색상 라이브러리(7색×최대 30개, `ColorLibrary`)의 특정 칸에 바로 저장하고 싶어 한다.

이 스펙은 라이브러리 편집 화면(`ColorLibraryDetailPopup`, `BrushControls.kt`)에 "이미지에서"
진입점을 추가해, 갤러리에서 고른 사진을 확대/이동하며 여러 칸에 연달아 색을 채워 넣는 기능을
다룬다.

## 흐름

1. 라이브러리 편집 화면(`ColorLibraryDetailPopup`)에서 칸 하나를 탭하면 지금과 똑같이
   `ColorPickerCard`가 열린다. 이 카드 안, 기존 "스포이드"(캔버스에서 뽑기) 버튼 옆에 "이미지에서"
   버튼을 새로 추가한다 — 라이브러리 칸을 편집하는 맥락일 때만 보인다(즉시선택 즐겨찾기나 팔레트
   그리드 편집처럼 "이웃 6칸"이라는 개념이 없는 다른 `ColorPickerCard` 용도에서는 안 보임).
2. "이미지에서"를 탭하면 Android 표준 Photo Picker(`ActivityResultContracts.PickVisualMedia`)가
   뜬다 — 별도 런타임 권한 요청이 필요 없다.
3. 사진을 고르면 전체화면 오버레이(`Dialog`, `usesPlatformDefaultWidth = false`)가 뜬다. 안에는:
   - 고른 사진 — 핀치로 확대(1~5배), 한 손가락 드래그로 이동 가능.
   - 지금 편집 중인 라이브러리의 7칸 스와치 줄 — "지금 채울 대상" 칸이 테두리로 강조돼 있다.
     처음 진입 시 대상은 방금 "이미지에서"를 누르기 직전에 열려 있던 `ColorPickerCard`가 편집
     중이던 바로 그 칸이다.
4. 사진 위를 한 손가락으로 누르고 있으면, 지금 캔버스 스포이드와 같은 방식으로 손가락 위쪽에
   확대 미리보기(루페 + 색상 스와치)가 뜬다. 손을 떼면 그 지점의 색이 강조된 대상 칸에 즉시
   저장된다(별도 확인 단계 없음 — 기존 스포이드와 동일한 "떼면 확정" 방식).
5. 대상을 바꾸려면 스와치 줄에서 다른 칸을 탭한다 — 사진과 확대/이동 상태는 그대로 유지된다.
6. "완료"를 누르면 오버레이가 닫히고 `ColorLibraryDetailPopup`으로 돌아간다. 바뀐 색은 이미 그
   때그때 저장돼 있으므로 별도 "저장" 동작은 없다.

## 데이터 흐름 및 재사용

- 이미지 디코딩은 새로 만들지 않고 기존 `decodeCoverBitmap(context, uri, maxDim)`
  (`SketchbookScreens.kt`, internal이라 모듈 내 다른 파일에서 바로 재사용 가능)을 그대로 쓴다 —
  이미 다운샘플링·`ImageDecoder`/`BitmapFactory` 분기(API 28 이하 폴백)를 처리해 준다. 표지 사진과
  같은 정밀도가 필요 없으므로 `maxDim`은 조금 더 작게(예: 1200) 줘서 메모리를 아낀다.
- 색 저장은 이미 있는 `onEditLibraryColor(libraryId, index, color)` 콜백을 그대로 호출한다 —
  `SessionStore.libraries` 갱신과 백업 동기화(`ColorLibrarySync.updateLibraryColorSynced`)까지
  이미 연결돼 있어 새로 만들 게 없다.
- 확대/이동 상태(`scale`/`offset`)는 `CoverImageCropContent`(`SketchbookScreens.kt`)가 쓰는
  `detectTransformGestures` + `graphicsLayer` 패턴을 그대로 따른다. 다만 그쪽은 멀티터치
  확대/이동만 처리하면 됐던 반면, 여기는 "한 손가락 드래그 = 색 뽑기", "두 손가락 = 확대/이동"을
  구분해야 한다 — `BrushView.onTouchEvent`가 이미 한 화면 안에서 손가락 개수로 그리기/핀치줌을
  가르는 것과 같은 방식(`awaitPointerEventScope`에서 포인터 개수 직접 판별)으로 구현한다.
- 색상 샘플링은 화면 좌표(현재 `scale`/`offset`/`ContentScale.Crop` 매핑 반영) → 비트맵 픽셀
  좌표로 역변환한 뒤 `Bitmap.getPixel(x, y)`로 뽑는다.

## 파일 구성

- **새 파일** `app/src/main/java/com/g1/sketchbook/brush/ImageEyedropper.kt` (패키지
  `com.g1.sketchbook.brush`): 전체화면 오버레이 컴포저블(`ImageEyedropperOverlay`), 제스처 판별
  (한 손가락 드래그=색 뽑기 vs 두 손가락=핀치줌/이동), 화면 좌표→비트맵 픽셀 좌표 역변환 및 색상
  샘플링 함수. `BrushControls.kt`가 이미 커서(1600줄대) 새 UI 뭉치를 여기 분리한다.
- **수정** `app/src/main/java/com/g1/sketchbook/brush/BrushControls.kt`:
  - `ColorPickerCard`에 선택적 파라미터를 추가해 "라이브러리 칸 편집" 맥락임을 알린다 — 그 칸이
    속한 라이브러리의 전체 7색 목록, 지금 편집 중인 인덱스, 그리고 색이 바뀔 때마다 부를 콜백
    (`onEditLibraryColor`와 같은 시그니처)을 담는다. 이 값이 있을 때만 "이미지에서" 버튼이 보인다.
  - `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` 호출과, 고른
    이미지가 있으면 `ImageEyedropperOverlay`를 띄우는 상태를 `ColorPickerCard` 안에 추가한다. 이
    컴포저블 자체가 Activity 트리 안에서 직접 launcher를 등록할 수 있어(표준 Compose 패턴), 세
    화면(`SketchbookScreens.kt`/`DiaryScreens.kt`/`SharedBookScreen.kt`)은 전혀 건드릴 필요가
    없다 — `ColorPickerCard`가 이미 그 세 화면 모두에서 간접적으로(`ColorLibraryDetailPopup`
    경유) 재사용되고 있기 때문.
  - `ColorLibraryDetailPopup`이 각 스와치를 탭해 `ColorPickerCard`를 열 때, 위 선택적 파라미터를
    채워서 넘기도록 호출부를 바꾼다.

## 에러 처리

- 이미지 디코드 실패(`decodeCoverBitmap`이 null 반환) — 오버레이를 열지 않고 짧은 안내만
  보여준다(`Toast` 또는 스낵바 — 기존 표지 사진 실패 처리와 같은 방식).
- 사용자가 Photo Picker에서 취소 — 아무 일도 일어나지 않는다(launcher 콜백의 `uri == null` 분기).

## 이번 스펙에서 다루지 않는 것

- 카메라로 직접 촬영.
- 고른 이미지를 앱에 보관하거나 다시 불러오는 기능 — 오버레이를 닫으면 그 이미지는 버려진다.
- 스와치 칸이 아닌 다른 대상(즉시선택 즐겨찾기, 브러시 현재 색상 등)에 이미지 스포이드를
  적용하는 기능.
- 이미지 스포이드 전용 undo — 색이 잘못 뽑혔으면 그 칸을 다시 탭해서 새로 뽑거나, 기존
  색상피커로 직접 고쳐 쓴다.

## 테스트

- 화면 좌표 → 비트맵 픽셀 좌표 역변환(스케일/오프셋 반영)은 Android 의존 없는 순수 계산으로
  뽑을 수 있으면 유닛 테스트 대상으로 분리한다.
- 나머지(Compose UI, Photo Picker 연동, 실제 이미지 디코딩)는 이 프로젝트 관례상 컴파일 확인 +
  수동 확인.
