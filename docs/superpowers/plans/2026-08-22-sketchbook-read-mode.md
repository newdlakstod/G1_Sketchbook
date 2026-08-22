# Sketchbook Read Mode (Page Curl) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only, full-screen "읽기모드" (read mode) to personal sketchbooks, reached from the page panel, that turns pages with a real OpenGL page-curl animation — one page at a time in portrait, two-page book spreads in landscape (표지-1, 2-3, 4-5, ..., 14-15), with only the right page of a landscape spread curling.

**Architecture:** Port the pure math/GL engine from the standalone reference project `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo` (GLSurfaceView + custom GLES 3.0 shaders; the curl math already takes page width/height as parameters, so it works unmodified for both a single portrait page and a landscape spread's right half) into a new `com.g1.sketchbook.readmode` package. Build a thin app-specific layer on top: a `SketchbookRepository`-backed texture provider, a pure spread-layout function, and a Compose entry screen that swaps GL textures per spread and falls back to a static pager on devices without GLES 3.0.

**Tech Stack:** Kotlin, Jetpack Compose, `GLSurfaceView` + OpenGL ES 3.0 (`android.opengl.GLES30`), `kotlin.test` (JVM unit tests, matches this repo's existing convention — see `CoverEditSelectionTest.kt`).

## Global Constraints

- Applies to **personal sketchbooks only** — do not touch `share/SharedBookScreen.kt`.
- Landscape spread turn: **only the right page curls**; the left page swaps instantly with no animation (confirmed with user — matches how a real book looks, since the left page's new content isn't the physical back of the flipped leaf).
- Read mode opens on the spread containing whatever page the user was editing; closing it returns the editor to that same page.
- Must not crash on devices without GLES 3.0 — detect capability the same way the reference project's `MainActivity.kt` does (`ActivityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000`) and fall back to a plain, non-animated pager.
- Page bitmaps for texture upload are downsampled to a 1600px longest edge — editor pages can be up to 3308px and uploading that every turn risks frame hitches.
- Every task ends with `./gradlew compileDebugKotlin` passing (and `./gradlew testDebugUnitTest` for tasks with tests). GL rendering itself cannot be verified outside a real device — say so plainly in the final report rather than claiming it works.
- Follow this repo's existing conventions: `internal`/`private` visibility matching what's actually needed elsewhere, Korean comments only where the *why* isn't obvious from the code (see any file in `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt` for the house style), no comments that just restate the code.

---

### Task 1: Port curl math primitives, mesh, and camera

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/math/Vec2.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/math/Vec3.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/math/MathUtils.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/PageMesh.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/PageCamera.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/PageMeshTest.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/PageCameraTest.kt`

**Reference sources** (read each, then write the destination file with only two mechanical changes: the `package` line, and — for the tests only — swap `org.junit.Test` / `org.junit.Assert.assertEquals` / `org.junit.Assert.assertTrue` / etc. imports for `kotlin.test.Test` / `kotlin.test.assertEquals` / `kotlin.test.assertTrue`, matching this repo's existing test style in `app/src/test/java/com/g1/sketchbook/sketchbook/CoverEditSelectionTest.kt`. No other line changes — these files have zero dependency on the demo's app package):
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\math\Vec2.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\math\Vec3.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\math\MathUtils.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\PageMesh.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\PageCamera.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\PageMeshTest.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\PageCameraTest.kt`

**Interfaces:**
- Produces: `Vec2(x: Float, y: Float)` with `plus`/`minus`/`times`/`dot`/`length`/`normalized`/`perpendicular`, companion `Vec2.LEFT`.
- Produces: `Vec3(x: Float, y: Float, z: Float)` (plain data class).
- Produces: top-level `fun clamp(value: Float, minimum: Float = 0f, maximum: Float = 1f): Float`, `fun lerp(start: Float, end: Float, amount: Float): Float`, `fun smoothStep(value: Float): Float` in package `com.g1.sketchbook.readmode.curl.math`.
- Produces: `class PageMesh(columns: Int = 80, rows: Int = 120)` with `vertexCount: Int`, `indexCount: Int`, `positions/uvs/shade/side: FloatArray`, `indices: IntArray`, matching `FloatBuffer`/`IntBuffer` fields, `fun resetFlat(width: Float, height: Float)`, `fun uploadMutableAttributes()`.
- Produces: `class PageCamera(verticalFieldOfViewDegrees: Float = 45f, nearPlane: Float = 0.5f, farPlane: Float = 10f)` with `fun distanceFor(pageWidth: Float, pageHeight: Float, viewportAspect: Float): Float` and `fun apparentScale(distance: Float, depth: Float): Float`.

- [ ] **Step 1: Copy the five source files**, applying only the package-line change described above.

- [ ] **Step 2: Copy the two test files**, applying the package-line change and the `org.junit` → `kotlin.test` import swap described above. Leave every assertion and test method body untouched.

- [ ] **Step 3: Run the ported tests**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.curl.PageMeshTest" --tests "com.g1.sketchbook.readmode.curl.PageCameraTest"`
Expected: both test classes PASS with no changes beyond the import swap (the assertions are unchanged from the reference project, which already had these passing).

- [ ] **Step 4: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/curl/math app/src/main/java/com/g1/sketchbook/readmode/curl/PageMesh.kt app/src/main/java/com/g1/sketchbook/readmode/curl/PageCamera.kt app/src/test/java/com/g1/sketchbook/readmode/curl/PageMeshTest.kt app/src/test/java/com/g1/sketchbook/readmode/curl/PageCameraTest.kt
git commit -m "feat(readmode): port curl math, mesh, and camera from PageCurlDemo"
```

---

### Task 2: Port curl geometry, state, animator, shadow, and shaders

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/CurlGeometry.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/CurlState.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/CurlAnimator.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/ShadowStrip.kt`
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/ShaderSources.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/CurlGeometryTest.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/CurlAnimatorTest.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/CurlShadowTest.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/curl/CurlCompletionTest.kt`

**Reference sources** (same copy rule as Task 1 — package line only for sources, package line + `org.junit`→`kotlin.test` import swap for tests):
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\CurlGeometry.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\CurlState.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\CurlAnimator.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\ShadowStrip.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\ShaderSources.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\CurlGeometryTest.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\CurlAnimatorTest.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\CurlShadowTest.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\curl\CurlCompletionTest.kt`

**Interfaces:**
- Consumes: `PageMesh`, `Vec2`, `clamp`/`lerp`/`smoothStep` from Task 1.
- Produces: `enum class CurlPhase { Idle, Dragging, SettlingToNext, SettlingToOrigin, Completed }`.
- Produces: `data class CurlState(phase: CurlPhase = Idle, dragPosition: Vec2 = Vec2(1f, 0.5f), progress: Float = 0f)` with `val drawsTurningPage: Boolean`, companion `completionTarget(y: Float): Vec2` and `at(phase: CurlPhase, dragPosition: Vec2): CurlState`.
- Produces: `class CurlGeometry` with `fun parameters(drag: Vec2): CurlParameters`, `fun deform(mesh: PageMesh, drag: Vec2, pageWidth: Float, pageHeight: Float): CurlStats`, `fun updateShadow(strip: ShadowStrip, parameters: CurlParameters, pageWidth: Float, pageHeight: Float)`.
- Produces: `class CurlAnimator` with `val isRunning: Boolean`, `fun start(from: Vec2, to: Vec2, startNanos: Long, durationNanos: Long, phase: CurlPhase)`, `fun sample(nowNanos: Long): CurlState`, `fun cancel(state: CurlState = CurlState())`.
- Produces: `class ShadowStrip` with `companion object { fun create(): ShadowStrip }`, `val segments/positions/alpha/indices/indexCount`, matching Buffers, `fun upload()`.
- Produces: `object ShaderSources` with `val PAGE_VERTEX`, `PAGE_FRAGMENT`, `SHADOW_VERTEX`, `SHADOW_FRAGMENT: String`.

- [ ] **Step 1: Copy the five source files**, package line only.

- [ ] **Step 2: Copy the four test files**, package line + `org.junit`→`kotlin.test` import swap only.

- [ ] **Step 3: Run the ported tests**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.curl.CurlGeometryTest" --tests "com.g1.sketchbook.readmode.curl.CurlAnimatorTest" --tests "com.g1.sketchbook.readmode.curl.CurlShadowTest" --tests "com.g1.sketchbook.readmode.curl.CurlCompletionTest"`
Expected: all PASS.

- [ ] **Step 4: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/curl/CurlGeometry.kt app/src/main/java/com/g1/sketchbook/readmode/curl/CurlState.kt app/src/main/java/com/g1/sketchbook/readmode/curl/CurlAnimator.kt app/src/main/java/com/g1/sketchbook/readmode/curl/ShadowStrip.kt app/src/main/java/com/g1/sketchbook/readmode/curl/ShaderSources.kt app/src/test/java/com/g1/sketchbook/readmode/curl/CurlGeometryTest.kt app/src/test/java/com/g1/sketchbook/readmode/curl/CurlAnimatorTest.kt app/src/test/java/com/g1/sketchbook/readmode/curl/CurlShadowTest.kt app/src/test/java/com/g1/sketchbook/readmode/curl/CurlCompletionTest.kt
git commit -m "feat(readmode): port curl geometry, state, animator, shadow, and shaders from PageCurlDemo"
```

---

### Task 3: Port the edge-drag input interpreter

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/input/DragInterpreter.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/input/DragInterpreterTest.kt`

**Reference sources** (same copy rule):
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\input\DragInterpreter.kt`
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\test\java\com\gdo\pagecurldemo\input\DragInterpreterTest.kt`

Note the source file's own imports reference `com.gdo.pagecurldemo.curl.math.Vec2` and `.clamp` — update those two import lines to `com.g1.sketchbook.readmode.curl.math.Vec2` / `.clamp` (this is the one file in the port whose imports, not just its own package line, need updating, since `math` lives under `curl` in the new layout same as the old one — double check the import paths match Task 1's actual output paths before moving on).

**Interfaces:**
- Consumes: `Vec2`, `clamp` from Task 1.
- Produces: `class DragInterpreter(edgeFraction: Float = 0.15f, completeThreshold: Float = 0.5f, flingThreshold: Float = -1.2f)` with `fun canStart(normalized: Vec2): Boolean`, `fun normalized(x: Float, y: Float, width: Int, height: Int): Vec2`, `fun shouldComplete(progress: Float, velocityX: Float): Boolean`.

- [ ] **Step 1: Copy the source file**, updating the package line and the two `com.gdo.pagecurldemo.curl.math` imports to `com.g1.sketchbook.readmode.curl.math`.

- [ ] **Step 2: Copy the test file**, package line + `org.junit`→`kotlin.test` import swap, plus the same `curl.math` import path fix if the test file imports `Vec2` directly.

- [ ] **Step 3: Run the test**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.input.DragInterpreterTest"`
Expected: PASS.

- [ ] **Step 4: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/input app/src/test/java/com/g1/sketchbook/readmode/input
git commit -m "feat(readmode): port edge-drag input interpreter from PageCurlDemo"
```

---

### Task 4: Page texture provider (bitmap loading + downsampling)

This is new app-specific code, not ported from the reference project — the reference's own README says it never built a texture-supply abstraction.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/PageTextureProvider.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/PageTextureProviderTest.kt`

**Interfaces:**
- Consumes: `SketchbookRepository.loadPage(id: String, index: Int): Bitmap?` (existing, `com.g1.sketchbook.sketchbook.SketchbookRepository`).
- Produces: top-level `fun downsampleTargetSize(width: Int, height: Int, maxEdge: Int = 1600): Pair<Int, Int>` in package `com.g1.sketchbook.readmode`.
- Produces: `class PageTextureProvider(repo: SketchbookRepository, bookId: String, maxEdge: Int = 1600)` with `fun pageBitmap(pageIndex: Int): Bitmap?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class PageTextureProviderTest {
    @Test
    fun sizeWithinBudgetIsUnchanged() {
        assertEquals(800 to 600, downsampleTargetSize(800, 600, maxEdge = 1600))
    }

    @Test
    fun oversizedSquareScalesDownToMaxEdge() {
        assertEquals(1600 to 1600, downsampleTargetSize(3200, 3200, maxEdge = 1600))
    }

    @Test
    fun oversizedRectangleScalesProportionally() {
        assertEquals(1600 to 800, downsampleTargetSize(3200, 1600, maxEdge = 1600))
    }

    @Test
    fun exactlyAtBudgetIsUnchanged() {
        assertEquals(1600 to 1200, downsampleTargetSize(1600, 1200, maxEdge = 1600))
    }
}
```

Save this to `app/src/test/java/com/g1/sketchbook/readmode/PageTextureProviderTest.kt`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.PageTextureProviderTest"`
Expected: FAIL — `downsampleTargetSize` is not defined yet.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlin.math.roundToInt

/** Computes the (width, height) a page bitmap should be downsampled to for GL texture upload —
 *  pure function, no Bitmap/Android dependency, so it's unit-testable on its own. Caps the longest
 *  edge at [maxEdge]; returns the size unchanged if it's already within budget. */
fun downsampleTargetSize(width: Int, height: Int, maxEdge: Int = 1600): Pair<Int, Int> {
    require(width > 0 && height > 0) { "width and height must be positive" }
    require(maxEdge > 0) { "maxEdge must be positive" }
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toFloat() / longest
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return scaledWidth to scaledHeight
}

/** Loads sketchbook page bitmaps for read mode, downsampled so GL texture uploads on page turns
 *  stay cheap even though the editor stores pages at full canvas resolution (up to 3308px, see
 *  `SketchbookRepository.loadPage`). Does no threading of its own — callers load off the main
 *  thread (see `ReadModeScreen`'s `LaunchedEffect` + `Dispatchers.IO`). */
class PageTextureProvider(
    private val repo: SketchbookRepository,
    private val bookId: String,
    private val maxEdge: Int = 1600,
) {
    /** Returns the downsampled bitmap for [pageIndex], or null if that page has no drawing yet. */
    fun pageBitmap(pageIndex: Int): Bitmap? {
        val full = repo.loadPage(bookId, pageIndex) ?: return null
        val (w, h) = downsampleTargetSize(full.width, full.height, maxEdge)
        if (w == full.width && h == full.height) return full
        return Bitmap.createScaledBitmap(full, w, h, true)
    }
}
```

Save this to `app/src/main/java/com/g1/sketchbook/readmode/PageTextureProvider.kt`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.PageTextureProviderTest"`
Expected: PASS (4/4).

- [ ] **Step 5: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/PageTextureProvider.kt app/src/test/java/com/g1/sketchbook/readmode/PageTextureProviderTest.kt
git commit -m "feat(readmode): add downsampling page texture provider"
```

---

### Task 5: Spread layout (page pairing for portrait/landscape)

New app-specific logic — the reference project has no concept of multi-page spreads at all.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/ReadSpreads.kt`
- Test: `app/src/test/java/com/g1/sketchbook/readmode/ReadSpreadsTest.kt`

**Interfaces:**
- Produces: `const val COVER_PAGE: Int` (sentinel, `-1`) in package `com.g1.sketchbook.readmode`.
- Produces: `fun buildSpreads(pageCount: Int, landscape: Boolean): List<List<Int>>`.
- Produces: `fun spreadIndexForPage(spreads: List<List<Int>>, page: Int): Int`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.g1.sketchbook.readmode

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadSpreadsTest {
    @Test
    fun portraitSpreadsAreOnePageEach() {
        val spreads = buildSpreads(pageCount = 15, landscape = false)
        assertEquals(15, spreads.size)
        assertEquals(listOf(0), spreads[0])
        assertEquals(listOf(14), spreads[14])
    }

    @Test
    fun landscapeSpreadsPairCoverWithPageOneThenStepByTwo() {
        val spreads = buildSpreads(pageCount = 15, landscape = true)
        assertEquals(
            listOf(
                listOf(COVER_PAGE, 0),
                listOf(1, 2),
                listOf(3, 4),
                listOf(5, 6),
                listOf(7, 8),
                listOf(9, 10),
                listOf(11, 12),
                listOf(13, 14),
            ),
            spreads,
        )
    }

    @Test
    fun landscapeWithATrailingOddPageHasAFinalSingleWidePageSpread() {
        val spreads = buildSpreads(pageCount = 4, landscape = true)
        assertEquals(
            listOf(listOf(COVER_PAGE, 0), listOf(1, 2), listOf(3)),
            spreads,
        )
    }

    @Test
    fun spreadIndexForPageFindsTheSpreadContainingThatPage() {
        val spreads = buildSpreads(pageCount = 15, landscape = true)
        assertEquals(0, spreadIndexForPage(spreads, page = 0))
        assertEquals(2, spreadIndexForPage(spreads, page = 4))
        assertEquals(7, spreadIndexForPage(spreads, page = 14))
    }

    @Test
    fun spreadIndexForPageFallsBackToZeroForAnOutOfRangePage() {
        val spreads = buildSpreads(pageCount = 15, landscape = false)
        assertEquals(0, spreadIndexForPage(spreads, page = 99))
    }
}
```

Save this to `app/src/test/java/com/g1/sketchbook/readmode/ReadSpreadsTest.kt`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.ReadSpreadsTest"`
Expected: FAIL — `buildSpreads`/`spreadIndexForPage`/`COVER_PAGE` not defined yet.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.g1.sketchbook.readmode

/** Sentinel page index meaning "the sketchbook's own cover art", used only in landscape spreads —
 *  real pages are always >= 0. */
const val COVER_PAGE = -1

/** Computes which page indices are shown together in each "spread" of the reader.
 *  - Portrait: one real page per spread.
 *  - Landscape: the cover pairs with page 0, then real pages pair up two at a time — i.e.
 *    "표지-1, 2-3, 4-5, ..." in 1-indexed display terms. If [pageCount] is even, the very last
 *    spread ends up with only one real page (no partner) — shown alone rather than paired with
 *    nothing. Not expected to happen with this app's fixed 15-page sketchbooks, but a plain
 *    [pageCount] of any size should still produce a sane layout. */
fun buildSpreads(pageCount: Int, landscape: Boolean): List<List<Int>> {
    require(pageCount > 0) { "pageCount must be positive" }
    if (!landscape) return (0 until pageCount).map { listOf(it) }
    val spreads = mutableListOf(listOf(COVER_PAGE, 0))
    var i = 1
    while (i < pageCount) {
        val right = i + 1
        spreads += if (right < pageCount) listOf(i, right) else listOf(i)
        i += 2
    }
    return spreads
}

/** Finds which spread contains [page], so read mode can open on the spread the user was already
 *  editing instead of always starting at the beginning. Falls back to the first spread if [page]
 *  isn't in any spread (shouldn't happen in practice, but a safe default beats a crash). */
fun spreadIndexForPage(spreads: List<List<Int>>, page: Int): Int {
    val found = spreads.indexOfFirst { page in it }
    return if (found >= 0) found else 0
}
```

Save this to `app/src/main/java/com/g1/sketchbook/readmode/ReadSpreads.kt`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.g1.sketchbook.readmode.ReadSpreadsTest"`
Expected: PASS (5/5).

- [ ] **Step 5: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/ReadSpreads.kt app/src/test/java/com/g1/sketchbook/readmode/ReadSpreadsTest.kt
git commit -m "feat(readmode): add portrait/landscape spread layout"
```

---

### Task 6: Texture loader (Bitmap → GL texture)

Adapted from the reference project's asset-only loader — this app has no bundled page assets, pages come from `PageTextureProvider` (Task 4) as live `Bitmap`s.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/curl/TextureLoader.kt`

**Interfaces:**
- Produces: `object TextureLoader` with `fun loadBitmap(bitmap: Bitmap): Int`, `fun updateBitmap(textureId: Int, bitmap: Bitmap)`, `fun release(textureId: Int)`.

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.readmode.curl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/** Uploads app-supplied page [Bitmap]s as GL textures — adapted from PageCurlDemo's asset-only
 *  `TextureLoader`. This app has no bundled page assets; pages come from `PageTextureProvider`. */
object TextureLoader {
    fun loadBitmap(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        check(ids[0] != 0) { "Unable to allocate GL texture" }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        configureParameters()
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    /** Re-specifies an existing texture's pixel data in place — avoids allocating a fresh texture
     *  id on every page turn. `GLUtils.texImage2D` re-specifies the size too, so this is safe even
     *  when [bitmap]'s dimensions differ from what the texture originally held. */
    fun updateBitmap(textureId: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release(textureId: Int) {
        if (textureId == 0) return
        GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
    }

    private fun configureParameters() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }
}
```

Save this to `app/src/main/java/com/g1/sketchbook/readmode/curl/TextureLoader.kt`.

- [ ] **Step 2: Compile the app module** (no unit test — this is a thin GLES30 wrapper, same as the reference project's own `TextureLoader`, which also has no test)

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/curl/TextureLoader.kt
git commit -m "feat(readmode): add bitmap-backed GL texture loader"
```

---

### Task 7: Read mode GL renderer

Adapted from the reference project's `PageCurlRenderer`. The biggest change: instead of two fixed asset textures, it takes a `SpreadTextures` bundle (uploaded on the GL thread via `setSpread`) and, in landscape, draws an extra static (non-curling) left-page quad next to the curling right page.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/ReadModeRenderer.kt`

**Reference source** (for the `MeshGpu`/`ShadowGpu` inner classes and `createProgram`/`compileShader` helpers only — these are unchanged GL boilerplate, copy them verbatim into the private section of the class below):
- `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\PageCurlRenderer.kt` (see its `private class MeshGpu`, `private class ShadowGpu`, `createProgram`, `compileShader` — copy these four members unchanged, just moving them inside `ReadModeRenderer`)

**Interfaces:**
- Consumes: `CurlAnimator`, `CurlGeometry`, `CurlPhase`, `CurlState`, `PageCamera`, `PageMesh`, `ShadowStrip`, `ShaderSources` (Tasks 1–2), `TextureLoader` (Task 6), `Vec2`/`clamp` (Task 1).
- Produces: `class SpreadTextures(turningFront: Bitmap, turningBack: Bitmap, nextRight: Bitmap, staticLeft: Bitmap?)`.
- Produces: `class ReadModeRenderer : GLSurfaceView.Renderer` with `fun setSpread(textures: SpreadTextures, landscape: Boolean)` (GL-thread only — callers must wrap in `queueEvent`, see Task 8), `fun onDragStart(position: Vec2)`, `fun onDrag(position: Vec2)`, `fun onDragEnd(complete: Boolean)`, `fun cancelDrag()`, `val didCompleteTurn: Boolean`.

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.readmode

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.g1.sketchbook.readmode.curl.CurlAnimator
import com.g1.sketchbook.readmode.curl.CurlGeometry
import com.g1.sketchbook.readmode.curl.CurlPhase
import com.g1.sketchbook.readmode.curl.CurlState
import com.g1.sketchbook.readmode.curl.PageCamera
import com.g1.sketchbook.readmode.curl.PageMesh
import com.g1.sketchbook.readmode.curl.ShadowStrip
import com.g1.sketchbook.readmode.curl.ShaderSources
import com.g1.sketchbook.readmode.curl.TextureLoader
import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.curl.math.clamp
import java.nio.Buffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** The bitmaps needed to render one spread — supplied fresh every time [ReadModeRenderer.setSpread]
 *  is called (spread change). [staticLeft] is null in portrait (single-page) mode. [turningBack] is
 *  the sketchbook's paper texture: pages here are single-sided digital drawings, not physical
 *  double-sided sheets, so the back of a turning leaf just shows blank paper rather than another
 *  page's content. */
class SpreadTextures(
    val turningFront: Bitmap,
    val turningBack: Bitmap,
    val nextRight: Bitmap,
    val staticLeft: Bitmap?,
)

/** Adapted from PageCurlDemo's `PageCurlRenderer`. Where the demo hardcoded two asset textures and
 *  a fixed single-page aspect, this renders whatever `SpreadTextures` it's last given, at either a
 *  single-page aspect (portrait) or a two-page-wide aspect (landscape) — the curl math itself
 *  (`CurlGeometry`/`PageMesh`/`PageCamera`) already takes width/height as parameters, so neither
 *  needed to change to support both. Only the *right* page curls; a landscape spread's left page is
 *  a second, static (non-deforming) quad. */
class ReadModeRenderer : GLSurfaceView.Renderer {
    private val geometry = CurlGeometry()
    private val animator = CurlAnimator()
    private val pageMesh = PageMesh()
    private val nextPageMesh = PageMesh(1, 1)
    private val staticLeftMesh = PageMesh(1, 1)
    private val shadowStrip = ShadowStrip.create()
    private val camera = PageCamera()
    private val projection = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    private var state = CurlState()
    private var program = 0
    private var shadowProgram = 0
    private var pageGpu: MeshGpu? = null
    private var nextPageGpu: MeshGpu? = null
    private var staticLeftGpu: MeshGpu? = null
    private var shadowGpu: ShadowGpu? = null
    private var turningFrontTexture = 0
    private var turningBackTexture = 0
    private var nextRightTexture = 0
    private var staticLeftTexture = 0
    private var mvpLocation = -1
    private var frontTextureLocation = -1
    private var backTextureLocation = -1
    private var staticPageLocation = -1
    private var shadowMvpLocation = -1

    private var pageWidth = PORTRAIT_WIDTH
    private var pageHeight = PORTRAIT_HEIGHT
    private var hasStaticLeft = false
    private var pendingSpread: SpreadTextures? = null
    private var lastSurfaceWidth = 0
    private var lastSurfaceHeight = 0

    /** GL-thread only — wrap calls in `queueEvent` from the view (see `ReadModeSurface`). Queues the
     *  new spread's bitmaps for upload on the next draw and resets any in-flight curl state. */
    fun setSpread(textures: SpreadTextures, landscape: Boolean) {
        hasStaticLeft = textures.staticLeft != null
        pageWidth = if (landscape) LANDSCAPE_SPREAD_WIDTH else PORTRAIT_WIDTH
        pageHeight = PORTRAIT_HEIGHT
        pendingSpread = textures
        state = CurlState()
        animator.cancel(state)
        if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) recomputeCamera(lastSurfaceWidth, lastSurfaceHeight)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGlObjects()
        program = createProgram(ShaderSources.PAGE_VERTEX, ShaderSources.PAGE_FRAGMENT)
        shadowProgram = createProgram(ShaderSources.SHADOW_VERTEX, ShaderSources.SHADOW_FRAGMENT)
        mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        frontTextureLocation = GLES30.glGetUniformLocation(program, "uFrontTexture")
        backTextureLocation = GLES30.glGetUniformLocation(program, "uBackTexture")
        staticPageLocation = GLES30.glGetUniformLocation(program, "uStaticPage")
        shadowMvpLocation = GLES30.glGetUniformLocation(shadowProgram, "uMvp")

        pageGpu = MeshGpu(pageMesh, dynamic = true)
        nextPageGpu = MeshGpu(nextPageMesh, dynamic = false)
        staticLeftGpu = MeshGpu(staticLeftMesh, dynamic = false)
        shadowGpu = ShadowGpu(shadowStrip)

        GLES30.glClearColor(0.89f, 0.86f, 0.79f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        recomputeCamera(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingSpread?.let { uploadSpread(it); pendingSpread = null }
        if (animator.isRunning) state = animator.sample(System.nanoTime())
        if (state.drawsTurningPage) {
            geometry.deform(pageMesh, state.dragPosition, rightPageWidth(), pageHeight)
            geometry.updateShadow(shadowStrip, geometry.parameters(state.dragPosition), rightPageWidth(), pageHeight)
            pageGpu?.updateDynamic(pageMesh)
            shadowGpu?.update(shadowStrip)
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)

        if (hasStaticLeft) {
            bindTextures(staticLeftTexture, staticLeftTexture)
            GLES30.glUniform1f(staticPageLocation, 1f)
            staticLeftGpu?.draw(staticLeftMesh.indexCount)
        }

        bindTextures(nextRightTexture, nextRightTexture)
        GLES30.glUniform1f(staticPageLocation, 1f)
        nextPageGpu?.draw(nextPageMesh.indexCount)

        if (state.drawsTurningPage) {
            GLES30.glUseProgram(shadowProgram)
            GLES30.glUniformMatrix4fv(shadowMvpLocation, 1, false, mvp, 0)
            shadowGpu?.draw(shadowStrip.indexCount)

            GLES30.glUseProgram(program)
            GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
            bindTextures(turningFrontTexture, turningBackTexture)
            GLES30.glUniform1f(staticPageLocation, 0f)
            pageGpu?.draw(pageMesh.indexCount)
        }

        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    fun onDragStart(position: Vec2) {
        animator.cancel()
        state = CurlState.at(CurlPhase.Dragging, sanitize(position))
    }

    fun onDrag(position: Vec2) {
        if (state.phase == CurlPhase.Dragging) state = CurlState.at(CurlPhase.Dragging, sanitize(position))
    }

    /** True once the settle animation lands on [CurlPhase.Completed] — `ReadModeSurface` polls this
     *  every frame while settling so it can tell the Compose layer to advance the spread index. */
    val didCompleteTurn: Boolean get() = state.phase == CurlPhase.Completed

    fun onDragEnd(complete: Boolean) {
        if (state.phase != CurlPhase.Dragging) return
        val phase = if (complete) CurlPhase.SettlingToNext else CurlPhase.SettlingToOrigin
        val target = if (complete) CurlState.completionTarget(state.dragPosition.y) else Vec2(1f, 0.5f)
        val duration = if (complete) COMPLETE_DURATION_NANOS else CANCEL_DURATION_NANOS
        animator.start(state.dragPosition, target, System.nanoTime(), duration, phase)
        state = CurlState.at(phase, state.dragPosition)
    }

    fun cancelDrag() = onDragEnd(complete = false)

    private fun rightPageWidth(): Float = if (hasStaticLeft) pageWidth / 2f else pageWidth

    private fun recomputeCamera(width: Int, height: Int) {
        val viewAspect = width.toFloat() / height.coerceAtLeast(1)
        val cameraDistance = camera.distanceFor(pageWidth, pageHeight, viewAspect)
        Matrix.perspectiveM(projection, 0, camera.verticalFieldOfViewDegrees, viewAspect, camera.nearPlane, camera.farPlane)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewMatrix, 0)
    }

    private fun uploadSpread(textures: SpreadTextures) {
        val rightWidth = rightPageWidth()
        val rightOffsetX = if (hasStaticLeft) rightWidth / 2f else 0f

        pageMesh.resetFlat(rightWidth, pageHeight)
        offsetMeshX(pageMesh, rightOffsetX)
        pageMesh.uploadMutableAttributes()

        nextPageMesh.resetFlat(rightWidth, pageHeight)
        offsetMeshX(nextPageMesh, rightOffsetX)
        for (vertex in 0 until nextPageMesh.vertexCount) nextPageMesh.positions[vertex * 3 + 2] = NEXT_PAGE_DEPTH
        nextPageMesh.uploadMutableAttributes()

        if (hasStaticLeft) {
            staticLeftMesh.resetFlat(rightWidth, pageHeight)
            offsetMeshX(staticLeftMesh, -rightWidth / 2f)
            staticLeftMesh.uploadMutableAttributes()
        }

        turningFrontTexture = replaceTexture(turningFrontTexture, textures.turningFront)
        turningBackTexture = replaceTexture(turningBackTexture, textures.turningBack)
        nextRightTexture = replaceTexture(nextRightTexture, textures.nextRight)
        textures.staticLeft?.let { staticLeftTexture = replaceTexture(staticLeftTexture, it) }
    }

    private fun offsetMeshX(mesh: PageMesh, offsetX: Float) {
        for (vertex in 0 until mesh.vertexCount) mesh.positions[vertex * 3] += offsetX
    }

    private fun replaceTexture(existing: Int, bitmap: Bitmap): Int =
        if (existing == 0) TextureLoader.loadBitmap(bitmap) else { TextureLoader.updateBitmap(existing, bitmap); existing }

    private fun sanitize(position: Vec2): Vec2 = Vec2(clamp(position.x, -0.15f, 1f), clamp(position.y))

    private fun bindTextures(front: Int, back: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, front)
        GLES30.glUniform1i(frontTextureLocation, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, back)
        GLES30.glUniform1i(backTextureLocation, 1)
    }

    private fun releaseGlObjects() {
        pageGpu?.release(); nextPageGpu?.release(); staticLeftGpu?.release(); shadowGpu?.release()
        pageGpu = null; nextPageGpu = null; staticLeftGpu = null; shadowGpu = null
        if (program != 0) GLES30.glDeleteProgram(program)
        if (shadowProgram != 0) GLES30.glDeleteProgram(shadowProgram)
        TextureLoader.release(turningFrontTexture)
        TextureLoader.release(turningBackTexture)
        TextureLoader.release(nextRightTexture)
        TextureLoader.release(staticLeftTexture)
        program = 0; shadowProgram = 0
        turningFrontTexture = 0; turningBackTexture = 0; nextRightTexture = 0; staticLeftTexture = 0
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        // Copy verbatim from PageCurlRenderer.createProgram (reference source listed above) —
        // compiles + links the vertex/fragment pair, throws with the GL info log on failure.
        TODO_COPY_FROM_REFERENCE()
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
        // Copy verbatim from PageCurlRenderer.compileShader.
        TODO_COPY_FROM_REFERENCE()
    }

    private class MeshGpu(mesh: PageMesh, dynamic: Boolean) {
        // Copy verbatim from PageCurlRenderer.MeshGpu — VAO/VBO setup for positions/uvs/shade/side
        // + index buffer, `updateDynamic(mesh)`, `draw(indexCount)`, `release()`.
        TODO_COPY_FROM_REFERENCE()
    }

    private class ShadowGpu(strip: ShadowStrip) {
        // Copy verbatim from PageCurlRenderer.ShadowGpu — VAO/VBO for the shadow strip's
        // position/alpha attributes + index buffer, `update(strip)`, `draw(indexCount)`, `release()`.
        TODO_COPY_FROM_REFERENCE()
    }

    private companion object {
        const val PORTRAIT_WIDTH = 2f
        const val PORTRAIT_HEIGHT = 8f / 3f
        const val LANDSCAPE_SPREAD_WIDTH = PORTRAIT_WIDTH * 2f
        const val NEXT_PAGE_DEPTH = -0.03f
        const val COMPLETE_DURATION_NANOS = 280_000_000L
        const val CANCEL_DURATION_NANOS = 220_000_000L
    }
}
```

`TODO_COPY_FROM_REFERENCE()` above is not real Kotlin — it is a marker for the next step. Do not leave it in the file.

- [ ] **Step 2: Replace the four marked bodies with the reference implementation**

Open `C:\Joon's Room\CODEX\GDO_DAILY SKETCH\PageCurlDemo\app\src\main\java\com\gdo\pagecurldemo\curl\PageCurlRenderer.kt` and copy these members' bodies unchanged into the file you just wrote, replacing each `TODO_COPY_FROM_REFERENCE()` placeholder:
- `createProgram(vertexSource: String, fragmentSource: String): Int` (its body calls `compileShader` twice, attaches, links, checks `GL_LINK_STATUS`)
- `compileShader(type: Int, source: String, label: String): Int` (compiles, checks `GL_COMPILE_STATUS`)
- `private class MeshGpu(mesh: PageMesh, dynamic: Boolean)` (the whole class body: `vertexArray`/`buffers` IntArrays, `init` block uploading 4 attributes + index buffer, `updateDynamic`, `draw`, `release`, `uploadAttribute`, `updateBuffer`)
- `private class ShadowGpu(strip: ShadowStrip)` (the whole class body: VAO/VBO setup for position + alpha attributes + index buffer, `update`, `draw`, `release`)

These four members are pure GL boilerplate with no dependency on the demo's fixed-texture design — nothing in them needs to change for this app. Add `import java.nio.Buffer` if not already present (used by `uploadAttribute`/`updateBuffer`'s `Buffer` parameter type).

- [ ] **Step 3: Compile the app module** (no unit test — GL rendering needs a real GPU context; this mirrors the reference project, which also leaves `PageCurlRenderer` untested and validates it manually on-device)

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If it fails on a missing symbol from the copied GL boilerplate, re-check Step 2 copied the exact field/method names the surrounding code in Step 1 already calls (`buffers[0..4]`, `vertexArray[0]`, etc.).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/ReadModeRenderer.kt
git commit -m "feat(readmode): add GL renderer supporting single-page and two-page-spread curl"
```

---

### Task 8: Read mode GL surface (touch handling)

Adapted from the reference project's `PageCurlSurface` almost verbatim — the one addition is polling for turn completion so the Compose layer can advance to the next spread.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/ReadModeSurface.kt`

**Interfaces:**
- Consumes: `ReadModeRenderer`, `SpreadTextures` (Task 7), `DragInterpreter` (Task 3), `Vec2` (Task 1).
- Produces: `class ReadModeSurface(context: Context) : GLSurfaceView(context)` with `var onTurnCompleted: (() -> Unit)?`, `fun setSpread(textures: SpreadTextures, landscape: Boolean)`.

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.readmode

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.g1.sketchbook.readmode.curl.math.Vec2
import com.g1.sketchbook.readmode.input.DragInterpreter

/** GLSurfaceView hosting [ReadModeRenderer]. Touch handling is PageCurlDemo's `PageCurlSurface`
 *  almost unchanged; the one addition is [onTurnCompleted], which fires once a completed drag's
 *  settle animation actually finishes on screen, so the Compose layer can advance the spread index
 *  and swap in the next [SpreadTextures] right as the paper visually lands. */
class ReadModeSurface(context: Context) : GLSurfaceView(context) {
    private val renderer = ReadModeRenderer()
    private val dragInterpreter = DragInterpreter()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastPosition = Vec2(1f, 0.5f)
    private var lastEventTime = 0L
    private var velocityX = 0f

    /** Invoked on the main thread (see [pollForCompletion]) — safe to touch Compose state from it. */
    var onTurnCompleted: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Queues the new spread's bitmaps onto the GL thread — see [ReadModeRenderer.setSpread]. */
    fun setSpread(textures: SpreadTextures, landscape: Boolean) {
        queueEvent { renderer.setSpread(textures, landscape) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startDrag(event)
            MotionEvent.ACTION_MOVE -> moveDrag(event)
            MotionEvent.ACTION_UP -> finishDrag(event, canceled = false)
            MotionEvent.ACTION_CANCEL -> finishDrag(event, canceled = true)
            else -> activePointerId != MotionEvent.INVALID_POINTER_ID
        }
    }

    private fun startDrag(event: MotionEvent): Boolean {
        val position = dragInterpreter.normalized(event.x, event.y, width, height)
        if (!dragInterpreter.canStart(position)) return false
        activePointerId = event.getPointerId(0)
        lastPosition = position
        lastEventTime = event.eventTime
        velocityX = 0f
        queueEvent { renderer.onDragStart(position) }
        return true
    }

    private fun moveDrag(event: MotionEvent): Boolean {
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val position = dragInterpreter.normalized(event.getX(pointerIndex), event.getY(pointerIndex), width, height)
        updateVelocity(position, event.eventTime)
        queueEvent { renderer.onDrag(position) }
        return true
    }

    private fun finishDrag(event: MotionEvent, canceled: Boolean): Boolean {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (!canceled && pointerIndex >= 0) {
            val position = dragInterpreter.normalized(event.getX(pointerIndex), event.getY(pointerIndex), width, height)
            updateVelocity(position, event.eventTime)
            queueEvent { renderer.onDrag(position) }
        }
        val complete = !canceled && dragInterpreter.shouldComplete(progress = 1f - lastPosition.x, velocityX = velocityX)
        queueEvent {
            if (canceled) {
                renderer.cancelDrag()
            } else {
                renderer.onDragEnd(complete)
                if (complete) pollForCompletion()
            }
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    /** The settle-to-next animation takes ~280ms (`ReadModeRenderer.COMPLETE_DURATION_NANOS`).
     *  Re-queues itself once per GL frame until `renderer.didCompleteTurn` flips, then hops to the
     *  main thread via `post` to fire [onTurnCompleted]. Simpler than wiring a formal
     *  animation-listener chain for a single one-shot event. */
    private fun pollForCompletion() {
        queueEvent {
            if (renderer.didCompleteTurn) {
                post { onTurnCompleted?.invoke() }
            } else {
                pollForCompletion()
            }
        }
    }

    private fun updateVelocity(position: Vec2, eventTime: Long) {
        val elapsedSeconds = (eventTime - lastEventTime).coerceAtLeast(1L) / 1_000f
        velocityX = (position.x - lastPosition.x) / elapsedSeconds
        lastPosition = position
        lastEventTime = eventTime
    }
}
```

Save this to `app/src/main/java/com/g1/sketchbook/readmode/ReadModeSurface.kt`.

- [ ] **Step 2: Compile the app module** (no unit test — same reasoning as Task 7; the reference project also leaves `PageCurlSurface` untested since it needs a real touchscreen + GPU)

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/ReadModeSurface.kt
git commit -m "feat(readmode): add GL surface with turn-completion callback"
```

---

### Task 9: Read mode Compose screen (entry point, orientation, GLES3 fallback)

New app-specific code tying everything together: computes the spread layout, loads textures per spread (including rendering the sketchbook's own cover — color or image — as the landscape mode's first spread's left page), and falls back to a plain non-animated view on devices without GLES 3.0.

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/readmode/ReadModeScreen.kt`

**Interfaces:**
- Consumes: `PageTextureProvider` (Task 4), `buildSpreads`/`spreadIndexForPage`/`COVER_PAGE` (Task 5), `ReadModeSurface`/`SpreadTextures` (Tasks 7–8), `Sketchbook`/`SketchbookRepository` (existing, `com.g1.sketchbook.sketchbook`), `DefaultSketchbookCoverColor` (existing, `com.g1.sketchbook.sketchbook.SketchbookCover.kt`).
- Produces: `@Composable fun ReadModeScreen(repo: SketchbookRepository, book: Sketchbook, startPage: Int, onClose: (lastPage: Int) -> Unit)`.

- [ ] **Step 1: Write the file**

```kotlin
package com.g1.sketchbook.readmode

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.g1.sketchbook.sketchbook.DefaultSketchbookCoverColor
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen, read-only page-turning viewer for a personal sketchbook — no drawing toolbar.
 *  Portrait shows one page per spread; landscape pairs pages like an open book
 *  ("표지-1, 2-3, 4-5, ..."). Falls back to a plain instant page swap (no curl animation) on
 *  devices without GLES 3.0, so read mode never crashes. [onClose] receives whichever page was
 *  showing (its 0-indexed page number, not spread number) so the caller can sync the editor back
 *  to it. */
@Composable
fun ReadModeScreen(
    repo: SketchbookRepository,
    book: Sketchbook,
    startPage: Int,
    onClose: (lastPage: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val supportsGles30 = remember(ctx) { deviceSupportsGles30(ctx) }
    val provider = remember(repo, book.id) { PageTextureProvider(repo, book.id) }
    val spreads = remember(book.pageCount, landscape) { buildSpreads(book.pageCount, landscape) }
    var spreadIndex by remember(landscape) { mutableIntStateOf(spreadIndexForPage(spreads, startPage)) }
    val targetW = remember(book) { downsampleTargetSize(book.size.pxW(), book.size.pxH()).first }
    val targetH = remember(book) { downsampleTargetSize(book.size.pxW(), book.size.pxH()).second }

    fun currentPage(): Int = spreads[spreadIndex].last { it != COVER_PAGE }

    BackHandler { onClose(currentPage()) }

    if (!supportsGles30) {
        FallbackSpreadView(spreads.size, spreadIndex, onClose = { onClose(currentPage()) })
        return
    }

    var surface by remember { mutableStateOf<ReadModeSurface?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, surface) {
        val current = surface
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> current?.onResume()
                Lifecycle.Event.ON_PAUSE -> current?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(spreadIndex, landscape, surface) {
        val activeSurface = surface ?: return@LaunchedEffect
        val textures = withContext(Dispatchers.IO) {
            loadSpreadTextures(provider, spreads[spreadIndex], book, repo, targetW, targetH)
        }
        activeSurface.setSpread(textures, landscape)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                ReadModeSurface(context).also {
                    it.onTurnCompleted = { if (spreadIndex < spreads.lastIndex) spreadIndex++ }
                    surface = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun deviceSupportsGles30(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
}

/** Loads the bitmaps for one spread. [spread] holds one real page (portrait) or
 *  `[COVER_PAGE, page]` / `[left, right]` (landscape). The "turning" page — the one that curls
 *  when the user drags — is always the rightmost entry; anything to its left is static. `nextRight`
 *  previews the page revealed once the turn completes, so the curling leaf's underside shows real
 *  content instead of a blank flash. Pages with no drawing yet, and the turning leaf's back side,
 *  render as plain white — this app's pages aren't physically double-sided, so there's no "real"
 *  back content to show; matching the sketchbook's actual paper texture there is a possible later
 *  polish pass, not required for the read-mode feature to work. */
private fun loadSpreadTextures(
    provider: PageTextureProvider,
    spread: List<Int>,
    book: Sketchbook,
    repo: SketchbookRepository,
    targetW: Int,
    targetH: Int,
): SpreadTextures {
    val turningIndex = spread.last()
    val blank = blankPage(targetW, targetH)
    val turningFront = provider.pageBitmap(turningIndex) ?: blank
    val staticLeftIndex = spread.firstOrNull { it != turningIndex }
    val staticLeft = when (staticLeftIndex) {
        null -> null
        COVER_PAGE -> renderCoverBitmap(book, repo.loadCover(book.id), targetW, targetH)
        else -> provider.pageBitmap(staticLeftIndex) ?: blank
    }
    return SpreadTextures(
        turningFront = turningFront,
        turningBack = blank,
        nextRight = provider.pageBitmap(turningIndex + 1) ?: blank,
        staticLeft = staticLeft,
    )
}

private fun blankPage(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.WHITE) }

/** Draws the sketchbook's cover — color or image, plus the spine strip — onto a plain Bitmap for
 *  use as a GL texture, mirroring `SketchbookCover.kt`'s Compose visuals: color fill, optional
 *  image on top, then a black spine strip along the left 9% of the width (20% opacity for a solid
 *  cover, 70% over an image — same numbers `SketchbookCover.kt` uses). Canvas-drawn rather than
 *  captured from a composition, matching this codebase's existing pattern for bitmap-only renders
 *  (see `diary/DiaryScreens.kt`'s `renderFramedDiaryBitmap`). */
private fun renderCoverBitmap(book: Sketchbook, coverImage: Bitmap?, width: Int, height: Int): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val coverColorArgb = (book.coverColor ?: (DefaultSketchbookCoverColor.toArgb().toLong() and 0xFFFFFFFFL) or 0xFF000000L).toInt()
    canvas.drawColor(coverColorArgb)
    if (coverImage != null) {
        canvas.drawBitmap(coverImage, Rect(0, 0, coverImage.width, coverImage.height), Rect(0, 0, width, height), null)
    }
    val spineAlpha = if (coverImage == null) 0.20f else 0.70f
    val spinePaint = Paint().apply { color = AndroidColor.BLACK; alpha = (spineAlpha * 255).roundToInt() }
    canvas.drawRect(0f, 0f, width * 0.09f, height.toFloat(), spinePaint)
    return out
}

@Composable
private fun FallbackSpreadView(spreadCount: Int, spreadIndex: Int, onClose: () -> Unit) {
    // Minimal fallback for devices without GLES 3.0 — no curl animation, just enough to avoid a
    // crash and tell the user why. Full parity (tap-to-turn without the 3D effect) is a later
    // polish pass, not required for this feature to ship on GLES-3-capable devices.
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            "이 기기는 3D 책장 넘기기를 지원하지 않아요 (${spreadIndex + 1}/$spreadCount)",
            color = Color.White,
        )
    }
    BackHandler { onClose() }
}
```

Save this to `app/src/main/java/com/g1/sketchbook/readmode/ReadModeScreen.kt`.

- [ ] **Step 2: Compile the app module** (no unit test — this is a Compose entry point wiring together AndroidView/lifecycle/coroutines; the project has no Compose UI test infrastructure, matching every other screen in this codebase, e.g. `PagePanel.kt`)

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/readmode/ReadModeScreen.kt
git commit -m "feat(readmode): add read mode Compose screen with GLES3 fallback"
```

---

### Task 10: Wire the "읽기모드" entry point into the page panel and editor

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/PagePanel.kt:82-198`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt:944, 1053-1065`

**Interfaces:**
- Consumes: `ReadModeScreen` (Task 9).
- Produces: `PagePanel(..., onReadMode: () -> Unit, ...)` — new parameter on the existing composable.

- [ ] **Step 1: Add the read-mode button to `PagePanel`**

In `app/src/main/java/com/g1/sketchbook/sketchbook/PagePanel.kt`, add the import:

```kotlin
import androidx.compose.material.icons.filled.AutoStories
```

Change the function signature (currently at line 82) from:

```kotlin
fun PagePanel(
    repo: SketchbookRepository,
    bookId: String,
    currentPage: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    onReorder: (newOrder: List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
```

to:

```kotlin
fun PagePanel(
    repo: SketchbookRepository,
    bookId: String,
    currentPage: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    onReorder: (newOrder: List<Int>) -> Unit,
    onReadMode: () -> Unit,
    onDismiss: () -> Unit,
) {
```

Then, right after the `Spacer(Modifier.height(14.dp))` that follows the `LazyVerticalGrid` block and right before the final `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ... 취소 / 완료 ... }` row, insert a new button row:

```kotlin
Surface(
    onClick = onReadMode, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.fillMaxWidth(),
) {
    Row(
        Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoStories, "읽기모드", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("읽기모드", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
Spacer(Modifier.height(10.dp))
```

- [ ] **Step 2: Wire read mode into the editor screen**

In `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`, find this line (currently line 944):

```kotlin
    var pagesOpen by remember { mutableStateOf(false) }
```

and add a sibling state variable right after it:

```kotlin
    var pagesOpen by remember { mutableStateOf(false) }
    var readModeOpen by remember { mutableStateOf(false) }
```

Then find the existing `PagePanel(...)` call (currently lines 1053–1065):

```kotlin
    if (pagesOpen) {
        PagePanel(
            repo, book.id, page, pageCount,
            onSelect = { p -> goTo(p) },
            onReorder = { order ->
                saveCurrent()
                repo.applyPageOrder(book.id, order)
                val newPage = order.indexOf(page)
                if (newPage != -1 && newPage != page) { page = newPage; view?.loadContent(repo.loadPage(book.id, newPage)) }
            },
            onDismiss = { pagesOpen = false },
        )
    }
```

and replace it with:

```kotlin
    if (pagesOpen) {
        PagePanel(
            repo, book.id, page, pageCount,
            onSelect = { p -> goTo(p) },
            onReorder = { order ->
                saveCurrent()
                repo.applyPageOrder(book.id, order)
                val newPage = order.indexOf(page)
                if (newPage != -1 && newPage != page) { page = newPage; view?.loadContent(repo.loadPage(book.id, newPage)) }
            },
            onReadMode = { saveCurrent(); pagesOpen = false; readModeOpen = true },
            onDismiss = { pagesOpen = false },
        )
    }
    if (readModeOpen) {
        com.g1.sketchbook.readmode.ReadModeScreen(
            repo = repo, book = book, startPage = page,
            onClose = { lastPage -> readModeOpen = false; goTo(lastPage) },
        )
    }
```

(`saveCurrent()` before entering read mode ensures the page you were just drawing on is what read mode actually shows — matches the existing `saveCurrent()` call in `goTo()`.)

- [ ] **Step 3: Compile the app module**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Compile-check the preview package too** (`preview/BrushCanvasPreview.kt` and others call `PagePanel`-adjacent screens; a signature change to a widely-used composable is exactly the kind of thing that silently breaks a Preview file — confirm nothing else calls `PagePanel(` with positional/named args that no longer match)

Run: `./gradlew :app:compileDebugKotlin --rerun-tasks`
Expected: BUILD SUCCESSFUL. If a preview file fails to compile because it calls `PagePanel` directly, add `onReadMode = {}` to that call site (previews don't need a working read-mode launch).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/g1/sketchbook/sketchbook/PagePanel.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt
git commit -m "feat(readmode): wire read-mode entry point into the page panel"
```

- [ ] **Step 6: Update PROGRESS.md**

Add a dated entry under `## Done` in `PROGRESS.md` (repo root) summarizing what was built, the files involved, and — following this repo's established convention for every OpenGL/GLSurfaceView-touching or otherwise device-only change — an explicit note that this needs real-device verification (drag feel, frame rate on a real page-turn, landscape rotation mid-read, and the GLES3-unsupported fallback path) since none of it can be meaningfully exercised through `compileDebugKotlin` alone or Compose Preview.

---

## Self-Review Notes

- **Spec coverage:** All decisions from the design doc (`docs/superpowers/specs/2026-08-22-sketchbook-read-mode-design.md`) are covered — engine port (Tasks 1–3, 6), right-page-only landscape curl (Task 7's `rightPageWidth`/`hasStaticLeft`), spread pairing incl. cover (Task 5, Task 9's `loadSpreadTextures`), downsampling (Task 4), GLES3 fallback (Task 9), start-page mapping (Task 5's `spreadIndexForPage`, wired in Task 9), personal-sketchbooks-only scope (Task 10 only touches `SketchbookScreens.kt`, not `share/`).
- **Placeholder scan:** The only literal `TODO`-shaped markers are the four `TODO_COPY_FROM_REFERENCE()` calls in Task 7 Step 1, which Task 7 Step 2 explicitly instructs to replace with named, already-existing, already-tested-by-the-reference-project code before moving on — the task is not done until they're gone. Every other step has real, complete code.
- **Type consistency:** `SpreadTextures` (Task 7) is constructed identically in Task 9's `loadSpreadTextures`. `PageTextureProvider.pageBitmap` (Task 4) is called the same way in Task 9. `buildSpreads`/`spreadIndexForPage`/`COVER_PAGE` (Task 5) match their Task 9 call sites. `ReadModeSurface.setSpread`/`onTurnCompleted` (Task 8) match Task 9's usage. `ReadModeScreen`'s signature (Task 9) matches Task 10's call site exactly (`repo`, `book`, `startPage`, `onClose`).
