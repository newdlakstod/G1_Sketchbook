# Nickname Dialog Design

## Goal

Replace the full-screen nickname onboarding step with a focused modal dialog over the login onboarding screen.

## Flow

- After Google sign-in, keep the login onboarding screen visible behind the modal while `needsNickname` is true.
- Replace `NicknameScreen` with `NicknameDialog` and keep the existing `RootViewModel.saveNickname` persistence path.
- Confirming a non-blank nickname saves it and continues to the main tabs.
- Pressing Cancel, tapping outside the dialog, or pressing Back calls `RootViewModel.signOut` and returns to the login screen.
- Do not allow an unnamed authenticated user to continue into the app.

## Dialog Layout

- Use a centered Material 3 `Dialog` with a rounded surface.
- Show one pill-shaped outlined text field with a fully transparent container.
- Render the placeholder `별명을 입력해주세요.` in light grey; entered text continues using the active theme's text color.
- Limit input to 16 characters, matching the current nickname behavior.
- Place `취소` and `확인` text buttons in a row aligned to the lower-right corner.
- Disable `확인` while the trimmed nickname is blank.
- Keep the pill background transparent in both themes and use a subtle theme-aware outline so its shape remains visible.
- Add Korean comments beside the pill styling and dismissal behavior so the edit points are clear in Android Studio.

## Code Boundaries

- Delete `ui/NicknameScreen.kt` and add `ui/NicknameDialog.kt`.
- Update `MainActivity.AppRoot` to render `LoginScreen` plus `NicknameDialog` for `needsNickname`.
- Update `OnboardingPreviews.kt` to preview the dialog state instead of the removed full-screen screen.
- Do not change authentication, nickname persistence, MainTab, or Firebase behavior.

## Verification

- Assert that `NicknameScreen` is no longer referenced.
- Assert that the dialog exposes `onCancel` and `onConfirm` callbacks, uses the requested placeholder, and limits input to 16 characters.
- Assert that the `needsNickname` branch connects Cancel to `signOut` and Confirm to `saveNickname`.
- Compile debug Kotlin and run debug unit tests.
- Refresh the nickname entry in `OnboardingPreviews.kt` and confirm the pill field and lower-right buttons are visible.
