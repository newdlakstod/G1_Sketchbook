# Onboarding Theme Inversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Invert ivory and Daymory Teal only inside the four onboarding states when the active Compose theme is dark.

**Architecture:** Keep the global Material color schemes unchanged. Derive whether the active scheme is dark from its original background luminance, select a two-color onboarding pair in `OnboardingLayout.kt`, and route every visual element through that pair.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Gradle Plugin

## Global Constraints

- Light onboarding: ivory background and `DaymoryTeal` foreground.
- Dark onboarding: `DaymoryTeal` background and ivory foreground.
- CTA label uses the current onboarding background color.
- Splash, Login, Loading, and Error continue sharing `OnboardingLayout`.
- Do not change `Theme.kt`, layout positions, sizing, copy, or callbacks.
- Add a Korean comment explaining the edit point.

---

### Task 1: Add the onboarding-only color branch

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/OnboardingLayout.kt`

**Interfaces:**
- Consumes: `DaymoryTeal`, `Ivory`, and `MaterialTheme.colorScheme.background`
- Produces: `internal fun onboardingColors(isDark: Boolean): Pair<Color, Color>` where the pair is `(background, foreground)`

- [ ] **Step 1: Run a failing source assertion**

```powershell
$p = 'app/src/main/java/com/g1/sketchbook/ui/OnboardingLayout.kt'
$s = Get-Content -LiteralPath $p -Raw
if ($s -match 'fun onboardingColors' -and $s -match 'Ivory to DaymoryTeal' -and $s -match 'DaymoryTeal to Ivory') { exit 0 }
exit 1
```

Expected: exit code 1 because the onboarding-only mapping does not exist.

- [ ] **Step 2: Add the minimal mapping and theme branch**

```kotlin
internal fun onboardingColors(isDark: Boolean): Pair<Color, Color> =
    if (isDark) DaymoryTeal to Ivory else Ivory to DaymoryTeal

// 온보딩 색상 조절: 라이트는 아이보리 배경/틸 요소, 다크는 두 색을 서로 반전한다.
val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
val (backgroundColor, foregroundColor) = onboardingColors(isDark)
```

Use `foregroundColor` for title, subtitle, duck tint, loading indicator, error copy, and CTA background. Use `backgroundColor` for the screen and CTA label.

- [ ] **Step 3: Run the source assertion again**

Run the Step 1 command.

Expected: exit code 0.

- [ ] **Step 4: Compile and run available unit tests**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the color branch**

```powershell
git add -- app/src/main/java/com/g1/sketchbook/ui/OnboardingLayout.kt
git commit -m "style: invert onboarding teal palette in dark mode"
```

---

### Task 2: Verify previews and document the result

**Files:**
- Verify: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: existing light/dark onboarding previews
- Produces: documented, verified onboarding inversion

- [ ] **Step 1: Confirm both preview modes still exist**

```powershell
$s = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt' -Raw
if ($s -match 'ThemeMode.LIGHT' -and $s -match 'ThemeMode.DARK') { exit 0 }
exit 1
```

Expected: exit code 0.

- [ ] **Step 2: Refresh the Android Studio onboarding Preview**

Open `OnboardingPreviews.kt`, select Split or Design, and confirm light uses ivory/teal while dark uses teal/ivory for Splash, Login, Loading, and Error.

- [ ] **Step 3: Update the handoff record**

Add a dated `Done` entry to `PROGRESS.md` stating that the inversion is onboarding-only and that all four states use the shared mapping.

- [ ] **Step 4: Run final verification**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
git diff --check -- PROGRESS.md
```

Expected: `BUILD SUCCESSFUL` and no whitespace errors.

- [ ] **Step 5: Commit the handoff update**

```powershell
git add -- PROGRESS.md
git commit -m "docs: record onboarding theme inversion"
```
