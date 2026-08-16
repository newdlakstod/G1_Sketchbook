# Shared Sketchbook Cover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render every existing and new sketchbook with one reusable yellow cover and a cover-dependent darker spine, without adding cover persistence or editor UI.

**Architecture:** A focused `SketchbookCover` composable owns the cover background, optional image, and left spine overlay. Home and list screens provide their existing badges/text through a content slot, while `Theme.kt` no longer owns a rotating cover palette.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Android Gradle Plugin 8.7.3, PowerShell source regression check

## Global Constraints

- Default cover color is exactly `#FFBF2A`.
- Solid-color covers use a black spine overlay at 20% opacity.
- Image covers use a black spine overlay at 70% opacity.
- Spine width is 9% of the cover width.
- Existing rounded shape, depth, title/date, shared badge, favorite/delete controls, and click behavior remain unchanged.
- Cover editing, image selection, color-wheel UI, persistence, and repository migration are outside this change.
- Add Korean comments beside all user-facing cover tuning constants.
- Preserve unrelated changes in the dirty worktree.

---

### Task 1: Add the failing cover contract check

**Files:**
- Create: `scripts/verify_sketchbook_cover.ps1`

**Interfaces:**
- Consumes: Kotlin source files under `app/src/main/java/com/g1/sketchbook`.
- Produces: A zero-exit verification command only when the shared cover API and caller cleanup are present.

- [ ] **Step 1: Write the failing source contract check**

```powershell
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$componentPath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookCover.kt'
$themePath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/ui/theme/Theme.kt'
$homePath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt'
$listPath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt'

function Assert-Contains([string]$Text, [string]$Needle, [string]$Message) {
    if (-not $Text.Contains($Needle)) { throw $Message }
}

if (-not (Test-Path -LiteralPath $componentPath)) { throw 'SketchbookCover.kt is missing.' }
$component = Get-Content -Raw -Encoding UTF8 -LiteralPath $componentPath
$theme = Get-Content -Raw -Encoding UTF8 -LiteralPath $themePath
$home = Get-Content -Raw -Encoding UTF8 -LiteralPath $homePath
$list = Get-Content -Raw -Encoding UTF8 -LiteralPath $listPath

Assert-Contains $component 'Color(0xFFFFBF2A)' 'Default cover color must be #FFBF2A.'
Assert-Contains $component '0.20f' 'Solid-color spine opacity must be 20%.'
Assert-Contains $component '0.70f' 'Image spine opacity must be 70%.'
Assert-Contains $component '0.09f' 'Spine width must be 9%.'
Assert-Contains $component 'coverImage: Painter? = null' 'Optional image cover API is missing.'
Assert-Contains $home 'SketchbookCover(' 'Home must use SketchbookCover.'
Assert-Contains $list 'SketchbookCover(' 'Sketchbook list must use SketchbookCover.'
if ($theme.Contains('CoverColors')) { throw 'Theme.kt still contains CoverColors.' }
if ($home.Contains('R.drawable.mascot_duck') -or $list.Contains('R.drawable.mascot_duck')) {
    throw 'A sketchbook cover still renders mascot_duck.'
}
```

- [ ] **Step 2: Run the check and verify RED**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify_sketchbook_cover.ps1`

Expected: FAIL with `SketchbookCover.kt is missing.` because production code has not been added.

- [ ] **Step 3: Commit the failing check**

```powershell
git add -- scripts/verify_sketchbook_cover.ps1
git commit -m "test: define sketchbook cover contract"
```

### Task 2: Add the shared cover and connect both screens

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookCover.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Test: `scripts/verify_sketchbook_cover.ps1`

**Interfaces:**
- Consumes: `Modifier`, optional `Painter`, selected `Color`, and caller-provided `BoxScope` content.
- Produces: `DefaultSketchbookCoverColor`, `SketchbookCoverShape`, and `SketchbookCover(modifier, coverColor, coverImage, content)`.

- [ ] **Step 1: Implement the minimal shared component**

```kotlin
val DefaultSketchbookCoverColor = Color(0xFFFFBF2A)
val SketchbookCoverShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp)
private const val CoverSpineWidthFraction = 0.09f
private const val SolidCoverSpineAlpha = 0.20f
private const val ImageCoverSpineAlpha = 0.70f

@Composable
fun SketchbookCover(
    modifier: Modifier = Modifier,
    coverColor: Color = DefaultSketchbookCoverColor,
    coverImage: Painter? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clip(SketchbookCoverShape).background(coverColor)) {
        coverImage?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(CoverSpineWidthFraction)
                .align(Alignment.CenterStart)
                .background(Color.Black.copy(alpha = if (coverImage == null) SolidCoverSpineAlpha else ImageCoverSpineAlpha)),
        )
        content()
    }
}
```

Place Korean comments immediately above the default color and the three tuning constants.

- [ ] **Step 2: Replace palette and duck rendering**

In `Theme.kt`, delete `CoverColors`. In `MainScreen.kt`, use `DefaultSketchbookCoverColor` for the two depth layers and `SketchbookCover` for the front face. In `SketchbookScreens.kt`, remove the color argument from `CoverCard` and render its existing scrim/text inside `SketchbookCover`.

- [ ] **Step 3: Run the contract check and verify GREEN**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify_sketchbook_cover.ps1`

Expected: exit code 0 with no error output.

- [ ] **Step 4: Compile and run unit tests**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit only the cover implementation**

Stage the new component and clean files normally. For `MainScreen.kt` and `SketchbookScreens.kt`, stage only the cover-related hunks so pre-existing user changes remain unstaged.

```powershell
git commit -m "feat: unify sketchbook cover design"
```

### Task 3: Record handoff and final verification

**Files:**
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: Passing contract check and Gradle verification output.
- Produces: A dated handoff entry documenting the shared cover behavior and deferred editor scope.

- [ ] **Step 1: Update `PROGRESS.md`**

Record that all covers now use the default yellow shared component, dynamic solid-color spines use a 20% black overlay, image spines use 70%, and image/color selection persistence remains deferred to the cover-editor feature.

- [ ] **Step 2: Re-run final verification**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify_sketchbook_cover.ps1
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
git diff --check
```

Expected: contract check exits 0, Gradle prints `BUILD SUCCESSFUL`, and `git diff --check` reports no whitespace errors in the cover changes.

- [ ] **Step 3: Commit the handoff update**

```powershell
git add -- PROGRESS.md
git commit -m "docs: record shared sketchbook cover"
```
