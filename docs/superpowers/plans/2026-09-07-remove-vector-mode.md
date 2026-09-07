# Remove Vector Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the vector sketchbook subsystem and permanently purge only legacy vector books, canvases, and brushes from local storage and the signed-in user's Firebase backup.

**Architecture:** Keep current `master` and remove vector-specific UI, model, rendering, export, and synchronization code. A small compatibility cleanup boundary reads only the legacy `vector == true` marker, deletes those local book directories and brush stores, and removes matching Firebase book nodes before normal bitmap/diary synchronization; remote vector rows are filtered even when deletion is temporarily unavailable.

**Tech Stack:** Kotlin, Android SharedPreferences/filesDir, Jetpack Compose, Firebase Realtime Database, JUnit, Gradle

**Spec:** `docs/superpowers/specs/2026-09-07-remove-vector-mode-design.md`

## Global Constraints

- Preserve bitmap sketchbooks, page PNGs, covers, diaries, shared sessions, settings, and post-v2.12 fixes.
- Delete only books whose legacy JSON or Firebase metadata explicitly contains `vector == true`.
- Delete local `vector_brushes`, `vector_brushes_v2`, and `g1_stamp_brushes`; delete remote `/backups/{uid}/stampBrushes`.
- Never restore remote vector books locally, including when the remote deletion request fails.
- Keep Compose `ImageVector`, Android VectorDrawable resources, `vectorDrawables`, and the untracked design file `image/icon/vector.svg`.
- Release as versionCode 148 / versionName 2.18.0 without moving or overwriting existing tags.

---

### Task 1: Local legacy vector data cleanup

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/sketchbook/LegacyVectorCleanup.kt`
- Create: `app/src/test/java/com/g1/sketchbook/sketchbook/LegacyVectorCleanupTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Produces: `internal data class LegacyVectorBookFilter(val retainedJson: String, val vectorBookIds: Set<String>)`
- Produces: `internal fun filterLegacyVectorBooks(raw: String): LegacyVectorBookFilter?`
- Produces: `internal class LegacyVectorCleanup(private val context: Context) { fun run() }`
- Consumes: SharedPreferences `g1_sketchbooks/books`, `g1_stamp_brushes`, and `context.filesDir`.

- [ ] **Step 1: Write failing filtering tests**

Create tests with concrete JSON fixtures asserting that only `{"vector":true}` rows are removed, missing/false vector rows remain byte-equivalent as parsed JSON objects, malformed JSON returns `null`, and a second pass returns no IDs.

```kotlin
@Test fun removesOnlyExplicitVectorBooks() {
    val raw = """[{"id":"bitmap","name":"A"},{"id":"vector","vector":true},{"id":"false","vector":false}]"""
    val result = requireNotNull(filterLegacyVectorBooks(raw))
    assertEquals(setOf("vector"), result.vectorBookIds)
    assertEquals(listOf("bitmap", "false"), JSONArray(result.retainedJson).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } })
}

@Test fun malformedMetadataIsNotRewritten() {
    assertNull(filterLegacyVectorBooks("not-json"))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.sketchbook.LegacyVectorCleanupTest" --no-daemon`

Expected: compilation failure because `filterLegacyVectorBooks` does not exist.

- [ ] **Step 3: Implement the parser and idempotent cleanup**

Implement `filterLegacyVectorBooks` with `JSONArray`/`JSONObject`, returning `null` without rewriting on parse failure. `LegacyVectorCleanup.run()` merges newly found IDs with a private pending-ID preference, rewrites the book list without explicit vectors, deletes only `filesDir/sketchbooks/{id}` for those IDs, retains failed IDs for retry, deletes `vector_brushes` and `vector_brushes_v2`, and clears `g1_stamp_brushes`. Validate IDs as direct children (no `/`, `\`, `.` or `..`) before constructing a path.

Call `LegacyVectorCleanup(context).run()` in `SketchbookRepository` initialization before `list()` can read the preference.

- [ ] **Step 4: Run local cleanup tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.sketchbook.LegacyVectorCleanupTest" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/com/g1/sketchbook/sketchbook/LegacyVectorCleanup.kt app/src/test/java/com/g1/sketchbook/sketchbook/LegacyVectorCleanupTest.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt
git commit -m "feat: purge legacy vector data locally"
```

### Task 2: Remote purge before bitmap backup reconciliation

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`
- Create: `app/src/test/java/com/g1/sketchbook/backup/LegacyVectorRemoteCleanupTest.kt`
- Modify/Delete: vector-specific backup tests after their production interfaces disappear

**Interfaces:**
- Produces: `internal data class LegacyVectorRemoteCleanup(val bookIds: Set<String>, val removeStampBrushes: Boolean)`
- Produces: `internal fun legacyVectorRemoteCleanup(books: List<RemoteSketchbook>, hasStampBrushes: Boolean): LegacyVectorRemoteCleanup`
- Produces: `suspend fun BackupRepository.removeLegacyVectorData(uid: String): Result<Unit>`
- `RemoteSketchbook` retains only `legacyVector: Boolean` as cleanup compatibility metadata; it no longer contains vector canvas payloads.

- [ ] **Step 1: Write failing remote cleanup contract tests**

```kotlin
@Test fun selectsOnlyExplicitRemoteVectorBooks() {
    val bitmap = remoteBook(id = "bitmap", legacyVector = false)
    val vector = remoteBook(id = "vector", legacyVector = true)
    assertEquals(
        LegacyVectorRemoteCleanup(setOf("vector"), removeStampBrushes = true),
        legacyVectorRemoteCleanup(listOf(bitmap, vector), hasStampBrushes = true),
    )
}

@Test fun normalReconciliationNeverReceivesRemoteVectorRows() {
    assertEquals(listOf("bitmap"), nonVectorRemoteBooks(listOf(bitmapRemote(), vectorRemote())).map { it.id })
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.backup.LegacyVectorRemoteCleanupTest" --no-daemon`

Expected: compilation failure for the missing cleanup contracts.

- [ ] **Step 3: Implement Firebase deletion and filtering**

Parse only `meta/vector` into `legacyVector`. Do not parse `vectorCanvas`, `vectorCanvasV2`, or brush payloads. In `removeLegacyVectorData`, read `/backups/{uid}`, generate a single `updateChildren` map containing `sketchbooks/{id} -> null` for explicit vector books and `stampBrushes -> null`, then await it. Invoke it before normal snapshot reconciliation, but independently filter `legacyVector` rows from every snapshot so a failed removal cannot restore them.

Remove vector/brush push APIs and reconciliation functions. Keep the public bitmap, cover, diary, settings, and shared-book APIs unchanged.

- [ ] **Step 4: Run backup tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.backup.*" --no-daemon`

Expected: PASS with no vector document/brush synchronization tests remaining.

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/com/g1/sketchbook/backup app/src/test/java/com/g1/sketchbook/backup
git commit -m "feat: purge legacy vector backups"
```

### Task 3: Remove vector mode from sketchbook UI and model

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/preview/FlowPreviews.kt`
- Modify: any preview fixtures that still construct vector books
- Test: existing sketchbook/main/preview unit tests

**Interfaces:**
- `Sketchbook` returns to bitmap/shared fields only.
- `SketchbookRepository.create(name, sizeKey, bgKey, shared, code)` no longer accepts vector arguments.
- `WType` contains only `PERSONAL`, `SHARED_NEW`, and `SHARED_JOIN`.

- [ ] **Step 1: Add/adjust compile-time UI contract tests**

Update existing source contract tests to assert there is no `WType.VECTOR`, `onNewVector`, `VectorCanvasScreen`, vector badge text, or vector preview branch in production screens.

- [ ] **Step 2: Run the affected tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.sketchbook.*" --no-daemon`

Expected: FAIL while vector routes remain.

- [ ] **Step 3: Remove vector model fields and UI branches**

Delete vector constructor/JSON fields after the cleanup initialization is in place. Remove vector creation state and screen sections, vector list action/badge/preview, vector routing, and MainScreen vector special cases. Normal bitmap and shared behavior must remain unchanged.

- [ ] **Step 4: Compile and run affected tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.g1.sketchbook.sketchbook.*" :app:compileDebugKotlin --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/com/g1/sketchbook app/src/main/java/com/g1/sketchbook/ui/main app/src/main/java/com/g1/sketchbook/preview app/src/test
git commit -m "refactor: remove vector sketchbook mode"
```

### Task 4: Delete the vector implementation and dead APIs

**Files:**
- Delete: `app/src/main/java/com/g1/sketchbook/vector/**`
- Delete: `app/src/test/java/com/g1/sketchbook/vector/**`
- Delete: `app/src/main/java/com/g1/sketchbook/sketchbook/VectorDocumentPersistenceCoordinator.kt`
- Delete: `app/src/test/java/com/g1/sketchbook/sketchbook/VectorDocumentPersistenceCoordinatorTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/Gallery.kt` — remove vector-only `saveSvgToGallery`
- Modify: `app/src/main/java/com/g1/sketchbook/brush/LineIcons.kt` — remove vector-only `IconVectorLine`

**Interfaces:**
- Consumes: Tasks 1–3 have removed all production imports of `com.g1.sketchbook.vector`.
- Produces: no vector package or vector runtime entry point.

- [ ] **Step 1: Run a reference inventory before deletion**

Run: `rg -n "com\.g1\.sketchbook\.vector|VectorCanvas|vectorCanvas|vector_canvas|stampBrush" app/src/main app/src/test`

Expected: matches are confined to the vector package/tests and the cleanup compatibility constants.

- [ ] **Step 2: Delete tracked vector-only sources**

Use `apply_patch` to delete the exact tracked paths listed above and remove `saveSvgToGallery` plus `IconVectorLine`. Retain Compose `ImageVector` and Android VectorDrawable resources.

- [ ] **Step 3: Verify static removal**

Run: `rg -n "com\.g1\.sketchbook\.vector|VectorCanvasScreen|VectorBrush|VectorDocument|vectorCanvasV2" app/src/main app/src/test`

Expected: no matches except raw legacy cleanup path strings or explanatory migration comments.

- [ ] **Step 4: Run all unit tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -u -- app/src/main app/src/test
git commit -m "refactor: delete vector editor implementation"
```

### Task 5: Version, documentation, full verification, and release

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `PROGRESS.md`

**Interfaces:**
- Produces: versionCode 148, versionName 2.18.0 and tag `v2.18.0`.

- [ ] **Step 1: Update version and handoff documentation**

Set `versionCode = 148`, `versionName = "2.18.0"`. Record the irreversible vector data removal, Firebase retry behavior, user requirement to update other old-version devices, exact test counts, lint result, CI run, and release URL in `PROGRESS.md`.

- [ ] **Step 2: Run full verification**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`

Expected: BUILD SUCCESSFUL, zero test failures/errors, zero lint errors.

- [ ] **Step 3: Run clean-checkout verification**

Create a `git archive` of the committed tree into an ignored `build/ci-verify-<sha>` directory, confirm it has no `local.properties`, and run `./gradlew :app:assembleDebug --no-daemon` there.

Expected: BUILD SUCCESSFUL using the vendored `pagecurl` module.

- [ ] **Step 4: Create and push the new release tag**

```powershell
git tag -a v2.18.0 -m "Release v2.18.0"
git push origin master v2.18.0
```

- [ ] **Step 5: Verify GitHub release delivery**

Wait for the `Build & Release APK` GitHub Actions run for the tag SHA. Confirm every job succeeds and `daymory-v2.18.0.apk` exists at `/releases/download/v2.18.0/daymory-v2.18.0.apk` with HTTP 200.
