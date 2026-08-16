# Onboarding Theme Contrast Design

## Goal

Make every onboarding state use the same background as MainScreen and invert all branded foreground elements between light and dark themes.

## Theme Colors

- Screen background uses `MaterialTheme.colorScheme.background`, identical to MainScreen.
- The `daymory` title, subtitle, duck image, loading indicator, and CTA background use `MaterialTheme.colorScheme.onBackground`.
- CTA label uses `MaterialTheme.colorScheme.background`, producing the inverse of its button background.
- Error copy uses `MaterialTheme.colorScheme.error` so failures remain distinguishable.

## Image Treatment

- Apply `ColorFilter.tint(onBackground)` to the duck PNG.
- Preserve transparency while rendering all visible duck pixels in the current theme's foreground color.
- Do not create separate light/dark bitmap assets.

## Cleanup

- Remove the obsolete onboarding-specific sage background palette, dark palette, palette data class, and palette function.
- Keep layout positions, sizing, copy, and screen behavior unchanged.

## Verification

- Assert that `OnboardingLayout` uses `background` and `onBackground` and applies a duck color filter.
- Assert that the legacy onboarding palette no longer exists.
- Compile debug Kotlin and run debug unit tests.
- Refresh the onboarding Preview in both light and dark themes.
