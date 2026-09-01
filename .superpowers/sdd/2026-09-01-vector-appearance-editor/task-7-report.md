# Task 7 report — unified vector appearance renderer

## Delivered

- Added one pure editable-path geometry contract: `RenderRoute`, `RenderedObjectGeometry`,
  `renderedPolygons`, `renderedObjectBounds`, and `pointInRenderedObject`.
- Added collision-safe cache keys from canonical immutable-value snapshots (not `hashCode`), with
  geometry and bounds cached together. Viewport state is deliberately excluded; replacement
  evicts only stale entries for that object or its referenced profile.
- Added `drawVectorDocument` and `vectorDocumentToSvg`. Legacy objects continue through the exact
  v1 canvas/SVG entry points and retain their original ordering in mixed documents. Editable SVG
  serializes the routed expanded polygons with distinct `data-role="fill"` and
  `data-role="stroke"` metadata.
- ART/PATTERN only route when the referenced typed profile is non-empty, finite, and
  non-degenerate. Missing, wrong-kind, empty, malformed, or degenerate profiles take BASIC
  without altering the stored profile ID. Final editable geometry applies persisted transforms
  before draw/export/bounds/hit testing.

## RED / GREEN evidence

- **RED:** `:app:testDebugUnitTest --tests VectorRenderGeometryTest --tests VectorSvgExportTest`
  failed at unit-test compilation with the expected unresolved v2 renderer/export/cache APIs.
- **GREEN focused:** 24 renderer/export/cache tests passed after implementation. One initial test
  assertion was corrected after confirming that round caps expand before persisted scale.
- **GREEN full vector suite:** `:app:testDebugUnitTest --tests "com.g1.sketchbook.vector.*" --no-daemon`
  passed: **146 tests, 0 failures, 0 errors**.

## Self-review

- Kept `drawVectorPage` and `vectorPageToSvg` unchanged and made document-level legacy dispatch
  call them directly, avoiding legacy conversion.
- Confirmed profile access uses safe lookup/casts after route selection; no `getValue` can throw
  after fallback.
- Added regression coverage for fallback families, transformed bounds/hits, thin BASIC hit
  tolerance, Pattern gaps using only final stamped extents, legacy/mixed SVG order, open-fill vs
  open-stroke SVG roles, and per-object/profile cache invalidation.
- Ran `git diff --check` successfully. `.kotlin/` remains untracked and is not part of this task.

## Concerns / follow-up

- `drawVectorDocument` accepts a cache so the Android host can retain it across frames; its
  default is intentionally per-call to keep the API ownership-free. Task 10 should hold and pass
  one cache instance for frame-to-frame reuse.
- No Task 8 selection/transform command code was added; it can consume the document-space bounds
  and hit-test APIs directly.
