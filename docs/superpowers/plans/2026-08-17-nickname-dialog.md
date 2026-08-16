# Nickname Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the full-screen nickname onboarding screen with a modal containing a transparent pill input and lower-right Cancel/Confirm actions.

**Architecture:** Keep nickname persistence in `RootViewModel`. Introduce a focused `NicknameDialog` composable, render it over the existing login onboarding screen while `needsNickname` is true, and route dismissal to sign-out.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Gradle Plugin

## Global Constraints

- The text field container is fully transparent in light and dark themes.
- The placeholder `별명을 입력해주세요.` is light grey; entered text uses the theme text color.
- The field remains pill-shaped through a rounded outline.
- Input is limited to 16 characters and Confirm is disabled for blank text.
- Cancel, outside tap, and Back sign out and return to Login.
- Confirm uses the existing `saveNickname` path.
- Add Korean comments beside the pill styling and dismissal behavior.

---

### Task 1: Replace the full-screen nickname composable

**Files:**
- Delete: `app/src/main/java/com/g1/sketchbook/ui/NicknameScreen.kt`
- Create: `app/src/main/java/com/g1/sketchbook/ui/NicknameDialog.kt`

**Interfaces:**
- Consumes: `onCancel: () -> Unit`, `onConfirm: (String) -> Unit`
- Produces: `@Composable fun NicknameDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit)`

- [ ] **Step 1: Run the failing structural assertion**

```powershell
$dialog = 'app/src/main/java/com/g1/sketchbook/ui/NicknameDialog.kt'
if (Test-Path $dialog) { exit 0 }
exit 1
```

Expected: exit code 1 because the dialog file does not exist.

- [ ] **Step 2: Create the minimal dialog**

```kotlin
@Composable
fun NicknameDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    placeholder = {
                        Text(
                            "별명을 입력해주세요.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("취소") }
                    TextButton(
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                    ) { Text("확인") }
                }
            }
        }
    }
}
```

Add a Korean comment above `Dialog(onDismissRequest = onCancel)` explaining that outside tap and Back use the same cancellation path, and another above the transparent container colors explaining how to edit the pill.

- [ ] **Step 3: Delete the old full-screen file**

Delete `NicknameScreen.kt`; do not keep a compatibility wrapper.

- [ ] **Step 4: Run the structural assertion again**

Expected: exit code 0, and this additional assertion exits 0:

```powershell
if (Test-Path 'app/src/main/java/com/g1/sketchbook/ui/NicknameScreen.kt') { exit 1 }
exit 0
```

- [ ] **Step 5: Compile the new composable**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected before Task 2: compilation fails only because existing callers still reference `NicknameScreen`; the new dialog itself has no Kotlin errors.

---

### Task 2: Connect the dialog to onboarding and Preview

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/MainActivity.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`

**Interfaces:**
- Consumes: `RootViewModel.signOut`, `RootViewModel.saveNickname`, `LoginScreen`
- Produces: authenticated nickname dialog flow and Android Studio Preview entry

- [ ] **Step 1: Run a failing wiring assertion**

```powershell
$s = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/MainActivity.kt' -Raw
if ($s -match 'NicknameDialog\(onCancel = vm::signOut, onConfirm = vm::saveNickname\)') { exit 0 }
exit 1
```

Expected: exit code 1 because `AppRoot` still calls `NicknameScreen`.

- [ ] **Step 2: Replace the `needsNickname` branch**

```kotlin
state.needsNickname -> {
    LoginScreen(busy = false, error = null, onSignIn = {})
    NicknameDialog(onCancel = vm::signOut, onConfirm = vm::saveNickname)
}
```

Replace the `NicknameScreen` import with `NicknameDialog`.

- [ ] **Step 3: Replace the nickname Preview**

```kotlin
@Preview(name = "05 Nickname dialog", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun NicknameDialogPreview() = PreviewTheme {
    LoginScreen(busy = false, error = null, onSignIn = {})
    NicknameDialog(onCancel = {}, onConfirm = {})
}
```

Replace the old Preview import and function rather than adding a duplicate.

- [ ] **Step 4: Run wiring and styling assertions**

```powershell
$activity = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/MainActivity.kt' -Raw
$dialog = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/ui/NicknameDialog.kt' -Raw
$preview = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt' -Raw
if (-not ($activity -match 'onCancel = vm::signOut' -and $activity -match 'onConfirm = vm::saveNickname')) { exit 1 }
if (-not ($dialog -match 'Color.Transparent' -and $dialog -match 'alpha = 0.35f' -and $dialog -match 'take\(16\)')) { exit 1 }
if (-not $preview.Contains('05 Nickname dialog')) { exit 1 }
exit 0
```

Expected: exit code 0.

- [ ] **Step 5: Compile and test**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Verify and document the completed flow

**Files:**
- Verify: `app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt`
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: the connected dialog flow
- Produces: verified Preview and handoff record

- [ ] **Step 1: Refresh the nickname Preview in Android Studio**

Open `OnboardingPreviews.kt` in Design or Split and confirm the input interior is transparent, the placeholder is light grey, and Cancel/Confirm are aligned lower-right.

- [ ] **Step 2: Update `PROGRESS.md`**

Add a dated `Done` entry describing the removed full-screen nickname step, transparent pill field, callback wiring, and verification command.

- [ ] **Step 3: Run final verification**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
git diff --check -- app/src/main/java/com/g1/sketchbook/ui/NicknameDialog.kt app/src/main/java/com/g1/sketchbook/MainActivity.kt app/src/main/java/com/g1/sketchbook/preview/OnboardingPreviews.kt PROGRESS.md
```

Expected: `BUILD SUCCESSFUL` and no whitespace errors.
