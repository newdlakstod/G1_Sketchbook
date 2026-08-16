# Login Button Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Google sign-in button use the Splash `enter` button's filled teal style while keeping content-driven width.

**Architecture:** Change only `LoginScreen.kt`. Reuse the existing `DaymoryTeal`, `RoundedCornerShape(50)`, and content padding; no new component or fixed width is introduced.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

## Global Constraints

- Keep the Google label and sign-in callback unchanged.
- Keep horizontal padding at `46.dp` and vertical padding at `14.dp`.
- Do not set a fixed button width.
- Keep loading and error states unchanged.

---

### Task 1: Match the Google Button to Enter

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/LoginScreen.kt`

**Interfaces:**
- Consumes: `DaymoryTeal`, `Color.White`, `onSignIn: () -> Unit`
- Produces: the existing `LoginScreen` API with updated button styling

- [ ] **Step 1: Run a failing source assertion**

Check that the Google button has no `.border(`, has `.background(DaymoryTeal)`, and its label uses `Color.White`. The current outlined implementation must fail this assertion.

- [ ] **Step 2: Implement the minimal style change**

Replace:

```kotlin
.border(2.dp, DaymoryTeal, RoundedCornerShape(50))
```

with:

```kotlin
.background(DaymoryTeal)
```

and change the Google label color from `DaymoryTeal` to `Color.White`. Remove the now-unused `border` import.

- [ ] **Step 3: Verify the assertion and compilation**

Run the source assertion again and run:

```powershell
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: source assertion passes and Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the focused change**

```powershell
git add app/src/main/java/com/g1/sketchbook/ui/LoginScreen.kt docs/superpowers/plans/2026-08-17-login-button-style.md
git commit -m "style: match login button to onboarding"
```
