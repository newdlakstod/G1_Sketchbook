# Main Tab Shared Layout Design

## Goal

Make the five main tabs share one editable Compose layout while preserving each tab's existing content and behavior.

## Structure

- Add `ui/main/MainTabLayout.kt`.
- `MainTabLayout` owns the portrait bottom navigation, landscape side navigation, screen background, and system-bar handling currently in `MainScreen`.
- `MainTabPage` owns the common top/side/bottom spacing, centered `daymory` header, per-screen title, title spacing, and an optional right-side action slot.
- Keep tab routing and wizard state in `MainScreen`; it supplies the selected tab content to `MainTabLayout`.
- Keep Home carousel, sketchbook filters/grid, diary calendar, and Settings controls in their existing feature files. They become the body content supplied to `MainTabPage`.

## Screen Mapping

- Home: title `Draw your time`; the personal/shared switch remains the header action.
- Personal sketchbooks: title `Sketchbook list`; filters and grid remain in `SketchbookScreens.kt`.
- Shared sketchbooks: title `Draw together`; create/join actions remain header actions.
- Diary: title `A piece of today`; calendar controls remain in `DiaryScreens.kt`.
- Settings: title `Setting`; settings cards remain in `MainScreen.kt`.

## Editing Rules

- Shared header/title positions and general margins are edited in `MainTabLayout.kt` or the existing `Dimens.Screen` values.
- Screen-specific content sizing remains in its current feature file or matching `Dimens` group.
- Add Korean comments beside the shared layout controls so future position and spacing edits are discoverable in Android Studio.

## Behavior Preservation

- Preserve tab selection, callbacks, Preview sample data, portrait bottom navigation, and landscape side navigation.
- Remove the sketchbook tab's redundant nested `Scaffold`; the shared layout supplies the background and insets once.
- Do not copy production UI into Preview-only code.
- Do not change colors, copy, data access, Firebase behavior, or navigation behavior as part of this refactor.

## Verification

- Add source-level assertions that all five tabs route through `MainTabLayout`/`MainTabPage` and that the redundant nested tab scaffold is removed.
- Compile debug Kotlin and run debug unit tests.
- Render all five entries in `MainTabPreviews.kt` and confirm their header, title, margins, and navigation positions remain aligned.
