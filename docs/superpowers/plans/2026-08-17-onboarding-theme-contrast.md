# Onboarding Theme Contrast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match MainScreen's background and invert all onboarding brand elements between light and dark themes.

**Architecture:** Derive `backgroundColor` and `foregroundColor` once in the shared `OnboardingLayout`. Apply that pair to every brand element and remove the now-unused onboarding palette. Add explicit dark Preview entries for visual comparison.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

## Global Constraints

- Preserve all onboarding layout coordinates, dimensions, copy, and behavior.
- Use `MaterialTheme.colorScheme.background` and `onBackground`; do not hardcode light/dark colors.
- Keep error text on `MaterialTheme.colorScheme.error`.
- Do not modify unrelated dirty files or the HTML mockup.

---

### Task 1: Theme-Adaptive Onboarding Colors

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/OnboardingLayout.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/OnboardingTitle.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: `MaterialTheme.colorScheme.background`, `onBackground`, and `error`
- Produces: unchanged `OnboardingLayout`, `SplashScreen`, and `LoginScreen` APIs

- [ ] **Step 1: Run a failing source regression assertion**

Assert that `OnboardingLayout` uses `background`, `onBackground`, and `ColorFilter.tint`, and that `OnboardingTitle.kt` no longer declares `onboardingPalette`. The current implementation must fail.

- [ ] **Step 2: Apply the theme color pair**

Use `backgroundColor` for the screen and CTA label. Use `foregroundColor` for the title, subtitle, duck tint, loading indicator, and CTA background. Use the theme error color for error copy.

- [ ] **Step 3: Remove obsolete palette code**

Delete the onboarding sage/dark constants, `OnboardingPalette`, and `onboardingPalette()` plus unused imports.

- [ ] **Step 4: Add dark previews**

Add Splash Dark and Login Dark Preview entries and allow the local Preview theme wrapper to accept `ThemeMode`.

- [ ] **Step 5: Verify and commit**

Run the source assertion, `compileDebugKotlin`, `testDebugUnitTest`, and `git diff --check`. Stage only the four listed files and this plan, then commit with `style: invert onboarding theme colors`.
