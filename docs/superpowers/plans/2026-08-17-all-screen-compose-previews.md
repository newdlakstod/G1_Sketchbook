# All-Screen Compose Previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reliable Android Studio Compose Preview catalog for all 18 user-visible destinations and major flow states.

**Architecture:** Preview wrappers call production Composables with deterministic sample data and no-op callbacks. Optional preview-only inputs bypass local storage and Firebase without changing runtime defaults.

**Tech Stack:** Kotlin, Jetpack Compose `@Preview`, Android custom `BrushView`, Gradle

## Global Constraints

- Preserve `design-tool/mockup.html` and all unrelated dirty-worktree files.
- Do not perform Firebase, authentication, or persistent local writes during Preview rendering.
- Keep runtime call sites source-compatible through default parameters.

---

### Task 1: Preview data and startup catalog

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/preview/PreviewData.kt`
- Create: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`

- [ ] Add deterministic sample books, dates, names, and participant labels.
- [ ] Add Splash, Login, Login error/busy, and Nickname previews using the real Composables.
- [ ] Run `gradlew.bat compileDebugKotlin` and confirm `BUILD SUCCESSFUL`.

### Task 2: Main navigation catalog

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`
- Replace: `app/src/main/java/com/g1/sketchbook/preview/HomeScreenPreview.kt`
- Create: `app/src/main/java/com/g1/sketchbook/preview/MainTabPreviews.kt`

- [ ] Add optional preview books/dates to bypass repositories while keeping runtime defaults.
- [ ] Add Home, personal, shared, diary, and settings previews.
- [ ] Run `gradlew.bat compileDebugKotlin` and confirm `BUILD SUCCESSFUL`.

### Task 3: Creation, canvas, diary, and calendar catalog

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/preview/BrushCanvasPreview.kt`
- Create: `app/src/main/java/com/g1/sketchbook/preview/FlowPreviews.kt`

- [ ] Expose data-free preview entry points for the three wizard modes and calendar detail.
- [ ] Add personal canvas, diary editor, full-calendar, and detail previews.
- [ ] Run `gradlew.bat compileDebugKotlin` and confirm `BUILD SUCCESSFUL`.

### Task 4: Shared canvas catalog

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/share/SharedBookScreen.kt`
- Create: `app/src/main/java/com/g1/sketchbook/preview/SharedCanvasPreviews.kt`

- [ ] Extract reusable shared canvas layout content from Firebase observation.
- [ ] Supply deterministic three-person grid and maximize states in previews.
- [ ] Verify no preview path creates `ShareRepository` or observes Firebase.

### Task 5: Final verification and handoff

**Files:**
- Modify: `PROGRESS.md`

- [ ] Run `gradlew.bat compileDebugKotlin compileReleaseKotlin`.
- [ ] Run scoped `git diff --check` on changed files.
- [ ] Open `MainTabPreviews.kt` in Android Studio.
- [ ] Commit only preview implementation, preview-enabling production changes, docs, and `PROGRESS.md`.
