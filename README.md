# G1 Sketchbook 🎨

친구·커플과 **함께 실시간으로 낙서**하는 공유 스케치북 (Android).

- **Google 로그인** (Firebase Auth)
- **실시간 공동 드로잉** — 상대가 긋는 선이 실시간으로 보임 (Firebase Realtime Database)
- **데일리 모드** — 오늘 날짜별 캔버스. 매일 새 도화지.
- **갤러리 아카이브** — 완성한 그림을 압축 WebP 1장으로 영구 저장 (용량 최소화, 휘발되지 않음)
- **방 코드**로 초대 — 6자리 코드만 알려주면 함께 그림

---

## 빠른 시작

### 1. Firebase 프로젝트 만들기
1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트 생성
2. **Android 앱 추가** — 패키지 이름은 반드시 `com.g1.sketchbook`
3. 디버그 **SHA-1** 지문을 등록 (Google 로그인에 필요). 아래 명령으로 확인:
   ```bash
   # 프로젝트 루트에서 (Windows는 Git Bash / PowerShell)
   ./gradlew signingReport
   ```
   출력의 `Variant: debug` 항목 SHA1 값을 Firebase 앱 설정에 추가.
4. `google-services.json`을 내려받아 **`app/google-services.json`을 교체** (저장소의 값은 빌드만 되는 더미).

### 2. Firebase 기능 켜기
- **Authentication → Sign-in method → Google** 사용 설정
- **Realtime Database** 생성 (아시아 리전 권장). 규칙 예시(테스트용):
  ```json
  {
    "rules": {
      "rooms": {
        "$roomId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      }
    }
  }
  ```
- **Storage** 생성. 규칙 예시:
  ```
  rules_version = '2';
  service firebase.storage {
    match /b/{bucket}/o {
      match /archive/{roomId}/{file} {
        allow read, write: if request.auth != null;
      }
    }
  }
  ```

### 3. Web client ID 넣기
Firebase 콘솔 → **프로젝트 설정 → 일반 → 웹 클라이언트 ID**(자동 생성) 또는
Google Cloud 콘솔의 OAuth 2.0 "Web client" ID를 복사해서
[`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml)의
`web_client_id` 값에 붙여넣기.

### 4. 빌드 & 설치
```bash
./gradlew :app:assembleDebug
# 기기 연결 후
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
또는 Android Studio로 열어서 실행.

---

## GitHub로 배포하기
1. 이 프로젝트를 GitHub 저장소에 push
2. 버전 태그를 밀면 GitHub Actions가 APK를 빌드해 **Release에 자동 첨부**:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. Actions 탭에서 `workflow_dispatch`로 수동 빌드도 가능 (APK가 artifact로 올라감)

> ⚠️ 배포된 APK가 **실제로 로그인/동기화되려면** 위 Firebase 설정과 실제
> `google-services.json`이 필요합니다. CI는 저장소의 더미 설정으로도 빌드는 되지만,
> 로그인은 실제 프로젝트 설정을 채운 뒤에 동작합니다.

---

## 구조
```
app/src/main/java/com/g1/sketchbook/
├─ SketchApp.kt            # Application + 수동 DI(Graph)
├─ MainActivity.kt         # 로그인 → 홈 → 캔버스/갤러리 라우팅
├─ auth/GoogleAuthClient   # Credential Manager + Firebase Auth
├─ data/
│  ├─ RoomRepository       # 방 생성/참여, 실시간 획 동기화(RTDB)
│  ├─ ArchiveRepository    # 완성본 WebP 압축 → Storage 저장
│  └─ model/Models         # Stroke / Member / ArchiveEntry
└─ ui/
   ├─ AppViewModel         # 인증/방 상태
   ├─ canvas/              # 실시간 드로잉 캔버스 + 렌더링
   └─ gallery/             # 아카이브 그리드
```

## 저장 용량 설계
- 실시간 중에는 **획(벡터) 데이터만** 주고받음 → 가볍고 빠름
- 하루가 끝나 "저장" 시 캔버스를 **최대 1080px WebP(품질 70)** 1장으로 압축 → 보통 수십 KB
- 갤러리는 이 스냅샷만 영구 보관 → 용량 최소화 + 추억은 계속 쌓임
