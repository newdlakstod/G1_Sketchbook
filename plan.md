# G1 Sketchbook — 프로젝트 계획서 (plan.md)

> 친구·커플이 하나의 방(스케치북)에서 매일 함께 그리고, 하루치 그림이 갤러리에
> 영구 보관되는 실시간 공유 스케치북 안드로이드 앱.
> _최종 갱신: 2026-08-11 · 현재 버전: v1.6.0_

---

## 1. 개요 / 컨셉
- **한 줄 소개:** "Draw together, keep the little days." — 함께 그리고 소중한 하루하루를 담는 스케치북.
- **핵심 루프:** 방(스케치북) 생성/참여 → 실시간 공동 드로잉 → 하루가 끝나면 그날 그림이 갤러리에 자동 보관.
- **타깃:** 커플·가족·친구 등 소수 인원의 사적인 공유 낙서장.
- **마스코트:** 베레모 쓴 오리(크림 라인아트).

## 2. 기술 스택
- **언어/UI:** Kotlin, Jetpack Compose (Material 3), Navigation은 상태 기반 전환.
- **백엔드:** Firebase (프로젝트 `g1-sketchbook`)
  - **Auth:** Google 로그인 (Credential Manager + `default_web_client_id`)
  - **Realtime Database:** 방/멤버/스트로크(실시간)/아카이브 저장
  - **Storage: 사용 안 함** (무료 Spark 플랜 유지 위해 이미지도 RTDB에 Base64로 저장)
- **백그라운드:** WorkManager (매일 자정 자동 아카이빙)
- **이미지 로딩:** Coil (일부), Base64 디코딩(Compose Image)
- **빌드:** Gradle 8.11.1 / AGP 8.7.3 / compileSdk 35 / minSdk 24 / JDK 17 타깃

## 3. 아키텍처
```
com.g1.sketchbook
├─ MainActivity            상태 기반 화면 전환(Login→Home→Canvas/Gallery), BackHandler
├─ SketchApp               Application, Graph(DI 수동), 자정 아카이브 스케줄 등록
├─ auth/GoogleAuthClient   Credential Manager → Firebase Auth
├─ data/
│  ├─ RoomRepository       방 생성/참여, 멤버, 스트로크(실시간/일회성)
│  ├─ ArchiveRepository    일일 스냅샷 저장(WebP→Base64→RTDB), 관찰
│  ├─ SessionStore         현재 방 + 내 스케치북 목록(SharedPreferences+JSON)
│  ├─ TaskAwait            Firebase Task→coroutine await
│  └─ model/Models         Stroke, Member, RoomMeta, ArchiveEntry, SketchbookRef
├─ ui/
│  ├─ AppViewModel         전역 상태(user/room/sketchbooks/recentEntry)
│  ├─ LoginScreen          네이비 시작 화면 + 오리 마스코트
│  ├─ HomeScreen           하단탭(홈/스케치북/내정보), 최근 썸네일, 표지 그리드
│  ├─ canvas/CanvasScreen  드로잉 캔버스(핀치 줌/팬, 툴바)
│  ├─ canvas/CanvasViewModel 스트로크 상태/동기화
│  ├─ canvas/StrokeRender  스트로크→비트맵(갤러리 스냅샷, 네이티브 Canvas)
│  ├─ canvas/Crayon        연필 그레인 텍스처(공용)
│  ├─ gallery/GalleryScreen 저장 그림 그리드
│  └─ theme/               팔레트, Shapes, PaperTexture
└─ work/DailyArchive       WorkManager 워커 + 스케줄러 + 보정 로직
```

### Realtime Database 구조
```
rooms/{roomId}/
  meta/ { name, createdBy, createdAt, members/{uid}: Member }
  days/{yyyy-MM-dd}/
    strokes/{pushId}: Stroke      // 완성된 획
    live/{uid}: Stroke            // 진행 중(실시간 브로드캐스트)
  archive/{yyyy-MM-dd}: ArchiveEntry  // 그날의 스냅샷(Base64 이미지 포함)
```

## 4. 완료된 기능 (버전 이력)
| 버전 | 내용 |
|------|------|
| v1.0.0 | 초기 MVP (실시간 드로잉, 방, 저장/갤러리 코드) |
| v1.0.1 | 실제 Firebase 연결, **Google 로그인 동작** |
| v1.1.0 | **매일 자정 자동 아카이빙**(WorkManager+보정), **무료 저장(RTDB+Base64)**, 저장 오류 해결, 크림/네이비 리디자인 |
| v1.2.0 | 뒤로가기 앱종료 버그 수정(BackHandler), **내 스케치북 목록**, 아이콘 액션, 종이 질감 |
| v1.3.0 | **크레파스/색연필 브러시 질감** |
| v1.4.0 | **핀치 확대/축소+이동**, **펜/지우개 칩**, **굵기 슬라이더**, 그런지 브러시, 샘플형 로그인 |
| v1.5.0 | **연필(삼성노트 느낌) 브러시** — 작성 중 흔들림 제거, 홈 화면 목업 반영(하단탭·최근 썸네일 카드) |
| v1.6.0 | **오리 마스코트 이미지** 적용(배경 제거), **적응형 그리드 + 스프링 노트 표지** |

## 5. 남은 작업 / 로드맵
### 단기 (다음 후보)
- [ ] 스케치북 표지 색상 정책 확정 (전부 파랑 통일 vs. 다양). 표지 전용 오리 아트 반영.
- [ ] 갤러리 상세 보기(썸네일 탭 → 확대), 아카이브 항목 삭제.
- [ ] 시안의 **용지 크기/비율 선택**(New sketchbook: A4/A5/B5, 1:1/4:3/9:16).
- [ ] **초대 기능**(코드 공유/딥링크)로 '참여' UX 개선.

### 중기
- [ ] 멤버 실시간 표시 고도화(홈 카드에 참여자 아바타).
- [ ] 계정 화면 실기능(아바타 편집, 알림 설정).
- [ ] 브러시 종류 확장(펜/크레파스/형광펜) 선택 UI.
- [ ] 오프라인 편집 후 동기화 견고화.

### 장기 / 검토
- [ ] 정식 배포용 **서명 release AAB** + Play 스토어 등록(서명 키 필요).
- [ ] 서버측 아카이빙(정확한 자정 보장) — Cloud Functions 스케줄러(**Blaze 유료 필요**).
- [ ] 이미지 저장 확장 시 RTDB Base64 → 별도 경로 분리 or Storage(Blaze).

## 6. 주요 결정사항 (재논의 방지)
- **Storage 대신 RTDB+Base64:** 무료(Spark) 유지. 이미지 작게: `MAX_DIMEN=720`, `QUALITY=60`.
- **자정 아카이빙은 클라이언트(WorkManager):** Cloud Functions는 Blaze 전용. 정확한 자정 보장은 안 되므로 **방 입장 시 어제치 보정**으로 신뢰성 확보.
- **라이트 테마 고정:** 따뜻한 종이 디자인이라 다크스킴 없음.
- **브러시:** 캔버스 좌표에 고정된 그레인(연필). 외곽 지터(DiscretePathEffect)는 작성 중 흔들려서 **제거**.
- **버전 정책:** 업로드마다 `versionName`+`versionCode` 올리고 새 `vX.Y.Z` 릴리스 생성(덮어쓰기 금지).

## 7. 제약 / 알려진 이슈
- **빌드 JDK:** 로컬 standalone JDK 없음 → Android Studio 번들 **JBR(21)** 사용.
  `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` (사용자 환경변수 설정됨).
- **Realtime Database 보안 규칙:** 테스트 모드 가능성 높음 → 배포 전 `auth != null` 등으로 **반드시 잠글 것**.
- **google-services.json / API 키:** 공개 저장소에 포함(사용자 선택). 실제 보호는 DB/Storage 보안 규칙에 의존.
- **`observeArchive`**: archive 노드 전체(Base64 포함) 일괄 로드 → 항목 많아지면 메타/이미지 분리 검토.

## 8. 빌드 & 배포
```bash
# 빌드 (JAVA_HOME을 JBR로)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat assembleDebug      # 산출물: app/build/outputs/apk/debug/app-debug.apk

# 릴리스 (버전 올린 뒤)
git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
gh release create vX.Y.Z --title "vX.Y.Z" --notes "..." <apk>
```
- **저장소:** https://github.com/newdlakstod/G1_Sketchbook
- **테스트:** 릴리스 페이지의 디버그 APK 설치(기기 "출처 불명 앱 설치" 허용).

## 9. 로그인이 안 될 때 (체크리스트)
1. Firebase Console → Authentication → **Google 사용 설정** 켜짐?
2. 앱 등록에 **디버그 SHA-1** 등록됨? (`D2:82:DC:F6:BD:C1:85:76:47:62:28:56:D7:2B:9A:8D:9A:33:FF:1B`)
3. `app/google-services.json`의 `oauth_client`에 **웹 클라이언트(client_type 3)** 존재?
4. Realtime Database / (필요 시)규칙이 접근 허용?
