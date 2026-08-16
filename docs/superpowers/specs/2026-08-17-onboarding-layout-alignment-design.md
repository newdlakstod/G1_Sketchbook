# Onboarding Layout Alignment Design

## Goal

Keep `daymory`, the duck image, the subtitle, and the CTA at identical positions across Splash, Login, Login Loading, and Login Error states.

## Shared Layout

- Introduce one shared onboarding layout used by both `SplashScreen` and `LoginScreen`.
- The shared layout owns the screen background, system-bar padding, outer padding, title, weighted duck area, subtitle, CTA slot, error slot, and bottom spacing.
- Splash supplies the `enter` button to the CTA slot.
- Login supplies the content-width Google sign-in button to the same CTA slot.
- Loading replaces the Google button inside the same fixed-height CTA slot.
- The error message renders inside a fixed-height error slot below the CTA. Empty states keep that slot blank so content above it never shifts.
- CTA width remains content-driven: label width plus `46.dp` horizontal padding. No fixed button width is added.

## Title Sizing

- Treat `Dimens.Onboarding.titleSp` as the preferred size and try it first.
- Measure `daymory` at that exact size against the available width.
- Shrink only when the preferred size overflows.
- Never enlarge beyond the configured preferred size.
- Keep the current lower bound of 55% of the preferred size.

## Behavior

- Keep Splash navigation, Google sign-in, loading, and error behavior unchanged.
- Keep the existing background, colors, font families, image, and copy unchanged.
- Preserve the filled `DaymoryTeal` CTA style and content-driven button widths.

## Verification

- Add a source-level regression assertion that both Splash and Login route through the shared layout and that title sizing starts from the preferred size.
- Compile debug Kotlin sources and run debug unit tests.
- Refresh all onboarding previews and compare the four states at the same preview dimensions.
