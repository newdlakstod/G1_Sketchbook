# Shared Sketchbook Cover Design

## Goal

Replace the index-based cover palette and duck artwork with one reusable sketchbook-cover component. Existing and newly created sketchbooks use the same default yellow cover, while the component remains ready for future user-selected colors and cover images.

## Visual Design

- Use `#FFBF2A` as the default cover color.
- Draw a vertical spine accent along the left edge, occupying about 9% of the visible cover width.
- For a solid-color cover, render the spine as a black overlay with 20% opacity over the selected color. This automatically produces a related darker shade for yellow, blue, or any future color without maintaining a second palette.
- For an image cover, render the image with crop scaling and keep the spine visible as a black overlay with 70% opacity.
- Remove the duck image from Home and sketchbook-list covers.
- Keep the existing rounded cover silhouette, stacked depth treatment, title/date text, shared badge, and click behavior.

## Shared Component

- Add a reusable `SketchbookCover` composable in the sketchbook UI package.
- Accept a cover color with `#FFBF2A` as the default.
- Accept an optional image painter. A non-null image takes visual precedence over the color, while the color remains as a loading/fallback background.
- Accept caller content so Home and list screens can keep their current title, date, and shared indicators without duplicating the cover background and spine logic.
- Keep the spine overlay above the color/image and below the caller content.
- Add Korean comments beside the default color, spine width, solid-color overlay opacity, and image overlay opacity so these values are easy to find in Android Studio.

## Data and Compatibility

- Remove `CoverColors` from `Theme.kt` and remove all index-based cover-color selection.
- Do not add cover fields to `Sketchbook` storage yet. The repository schema and Firebase/local persistence remain unchanged.
- Existing and new sketchbooks therefore render with the same default yellow cover immediately.
- The optional image and color parameters establish the rendering path for a later cover editor, but this task does not add image selection, a color wheel, URI storage, migration, or editing UI.

## Code Boundaries

- Add the shared cover component and its visual constants in a focused new Kotlin file.
- Update `MainScreen.kt` and `SketchbookScreens.kt` to use it.
- Remove only cover-specific uses of `mascot_duck`; keep the drawable because other app screens may still use it.
- Preserve unrelated work already present in the dirty worktree.

## Verification

- Add source-level regression tests for the default color, dynamic solid-color spine overlay, image spine overlay, and removal of `CoverColors`/cover duck usages.
- Compile debug Kotlin and run debug unit tests.
- Refresh Home, Personal Sketchbooks, and Shared Sketchbooks previews and confirm that every cover uses the shared yellow design with a darker left spine and no duck.

