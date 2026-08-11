# PROGRESS — G1 Sketchbook

친구·커플용 실시간 공유 스케치북 (Android, Kotlin/Compose, Firebase).

## Done (2026-08-11)
- **프로젝트 스캐폴드**: Gradle 8.11.1 wrapper, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12, version catalog(`gradle/libs.versions.toml`). `compileSdk 35 / minSdk 24`.
- **Firebase 연동**: Auth + Realtime Database + Storage (BOM 33.7.0), google-services 플러그인. `app/google-services.json`은 **빌드용 더미** — 실제 값으로 교체 필요.
- **인증**: `auth/GoogleAuthClient` — Credential Manager(GoogleIdOption) → Firebase `signInWithCredential`. Web client ID는 `res/values/strings.xml`의 `web_client_id`에서 읽음(직접 붙여넣어야 함).
- **방(Room)**: `data/RoomRepository` — 6자리 코드(혼동문자 제외)로 방 생성/참여, 멤버 등록, 실시간 획 동기화.
- **실시간 드로잉**: `ui/canvas/` — Compose Canvas + `detectDragGestures`. 좌표는 **0~1 정규화**로 저장(기기 해상도 무관). 완성 획은 `days/{date}/strokes`에 push(ChildEventListener), 진행 중 획은 `days/{date}/live/{uid}`로 브로드캐스트(throttle 45ms, onDisconnect 정리). Undo(자기 획), 전체 지우기, 색/굵기/지우개.
- **데일리 모드 + 갤러리**: 캔버스는 오늘 날짜별. "저장" 시 `renderStrokesToBitmap` → 1080px WebP(q70) → Storage `archive/{roomId}/{date}.webp`, 메타는 RTDB `rooms/{roomId}/archive/{date}`. `ui/gallery/`가 Coil로 그리드 표시.
- **빌드 검증 완료**: `./gradlew :app:assembleDebug` → `app-debug.apk`(~19MB) 생성 성공. (JBR JDK21로 빌드)
- **배포**: `.github/workflows/release.yml` — `v*` 태그 push 시 APK 빌드 → Release 첨부, 수동 실행도 지원.
- **문서**: `README.md`에 Firebase 셋업 전 과정 정리.

## Next (실제 동작시키려면 — 코드 아님, 설정)
1. Firebase 프로젝트 생성 → `google-services.json` 교체
2. 디버그 SHA-1 등록(`./gradlew signingReport`) + Google 로그인 사용 설정
3. RTDB/Storage 규칙 적용(README 참고)
4. `strings.xml`의 `web_client_id` 채우기
5. 실기기 2대로 실시간 동기화 스모크 테스트

## Next (기능 아이디어 — 백로그)
- 라이브 커서(누가 어디 그리는지 이름표) — 지금은 진행 중 "획"만 공유
- 타임랩스/리플레이(저장된 획 순서 재생)
- 자정 자동 아카이브 + 다음날 새 캔버스 자동 전환
- 이어그리기(가틱폰) 게임 모드, 데일리 랜덤 주제
- 초대 딥링크(코드 대신 링크 탭)

## Decisions
- **네이티브 Android(Kotlin+Compose)** 선택 — 사용자가 "안드로이드 앱 + GitHub 배포" 명시.
- **Firebase Realtime Database**(Firestore 아님) — 고빈도 획 업데이트에 지연·비용 유리.
- **좌표 정규화(0~1)** — 기기 해상도 차이에도 그림이 맞게 스케일.
- **완성본만 WebP 저장** — 실시간엔 벡터만, 아카이브는 압축 이미지 → 용량 최소화(사용자 요구).
- **수동 DI(Graph)** — MVP엔 Hilt 과함.
- **google-services.json 더미 커밋** — 누구나 즉시 빌드 가능하게. 실제 배포 전 교체.

## Open / Blockers
- 로그인·동기화 **실기기 검증 미완**(실제 Firebase 프로젝트 필요, 위 Next 1~5).
- 릴리스 APK는 현재 **debug 서명**으로 배포(친구 공유엔 충분). 정식 배포 시 release 서명키 + CI secrets 필요.
- 자정 롤오버는 수동("저장" 버튼) — 자동화는 백로그.
