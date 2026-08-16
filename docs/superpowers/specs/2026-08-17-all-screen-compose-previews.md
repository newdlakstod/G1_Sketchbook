# All-Screen Compose Preview Design

## Goal

Provide an Android Studio Preview catalog for every user-visible app destination and major creation state while keeping `design-tool/mockup.html` unchanged.

## Architecture

Preview functions call the real production Composables. Preview wrappers supply deterministic sample values and no-op callbacks; repository- and Firebase-bound screens receive preview data through optional parameters or dedicated data-free preview content. Runtime callers continue using existing defaults, so app behavior is unchanged.

## Preview catalog

1. Splash
2. Login
3. Login busy/error
4. Nickname
5. Home
6. Personal sketchbooks
7. Shared sketchbooks
8. Diary tab
9. Settings
10. Personal-book creation
11. Shared-book creation
12. Shared-book join
13. Personal canvas editor
14. Diary editor
15. Full calendar grid
16. Calendar day detail
17. Shared canvas grid
18. Shared canvas maximize

Transient confirmations and brush/color popups are not separate destinations; they remain available through Interactive Preview where their parent preview supports interaction.

## File layout

- `preview/PreviewData.kt`: deterministic books, dates, and participants.
- `preview/OnboardingPreviews.kt`: splash/login/nickname states.
- `preview/MainTabPreviews.kt`: the five main navigation destinations.
- `preview/FlowPreviews.kt`: creation, canvas, diary, and calendar destinations.
- `preview/SharedCanvasPreviews.kt`: data-free shared canvas layouts.

## Safety and constraints

- No preview may sign in, write local app data, or connect to Firebase.
- Production Composables remain the single source of truth; do not duplicate complete screen layouts in preview files.
- New preview inputs are optional and default to the existing runtime behavior.
- Existing uncommitted user changes and the HTML mockup are preserved.

## Verification

- Compile both debug and release Kotlin source sets.
- Open each preview catalog file in Android Studio and confirm the Preview gutter action is available.
- Record any renderer-only limitation separately; compilation alone does not prove Layoutlib rendering.
