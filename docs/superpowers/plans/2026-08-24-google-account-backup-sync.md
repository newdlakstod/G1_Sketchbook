# Google-Account Backup/Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Personal sketchbooks, grim diary entries, and settings automatically sync across a user's own devices (phone/tablet) via their Google account, using the same Firebase Realtime Database the app already has (no Storage, no new paid tier).

**Architecture:** A new `BackupRepository` (Firebase RTDB I/O, mirrors the existing `ShareRepository`) plus a pure `decideSyncAction` function (unit-testable, no Android/Firebase types) that both a reconciliation pass (`reconcileBackup`, run on sign-in and app-foreground) and item-level push calls (wired in at the existing local-save call sites) use for last-write-wins conflict resolution. `SketchbookRepository`/`DiaryRepository`/`SessionStore` stay pure-local; sync calls sit beside their existing local-save calls, matching how `SharedBookScreen.kt` already calls `sbRepo.savePage(...)` and `share.pushSnapshot(...)` side by side.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Realtime Database + Auth (already in the project), `kotlin.test` for unit tests (see `CoverEditSelectionTest.kt` for the existing style).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-24-google-account-backup-sync-design.md` — read it before starting, this plan implements it with one schema refinement noted in Task 5 (per-page/per-cover `updatedAt`, needed for correct conflict resolution — the spec's schema sketch was flat `base64` without a timestamp).
- Firebase RTDB free tier only — no Storage, no Blaze plan. Images are JPEG, longest edge 1800px, quality 90.
- Shared sketchbooks (`Sketchbook.shared == true`) are excluded from this feature entirely — never read or write them here.
- Every push is fire-and-forget (no error UI) — matches the existing `ShareRepository.pushSnapshot` pattern.
- Settings sync pushes the whole settings blob as one item, not per-field — triggered only from `RootViewModel` (theme, avatar) and the app's `ON_STOP` lifecycle event (catches everything else: brush color/size/opacity, eraser settings, gestures, grid columns). Do not add push calls to individual brush/gesture/grid-column setters — that's deliberately out of scope for this plan (see spec's "동기화 트리거" section; the `ON_STOP` catch-all is what covers those without needing dozens of call-site edits).

---

## File Structure

New files:
- `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt` — pure data classes + `decideSyncAction` (no Android/Firebase imports; unit-testable).
- `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt` — Firebase RTDB reads/writes, JPEG encode/decode.
- `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt` — orchestration: `reconcileBackup` (full pull+merge pass) and `syncSettingsUp` (push current settings).
- `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt` — thin wrappers pairing each `SketchbookRepository` mutator with a matching backup push, shared by the Home tab and List tab (both call the same `SketchbookRepository` mutators independently today).
- `app/src/test/java/com/g1/sketchbook/backup/BackupModelsTest.kt` — tests for `decideSyncAction`.

Modified files:
- `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt` — add `updatedAt` field, `upsert()`, `pageUpdatedAt()`, `coverUpdatedAt()`.
- `app/src/main/java/com/g1/sketchbook/diary/DiaryRepository.kt` — add `listDates()`, `updatedAt()`.
- `app/src/main/java/com/g1/sketchbook/data/SessionStore.kt` — add `settingsSyncedAt`.
- `app/src/main/java/com/g1/sketchbook/SketchApp.kt` — add `BackupRepository` to `Graph`.
- `app/src/main/java/com/g1/sketchbook/ui/RootViewModel.kt` — `syncNow`, `flushSettings`, wire into `signIn`/`setTheme`/`setAvatarImage`.
- `app/src/main/java/com/g1/sketchbook/MainActivity.kt` — lifecycle observer for `ON_START` (pull) / `ON_STOP` (settings flush); pass `myUid` into `DiaryEditorScreen`.
- `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt` — wire `SketchbookSync.kt` helpers into `CreateWizard`/`SketchbookTab`/`SketchbookCanvasScreen`.
- `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt` — wire the same helpers into `HomeTab`'s cover-edit/delete flow.
- `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt` — push after each local diary save; accept `myUid`.

---

### Task 1: Pure sync-decision logic

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Test: `app/src/test/java/com/g1/sketchbook/backup/BackupModelsTest.kt`

**Interfaces:**
- Produces: `SyncAction` enum (`PUSH`, `PULL`, `DELETE_LOCAL`, `NOOP`), `fun decideSyncAction(localUpdatedAt: Long?, remoteUpdatedAt: Long?, remoteDeleted: Boolean = false): SyncAction`, and the plain data classes `RemoteSketchbook`, `RemoteSnapshot`, `RemoteSettings` (used by Task 5/6).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.g1.sketchbook.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupModelsTest {
    @Test fun bothMissingIsNoop() {
        assertEquals(SyncAction.NOOP, decideSyncAction(null, null))
    }

    @Test fun onlyRemoteExistsPulls() {
        assertEquals(SyncAction.PULL, decideSyncAction(null, 100L))
    }

    @Test fun onlyLocalExistsPushes() {
        assertEquals(SyncAction.PUSH, decideSyncAction(100L, null))
    }

    @Test fun remoteNewerPulls() {
        assertEquals(SyncAction.PULL, decideSyncAction(100L, 200L))
    }

    @Test fun localNewerPushes() {
        assertEquals(SyncAction.PUSH, decideSyncAction(200L, 100L))
    }

    @Test fun tieGoesToLocalPush() {
        assertEquals(SyncAction.PUSH, decideSyncAction(100L, 100L))
    }

    @Test fun remoteTombstoneWithLocalCopyDeletesLocal() {
        assertEquals(SyncAction.DELETE_LOCAL, decideSyncAction(100L, 999L, remoteDeleted = true))
    }

    @Test fun remoteTombstoneWithNoLocalCopyIsNoop() {
        assertEquals(SyncAction.NOOP, decideSyncAction(null, 999L, remoteDeleted = true))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.backup.BackupModelsTest"`
Expected: FAIL (compile error — `decideSyncAction`/`SyncAction` don't exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.g1.sketchbook.backup

/** One sketchbook's page/cover/meta as read from Firebase — plain data, no Android/Firebase types,
 *  so [decideSyncAction] can be unit-tested without a device or emulator. */
data class RemoteSketchbook(
    val id: String, val name: String, val sizeKey: String, val bgKey: String,
    val createdAt: Long, val pageCount: Int, val fav: Boolean, val coverColor: Long?,
    val updatedAt: Long, val deleted: Boolean,
    val coverBase64: String?, val coverUpdatedAt: Long?,
    val pages: Map<Int, Pair<Long, String>>, // index -> (updatedAt, base64)
)

data class RemoteSnapshot(
    val sketchbooks: List<RemoteSketchbook>,
    val diary: Map<String, Pair<Long, String>>, // date -> (updatedAt, base64)
    val settings: RemoteSettings?,
)

data class RemoteSettings(
    val nickname: String?, val themeMode: String, val favoriteColors: List<Long>,
    val gesture2Tap: String, val gesture3Tap: String, val gestureLongPress: String,
    val gridColumns: Int, val brushColor: Long,
    val brushSizes: Map<String, Float>, val brushOpacities: Map<String, Float>,
    val eraserSize: Float, val eraserOpacity: Float, val eraserBlur: Float,
    val avatarBase64: String?, val updatedAt: Long,
)

/** What to do with one synced item (a sketchbook's meta, one page, one cover, one diary day). */
enum class SyncAction { PUSH, PULL, DELETE_LOCAL, NOOP }

/** [localUpdatedAt]/[remoteUpdatedAt] are epoch millis; null means the item doesn't exist on that
 *  side. [remoteDeleted] is only meaningful for sketchbooks (tombstone) — when true every other
 *  field is ignored: the item is deleted locally if we still have it, otherwise left alone. Ties
 *  (equal timestamps) resolve to PUSH — harmless, since pushing identical data is a no-op remotely. */
fun decideSyncAction(localUpdatedAt: Long?, remoteUpdatedAt: Long?, remoteDeleted: Boolean = false): SyncAction {
    if (remoteDeleted) return if (localUpdatedAt != null) SyncAction.DELETE_LOCAL else SyncAction.NOOP
    return when {
        localUpdatedAt == null && remoteUpdatedAt == null -> SyncAction.NOOP
        localUpdatedAt == null -> SyncAction.PULL
        remoteUpdatedAt == null -> SyncAction.PUSH
        remoteUpdatedAt > localUpdatedAt -> SyncAction.PULL
        else -> SyncAction.PUSH
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.g1.sketchbook.backup.BackupModelsTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/test/java/com/g1/sketchbook/backup/BackupModelsTest.kt
git commit -m "feat(backup): add pure sync-decision logic (decideSyncAction)"
```

---

### Task 2: SketchbookRepository — timestamps and restore support

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Consumes: existing `Sketchbook`, `list()`, `save()`, `pageFile()`, `coverFile()`.
- Produces: `Sketchbook.updatedAt: Long`, `fun upsert(book: Sketchbook)`, `fun pageUpdatedAt(id: String, index: Int): Long`, `fun coverUpdatedAt(id: String): Long`.

- [ ] **Step 1: Add `updatedAt` to the `Sketchbook` data class**

In `SketchbookRepository.kt`, change:

```kotlin
data class Sketchbook(
    val id: String,
    val name: String,
    val sizeKey: String,
    val bgKey: String,
    val createdAt: Long,
    val pageCount: Int,
    val fav: Boolean = false,
    val shared: Boolean = false,   // a "draw together" book, grouped separately
    val code: String? = null,      // invite/session code for shared books
    val coverColor: Long? = null,  // custom solid cover colour (ARGB); null = default yellow
    /** 표지 이미지 파일이 바뀔 때마다 올라간다 — id는 그대로라 LaunchedEffect(book.id)만으론 새
     *  파일을 다시 읽어오지 않으므로, 이 값을 키에 함께 넣어 캐시를 무효화한다. */
    val coverVersion: Int = 0,
) {
```

to:

```kotlin
data class Sketchbook(
    val id: String,
    val name: String,
    val sizeKey: String,
    val bgKey: String,
    val createdAt: Long,
    val pageCount: Int,
    val fav: Boolean = false,
    val shared: Boolean = false,   // a "draw together" book, grouped separately
    val code: String? = null,      // invite/session code for shared books
    val coverColor: Long? = null,  // custom solid cover colour (ARGB); null = default yellow
    /** 표지 이미지 파일이 바뀔 때마다 올라간다 — id는 그대로라 LaunchedEffect(book.id)만으론 새
     *  파일을 다시 읽어오지 않으므로, 이 값을 키에 함께 넣어 캐시를 무효화한다. */
    val coverVersion: Int = 0,
    /** 메타(이름/즐겨찾기/표지색/표지버전)가 마지막으로 바뀐 시각 — 구글 계정 백업 동기화의
     *  last-write-wins 비교에 쓰인다. 새로 만들 때(create)는 기본값(호출 시점)이 곧 맞는 값이라
     *  따로 안 넘겨도 된다. */
    val updatedAt: Long = System.currentTimeMillis(),
) {
```

- [ ] **Step 2: Read/write `updatedAt` in the JSON (de)serializers**

In `list()`, change:

```kotlin
                Sketchbook(o.getString("id"), o.getString("name"), o.getString("size"),
                    o.getString("bg"), o.optLong("createdAt"), o.optInt("pages", 1), o.optBoolean("fav", false),
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null },
                    o.optLong("coverColor", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }, o.optInt("coverVer", 0))
```

to:

```kotlin
                Sketchbook(o.getString("id"), o.getString("name"), o.getString("size"),
                    o.getString("bg"), o.optLong("createdAt"), o.optInt("pages", 1), o.optBoolean("fav", false),
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null },
                    o.optLong("coverColor", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }, o.optInt("coverVer", 0),
                    o.optLong("updatedAt", o.optLong("createdAt")))
```

In `save()`, change:

```kotlin
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: "")
                .put("coverColor", it.coverColor ?: Long.MIN_VALUE).put("coverVer", it.coverVersion))
```

to:

```kotlin
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: "")
                .put("coverColor", it.coverColor ?: Long.MIN_VALUE).put("coverVer", it.coverVersion)
                .put("updatedAt", it.updatedAt))
```

- [ ] **Step 3: Bump `updatedAt` in the meta mutators**

Change `toggleFav`, `rename`, `setCoverColor`, and `bumpCoverVersion` from:

```kotlin
    fun toggleFav(id: String) {
        save(list().map { if (it.id == id) it.copy(fav = !it.fav) else it })
    }

    /** 표지 길게 눌러 수정하기 — 이름만 바꾼다(사이즈·종이 재질은 이미 그려둔 페이지에 쓰이므로 제외). */
    fun rename(id: String, name: String) {
        save(list().map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })
    }
```

to:

```kotlin
    fun toggleFav(id: String) {
        save(list().map { if (it.id == id) it.copy(fav = !it.fav, updatedAt = System.currentTimeMillis()) else it })
    }

    /** 표지 길게 눌러 수정하기 — 이름만 바꾼다(사이즈·종이 재질은 이미 그려둔 페이지에 쓰이므로 제외). */
    fun rename(id: String, name: String) {
        save(list().map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, updatedAt = System.currentTimeMillis()) else it })
    }
```

and:

```kotlin
    fun setCoverColor(id: String, color: Long?) {
        save(list().map { if (it.id == id) it.copy(coverColor = color) else it })
    }

    /** id는 그대로 유지되는 book 갱신이라 `LaunchedEffect(book.id)`만으론 목록 썸네일이 새 표지
     *  이미지를 다시 읽어오지 않는다 — 이 값을 실제로 바꿔서 캐시를 무효화시킨다. */
    private fun bumpCoverVersion(id: String) {
        save(list().map { if (it.id == id) it.copy(coverVersion = it.coverVersion + 1) else it })
    }
```

to:

```kotlin
    fun setCoverColor(id: String, color: Long?) {
        save(list().map { if (it.id == id) it.copy(coverColor = color, updatedAt = System.currentTimeMillis()) else it })
    }

    /** id는 그대로 유지되는 book 갱신이라 `LaunchedEffect(book.id)`만으론 목록 썸네일이 새 표지
     *  이미지를 다시 읽어오지 않는다 — 이 값을 실제로 바꿔서 캐시를 무효화시킨다. */
    private fun bumpCoverVersion(id: String) {
        save(list().map { if (it.id == id) it.copy(coverVersion = it.coverVersion + 1, updatedAt = System.currentTimeMillis()) else it })
    }
```

- [ ] **Step 4: Add `upsert`, `pageUpdatedAt`, `coverUpdatedAt`**

Add these members (e.g. right after `delete`):

```kotlin
    /** [book]을 그대로 넣는다(같은 id가 있으면 교체, 없으면 추가) — [create]와 달리 새 id를 만들지
     *  않는다. 구글 계정 백업에서 다른 기기가 만든 스케치북을 복원할 때 씀 — 클라우드의 id를
     *  그대로 로컬 id로 써야 다음 동기화 때도 같은 항목으로 계속 매칭된다. */
    fun upsert(book: Sketchbook) {
        val current = list()
        val next = if (current.any { it.id == book.id }) current.map { if (it.id == book.id) book else it } else current + book
        save(next)
        File(root, book.id).mkdirs()
    }
```

Add these members right after `savePage`:

```kotlin
    /** 페이지 파일이 마지막으로 저장된 시각 — 파일시스템 mtime을 그대로 씀(별도 타임스탬프 저장 불
     *  필요). 안 그려진 페이지는 0을 반환한다(파일이 없으면 File.lastModified()는 0). */
    fun pageUpdatedAt(id: String, index: Int): Long = pageFile(id, index).lastModified()
```

Add this member right after `removeCover`:

```kotlin
    /** 표지 파일이 마지막으로 저장된 시각 — [pageUpdatedAt]과 같은 이유로 mtime을 그대로 씀. */
    fun coverUpdatedAt(id: String): Long = coverFile(id).lastModified()
```

- [ ] **Step 5: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "feat(sketchbook): track updatedAt, add upsert/pageUpdatedAt/coverUpdatedAt for backup sync"
```

---

### Task 3: DiaryRepository — enumerate local dates and expose mtime

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryRepository.kt`

**Interfaces:**
- Produces: `fun listDates(): List<String>`, `fun updatedAt(date: String): Long`.

- [ ] **Step 1: Add the two functions**

Change:

```kotlin
    fun save(date: String, bmp: Bitmap) {
        FileOutputStream(file(date)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
```

to:

```kotlin
    fun save(date: String, bmp: Bitmap) {
        FileOutputStream(file(date)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 로컬에 그림이 있는 모든 날짜("yyyy-MM-dd") — 백업 동기화가 "이 기기에만 있고 아직 클라우드에
     *  안 올라간 일기"를 찾을 때 쓴다(달력 UI는 안 씀, 그건 월 단위로 하루씩 hasEntry로 확인). */
    fun listDates(): List<String> = dir.listFiles { f -> f.name.endsWith(".png") }
        ?.map { it.name.removeSuffix(".png") } ?: emptyList()

    /** 해당 날짜 파일이 마지막으로 저장된 시각 — 파일시스템 mtime. 항목이 없으면 0. */
    fun updatedAt(date: String): Long = file(date).lastModified()
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/diary/DiaryRepository.kt
git commit -m "feat(diary): add listDates/updatedAt for backup sync"
```

---

### Task 4: SessionStore — settings sync marker

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/data/SessionStore.kt`

**Interfaces:**
- Produces: `var settingsSyncedAt: Long`.

- [ ] **Step 1: Add the field**

Change:

```kotlin
    var eraserBlur: Float
        get() = prefs.getFloat(KEY_ERASER_BLUR, 0f)
        set(value) = prefs.edit().putFloat(KEY_ERASER_BLUR, value).apply()

    private fun defaultBrushSize(type: BrushType): Float = when (type) {
```

to:

```kotlin
    var eraserBlur: Float
        get() = prefs.getFloat(KEY_ERASER_BLUR, 0f)
        set(value) = prefs.edit().putFloat(KEY_ERASER_BLUR, value).apply()

    /** 마지막으로 구글 계정 백업과 설정값을 맞춘 시각(0 = 한 번도 안 함) — 클라우드 설정값의
     *  updatedAt과 비교해서 더 최신이면 받아올지 판단하는 데 쓴다. 설정값은 필드마다 따로가
     *  아니라 통째로 한 항목으로 동기화하므로, 이 값 하나면 충분하다. */
    var settingsSyncedAt: Long
        get() = prefs.getLong(KEY_SETTINGS_SYNCED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SETTINGS_SYNCED_AT, value).apply()

    private fun defaultBrushSize(type: BrushType): Float = when (type) {
```

And add the key constant — change:

```kotlin
        private const val KEY_ERASER_BLUR = "eraser_blur"
        val DefaultFavorites = listOf(0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L)
```

to:

```kotlin
        private const val KEY_ERASER_BLUR = "eraser_blur"
        private const val KEY_SETTINGS_SYNCED_AT = "settings_synced_at"
        val DefaultFavorites = listOf(0xFF1E2D4CL, 0xFFACBDAAL, 0xFFE05454L, 0xFFE0A53CL, 0xFF6E9646L)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/data/SessionStore.kt
git commit -m "feat(settings): add settingsSyncedAt marker for backup sync"
```

---

### Task 5: BackupRepository — Firebase I/O

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`

**Interfaces:**
- Consumes: `RemoteSketchbook`, `RemoteSnapshot`, `RemoteSettings` (Task 1), `com.g1.sketchbook.sketchbook.Sketchbook`/`MAX_PAGES`, `com.g1.sketchbook.data.await`.
- Produces: `class BackupRepository` with `pushSketchbookMeta`, `pushSketchbookCover`, `deleteSketchbookCover`, `pushSketchbookPage`, `deleteSketchbook`, `pushDiaryDay`, `pushSettings`, `suspend fun pullAll(uid: String): RemoteSnapshot`, `fun decodeImage(base64: String): Bitmap?`.

**Note on the RTDB schema** (refines the spec's sketch): pages and the cover each need their own `updatedAt` for `decideSyncAction` to work, so they're stored as `{ updatedAt, image }` objects rather than a flat base64 string:

```
backups/{uid}/
  sketchbooks/{bookId}/
    meta: { name, sizeKey, bgKey, createdAt, pageCount, fav, coverColor, updatedAt }
    deleted: true                     (tombstone; when present the other fields are stale/ignored)
    cover: { updatedAt, image: base64 }
    pages/{index}: { updatedAt, image: base64 }
  diary/{date}/ { updatedAt, image: base64 }
  settings/ { nickname, themeMode, favoriteColors, gesture2Tap, gesture3Tap, gestureLongPress,
              gridColumns, brushColor, brushSizes, brushOpacities, eraserSize, eraserOpacity,
              eraserBlur, avatarImage: base64, updatedAt }
```

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.g1.sketchbook.data.await
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Google-account backup: personal sketchbooks, diary, and settings synced across a user's devices
 * via Firebase Realtime Database (base64-encoded JPEGs — no Firebase Storage, see
 * docs/superpowers/specs/2026-08-24-google-account-backup-sync-design.md for why). Shared
 * sketchbooks are NOT covered here — those already sync live via ShareRepository.
 */
class BackupRepository {
    private val root = FirebaseDatabase.getInstance().reference.child("backups")

    private fun encode(bmp: Bitmap, maxSide: Int = 1800, quality: Int = 90): String {
        val s = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeImage(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun pushSketchbookMeta(uid: String, book: Sketchbook) {
        root.child(uid).child("sketchbooks").child(book.id).child("meta").setValue(
            mapOf(
                "name" to book.name, "sizeKey" to book.sizeKey, "bgKey" to book.bgKey,
                "createdAt" to book.createdAt, "pageCount" to book.pageCount, "fav" to book.fav,
                "coverColor" to (book.coverColor ?: Long.MIN_VALUE), "updatedAt" to book.updatedAt,
            ),
        )
    }

    fun pushSketchbookCover(uid: String, bookId: String, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover")
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    fun deleteSketchbookCover(uid: String, bookId: String) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover").removeValue()
    }

    fun pushSketchbookPage(uid: String, bookId: String, index: Int, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("pages").child(index.toString())
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    /** Tombstones the book instead of removing the node outright — a hard remove would look like
     *  "never existed" to another device's next pull, which would resurrect it as a brand-new push. */
    fun deleteSketchbook(uid: String, bookId: String) {
        root.child(uid).child("sketchbooks").child(bookId)
            .setValue(mapOf("deleted" to true, "updatedAt" to ServerValue.TIMESTAMP))
    }

    fun pushDiaryDay(uid: String, date: String, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("diary").child(date)
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    /** [avatarBmp] is encoded here (mirrors the other pushX functions taking a raw Bitmap) —
     *  [record].avatarBase64 is a read-direction-only field (populated by [pullAll]), ignored here. */
    fun pushSettings(uid: String, record: RemoteSettings, avatarBmp: Bitmap?) {
        val payload = mutableMapOf<String, Any?>(
            "nickname" to record.nickname, "themeMode" to record.themeMode,
            "favoriteColors" to record.favoriteColors, "gesture2Tap" to record.gesture2Tap,
            "gesture3Tap" to record.gesture3Tap, "gestureLongPress" to record.gestureLongPress,
            "gridColumns" to record.gridColumns, "brushColor" to record.brushColor,
            "brushSizes" to record.brushSizes, "brushOpacities" to record.brushOpacities,
            "eraserSize" to record.eraserSize, "eraserOpacity" to record.eraserOpacity,
            "eraserBlur" to record.eraserBlur, "updatedAt" to record.updatedAt,
        )
        if (avatarBmp != null) payload["avatarImage"] = encode(avatarBmp)
        root.child(uid).child("settings").setValue(payload)
    }

    suspend fun pullAll(uid: String): RemoteSnapshot {
        val snap = root.child(uid).get().await()

        val sketchbooks = snap.child("sketchbooks").children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            val meta = c.child("meta")
            val pages = c.child("pages").children.mapNotNull { pc ->
                val idx = pc.key?.toIntOrNull() ?: return@mapNotNull null
                val updatedAt = pc.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
                val image = pc.child("image").getValue(String::class.java) ?: return@mapNotNull null
                idx to (updatedAt to image)
            }.toMap()
            RemoteSketchbook(
                id = id,
                name = meta.child("name").getValue(String::class.java) ?: "",
                sizeKey = meta.child("sizeKey").getValue(String::class.java) ?: "a4",
                bgKey = meta.child("bgKey").getValue(String::class.java) ?: "watercolor",
                createdAt = meta.child("createdAt").getValue(Long::class.java) ?: 0L,
                pageCount = meta.child("pageCount").getValue(Int::class.java) ?: MAX_PAGES,
                fav = meta.child("fav").getValue(Boolean::class.java) ?: false,
                coverColor = meta.child("coverColor").getValue(Long::class.java)?.takeIf { it != Long.MIN_VALUE },
                updatedAt = meta.child("updatedAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
                coverBase64 = c.child("cover").child("image").getValue(String::class.java),
                coverUpdatedAt = c.child("cover").child("updatedAt").getValue(Long::class.java),
                pages = pages,
            )
        }

        val diary = snap.child("diary").children.mapNotNull { c ->
            val date = c.key ?: return@mapNotNull null
            val updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
            val image = c.child("image").getValue(String::class.java) ?: return@mapNotNull null
            date to (updatedAt to image)
        }.toMap()

        val s = snap.child("settings")
        val settings = if (!s.exists()) null else RemoteSettings(
            nickname = s.child("nickname").getValue(String::class.java),
            themeMode = s.child("themeMode").getValue(String::class.java) ?: "system",
            favoriteColors = s.child("favoriteColors").children.mapNotNull { it.getValue(Long::class.java) },
            gesture2Tap = s.child("gesture2Tap").getValue(String::class.java) ?: "NONE",
            gesture3Tap = s.child("gesture3Tap").getValue(String::class.java) ?: "NONE",
            gestureLongPress = s.child("gestureLongPress").getValue(String::class.java) ?: "NONE",
            gridColumns = s.child("gridColumns").getValue(Int::class.java) ?: 3,
            brushColor = s.child("brushColor").getValue(Long::class.java) ?: 0xFF1E2D4CL,
            brushSizes = s.child("brushSizes").children.associate {
                (it.key ?: "") to (it.getValue(Double::class.java)?.toFloat() ?: 0f)
            },
            brushOpacities = s.child("brushOpacities").children.associate {
                (it.key ?: "") to (it.getValue(Double::class.java)?.toFloat() ?: 100f)
            },
            eraserSize = s.child("eraserSize").getValue(Double::class.java)?.toFloat() ?: 10f,
            eraserOpacity = s.child("eraserOpacity").getValue(Double::class.java)?.toFloat() ?: 100f,
            eraserBlur = s.child("eraserBlur").getValue(Double::class.java)?.toFloat() ?: 0f,
            avatarBase64 = s.child("avatarImage").getValue(String::class.java),
            updatedAt = s.child("updatedAt").getValue(Long::class.java) ?: 0L,
        )

        return RemoteSnapshot(sketchbooks, diary, settings)
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt
git commit -m "feat(backup): add BackupRepository (Firebase RTDB push/pull)"
```

---

### Task 6: BackupSync — reconciliation orchestration

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: `SketchbookRepository` (Task 2: `upsert`, `pageUpdatedAt`, `coverUpdatedAt`), `DiaryRepository` (Task 3: `listDates`, `updatedAt`), `SessionStore` (Task 4: `settingsSyncedAt`), `BackupRepository` (Task 5), `decideSyncAction`/`SyncAction`/`RemoteSketchbook`/`RemoteSnapshot`/`RemoteSettings` (Task 1).
- Produces: `suspend fun reconcileBackup(context: Context, uid: String, backup: BackupRepository)`, `fun syncSettingsUp(session: SessionStore, backup: BackupRepository, uid: String)`.

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.backup

import android.content.Context
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.diary.DiaryRepository
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository

/** Runs one full reconcile pass: pulls everything from the cloud, compares against local state
 *  item-by-item via [decideSyncAction], and applies whichever side is newer. Called on sign-in and
 *  whenever the app returns to the foreground (see MainActivity's lifecycle observer) — this is
 *  what makes phone/tablet sync "automatic" without a manual button. */
suspend fun reconcileBackup(context: Context, uid: String, backup: BackupRepository) {
    val sketchbookRepo = SketchbookRepository(context)
    val diaryRepo = DiaryRepository(context)
    val session = SessionStore(context)
    val remote = backup.pullAll(uid)

    reconcileSketchbooks(sketchbookRepo, backup, uid, remote.sketchbooks)
    reconcileDiary(diaryRepo, backup, uid, remote.diary)
    reconcileSettings(session, backup, uid, remote.settings)
}

private fun reconcileSketchbooks(repo: SketchbookRepository, backup: BackupRepository, uid: String, remote: List<RemoteSketchbook>) {
    val local = repo.list().filter { !it.shared }
    val remoteById = remote.associateBy { it.id }
    val allIds = (local.map { it.id } + remote.map { it.id }).toSet()

    for (id in allIds) {
        val l = local.firstOrNull { it.id == id }
        val r = remoteById[id]

        when (decideSyncAction(l?.updatedAt, r?.updatedAt, r?.deleted ?: false)) {
            SyncAction.DELETE_LOCAL -> repo.delete(id)
            SyncAction.PULL -> if (r != null) {
                repo.upsert(Sketchbook(id, r.name, r.sizeKey, r.bgKey, r.createdAt, r.pageCount, r.fav, coverColor = r.coverColor, updatedAt = r.updatedAt))
            }
            SyncAction.PUSH -> if (l != null) backup.pushSketchbookMeta(uid, l)
            SyncAction.NOOP -> {}
        }
        if (r?.deleted == true) continue // just tombstoned/deleted locally above — nothing else to sync for it

        val localCoverAt = repo.loadCover(id)?.let { repo.coverUpdatedAt(id) }
        when (decideSyncAction(localCoverAt, r?.coverUpdatedAt)) {
            SyncAction.PULL -> r?.coverBase64?.let(backup::decodeImage)?.let { repo.saveCover(id, it) }
            SyncAction.PUSH -> repo.loadCover(id)?.let { backup.pushSketchbookCover(uid, id, it, repo.coverUpdatedAt(id)) }
            else -> {}
        }

        val pageCount = maxOf(l?.pageCount ?: 0, r?.pageCount ?: MAX_PAGES)
        for (index in 0 until pageCount) {
            val localPageAt = repo.loadPage(id, index)?.let { repo.pageUpdatedAt(id, index) }
            val remotePage = r?.pages?.get(index)
            when (decideSyncAction(localPageAt, remotePage?.first)) {
                SyncAction.PULL -> remotePage?.let { backup.decodeImage(it.second) }?.let { repo.savePage(id, index, it) }
                SyncAction.PUSH -> repo.loadPage(id, index)?.let { backup.pushSketchbookPage(uid, id, index, it, repo.pageUpdatedAt(id, index)) }
                else -> {}
            }
        }
    }
}

private fun reconcileDiary(repo: DiaryRepository, backup: BackupRepository, uid: String, remote: Map<String, Pair<Long, String>>) {
    val allDates = (repo.listDates() + remote.keys).toSet()
    for (date in allDates) {
        val localAt = if (repo.hasEntry(date)) repo.updatedAt(date) else null
        val remotePair = remote[date]
        when (decideSyncAction(localAt, remotePair?.first)) {
            SyncAction.PULL -> remotePair?.let { backup.decodeImage(it.second) }?.let { repo.save(date, it) }
            SyncAction.PUSH -> repo.load(date)?.let { backup.pushDiaryDay(uid, date, it, repo.updatedAt(date)) }
            SyncAction.DELETE_LOCAL, SyncAction.NOOP -> {} // diary has no delete feature — tombstones never occur
        }
    }
}

/** Settings always "exist" locally (SessionStore has defaults from the start), so unlike sketchbook
 *  pages/covers there's no PUSH-if-local-exists-and-remote-doesn't case to weigh — this is simpler
 *  than [decideSyncAction]: seed the cloud on first ever sync, otherwise pull only if the cloud is
 *  strictly newer than what we last synced from (frequent local changes — brush size, gestures —
 *  are pushed by the ON_STOP catch-all in MainActivity, not tracked field-by-field here). */
private fun reconcileSettings(session: SessionStore, backup: BackupRepository, uid: String, remote: RemoteSettings?) {
    if (remote == null) {
        syncSettingsUp(session, backup, uid)
    } else if (remote.updatedAt > session.settingsSyncedAt) {
        applyRemoteSettings(session, backup, remote)
    }
}

/** Pushes the current settings snapshot (including the avatar image, if any) and records the sync
 *  point. Called both by [reconcileSettings] and directly by RootViewModel/MainActivity whenever a
 *  setting changes that should sync right away (theme, avatar) or when the app backgrounds. */
fun syncSettingsUp(session: SessionStore, backup: BackupRepository, uid: String) {
    val now = System.currentTimeMillis()
    val record = RemoteSettings(
        nickname = session.nickname, themeMode = session.themeMode, favoriteColors = session.favoriteColors,
        gesture2Tap = session.twoFingerTapAction.name, gesture3Tap = session.threeFingerTapAction.name,
        gestureLongPress = session.longPressAction.name, gridColumns = session.gridColumns,
        brushColor = session.brushColor,
        brushSizes = BrushType.entries.associate { it.name to session.brushSize(it) },
        brushOpacities = BrushType.entries.associate { it.name to session.brushOpacity(it) },
        eraserSize = session.eraserSize, eraserOpacity = session.eraserOpacity, eraserBlur = session.eraserBlur,
        avatarBase64 = null, updatedAt = now,
    )
    backup.pushSettings(uid, record, session.loadAvatarImage())
    session.settingsSyncedAt = now
}

private fun applyRemoteSettings(session: SessionStore, backup: BackupRepository, r: RemoteSettings) {
    if (r.nickname != null) session.nickname = r.nickname
    session.themeMode = r.themeMode
    if (r.favoriteColors.size == 5) session.favoriteColors = r.favoriteColors
    session.twoFingerTapAction = runCatching { GestureAction.valueOf(r.gesture2Tap) }.getOrDefault(GestureAction.NONE)
    session.threeFingerTapAction = runCatching { GestureAction.valueOf(r.gesture3Tap) }.getOrDefault(GestureAction.NONE)
    session.longPressAction = runCatching { GestureAction.valueOf(r.gestureLongPress) }.getOrDefault(GestureAction.NONE)
    session.gridColumns = r.gridColumns
    session.brushColor = r.brushColor
    BrushType.entries.forEach { t ->
        r.brushSizes[t.name]?.let { session.setBrushSize(t, it) }
        r.brushOpacities[t.name]?.let { session.setBrushOpacity(t, it) }
    }
    session.eraserSize = r.eraserSize
    session.eraserOpacity = r.eraserOpacity
    session.eraserBlur = r.eraserBlur
    r.avatarBase64?.let { b64 -> backup.decodeImage(b64)?.let { session.saveAvatarImage(it) } }
    session.settingsSyncedAt = r.updatedAt
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt
git commit -m "feat(backup): add reconcileBackup/syncSettingsUp orchestration"
```

---

### Task 7: Wire BackupRepository into the app graph

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/SketchApp.kt`

**Interfaces:**
- Produces: `Graph.backupRepository: BackupRepository`.

- [ ] **Step 1: Add it to `Graph`**

Change:

```kotlin
package com.g1.sketchbook

import android.app.Application
import com.g1.sketchbook.auth.GoogleAuthClient
import com.g1.sketchbook.data.SessionStore

/** Simple manual DI container. Held by the Application so ViewModels can reach shared singletons. */
class Graph(app: Application) {
    val sessionStore = SessionStore(app)
    val authClient = GoogleAuthClient(
        context = app,
        // Auto-generated by the google-services plugin from google-services.json (web OAuth client).
        webClientId = app.getString(R.string.default_web_client_id),
    )
}
```

to:

```kotlin
package com.g1.sketchbook

import android.app.Application
import com.g1.sketchbook.auth.GoogleAuthClient
import com.g1.sketchbook.backup.BackupRepository
import com.g1.sketchbook.data.SessionStore

/** Simple manual DI container. Held by the Application so ViewModels can reach shared singletons. */
class Graph(app: Application) {
    val sessionStore = SessionStore(app)
    val authClient = GoogleAuthClient(
        // Auto-generated by the google-services plugin from google-services.json (web OAuth client).
        webClientId = app.getString(R.string.default_web_client_id),
    )
    val backupRepository = BackupRepository()
}
```

(Note: `GoogleAuthClient`'s constructor no longer takes `context` — that was already changed in an earlier session to fix Google sign-in on tablets; this diff keeps that change and only adds `backupRepository`.)

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/SketchApp.kt
git commit -m "feat(backup): wire BackupRepository into the app graph"
```

---

### Task 8: RootViewModel — sync triggers

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/RootViewModel.kt`

**Interfaces:**
- Consumes: `graph.backupRepository` (Task 7), `reconcileBackup`/`syncSettingsUp` (Task 6).
- Produces: `fun syncNow(context: Context)`, `fun flushSettings()`.

- [ ] **Step 1: Add imports**

Change:

```kotlin
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

to:

```kotlin
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.backup.reconcileBackup
import com.g1.sketchbook.backup.syncSettingsUp
import com.g1.sketchbook.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: Trigger a sync right after sign-in succeeds**

Change:

```kotlin
    fun signIn(activityContext: Context) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            graph.authClient.signIn(activityContext).fold(
                onSuccess = { user ->
                    val nick = graph.sessionStore.nickname
                    _state.value = _state.value.copy(
                        user = user, busy = false, uid = user.uid,
                        needsNickname = nick.isNullOrBlank(), nickname = nick,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "로그인 실패")
                },
            )
        }
    }
```

to:

```kotlin
    fun signIn(activityContext: Context) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            graph.authClient.signIn(activityContext).fold(
                onSuccess = { user ->
                    val nick = graph.sessionStore.nickname
                    _state.value = _state.value.copy(
                        user = user, busy = false, uid = user.uid,
                        needsNickname = nick.isNullOrBlank(), nickname = nick,
                    )
                    syncNow(activityContext)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "로그인 실패")
                },
            )
        }
    }

    /** Full pull+merge pass against the cloud backup — called right after sign-in and again every
     *  time the app comes back to the foreground (MainActivity's lifecycle observer). No-ops while
     *  signed out. */
    fun syncNow(context: Context) {
        val uid = _state.value.uid ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { reconcileBackup(context, uid, graph.backupRepository) } }
    }

    /** Pushes the current settings snapshot to the cloud backup — called from MainActivity's
     *  ON_STOP so brush/gesture/grid-column changes made during the session sync without needing a
     *  push call at every individual setter (see the plan's Global Constraints). No-ops while
     *  signed out. */
    fun flushSettings() {
        val uid = _state.value.uid ?: return
        viewModelScope.launch(Dispatchers.IO) { syncSettingsUp(graph.sessionStore, graph.backupRepository, uid) }
    }
```

- [ ] **Step 3: Push settings immediately on theme/avatar change**

Change:

```kotlin
    fun setTheme(mode: ThemeMode) {
        graph.sessionStore.themeMode = mode.name.lowercase()
        _state.value = _state.value.copy(theme = mode)
    }

    fun setAvatarImage(bmp: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.sessionStore.saveAvatarImage(bmp)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(avatarVersion = _state.value.avatarVersion + 1) }
        }
    }
```

to:

```kotlin
    fun setTheme(mode: ThemeMode) {
        graph.sessionStore.themeMode = mode.name.lowercase()
        _state.value = _state.value.copy(theme = mode)
        flushSettings()
    }

    fun setAvatarImage(bmp: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.sessionStore.saveAvatarImage(bmp)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(avatarVersion = _state.value.avatarVersion + 1) }
            _state.value.uid?.let { syncSettingsUp(graph.sessionStore, graph.backupRepository, it) }
        }
    }
```

- [ ] **Step 4: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/ui/RootViewModel.kt
git commit -m "feat(backup): trigger sync on sign-in, theme, and avatar changes"
```

---

### Task 9: MainActivity — foreground pull / background settings flush

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/MainActivity.kt`

**Interfaces:**
- Consumes: `RootViewModel.syncNow(Context)`, `RootViewModel.flushSettings()` (Task 8).

- [ ] **Step 1: Add the lifecycle observer**

Change:

```kotlin
package com.g1.sketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.NicknameDialog
import com.g1.sketchbook.ui.RootViewModel
import com.g1.sketchbook.ui.SplashScreen
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot(vm: RootViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DaymoryTheme(mode = state.theme) {
        var splash by remember { mutableStateOf(true) }
        when {
            splash -> SplashScreen(onEnter = { splash = false })
            state.user == null -> LoginScreen(busy = state.busy, error = state.error, onSignIn = { vm.signIn(context) })
            state.needsNickname -> {
                LoginScreen(busy = false, error = null, onSignIn = {})
                NicknameDialog(onCancel = vm::signOut, onConfirm = vm::saveNickname)
            }
            state.openBookId != null -> com.g1.sketchbook.sketchbook.SketchbookCanvasScreen(
                bookId = state.openBookId!!, myUid = state.uid ?: "", myName = state.nickname ?: "나",
                onBack = vm::closeBook,
            )
            state.openDiaryDate != null -> com.g1.sketchbook.diary.DiaryEditorScreen(
                date = state.openDiaryDate!!, onBack = vm::closeDiary,
            )
```

to:

```kotlin
package com.g1.sketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.NicknameDialog
import com.g1.sketchbook.ui.RootViewModel
import com.g1.sketchbook.ui.SplashScreen
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot(vm: RootViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 폰↔태블릿 자동 동기화: 앱이 포그라운드로 올라올 때마다 클라우드 백업을 받아와 병합하고,
    // 백그라운드로 내려갈 때 지금 설정값을 올린다(브러시 색상/굵기처럼 자주 바뀌는 값을 매번
    // 따로 안 올리고 여기서 한 번에 흘려보내는 지점 — GoogleAccountBackupSync 계획 참고).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.syncNow(context)
                Lifecycle.Event.ON_STOP -> vm.flushSettings()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DaymoryTheme(mode = state.theme) {
        var splash by remember { mutableStateOf(true) }
        when {
            splash -> SplashScreen(onEnter = { splash = false })
            state.user == null -> LoginScreen(busy = state.busy, error = state.error, onSignIn = { vm.signIn(context) })
            state.needsNickname -> {
                LoginScreen(busy = false, error = null, onSignIn = {})
                NicknameDialog(onCancel = vm::signOut, onConfirm = vm::saveNickname)
            }
            state.openBookId != null -> com.g1.sketchbook.sketchbook.SketchbookCanvasScreen(
                bookId = state.openBookId!!, myUid = state.uid ?: "", myName = state.nickname ?: "나",
                onBack = vm::closeBook,
            )
            state.openDiaryDate != null -> com.g1.sketchbook.diary.DiaryEditorScreen(
                date = state.openDiaryDate!!, myUid = state.uid ?: "", onBack = vm::closeDiary,
            )
```

(The rest of the `when` block — `cleanCalendar`, `else -> MainScreen(...)` — is unchanged.)

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: FAIL — `DiaryEditorScreen` doesn't have a `myUid` parameter yet. That's expected; Task 12 adds it. Confirm the *only* error is that missing parameter, then continue — Task 12 will make this compile.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/MainActivity.kt
git commit -m "feat(backup): sync on foreground, flush settings on background"
```

---

### Task 10: SketchbookSync helpers + wire into SketchbookScreens.kt

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`

**Interfaces:**
- Consumes: `SketchbookRepository` (Task 2), `BackupRepository` (Task 5).
- Produces: `createSynced`, `renameSynced`, `toggleFavSynced`, `setCoverColorSynced`, `saveCoverSynced`, `removeCoverSynced`, `deleteSynced`, `savePageSynced` — used again in Task 11.

- [ ] **Step 1: Write the helpers**

```kotlin
package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import com.g1.sketchbook.backup.BackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pairs each [SketchbookRepository] mutation with a matching [BackupRepository] push, so every
 * screen that creates/edits/deletes a personal sketchbook stays in sync without duplicating the
 * push call — the Home tab's cover-edit dialog and the List tab's both call the same
 * SketchbookRepository mutators independently today. [uid] blank means "not signed in": the local
 * mutation still happens, the push is just skipped. Pushes run fire-and-forget on [scope].
 */
fun createSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, name: String, sizeKey: String, bgKey: String): Sketchbook {
    val book = repo.create(name, sizeKey, bgKey)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.pushSketchbookMeta(uid, book) }
    return book
}

fun renameSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, name: String) {
    repo.rename(id, name)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun toggleFavSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.toggleFav(id)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun setCoverColorSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, color: Long?) {
    repo.setCoverColor(id, color)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun saveCoverSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, bmp: Bitmap) {
    repo.saveCover(id, bmp)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) }
        repo.loadCover(id)?.let { backup.pushSketchbookCover(uid, id, it, repo.coverUpdatedAt(id)) }
    }
}

fun removeCoverSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.removeCover(id)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) }
        backup.deleteSketchbookCover(uid, id)
    }
}

fun deleteSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.delete(id)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteSketchbook(uid, id) }
}

fun savePageSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, bookId: String, index: Int, bmp: Bitmap) {
    repo.savePage(bookId, index, bmp)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        backup.pushSketchbookPage(uid, bookId, index, bmp, repo.pageUpdatedAt(bookId, index))
    }
}

private fun pushMetaIfSignedIn(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    if (uid.isBlank()) return
    scope.launch(Dispatchers.IO) { repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) } }
}
```

- [ ] **Step 2: Wire into `CreateWizard`'s `finishPersonal`**

In `SketchbookScreens.kt`, find `CreateWizard` (it already has `myUid: String` and `val scope = rememberCoroutineScope()`). Add, right after the existing `val scope = rememberCoroutineScope()` line inside `CreateWizard`:

```kotlin
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
```

Change:

```kotlin
    fun finishPersonal() { repo?.create(name, sizeKey, bgKey)?.let(onCreated) }
```

to:

```kotlin
    fun finishPersonal() { repo?.let { createSynced(scope, it, backup, myUid, name, sizeKey, bgKey) }?.let(onCreated) }
```

- [ ] **Step 3: Wire into `SketchbookTab`'s `SketchbookListScreen` call**

`SketchbookTab` already has `myUid: String`. Add a coroutine scope and backup repo — find:

```kotlin
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    var refresh by remember { mutableIntStateOf(0) }
```

(this is inside `SketchbookTab`) and change it to:

```kotlin
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var refresh by remember { mutableIntStateOf(0) }
```

Then change:

```kotlin
        onOpen = { onOpenBook(it.id) },
        onDelete = { repo?.delete(it.id); refresh++ },
        onToggleFav = { repo?.toggleFav(it.id); refresh++ },
        onEditBook = { book, name, newCover, removeCover, newColor ->
            repo?.rename(book.id, name)
            if (newCover != null) repo?.saveCover(book.id, newCover) else if (removeCover) repo?.removeCover(book.id)
            repo?.setCoverColor(book.id, newColor)
            refresh++
        },
```

to:

```kotlin
        onOpen = { onOpenBook(it.id) },
        onDelete = { repo?.let { r -> deleteSynced(scope, r, backup, myUid, it.id) }; refresh++ },
        onToggleFav = { repo?.let { r -> toggleFavSynced(scope, r, backup, myUid, it.id) }; refresh++ },
        onEditBook = { book, name, newCover, removeCover, newColor ->
            repo?.let { r -> renameSynced(scope, r, backup, myUid, book.id, name) }
            if (newCover != null) repo?.let { r -> saveCoverSynced(scope, r, backup, myUid, book.id, newCover) }
            else if (removeCover) repo?.let { r -> removeCoverSynced(scope, r, backup, myUid, book.id) }
            repo?.let { r -> setCoverColorSynced(scope, r, backup, myUid, book.id, newColor) }
            refresh++
        },
```

- [ ] **Step 4: Wire into `SketchbookCanvasScreen`'s page-save**

`SketchbookCanvasScreen` already has `myUid: String`, `val repo = remember { SketchbookRepository(context) }`, and (after the early-return guards for a missing/shared book) `val scope = rememberCoroutineScope()`. Add, right after that `scope` line:

```kotlin
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
```

Change:

```kotlin
                    v.onStrokeEnd = { val pg = page; v.exportContent()?.let { b -> scope.launch(Dispatchers.IO) { repo.savePage(book.id, pg, b) } } }
```

to:

```kotlin
                    v.onStrokeEnd = { val pg = page; v.exportContent()?.let { b -> savePageSynced(scope, repo, backup, myUid, book.id, pg, b) } }
```

- [ ] **Step 5: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt
git commit -m "feat(backup): sync sketchbook create/edit/delete/page-save (List tab, canvas)"
```

---

### Task 11: Wire the same sync helpers into MainScreen.kt's Home tab

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`

**Interfaces:**
- Consumes: `createSynced`/`renameSynced`/`toggleFavSynced`/`setCoverColorSynced`/`saveCoverSynced`/`removeCoverSynced`/`deleteSynced` (Task 10).

- [ ] **Step 1: Thread `myUid` into `HomeTab` and `MainScreen`'s `content` dispatch**

Change:

```kotlin
            0 -> HomeTab(
                onOpenBook = onOpenBook,
                previewBooks = previewBooks,
            )
```

to:

```kotlin
            0 -> HomeTab(
                onOpenBook = onOpenBook,
                myUid = myUid,
                previewBooks = previewBooks,
            )
```

Change:

```kotlin
private fun HomeTab(
    onOpenBook: (String) -> Unit,
    previewBooks: List<Sketchbook>? = null,
) {
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    var refresh by remember { mutableStateOf(0) }
```

to:

```kotlin
private fun HomeTab(
    onOpenBook: (String) -> Unit,
    myUid: String,
    previewBooks: List<Sketchbook>? = null,
) {
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var refresh by remember { mutableStateOf(0) }
```

- [ ] **Step 2: Wire the edit-cover dialog and delete confirm**

Change:

```kotlin
    editing?.let { target ->
        // books에서 최신 상태를 다시 찾아 쓴다 — 그래야 즐겨찾기를 토글해도 다이얼로그를 닫지 않고
        // 별 아이콘이 바로 갱신된다(editing 자체는 다이얼로그를 연 시점의 스냅샷이라 갱신되지 않음).
        val current = allBooks.firstOrNull { it.id == target.id } ?: target
        com.g1.sketchbook.sketchbook.EditCoverDialog(
            book = current,
            repo = repo,
            onCancel = { editing = null },
            onSave = { name, newCover, removeCover, newColor ->
                repo?.rename(current.id, name)
                if (newCover != null) repo?.saveCover(current.id, newCover) else if (removeCover) repo?.removeCover(current.id)
                repo?.setCoverColor(current.id, newColor)
                refresh++; editing = null
            },
            onToggleFav = { repo?.toggleFav(current.id); refresh++ },
            onDelete = { editing = null; pendingDelete = current },
        )
    }
```

to:

```kotlin
    editing?.let { target ->
        // books에서 최신 상태를 다시 찾아 쓴다 — 그래야 즐겨찾기를 토글해도 다이얼로그를 닫지 않고
        // 별 아이콘이 바로 갱신된다(editing 자체는 다이얼로그를 연 시점의 스냅샷이라 갱신되지 않음).
        val current = allBooks.firstOrNull { it.id == target.id } ?: target
        com.g1.sketchbook.sketchbook.EditCoverDialog(
            book = current,
            repo = repo,
            onCancel = { editing = null },
            onSave = { name, newCover, removeCover, newColor ->
                repo?.let { r ->
                    com.g1.sketchbook.sketchbook.renameSynced(scope, r, backup, myUid, current.id, name)
                    if (newCover != null) com.g1.sketchbook.sketchbook.saveCoverSynced(scope, r, backup, myUid, current.id, newCover)
                    else if (removeCover) com.g1.sketchbook.sketchbook.removeCoverSynced(scope, r, backup, myUid, current.id)
                    com.g1.sketchbook.sketchbook.setCoverColorSynced(scope, r, backup, myUid, current.id, newColor)
                }
                refresh++; editing = null
            },
            onToggleFav = { repo?.let { r -> com.g1.sketchbook.sketchbook.toggleFavSynced(scope, r, backup, myUid, current.id) }; refresh++ },
            onDelete = { editing = null; pendingDelete = current },
        )
    }
```

Change:

```kotlin
                TextButton(onClick = { repo?.delete(target.id); refresh++; pendingDelete = null }) {
```

to:

```kotlin
                TextButton(onClick = { repo?.let { r -> com.g1.sketchbook.sketchbook.deleteSynced(scope, r, backup, myUid, target.id) }; refresh++; pendingDelete = null }) {
```

(Search for `TextButton(onClick = { repo?.delete(target.id)` — it's inside `HomeTab`'s `pendingDelete` `AlertDialog`, not the `SettingsTab` one further down the file. If your editor shows more than one match for `repo?.delete(`, use the one whose surrounding `AlertDialog` title is `"스케치북 삭제"` inside `HomeTab`, i.e. right after the `pendingDelete?.let { target -> AlertDialog(...) }` opening in this function — not the identically-named dialog inside `SketchbookScreens.kt`, which Task 10 already handled.)

- [ ] **Step 3: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt
git commit -m "feat(backup): sync sketchbook create/edit/delete from the Home tab"
```

---

### Task 12: Diary — accept myUid, push after each save

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`

**Interfaces:**
- Consumes: `BackupRepository` (Task 5).
- Produces: `DiaryEditorScreen(date, myUid, onBack, previewMode)` — the `myUid` param `MainActivity.kt`'s Task 9 diff already passes.

- [ ] **Step 1: Add `myUid` to `DiaryEditorScreen` and construct a `BackupRepository`**

Change:

```kotlin
fun DiaryEditorScreen(date: String, onBack: () -> Unit, previewMode: Boolean = false) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    val scope = rememberCoroutineScope()
```

to:

```kotlin
fun DiaryEditorScreen(date: String, myUid: String = "", onBack: () -> Unit, previewMode: Boolean = false) {
    val ctx = LocalContext.current
    val repo = if (previewMode) null else remember(ctx) { DiaryRepository(ctx) }
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    val scope = rememberCoroutineScope()
```

- [ ] **Step 2: Push after each local save**

Change:

```kotlin
                            v.onStrokeEnd = { v.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo?.save(date, b) } } }
```

to:

```kotlin
                            v.onStrokeEnd = {
                                v.exportBitmap()?.let { b ->
                                    scope.launch(Dispatchers.IO) {
                                        repo?.save(date, b)
                                        if (myUid.isNotBlank()) repo?.let { backup.pushDiaryDay(myUid, date, b, it.updatedAt(date)) }
                                    }
                                }
                            }
```

Change:

```kotlin
            onClear = { view?.clearCanvas(); view?.exportBitmap()?.let { b -> scope.launch(Dispatchers.IO) { repo?.save(date, b) } } },
```

to:

```kotlin
            onClear = {
                view?.clearCanvas()
                view?.exportBitmap()?.let { b ->
                    scope.launch(Dispatchers.IO) {
                        repo?.save(date, b)
                        if (myUid.isNotBlank()) repo?.let { backup.pushDiaryDay(myUid, date, b, it.updatedAt(date)) }
                    }
                }
            },
```

- [ ] **Step 3: Compile check**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this also resolves the Task 9 compile error (`DiaryEditorScreen` now has `myUid`).

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: PASS (all existing tests plus the 8 new `BackupModelsTest` ones)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt
git commit -m "feat(backup): sync diary entries after each save"
```

---

## Manual verification (not automatable — needs two real/emulated devices)

Unit tests cover `decideSyncAction` in isolation, but the actual Firebase round-trip and multi-device merge can't run in a JVM unit test. Before calling this done:

1. Sign in on device A, draw a few pages in a personal sketchbook, write a diary entry, change the theme and a brush color.
2. Sign in with the **same Google account** on device B (or a second emulator). Confirm the sketchbook, its pages, the diary entry, the theme, and the brush color all appear after the app finishes its `ON_START` sync.
3. On device B, edit a different page of the same sketchbook. Background the app (home button) so `ON_STOP` flushes settings, then bring device A back to the foreground and confirm the new page appears there too.
4. Delete a sketchbook on device A, foreground device B, confirm it disappears there too (tombstone path).
5. Check Firebase console (Realtime Database tab) for the `backups/{uid}` tree to sanity-check the data shape matches Task 5's schema.
