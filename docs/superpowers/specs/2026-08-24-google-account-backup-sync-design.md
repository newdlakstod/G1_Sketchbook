# 구글 계정 기반 백업/동기화 — 설계

## 배경

지금 앱은 완전히 로컬 우선 저장이다 — 개인 스케치북(`SketchbookRepository`, `filesDir/sketchbooks/{id}/page_{i}.png` + `cover.jpg`), 그림일기(`DiaryRepository`, `filesDir/diary/{date}.png`), 설정값(`SessionStore`, SharedPreferences)이 전부 기기 안에만 있다. 같은 구글 계정으로 폰과 태블릿에 각각 설치해도 서로 다른 기기라 데이터가 안 이어진다.

Firebase는 이미 두 가지 용도로 쓰이고 있다: **Auth**(Credential Manager 기반 구글 로그인, `GoogleAuthClient`)와 **Realtime Database**(공유 스케치북 실시간 협업, `ShareRepository` — `shareSessions/{code}/slots/{uid}/...`). Storage는 안 쓴다.

## 확정된 결정 (사용자 확인)

- **동기화 방식**: 수동 백업/복원 버튼이 아니라 **자동** — 사용자가 직접 누르지 않아도 반영된다.
- **"자동"의 범위**: 앱을 쓰는 동안 자동(로그인 직후 + 앱을 다시 열 때 받아오고, 저장이 일어날 때마다 올림). OS 백그라운드 서비스(WorkManager 등, 앱이 완전히 꺼져 있어도 도는 방식)는 아니다 — 배터리/권한 부담 없이 다음에 앱을 열 때 따라잡는 정도로 충분하다고 판단.
- **동기화 대상**: 개인 스케치북(그림+표지+메타데이터) + 그림일기 + 설정값(브러시 색상/굵기/투명도, 제스처, 즐겨찾기 색상, 테마, 별명, 계정 이미지, 그리드 열수). **공유 스케치북은 제외** — 이미 `ShareRepository`로 실시간 동기화되고 있어서 별개.
- **저장소**: Firebase Storage를 새로 붙이지 않고(결제수단 등록 필요) 지금 쓰는 **Realtime Database에 이미지를 base64로 인코딩해서** 저장 — 무료 티어 그대로 유지.
  - 확인된 제약: RTDB 무료 티어(저장 1GB, 월 다운로드 10GB)는 **유저별이 아니라 프로젝트 전체 공유**다. 공유 스케치북 기능과 같은 풀을 나눠 쓴다. 사용자가 소수인 지금은 문제 없지만, 늘어나면 한도 소진이 빨라질 수 있다는 점을 인지하고 진행.
- **동기화 단위(접근 A, 채택)**: 페이지/표지/일기 한 장/설정값 전체처럼 **항목 단위**로 올리고 받는다. 스케치북 하나를 통째로 재전송하지 않는다 — 이미 `ShareRepository.pushSnapshot`이 쓰는 것과 같은 패턴(스트로크 끝날 때 현재 페이지 1장만 올림)이라 구현 스타일이 자연스럽게 이어진다.

## 아키텍처

새 클래스 `BackupRepository`(`com.g1.sketchbook.backup` 패키지, `ShareRepository`와 같은 스타일)를 만든다. `SketchbookRepository`/`DiaryRepository`/`SessionStore`는 **순수 로컬 저장소로 그대로 둔다** — 백업 로직을 그 안에 섞지 않는다. 대신 이미 로컬 저장이 일어나는 호출부(Compose 화면·ViewModel)에서, 로컬 저장 직후 `BackupRepository`의 push 함수를 나란히 호출한다. 이건 `SharedBookScreen.kt`가 `sbRepo.savePage(...)`와 `share.pushSnapshot(...)`을 나란히 부르는 것과 동일한 패턴이라, 기존 코드 스타일과 자연스럽게 맞는다.

`BackupRepository`가 제공할 함수 (전부 fire-and-forget, `pushX`는 `Result` 반환 없이 실패해도 로컬 동작을 막지 않음):

- `pushSketchbookMeta(uid, book: Sketchbook)`
- `pushSketchbookPage(uid, bookId, index, bmp)`
- `pushSketchbookCover(uid, bookId, bmp)` / `pushSketchbookCoverRemoved(uid, bookId)`
- `deleteSketchbook(uid, bookId)` (툼스톤 기록)
- `pushDiaryDay(uid, date, bmp)`
- `pushSettings(uid, settings: SessionStore)` (스냅샷 전체를 한 번에 직렬화)
- `pullAll(uid): BackupSnapshot` — 전체 트리를 한 번에 읽어와 로컬과 비교·병합할 때 사용

## Firebase RTDB 데이터 구조

```
backups/{uid}/
  sketchbooks/{bookId}/
    meta: { name, sizeKey, bgKey, createdAt, pageCount, fav, coverColor, updatedAt }
    cover: base64            (커스텀 표지 이미지 있을 때만)
    pages/{index}: base64    (실제로 그린 페이지만 — 빈 페이지는 안 올림)
    deleted: true            (삭제 툼스톤 — 있으면 다른 값은 무시)
  diary/{date}/
    image: base64
    updatedAt
  settings/
    { nickname, themeMode, favoriteColors, gesture2Tap, gesture3Tap, gestureLongPress,
      gridColumns, brushColor, brushSizes: { PEN, PENCIL, CRAYON, WATER },
      brushOpacities: { PEN, PENCIL, CRAYON, WATER }, eraserSize, eraserOpacity, eraserBlur,
      avatarImage: base64, updatedAt }
```

공유 스케치북(`shareSessions/...`)은 완전히 별개 트리라 안 건드린다.

## 이미지 압축

원본 PNG(무손실, 캔버스 최대 3308px)를 그대로 올리면 base64 인코딩까지 겹쳐서 용량이 너무 크다. 공유 스냅샷(`encodeSnapshot`, 1400px/JPEG 품질 85)과 같은 다운샘플 방식을 쓰되, 백업은 그림을 실제로 보존하는 용도라 조금 더 여유 있게 **긴 변 1800px / JPEG 품질 90**으로 잡는다. 로컬 원본 파일 자체는 압축 없이 그대로 두고, 클라우드에는 압축본만 올라간다 — 즉 백업은 "완전한 원본 복제"가 아니라 "다른 기기에서 계속 그릴 수 있는 수준의 화질" 백업이다. 수치는 나중에 바로 조정 가능.

## 동기화 트리거

**올리기(push)** — 로컬 저장이 실제로 일어나는 지점마다 그 항목만:

| 지점 | 파일 | 기존 로컬 저장 호출 |
|---|---|---|
| 스케치북 페이지 저장 | `SketchbookScreens.kt`(`SketchbookCanvasScreen`) `onStrokeEnd` | `repo.savePage(...)` |
| 스케치북 생성/이름변경/즐겨찾기/표지색상/표지사진 | `SketchbookScreens.kt`, `SketchbookRepository` 호출부 | `repo.create/rename/toggleFav/setCoverColor/saveCover/removeCover(...)` |
| 스케치북 삭제 | 위와 동일 | `repo.delete(...)` |
| 일기 페이지 저장 | `DiaryScreens.kt`(`DiaryEditorScreen`) `onStrokeEnd`/지우기 | `repo?.save(date, b)` |
| 설정값 변경 | `MainScreen.kt`(`SettingsTab`) 각 setter, `RootViewModel.setTheme/setAvatarImage` | `session.xxx = ...` |

**받아오기(pull) + 병합**: 로그인 성공 직후, 그리고 앱이 다시 포그라운드로 올라올 때(예: `RootViewModel`에 `androidx.lifecycle.DefaultLifecycleObserver`나 `ProcessLifecycleOwner`로 붙여서 `ON_START`마다) 한 번씩 `pullAll(uid)`로 전체 트리를 읽어 로컬과 비교한다. 항목마다(스케치북 메타/페이지/표지, 일기 한 장, 설정값 전체 하나) `updatedAt`을 비교해 **더 최신인 쪽이 이긴다**(last-write-wins):

- 로컬에만 있는 항목 → 클라우드로 올림(새 기기가 첫 동기화 전에 만든 것 포함)
- 클라우드에만 있는 항목 → 로컬에 새로 받아 생성(다른 기기에서 만든 것)
- 둘 다 있으면 `updatedAt`이 더 큰 쪽으로 맞춤

**주의**: 클라우드에만 있던 스케치북을 로컬에 새로 만들 때는 `SketchbookRepository.create()`(랜덤 새 id 생성)를 그대로 쓰면 안 된다 — 클라우드의 `bookId`를 그대로 로컬 id로 써서 만들어야 다음 동기화 때도 같은 항목으로 계속 매칭된다. `SketchbookRepository`에 id를 지정해서 만드는 함수(또는 기존 `create()`에 선택적 id 파라미터)가 필요하다.

## 삭제 처리

스케치북을 지우면 로컬에서 즉시 삭제하고, 클라우드 노드는 완전히 지우는 대신 `deleted: true` 툼스톤을 남긴다. 그냥 지워버리면 다른 기기가 다음 pull 때 "로컬에 없는 새 항목"으로 착각해서 되살려버리기 때문. pull 로직은 `deleted: true`를 보면 로컬에서도 지우고 넘어간다.

그림일기는 삭제 기능 자체가 없다(하루 1장, 계속 덮어쓰기만) — 툼스톤 불필요.

## 충돌 처리

동기화가 자동이라, 두 기기에서 거의 동시에 같은 항목을 고칠 가능성이 있다. 항목(페이지 1장/표지/일기 1장/설정값 전체) 단위로 **last-write-wins**만 적용한다 — 그림을 부분적으로 합치는 병합은 안 함. 그림 도중 두 기기가 정확히 같은 페이지를 동시에 그릴 일은 드물다고 보고, 발생하면 나중에 저장된 쪽이 이기는 단순한 규칙으로 충분하다고 판단.

## 에러 처리

- push는 전부 fire-and-forget — 실패해도(오프라인 등) 로컬 저장/편집은 그대로 진행되고 사용자에게 별도 에러를 띄우지 않는다(공유 기능의 `pushSnapshot`과 동일한 태도).
- pull은 실패하면 그냥 로컬 상태 그대로 유지 — 재시도는 다음 앱 오픈/포그라운드 때 자연히 일어난다.
- 로그아웃 상태(비로그인)에서는 백업/동기화 자체가 동작하지 않는다(현재도 로그인 필수 앱이라 해당 없음).

## 테스트

- `BackupRepository`의 병합 로직(로컬-우선/클라우드-우선/충돌 시 최신 판단, 툼스톤 처리)은 Firebase 없이 순수 함수로 뽑아서(입력: 로컬 스냅샷 + 원격 스냅샷, 출력: 적용할 변경 목록) 유닛 테스트 가능하게 만든다 — `CurlGeometry`처럼 이 프로젝트가 이미 쓰는 "순수 로직은 분리해서 테스트" 패턴을 따른다.
- 실제 Firebase 왕복(두 기기 시뮬레이션)은 유닛 테스트로 못 하니 실기기 확인이 필요하다.

## 스코프 밖(이번에 안 함)

- 진짜 OS 백그라운드 동기화(WorkManager 주기 작업)
- 부분 병합(같은 페이지의 서로 다른 획을 합치는 것)
- 백업 이력/버전 관리(되돌리기)
- 다른 유저 초대 없이 내 계정 데이터만 대상 — 여러 계정 전환 시나리오는 고려 안 함
