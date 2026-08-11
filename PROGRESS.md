# G1 Sketchbook — Progress

실시간 공유 스케치북 (Android + Jetpack Compose + Firebase). 친구·커플이 하나의 방(room)에서
매일 함께 그리고, 하루치 그림이 갤러리에 영구 보관되는 앱.

## Done
- **Firebase 실연결**: 프로젝트 `g1-sketchbook`. Google 로그인 동작 (SHA-1 등록, 웹 OAuth 클라이언트 생성,
  코드가 `default_web_client_id` 자동 생성값 사용). `app/google-services.json`은 실제 값.
- **매일 자정 자동 아카이빙** (v1.1.0): `work/DailyArchive.kt`
  - WorkManager 원타임 워커가 자정에 전날 캔버스를 렌더→저장, 다음 자정 재예약 (`ArchiveScheduler`).
  - 놓친 날 보정: 방 입장 시 `CanvasViewModel.bind`에서 `archiveDayIfNeeded(어제)` 호출.
- **무료 저장 전환** (v1.1.0): Firebase Storage(유료 Blaze 필요) 제거.
  스냅샷을 WebP 압축 후 Base64로 **Realtime Database**의 `rooms/{roomId}/archive/{date}`에 저장.
  `ArchiveEntry.url` → `ArchiveEntry.image(base64)`. 갤러리는 Base64 디코딩해 표시.
- **UI 리디자인** (v1.1.0): 크림+네이비+동화풍 팔레트 (`ui/theme/Theme.kt`, 라이트 전용).
  Welcome(네이비)·Home(카드형)·캔버스 떠있는 툴바.
- **릴리스**: v1.0.0(초기) → v1.0.1(로그인 동작) → v1.1.0(아카이빙+디자인). 각 릴리스에 디버그 APK 첨부.

## Next
- 시안(참고 이미지)의 미구현 화면 = **신규 기능**:
  - 여러 스케치북 목록/커버 그리드 + 하단 탭(Home/Sketchbooks) — 현재는 1방 단위 모델.
  - 용지 크기/비율 선택(New sketchbook), 계정 설정 화면(Edit avatar/Notifications/…).
  - 커스텀 손그림 마스코트/일러스트(지금은 이모지 플레이스홀더).
- 갤러리 상세 보기(이미지 탭 시 크게), 아카이브 삭제.
- `observeArchive`가 archive 노드 전체(base64 포함)를 한 번에 읽음 → 항목 많아지면
  메타데이터/이미지 경로 분리 고려.

## Decisions
- **Storage 대신 RTDB+Base64**: 사용자가 무료(Spark) 유지 선택. 카드 등록 불필요.
  이미지 작게 유지: `MAX_DIMEN=720`, `QUALITY=60`.
- **자정 아카이빙은 클라이언트(WorkManager)**: Cloud Functions 스케줄러는 Blaze 전용이라 제외.
  → 정확히 자정 보장 X(Doze/전원off), 그래서 "방 입장 시 보정"으로 신뢰성 확보.
- **라이트 테마 고정**: 따뜻한 종이 디자인이라 다크스킴 없음.

## Open / Blockers
- **빌드 JDK**: 로컬에 standalone JDK 없음. Android Studio 번들 JBR(21) 사용.
  사용자 환경변수 `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr` 설정됨.
  CLI 빌드 시 이 JAVA_HOME 필요.
- **Realtime Database 보안 규칙**: 테스트 모드일 가능성 높음 → 배포 전 `auth != null` 등으로 잠글 것.
- **Storage 미사용**: `firebase-storage` 의존성 제거함. 다시 쓰려면 Blaze 업그레이드 필요.
