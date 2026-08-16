# Login Button Style Design

## Goal

Make the Google sign-in button visually match the Splash screen's `enter` button.

## Design

- Use the same filled `DaymoryTeal` pill background and white label as `enter`.
- Remove the outlined border from the Google sign-in button.
- Keep the existing `RoundedCornerShape(50)` and vertical padding of `14.dp`.
- Do not set a fixed width. Both buttons keep `46.dp` horizontal content padding, so each button's width follows its label length; the Google label naturally produces a longer button.
- Keep loading, error, and sign-in behavior unchanged.

## Verification

- Confirm the Login button uses a filled teal background, white text, and no border.
- Compile the debug Kotlin source set.
- Refresh `OnboardingPreviews.kt` to compare Splash and Login previews.
