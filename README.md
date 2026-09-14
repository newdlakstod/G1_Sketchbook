# Daymory · G1 Sketchbook

종이와 문구의 질감을 살린 Android 스케치북. 개인 스케치북, 날짜별 그림일기,
서로의 그림을 보며 그리는 공유 세션을 제공한다.

현재 코드 기준: **2.21.1 / versionCode 153**, 2026-09-14 확인.
소스 설정이며 배포 완료 여부를 뜻하지 않는다.

## 먼저 읽을 문서

- [PROGRESS.md](PROGRESS.md): 완료 내역, 현재 우선순위, 결정과 미해결 사항.
- [코드·UI·UX 검토](docs/reviews/2026-09-14-code-ui-ux-review.md): 구조, 디자인 평가, 근거와 후속 검증.
- [기능별 설계 기록](docs/superpowers/specs/): 기능별 확정 설계.
- [plan.md](plan.md): 초기 v1 기획의 역사 자료. 현재 구현 안내로 사용하지 않는다.

## 현재 구현

| 영역 | 동작 |
|---|---|
| 시작·계정 | 시작 화면 → Google 로그인 → 별명 → 메인 화면, 프로필 사진과 테마 설정 |
| 홈 | 개인/공유 책 표지 탐색. 가로에서는 읽기 패널과 책 목록 |
| 개인 스케치북 | 6종 용지 크기, 5종 종이, 새 책 15페이지, 표지 편집, 페이지 순서 변경, 이미지 내보내기 |
| 드로잉 | 펜·색연필·크레용·수채화, 지우개, 채우기, 올가미, 확대·이동, S펜 버튼, Undo/Redo |
| 색상 | 7색 라이브러리 최대 30개, 최대 3개 활성화, 빠른 색 3개, 이미지 스포이드 |
| 그림일기 | 날짜별 그림과 달력 탐색, 오늘 일기 편집, 달력 합성·이미지 내보내기 |
| 공유 | 최대 4명이 각자의 캔버스를 그림. 획 종료 등에 갱신한 페이지 스냅샷을 서로 확인. 초대 코드·선생님 모드·호스트 이전 |
| 읽기 | 공용 `:pagecurl` 모듈로 페이지 넘김 |
| 백업 | Google 계정별 스케치북·일기·설정·색상 라이브러리 병합 |

공유는 동일 캔버스의 획을 공동 편집하는 방식이 아니다. 이전 벡터 모드는 제거되었다.
자동저장과 동기화의 알려진 한계는 검토 문서와 PROGRESS.md를 참고한다.

## 코드 지도

기준 디렉터리: `app/src/main/java/com/g1/sketchbook/`

| 위치 | 책임 |
|---|---|
| `MainActivity.kt`, `ui/RootViewModel.kt` | 인증·테마·현재 화면 상태, 앱 생명주기 동기화 |
| `SketchApp.kt`, `auth/` | Application 서비스 구성, Google/Firebase 인증 |
| `ui/main/` | 5개 탭 배치, 홈, 설정 |
| `ui/theme/` | 색·서체·공통 치수 |
| `ui/Interactions.kt` | 공통 눌림 애니메이션과 터치 처리 |
| `brush/BrushView.kt` | Android Canvas 기반 비트맵 드로잉, 제스처, Undo/Redo |
| `brush/BrushControls.kt`, `brush/ImageEyedropper.kt` | 도구 UI, 색상 편집, 사진에서 색 추출 |
| `sketchbook/` | 책 생성·목록·표지·편집 화면, 로컬 저장소와 백업 연결 |
| `diary/` | 날짜별 저장소, 편집·달력·합성 내보내기 |
| `share/` | 참가자별 공유 세션, 상대 그림 표시 |
| `data/` | 설정 저장, 색상 라이브러리 모델·동기화 |
| `backup/` | 계정별 원격 데이터, 삭제 표시, 로컬/원격 병합 |
| `readmode/` | 페이지 비트맵 공급과 읽기 화면 |
| `preview/` | 실제 화면 Composable을 사용하는 Android Studio Preview |

메인 UI는 Jetpack Compose/Material 3, 드로잉은 `AndroidView(BrushView)`를 사용한다.
로컬 메타데이터는 SharedPreferences/JSON, 그림은 PNG 파일에 저장한다.
원격 데이터는 Firebase Realtime Database의 `shareSessions/{code}`와 `backups/{uid}`에
보관하며 이미지는 Base64로 인코딩한다. 현재 앱 의존성에 Firebase Storage는 없다.

## 개발과 검증

- Android Studio, JDK 17 호환 환경, Android SDK 35. 최소 Android API 24.
- 이 PC에서는 Android Studio 번들 JBR를 사용한다.
- `local.properties`에 로컬 SDK 경로를 지정한다.
- `:pagecurl` 기본 경로는 저장소 안의 `pagecurl/`이다. `local.properties`의 `pagecurl.dir`이 있으면 해당 외부 경로가 우선한다.
- Firebase Google 로그인에는 해당 앱의 OAuth 설정과 빌드 서명 SHA 등록이 필요하다.
  원격 접근 권한은 실제 배포된 RTDB 규칙에 달려 있다. 이 문서는 운영 규칙의 적정성을 검증하지 않는다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
태그 배포는 `.github/workflows/`의 GitHub Actions를 사용한다.
매 배포마다 버전과 새 태그를 올리며 기존 태그는 덮어쓰지 않는다.

2026-09-14 검토에서는 테스트·lint를 시도했으나 `:app:processDebugResources`의
`R.jar` 삭제 오류로 중단됐다. 현재 변경분의 테스트 통과를 확인한 상태는 아니다.
실행 환경과 남은 검증은 [검토 문서](docs/reviews/2026-09-14-code-ui-ux-review.md)에 기록했다.
