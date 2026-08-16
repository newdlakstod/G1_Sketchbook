# Onboarding Layout Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render Splash, Login, Login Loading, and Login Error with identical title, duck, subtitle, and CTA coordinates while applying the configured title size before responsive shrink.

**Architecture:** Extract the duplicated Splash/Login structure into one `OnboardingLayout` composable with fixed CTA and error slots. Keep screen-specific behavior in the thin screen wrappers. Retain `OnboardingTitle` measurement but rename its input to `preferredFontSize` and make the preferred-first flow explicit.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

## Global Constraints

- Keep all copy, colors, fonts, image resources, navigation, login, loading, and error behavior unchanged.
- Keep CTA width content-driven with `46.dp` horizontal padding.
- Use `Dimens.Onboarding.titleSp` as the first attempted title size; shrink only on overflow and never enlarge it.
- Do not modify the HTML mockup or unrelated dirty files.

---

### Task 1: Shared Onboarding Layout

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/ui/OnboardingLayout.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/SplashScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/LoginScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/OnboardingTitle.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: `Dimens.Onboarding`, `OnboardingTitle`, `R.drawable.onboarding_duck2`, `DaymoryTeal`
- Produces: `OnboardingLayout(contentDescription, busy, error, ctaLabel, onCta)` and unchanged public `SplashScreen`/`LoginScreen` APIs

- [ ] **Step 1: Run the failing source regression assertion**

Assert that both `SplashScreen.kt` and `LoginScreen.kt` call `OnboardingLayout`, that neither duplicates the outer `Column`, and that `OnboardingTitle.kt` uses `preferredFontSize`. The current implementation must fail.

- [ ] **Step 2: Add the shared layout**

Create `OnboardingLayout.kt` containing the shared background, title, weighted duck area, subtitle, a centered `56.dp` CTA slot, a centered `40.dp` error slot, and common bottom spacing. The CTA button uses the existing filled teal pill and content padding; loading uses the same CTA slot.

- [ ] **Step 3: Replace screen duplication**

Make `SplashScreen` call `OnboardingLayout` with `enter`; make `LoginScreen` call it with `Google 계정으로 로그인`, `busy`, and `error`. Keep both public function signatures unchanged.

- [ ] **Step 4: Clarify preferred-first title fitting**

Rename `maxFontSize` to `preferredFontSize`, initialize the resolved size from it, keep the 55% lower bound, and shrink in 4sp steps only while the preferred size overflows.

- [ ] **Step 5: Verify**

Split Loading and Error into separate Preview entries, then run the source assertions,
`compileDebugKotlin`, `testDebugUnitTest`, and `git diff --check`. Expected: assertions pass and
Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the focused implementation**

Stage only the four onboarding Kotlin files and this plan, then commit with `feat: align onboarding screen states`. Leave all unrelated dirty files untouched.
