# Onboarding Theme Inversion Design

## Goal

Invert the fixed ivory and Daymory Teal onboarding palette in dark mode without changing the rest of the app theme.

## Theme Colors

- Light mode uses ivory for the screen background and `DaymoryTeal` for the title, subtitle, duck image, loading indicator, error copy, and CTA background.
- Dark mode swaps those two colors: `DaymoryTeal` becomes the screen background and ivory becomes every foreground element and the CTA background.
- The CTA label always uses the current screen background color, keeping it inverse to the button background.
- The palette branch lives only in the shared `OnboardingLayout`; the global light and dark color schemes remain unchanged.

## Image Treatment

- Apply `ColorFilter.tint(foregroundColor)` to the duck PNG.
- Preserve transparency while rendering all visible duck pixels in the current theme's foreground color.
- Do not create separate light/dark bitmap assets.

## Cleanup

- Reuse the existing `DaymoryTeal` and `Ivory` constants; do not add another palette type or dependency.
- Add a Korean comment beside the Kotlin theme branch explaining the light/dark inversion, matching the project's requested edit guidance.
- Keep layout positions, sizing, copy, and screen behavior unchanged.

## Verification

- Assert that `OnboardingLayout` selects ivory/teal for light mode and teal/ivory for dark mode.
- Assert that the CTA label uses the selected background and the duck uses the selected foreground.
- Assert that no global theme color scheme is changed.
- Compile debug Kotlin and run debug unit tests.
- Refresh the onboarding Preview in both light and dark themes.
