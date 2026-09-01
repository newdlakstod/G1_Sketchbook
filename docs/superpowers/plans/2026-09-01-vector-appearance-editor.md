# Vector Appearance Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the vector canvas as a touch-first path editor where selected paths and new-path defaults share one Appearance panel, with real Basic, Art, and Pattern brush rendering and non-destructive v1 compatibility.

**Architecture:** Introduce a versioned `VectorDocument` made of stable-id legacy or editable path objects, and keep the deployed v1 document untouched in both local storage and Firebase. A pure editor state owns selection, commands, and appearance; focused geometry, brush, selection, transform, and codec units feed a thin Android canvas host and adaptive Compose shell.

**Tech Stack:** Kotlin 2.x, Android custom `View`/`Canvas`, Jetpack Compose Material 3, Firebase Realtime Database, pure Kotlin/JVM unit tests, Gradle 8.11.1.

**Spec:** `docs/superpowers/specs/2026-09-01-vector-appearance-editor-design.md`

## Global Constraints

- Do not modify or delete deployed local `vector_canvas.json`; write new documents only to `vector_canvas_v2.json`.
- Do not modify or delete Firebase `vectorCanvas`; write new documents only to sibling node `vectorCanvasV2`.
- A valid v2 document wins; missing or invalid v2 falls back to v1 without writing either source.
- Existing stamp brushes are Pattern brushes; a missing brush profile renders as Basic without erasing the stored profile id.
- Fill virtually closes an open path; Stroke closes only when `geometry.closed == true`.
- Selected paths receive Appearance changes; with no selection, changes update only the defaults for future paths.
- Art brushes stretch one normalized SVG source along the entire path; Pattern brushes repeat shapes by arc length.
- Anchor-point and Bézier-handle editing, groups, compound paths, text, gradients, and vector collaboration remain out of scope.
- No new runtime dependency is required; the v2 codec stays pure Kotlin so local JVM tests can execute it.
- Every task uses failing-test-first development and commits only the files listed for that task.

## File Structure

### New model and engine files

- `app/src/main/java/com/g1/sketchbook/vector/VectorDocument.kt` — v2 document, object, geometry, Appearance, legacy adapter.
- `app/src/main/java/com/g1/sketchbook/vector/VectorDocumentCodec.kt` — deterministic pure-Kotlin v2 JSON codec and v1 fallback selection.
- `app/src/main/java/com/g1/sketchbook/vector/VectorDocumentStore.kt` — dual-file local read and atomic v2-only writes.
- `app/src/main/java/com/g1/sketchbook/vector/PathGeometry.kt` — virtual Fill closure and Basic variable-width Stroke geometry.
- `app/src/main/java/com/g1/sketchbook/vector/VectorBrushProfile.kt` — shared typed Art/Pattern profile contract.
- `app/src/main/java/com/g1/sketchbook/vector/ArtBrush.kt` — Art profile normalization and arc-length deformation.
- `app/src/main/java/com/g1/sketchbook/vector/VectorBrushRepository.kt` — typed Art/Pattern profile persistence and v1 stamp compatibility.
- `app/src/main/java/com/g1/sketchbook/vector/VectorSelectionEngine.kt` — topmost hit test, lasso selection, rendered selection bounds.
- `app/src/main/java/com/g1/sketchbook/vector/VectorTransformEngine.kt` — translate, scale, and rotate selected objects.
- `app/src/main/java/com/g1/sketchbook/vector/VectorEditorCommand.kt` — reversible editor commands.
- `app/src/main/java/com/g1/sketchbook/vector/VectorEditorState.kt` — document, selection, defaults, tools, undo/redo.
- `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasInteraction.kt` — pure handle/gesture decisions used by Android input.
- `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasHost.kt` — Android View rendering and pointer bridge.

### New Compose files

- `app/src/main/java/com/g1/sketchbook/vector/VectorEditorLayout.kt` — adaptive portrait-bottom/landscape-side shell and top bar.
- `app/src/main/java/com/g1/sketchbook/vector/VectorToolRail.kt` — select, pen, eraser, and hand rail.
- `app/src/main/java/com/g1/sketchbook/vector/VectorAppearancePanel.kt` — selected/default Appearance controls.
- `app/src/main/java/com/g1/sketchbook/vector/VectorBrushLibrary.kt` — Basic/Art/Pattern tabs, previews, import, and profile management.

### Existing files changed

- `VectorPage.kt` remains the v1 parser/model and gains no destructive migration behavior.
- `StrokeGeometry.kt`, `VectorRenderer.kt`, and `VectorSvgExport.kt` route v2 geometry while preserving legacy rendering.
- `StampBrush.kt` and `StampBrushRepository.kt` become Pattern-compatible adapters over typed profiles.
- `SketchbookRepository.kt` and `SketchbookSync.kt` gain v2 document/store APIs while keeping v1 APIs.
- `BackupModels.kt`, `BackupRepository.kt`, and `BackupSync.kt` add the independent `vectorCanvasV2` and typed brush payloads.
- `VectorCanvasScreen.kt` becomes a small compatibility entry point delegating to `VectorEditorLayout`.
- `PROGRESS.md` records completed milestones and verification evidence.

---

### Task 1: Versioned document model and legacy adapter

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorDocument.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorDocumentTest.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorDocumentTestFixtures.kt`
- Read only: `app/src/main/java/com/g1/sketchbook/vector/VectorPage.kt`

**Interfaces:**
- Consumes: `VectorPage`, `VectorStroke`, `VectorPoint`, `VectorCap`.
- Produces: `VectorDocument`, `VectorObject`, `EditablePathObject`, `LegacyStrokeObject`, `PathGeometry`, `PathPoint`, `PathAppearance`, `legacyPageAsDocument(page)`, `convertLegacyObject(object)`.

- [ ] **Step 1: Write failing model and conversion tests**

```kotlin
@Test fun legacyPageBecomesStableLegacyObjectsWithoutChangingStrokes() {
    val stroke = VectorStroke(0xFF123456, listOf(VectorPoint(1f, 2f, 4f), VectorPoint(5f, 6f, 2f)))
    val page = VectorPage(listOf(stroke))
    val document = legacyPageAsDocument(page)
    val legacy = assertIs<LegacyStrokeObject>(document.objects.single())
    assertEquals(stroke, legacy.stroke)
    assertEquals("legacy-0-${stableLegacyHash(stroke)}", legacy.id)
}

@Test fun convertingLegacyPreservesRelativeWidthsAndProfileReference() {
    val legacy = LegacyStrokeObject("a", VectorStroke(
        color = 0xFF112233, points = listOf(VectorPoint(0f, 0f, 8f), VectorPoint(10f, 0f, 4f)),
        cap = VectorCap.ROUND, fillEnabled = false, strokeColor = 0xFF445566,
        strokeWidthPx = 3f, brushProfileId = "brush-7",
    ))
    val editable = convertLegacyObject(legacy)
    assertEquals(listOf(1f, 0.5f), editable.geometry.points.map { it.widthFactor })
    assertEquals(8f, editable.appearance.stroke.width)
    assertEquals(VectorBrushKind.PATTERN, editable.appearance.brush.kind)
    assertEquals("brush-7", editable.appearance.brush.profileId)
}
```

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorDocumentTest" --no-daemon`

Expected: compilation fails because the v2 model and adapter functions do not exist.

- [ ] **Step 3: Add the exact v2 model contracts**

```kotlin
enum class VectorJoin { MITER, ROUND, BEVEL }
enum class VectorBrushKind { BASIC, ART, PATTERN }

data class PathPoint(val x: Float, val y: Float, val widthFactor: Float)
data class PathGeometry(val points: List<PathPoint>, val closed: Boolean = false)
data class FillStyle(val enabled: Boolean = false, val color: Long = 0xFFFFFFFF)
data class StrokeStyle(
    val enabled: Boolean = true,
    val color: Long = 0xFF172E58,
    val width: Float = 8f,
    val cap: VectorCap = VectorCap.ROUND,
    val join: VectorJoin = VectorJoin.ROUND,
)
data class BrushStyle(val kind: VectorBrushKind = VectorBrushKind.BASIC, val profileId: String? = null)
data class PathAppearance(
    val fill: FillStyle = FillStyle(),
    val stroke: StrokeStyle = StrokeStyle(),
    val brush: BrushStyle = BrushStyle(),
)
data class ObjectTransform(
    val translateX: Float = 0f, val translateY: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val rotationDegrees: Float = 0f,
)

sealed interface VectorObject { val id: String }
data class LegacyStrokeObject(override val id: String, val stroke: VectorStroke) : VectorObject
data class EditablePathObject(
    override val id: String,
    val geometry: PathGeometry,
    val appearance: PathAppearance,
    val transform: ObjectTransform = ObjectTransform(),
) : VectorObject
data class VectorDocument(val version: Int = 2, val objects: List<VectorObject>)
```

Implement `stableLegacyHash` using a deterministic 31-based hash over color, point float bits, cap, fill/stroke fields, brush id, and fill color. `legacyPageAsDocument` uses `legacy-$index-$hash`. `convertLegacyObject` sets `baseWidth=max(point.w)`, `widthFactor=(w/baseWidth).coerceIn(0.05f, 1f)`, converts `brushProfileId` to `PATTERN`, and does not mutate the source stroke.

Add shared pure test fixtures used by later vector tests:

```kotlin
internal fun editablePath(id: String = "path", width: Float = 8f, brush: BrushStyle = BrushStyle()) =
    EditablePathObject(
        id,
        PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f))),
        PathAppearance(stroke = StrokeStyle(width = width), brush = brush),
    )

internal fun editableLine(id: String, start: Point, end: Point, width: Float = 8f) =
    EditablePathObject(id, PathGeometry(listOf(PathPoint(start.x, start.y, 1f), PathPoint(end.x, end.y, 1f))), PathAppearance(stroke = StrokeStyle(width = width)))

internal fun editableRect(id: String, left: Float, top: Float, right: Float, bottom: Float) =
    EditablePathObject(id, PathGeometry(listOf(
        PathPoint(left, top, 1f), PathPoint(right, top, 1f),
        PathPoint(right, bottom, 1f), PathPoint(left, bottom, 1f),
    ), closed = true), PathAppearance(fill = FillStyle(true, 0xFFFFFFFF), stroke = StrokeStyle(enabled = false)))

internal fun legacyLine(id: String, width: Float = 4f) = LegacyStrokeObject(
    id, VectorStroke(0xFF000000, listOf(VectorPoint(0f, 0f, width), VectorPoint(10f, 0f, width))),
)

internal fun appearance(width: Float = 8f) = PathAppearance(stroke = StrokeStyle(width = width))
internal fun legacyPage() = VectorPage(listOf((legacyLine("legacy") as LegacyStrokeObject).stroke))
internal fun twoStrokeLegacyPage() = VectorPage(listOf(legacyLine("a").stroke, legacyLine("b").stroke))
```

- [ ] **Step 4: Run the model tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorDocumentTest" --no-daemon`

Expected: all `VectorDocumentTest` cases pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorDocument.kt app/src/test/java/com/g1/sketchbook/vector/VectorDocumentTest.kt app/src/test/java/com/g1/sketchbook/vector/VectorDocumentTestFixtures.kt
git commit -m "feat(vector): add versioned path document model"
```

### Task 2: Pure v2 codec and non-destructive local store

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorDocumentCodec.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorDocumentStore.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorDocumentCodecTest.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorDocumentStoreTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt`

**Interfaces:**
- Consumes: Task 1 model and existing `vectorPageFromJson`.
- Produces: `encodeVectorDocument(document): String`, `decodeVectorDocument(text): VectorDocument?`, `decodeStoredVectorDocument(v2Text, legacyText): VectorDocument?`, `VectorDocumentStore.load()`, `saveV2(document)`, and repository v2 APIs.

- [ ] **Step 1: Write failing codec and file-preservation tests**

```kotlin
@Test fun v2RoundTripKeepsLegacyAndEditableObjects() {
    val source = VectorDocument(objects = listOf(
        LegacyStrokeObject("old", VectorStroke(0xFF000000, listOf(VectorPoint(0f, 0f, 2f), VectorPoint(2f, 2f, 1f)))),
        EditablePathObject("new", PathGeometry(listOf(PathPoint(1f, 2f, 1f), PathPoint(3f, 4f, .5f))), PathAppearance()),
    ))
    assertEquals(source, decodeVectorDocument(encodeVectorDocument(source)))
}

@Test fun invalidV2FallsBackToV1() {
    val legacy = VectorPage(listOf(VectorStroke(0xFF000000, listOf(VectorPoint(0f, 0f, 2f), VectorPoint(2f, 2f, 1f)))))
    assertEquals(legacyPageAsDocument(legacy), decodeStoredVectorDocument("{broken", legacy.toJson()))
}

@Test fun savingV2LeavesLegacyBytesAndMtimeUnchanged() {
    val root = createTempDir(prefix = "vector-store-")
    val v1 = File(root, "vector_canvas.json").apply { writeText("legacy bytes"); setLastModified(123456L) }
    VectorDocumentStore(root).saveV2(VectorDocument(objects = emptyList()))
    assertEquals("legacy bytes", v1.readText())
    assertEquals(123456L, v1.lastModified())
    assertNotNull(decodeVectorDocument(File(root, "vector_canvas_v2.json").readText()))
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorDocumentCodecTest" --tests "com.g1.sketchbook.vector.VectorDocumentStoreTest" --no-daemon`

Expected: compilation fails for missing codec/store symbols.

- [ ] **Step 3: Implement deterministic JSON and atomic v2 writes**

Implement a small pure-Kotlin recursive JSON reader inside `VectorDocumentCodec.kt` supporting object, array, string escapes, finite number, boolean, and null. Reject non-finite floats, unknown document versions, missing ids, objects with fewer than two legacy points, and editable `widthFactor` outside `0.05f..1f`. Emit keys in a fixed order so repeated saves are byte-stable.

```kotlin
fun decodeStoredVectorDocument(v2Text: String?, legacyText: String?): VectorDocument? {
    v2Text?.let(::decodeVectorDocument)?.let { return it }
    return legacyText?.let(::vectorPageFromJson)?.let(::legacyPageAsDocument)
}

class VectorDocumentStore(private val bookDir: File) {
    private val v1 = File(bookDir, "vector_canvas.json")
    private val v2 = File(bookDir, "vector_canvas_v2.json")

    fun load(): VectorDocument? = decodeStoredVectorDocument(
        v2.takeIf(File::exists)?.readText(),
        v1.takeIf(File::exists)?.readText(),
    )

    fun saveV2(document: VectorDocument) {
        val encoded = encodeVectorDocument(document)
        requireNotNull(decodeVectorDocument(encoded))
        val tmp = File(bookDir, "vector_canvas_v2.json.tmp")
        tmp.writeText(encoded)
        check(tmp.renameTo(v2) || run { tmp.copyTo(v2, overwrite = true); tmp.delete() })
    }
}
```

Add `loadVectorDocument`, `saveVectorDocument`, `vectorDocumentUpdatedAt`, and `setVectorDocumentUpdatedAt` to `SketchbookRepository`; keep all v1 methods unchanged.

- [ ] **Step 4: Run codec/store tests and all existing vector serialization tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorDocument*" --tests "com.g1.sketchbook.vector.VectorPageTest" --no-daemon`

Expected: v2 tests pass and all legacy `VectorPageTest` cases remain green.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorDocumentCodec.kt app/src/main/java/com/g1/sketchbook/vector/VectorDocumentStore.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookRepository.kt app/src/test/java/com/g1/sketchbook/vector/VectorDocumentCodecTest.kt app/src/test/java/com/g1/sketchbook/vector/VectorDocumentStoreTest.kt
git commit -m "feat(vector): store v2 documents without touching v1"
```

### Task 3: Independent Firebase v2 synchronization

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`
- Create: `app/src/test/java/com/g1/sketchbook/backup/VectorDocumentSyncTest.kt`

**Interfaces:**
- Consumes: v2 codec/store/repository APIs from Task 2 and existing `decideSyncAction`.
- Produces: `RemoteSketchbook.vectorCanvasV2`, `BackupRepository.pushVectorDocument`, and `decideVectorDocumentSyncAction`.

- [ ] **Step 1: Write failing non-destructive sync-policy tests**

```kotlin
@Test fun v1OnlyRemoteNeverCausesAV2PushUntilLocalV2Exists() {
    assertEquals(SyncAction.NOOP, decideVectorDocumentSyncAction(localV2At = null, remoteV2At = null))
}

@Test fun existingV2UsesOnlyV2Timestamps() {
    assertEquals(SyncAction.PULL, decideVectorDocumentSyncAction(localV2At = 10L, remoteV2At = 20L))
    assertEquals(SyncAction.PUSH, decideVectorDocumentSyncAction(localV2At = 30L, remoteV2At = 20L))
}

@Test fun invalidRemoteV2DoesNotAuthorizeOverwritingLocalOrV1() {
    assertEquals(SyncAction.NOOP, decideVectorDocumentSyncAction(localV2At = null, remoteV2At = 20L, remoteV2Valid = false))
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.backup.VectorDocumentSyncTest" --no-daemon`

Expected: compilation fails because `vectorCanvasV2` policy is absent.

- [ ] **Step 3: Add the v2 sibling node and sync branch**

```kotlin
data class RemoteVectorDocument(val updatedAt: Long, val json: String)

fun decideVectorDocumentSyncAction(
    localV2At: Long?, remoteV2At: Long?, remoteV2Valid: Boolean = true,
): SyncAction {
    if (!remoteV2Valid) return SyncAction.NOOP
    if (localV2At == null && remoteV2At == null) return SyncAction.NOOP
    return decideSyncAction(localV2At, remoteV2At)
}
```

Add `vectorCanvasV2: RemoteVectorDocument?` without changing `vectorCanvas`. Read/write Firebase path `sketchbooks/{bookId}/vectorCanvasV2` with `{updatedAt, document}`. Pull only after `decodeVectorDocument` succeeds; then save v2 and restore the remote timestamp. `saveVectorDocumentSynced` pushes only v2. The legacy sync branch continues handling v1 books only until a v2 file exists, then stops writing v1.

- [ ] **Step 4: Run backup tests and compile**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.backup.*" :app:compileDebugKotlin --no-daemon`

Expected: sync policy and existing backup tests pass; Kotlin compilation succeeds.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt app/src/test/java/com/g1/sketchbook/backup/VectorDocumentSyncTest.kt
git commit -m "feat(vector): sync v2 documents in a separate remote node"
```

### Task 4: Fill and Basic Stroke geometry

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/PathGeometry.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/PathGeometryTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`

**Interfaces:**
- Consumes: `PathGeometry`, `PathAppearance`, `Point`.
- Produces: `fillPolygon(geometry)`, `basicStrokeOutline(geometry, stroke)`, and explicit Join geometry.

- [ ] **Step 1: Write failing open/closed Fill and variable-width Stroke tests**

```kotlin
@Test fun openPathFillVirtuallyClosesButStrokeDoesNot() {
    val geometry = PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, 1f), PathPoint(10f, 10f, 1f)), closed = false)
    assertEquals(Point(0f, 0f), fillPolygon(geometry).last())
    assertFalse(basicStrokeCenterSegments(geometry).contains(Point(0f, 0f) to Point(10f, 10f)))
}

@Test fun closedPathAddsLastToFirstStrokeSegment() {
    val geometry = PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, .5f), PathPoint(10f, 10f, 1f)), closed = true)
    assertTrue(basicStrokeCenterSegments(geometry).contains(Point(10f, 10f) to Point(0f, 0f)))
}

@Test fun strokeWidthMultipliesWidthFactor() {
    val outline = basicStrokeOutline(PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(10f, 0f, .5f))), StrokeStyle(width = 8f))
    assertEquals(4f, outline.first().y.absoluteValue, .001f)
    assertEquals(2f, outline[1].y.absoluteValue, .001f)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.PathGeometryTest" --no-daemon`

Expected: compilation fails for missing geometry functions.

- [ ] **Step 3: Implement Fill closure and Basic outline**

```kotlin
fun fillPolygon(geometry: PathGeometry): List<Point> {
    if (geometry.points.size < 3) return emptyList()
    val result = geometry.points.map { Point(it.x, it.y) }.toMutableList()
    if (result.first() != result.last()) result += result.first()
    return result
}

fun basicStrokeCenterSegments(geometry: PathGeometry): List<Pair<Point, Point>> {
    val points = geometry.points.map { Point(it.x, it.y) }
    val segments = points.zipWithNext().toMutableList()
    if (geometry.closed && points.size > 2) segments += points.last() to points.first()
    return segments
}
```

Build the outline from per-point half-width `stroke.width * widthFactor / 2f`; add round/miter/bevel Join vertices and reuse existing cap arcs only on open paths. Closed paths have no endpoint caps.

- [ ] **Step 4: Run new and existing geometry tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.PathGeometryTest" --tests "com.g1.sketchbook.vector.StrokeGeometryTest" --no-daemon`

Expected: all geometry tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/PathGeometry.kt app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt app/src/test/java/com/g1/sketchbook/vector/PathGeometryTest.kt
git commit -m "feat(vector): add path fill and basic stroke geometry"
```

### Task 5: Real Art Brush deformation

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/ArtBrush.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushProfile.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/ArtBrushTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/SvgShapeParser.kt`

**Interfaces:**
- Consumes: parsed SVG polygons, `PathGeometry`, `StrokeStyle`.
- Produces: `ArtBrushProfile`, `normalizeArtBrush`, `mapArtBrush(profile, geometry, stroke)`.

- [ ] **Step 1: Write failing straight, curved, and pressure mapping tests**

```kotlin
@Test fun straightPathStretchesOneSourceAcrossFullLength() {
    val profile = ArtBrushProfile("a", "chalk", listOf(listOf(Point(0f, -1f), Point(1f, -1f), Point(1f, 1f), Point(0f, 1f))))
    val mapped = mapArtBrush(profile, PathGeometry(listOf(PathPoint(10f, 20f, 1f), PathPoint(110f, 20f, 1f))), StrokeStyle(width = 10f))
    val bounds = pointsBounds(mapped.flatten())!!
    assertEquals(10f, bounds.minX, .01f); assertEquals(110f, bounds.maxX, .01f)
    assertEquals(15f, bounds.minY, .01f); assertEquals(25f, bounds.maxY, .01f)
}

@Test fun widthFactorNarrowsTheArtBrushAtTheEnd() {
    val profile = ArtBrushProfile("a", "taper", listOf(listOf(Point(0f, -1f), Point(1f, -1f), Point(1f, 1f), Point(0f, 1f))))
    val mapped = mapArtBrush(profile, PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(100f, 0f, .5f))), StrokeStyle(width = 20f))
    assertEquals(5f, mapped.flatten().filter { it.x > 99f }.maxOf { kotlin.math.abs(it.y) }, .01f)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.ArtBrushTest" --no-daemon`

Expected: compilation fails because the Art profile/mapper is absent.

- [ ] **Step 3: Implement normalization and arc-length mapping**

```kotlin
sealed interface VectorBrushProfile {
    val id: String
    val name: String
    val originalSvg: String
}

data class ArtBrushProfile(
    override val id: String,
    override val name: String,
    val shapes: List<List<Point>>,
    override val originalSvg: String = "",
) : VectorBrushProfile

fun mapArtBrush(
    profile: ArtBrushProfile,
    geometry: PathGeometry,
    stroke: StrokeStyle,
): List<List<Point>> {
    val sampler = ArcLengthSampler(geometry.points)
    return profile.shapes.map { shape -> shape.map { source ->
        val sample = sampler.sample(source.x.coerceIn(0f, 1f))
        val offset = source.y * stroke.width * sample.widthFactor / 2f
        Point(sample.x + sample.normalX * offset, sample.y + sample.normalY * offset)
    } }
}
```

`normalizeArtBrush` maps source x bounds to `0f..1f` and source y around the vertical center to `-1f..1f`. `ArcLengthSampler` interpolates position, tangent, normal, and width factor; a reversed target path reverses the brush direction deterministically.

- [ ] **Step 4: Run Art and SVG parser tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.ArtBrushTest" --tests "com.g1.sketchbook.vector.Svg*Test" --no-daemon`

Expected: all tests pass without regressing existing SVG support.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushProfile.kt app/src/main/java/com/g1/sketchbook/vector/ArtBrush.kt app/src/main/java/com/g1/sketchbook/vector/SvgShapeParser.kt app/src/test/java/com/g1/sketchbook/vector/ArtBrushTest.kt
git commit -m "feat(vector): deform art brush shapes along paths"
```

### Task 6: Typed Art and Pattern brush persistence

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushRepository.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorBrushProfileTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushProfile.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StampBrush.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt`

**Interfaces:**
- Consumes: Art profiles from Task 5 and existing stamp profiles.
- Produces: `VectorBrushProfile`, `PatternBrushProfile`, `VectorBrushRepository.list/importArt/importPattern`, and typed remote brush payloads.

- [ ] **Step 1: Write failing compatibility and type tests**

```kotlin
@Test fun missingRemoteTypeDefaultsToPattern() {
    assertEquals(VectorBrushKind.PATTERN, remoteBrushKind(null))
}

@Test fun patternProfileRetainsSpacingAndSize() {
    val profile = PatternBrushProfile("p", "dots", listOf(listOf(Point(0f, 0f))), spacingPx = 24f, sizePx = 32f)
    assertEquals(profile, decodeVectorBrushProfile(encodeVectorBrushProfile(profile)))
}

@Test fun artProfileRetainsOriginalSvgAndType() {
    val profile = ArtBrushProfile("a", "chalk", listOf(listOf(Point(0f, 0f), Point(1f, 1f))), "<svg/>")
    assertEquals(profile, decodeVectorBrushProfile(encodeVectorBrushProfile(profile)))
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorBrushProfileTest" --no-daemon`

Expected: compilation fails for typed profile APIs.

- [ ] **Step 3: Implement typed profiles and adapters**

```kotlin
data class PatternBrushProfile(
    override val id: String, override val name: String, val shapes: List<List<Point>>,
    val spacingPx: Float = 24f, val sizePx: Float = 32f, override val originalSvg: String = "",
) : VectorBrushProfile
```

Make `StampBrushProfile` a compatibility typealias for `PatternBrushProfile`. Store typed files under `filesDir/vector_brushes_v2/{id}.json`; read existing `vector_brushes` entries as Pattern without changing them. Remote payload gains `type`; missing type decodes as `PATTERN`. Firebase pull validates SVG and type before local save.

`PatternBrushProfile` implements the interface introduced in Task 5. Both codec branches persist `id`, `name`,
`type`, `originalSvg`, and their type-specific numeric fields.

- [ ] **Step 4: Run brush, parser, and backup tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.*Brush*Test" --tests "com.g1.sketchbook.backup.*" --no-daemon`

Expected: typed profile tests and existing stamp tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorBrushProfile.kt app/src/main/java/com/g1/sketchbook/vector/VectorBrushRepository.kt app/src/main/java/com/g1/sketchbook/vector/StampBrush.kt app/src/main/java/com/g1/sketchbook/vector/StampBrushRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupModels.kt app/src/main/java/com/g1/sketchbook/backup/BackupRepository.kt app/src/main/java/com/g1/sketchbook/backup/BackupSync.kt app/src/test/java/com/g1/sketchbook/vector/VectorBrushProfileTest.kt
git commit -m "feat(vector): persist typed art and pattern brushes"
```

### Task 7: Unified legacy/Basic/Art/Pattern renderer and SVG export

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt`
- Modify: `app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorRenderGeometryTest.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorGeometryCache.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorGeometryCacheTest.kt`

**Interfaces:**
- Consumes: v2 model, Basic geometry, Art mapper, Pattern mapper, typed profile map.
- Produces: `drawVectorDocument`, `renderedPolygons`, `vectorDocumentToSvg`, `renderedObjectBounds`.

- [ ] **Step 1: Write failing renderer-routing and export tests**

```kotlin
@Test fun missingProfileFallsBackToBasicWithoutChangingReference() {
    val objectPath = editablePath(brush = BrushStyle(VectorBrushKind.ART, "missing"))
    val geometry = renderedPolygons(objectPath, emptyMap())
    assertTrue(geometry.strokeShapes.flatten().isNotEmpty())
    assertEquals("missing", objectPath.appearance.brush.profileId)
}

@Test fun openFillSvgClosesFillButLeavesBasicStrokeOpen() {
    val openFilled = editablePath("open").copy(
        geometry = PathGeometry(listOf(PathPoint(0f, 0f, 1f), PathPoint(50f, 0f, 1f), PathPoint(50f, 50f, 1f))),
        appearance = PathAppearance(fill = FillStyle(true, 0xFFFFFFFF), stroke = StrokeStyle()),
    )
    val svg = vectorDocumentToSvg(VectorDocument(objects = listOf(openFilled)), Bounds(0f, 0f, 100f, 100f), emptyMap())
    assertTrue(svg.contains("data-role=\"fill\""))
    assertTrue(svg.contains(" Z\" fill="))
    assertTrue(svg.contains("data-role=\"stroke\""))
}

@Test fun artAndPatternTakeDifferentRenderRoutes() {
    val art = ArtBrushProfile("a", "art", listOf(listOf(Point(0f, -1f), Point(1f, 1f))))
    val pattern = PatternBrushProfile("p", "pattern", listOf(listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f))))
    assertEquals(RenderRoute.ART, renderRoute(BrushStyle(VectorBrushKind.ART, "a"), mapOf("a" to art)))
    assertEquals(RenderRoute.PATTERN, renderRoute(BrushStyle(VectorBrushKind.PATTERN, "p"), mapOf("p" to pattern)))
}

@Test fun cacheKeyChangesForAppearanceButNotViewport() {
    val objectPath = editablePath("a", width = 8f)
    val first = geometryCacheKey(objectPath, profile = null)
    val changed = geometryCacheKey(objectPath.copy(appearance = appearance(width = 12f)), profile = null)
    assertNotEquals(first, changed)
    assertEquals(first, geometryCacheKey(objectPath, profile = null))
}
```

- [ ] **Step 2: Run renderer/export tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorRenderGeometryTest" --tests "com.g1.sketchbook.vector.VectorSvgExportTest" --no-daemon`

Expected: compilation fails for v2 renderer/export APIs.

- [ ] **Step 3: Route every object through one render entry point**

```kotlin
enum class RenderRoute { BASIC, ART, PATTERN }
data class RenderedObjectGeometry(val fill: List<Point>, val strokeShapes: List<List<Point>>)

fun renderRoute(brush: BrushStyle, profiles: Map<String, VectorBrushProfile>): RenderRoute = when {
    brush.kind == VectorBrushKind.ART && profiles[brush.profileId] is ArtBrushProfile -> RenderRoute.ART
    brush.kind == VectorBrushKind.PATTERN && profiles[brush.profileId] is PatternBrushProfile -> RenderRoute.PATTERN
    else -> RenderRoute.BASIC
}

fun mapPatternBrush(profile: PatternBrushProfile, geometry: PathGeometry): List<List<Point>> =
    stampPolygons(profile, geometry.points.map { VectorPoint(it.x, it.y, it.widthFactor) })

fun renderedPolygons(
    objectPath: EditablePathObject,
    profiles: Map<String, VectorBrushProfile>,
): RenderedObjectGeometry {
    val fill = if (objectPath.appearance.fill.enabled) fillPolygon(objectPath.geometry) else emptyList()
    val brush = objectPath.appearance.brush
    val strokeShapes = if (!objectPath.appearance.stroke.enabled) emptyList() else when (renderRoute(brush, profiles)) {
        RenderRoute.BASIC -> listOf(basicStrokeOutline(objectPath.geometry, objectPath.appearance.stroke))
        RenderRoute.ART -> mapArtBrush(profiles.getValue(brush.profileId!!) as ArtBrushProfile, objectPath.geometry, objectPath.appearance.stroke)
        RenderRoute.PATTERN -> mapPatternBrush(profiles.getValue(brush.profileId!!) as PatternBrushProfile, objectPath.geometry)
    }
    return RenderedObjectGeometry(fill, strokeShapes)
}

data class VectorGeometryCacheKey(val objectId: String, val objectRevision: Int, val profileRevision: Int)
fun geometryCacheKey(objectPath: EditablePathObject, profile: VectorBrushProfile?) =
    VectorGeometryCacheKey(objectPath.id, objectPath.hashCode(), profile?.hashCode() ?: 0)
```

Legacy objects must call the existing `drawVectorPage` path byte-for-byte in behavior. `drawVectorDocument`, thumbnail rendering, hit bounds, and SVG export all use the same route/profile fallback. Keep existing v1 `vectorPageToSvg` public for compatibility.

Add `VectorGeometryCache` keyed by object id, immutable object hash, and profile hash. Viewport scale/translation is not part of the key, so panning and zooming reuse cached geometry. Replacing one immutable object changes only that object's key.

- [ ] **Step 4: Run all vector geometry/export tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.*" --no-daemon`

Expected: all vector unit tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorRenderer.kt app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt app/src/main/java/com/g1/sketchbook/vector/StrokeGeometry.kt app/src/main/java/com/g1/sketchbook/vector/VectorGeometryCache.kt app/src/test/java/com/g1/sketchbook/vector/VectorSvgExportTest.kt app/src/test/java/com/g1/sketchbook/vector/VectorRenderGeometryTest.kt app/src/test/java/com/g1/sketchbook/vector/VectorGeometryCacheTest.kt
git commit -m "feat(vector): render and export appearance paths"
```

### Task 8: Selection and object transforms

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorSelectionEngine.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorTransformEngine.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorSelectionEngineTest.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorTransformEngineTest.kt`

**Interfaces:**
- Consumes: rendered object geometry/bounds from Task 7.
- Produces: `topmostObjectAt`, `objectsTouchingLasso`, `selectionBounds`, `transformObjects`.

- [ ] **Step 1: Write failing topmost, lasso, and transform tests**

```kotlin
@Test fun hitTestReturnsTopmostMatchingObject() {
    val bottom = editableRect("bottom", 0f, 0f, 20f, 20f)
    val top = editableRect("top", 0f, 0f, 20f, 20f)
    assertEquals("top", topmostObjectAt(listOf(bottom, top), Point(10f, 10f), emptyMap(), viewportScale = 1f)?.id)
}

@Test fun rotationUsesSelectionCenter() {
    val objectPath = editableLine("a", Point(0f, 0f), Point(10f, 0f))
    val transformed = transformObjects(listOf(objectPath), setOf("a"), SelectionTransform(rotationDegrees = 90f, pivot = Point(5f, 0f)))
    val points = (transformed.single() as EditablePathObject).geometry.points
    assertEquals(5f, points[0].x, .01f); assertEquals(-5f, points[0].y, .01f)
    assertEquals(5f, points[1].x, .01f); assertEquals(5f, points[1].y, .01f)
}

@Test fun scalingAlsoScalesLegacyPointWidths() {
    val legacy = legacyLine("old", width = 4f)
    val scaled = transformObjects(listOf(legacy), setOf("old"), SelectionTransform(scaleX = 2f, scaleY = 2f, pivot = Point(0f, 0f)))
    assertEquals(8f, (scaled.single() as LegacyStrokeObject).stroke.points.first().w)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSelectionEngineTest" --tests "com.g1.sketchbook.vector.VectorTransformEngineTest" --no-daemon`

Expected: compilation fails for selection/transform APIs.

- [ ] **Step 3: Implement pure selection and transform engines**

```kotlin
data class SelectionTransform(
    val translateX: Float = 0f, val translateY: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f, val pivot: Point,
)

fun topmostObjectAt(
    objects: List<VectorObject>, point: Point, profiles: Map<String, VectorBrushProfile>, viewportScale: Float,
): VectorObject? = objects.asReversed().firstOrNull {
    pointInRenderedObject(point, it, profiles, tolerance = 12f / viewportScale.coerceAtLeast(0.01f))
}
```

Use the same rendered polygons as the renderer. For thin/open paths, accept a 12px canvas-space distance adjusted by viewport scale. `selectionBounds` includes Fill and rendered brush extents. `transformObjects` returns a new object list and compounds the transform into all points at command completion.

- [ ] **Step 4: Run focused and renderer tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorSelectionEngineTest" --tests "com.g1.sketchbook.vector.VectorTransformEngineTest" --tests "com.g1.sketchbook.vector.VectorRenderGeometryTest" --no-daemon`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorSelectionEngine.kt app/src/main/java/com/g1/sketchbook/vector/VectorTransformEngine.kt app/src/test/java/com/g1/sketchbook/vector/VectorSelectionEngineTest.kt app/src/test/java/com/g1/sketchbook/vector/VectorTransformEngineTest.kt
git commit -m "feat(vector): select and transform path objects"
```

### Task 9: Reversible commands and editor state

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorEditorCommand.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorEditorState.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorEditorStateTest.kt`

**Interfaces:**
- Consumes: document model, transforms, legacy conversion.
- Produces: `EditorCommand`, `VectorEditorState`, `VectorEditorSnapshot`, `AppearanceEdit`.

- [ ] **Step 1: Write failing selected/default, conversion, and undo/redo tests**

```kotlin
@Test fun appearanceEditWithNoSelectionChangesOnlyFutureDefaults() {
    val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"))))
    val before = state.snapshot.value.document
    state.applyAppearance(AppearanceEdit.StrokeWidth(14f))
    assertEquals(before, state.snapshot.value.document)
    assertEquals(14f, state.snapshot.value.defaultAppearance.stroke.width)
}

@Test fun appearanceEditConvertsOnlySelectedLegacyObject() {
    val state = VectorEditorState(VectorDocument(objects = listOf(legacyLine("a"), legacyLine("b"))))
    state.select(setOf("a")); state.applyAppearance(AppearanceEdit.FillEnabled(true))
    assertIs<EditablePathObject>(state.snapshot.value.document.objects[0])
    assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[1])
}

@Test fun sliderPreviewCommitsAsOneUndoStep() {
    val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"))))
    state.select(setOf("a")); state.beginAppearanceGesture()
    state.previewAppearance(AppearanceEdit.StrokeWidth(10f)); state.previewAppearance(AppearanceEdit.StrokeWidth(18f))
    state.commitAppearanceGesture(); state.undo()
    assertEquals(8f, (state.snapshot.value.document.objects.single() as EditablePathObject).appearance.stroke.width)
    assertFalse(state.snapshot.value.canUndo)
    state.redo(); assertEquals(18f, (state.snapshot.value.document.objects.single() as EditablePathObject).appearance.stroke.width)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorEditorStateTest" --no-daemon`

Expected: compilation fails for state/command APIs.

- [ ] **Step 3: Implement immutable snapshot and command stacks**

```kotlin
enum class VectorTool { SELECT, PEN, ERASER, HAND }
sealed interface AppearanceEdit {
    data class FillEnabled(val enabled: Boolean) : AppearanceEdit
    data class FillColor(val color: Long) : AppearanceEdit
    data class StrokeEnabled(val enabled: Boolean) : AppearanceEdit
    data class StrokeColor(val color: Long) : AppearanceEdit
    data class StrokeWidth(val width: Float) : AppearanceEdit
    data class Cap(val cap: VectorCap) : AppearanceEdit
    data class Join(val join: VectorJoin) : AppearanceEdit
    data class Brush(val brush: BrushStyle) : AppearanceEdit
}

data class VectorEditorSnapshot(
    val document: VectorDocument,
    val selectedIds: Set<String> = emptySet(),
    val tool: VectorTool = VectorTool.SELECT,
    val defaultAppearance: PathAppearance = PathAppearance(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface EditorCommand {
    fun apply(document: VectorDocument): VectorDocument
    fun revert(document: VectorDocument): VectorDocument
}

class VectorEditorState(initialDocument: VectorDocument) {
    val snapshot: StateFlow<VectorEditorSnapshot>
    var onDocumentCommitted: ((VectorDocument) -> Unit)? = null
    fun select(ids: Set<String>)
    fun setTool(tool: VectorTool)
    fun applyAppearance(edit: AppearanceEdit)
    fun beginAppearanceGesture()
    fun previewAppearance(edit: AppearanceEdit)
    fun commitAppearanceGesture()
    fun dispatch(command: EditorCommand)
    fun undo()
    fun redo()
}
```

Add `AddObject`, `DeleteObjects`, `TransformObjects`, `ChangeAppearance`, and `ChangeClosedState`. `VectorEditorState` owns `MutableStateFlow<VectorEditorSnapshot>`, undo/redo stacks, and one pending appearance gesture. A new committed command clears Redo. Emit `onDocumentCommitted` only once per completed command.

- [ ] **Step 4: Run editor-state tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorEditorStateTest" --no-daemon`

Expected: all editor command and state tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorEditorCommand.kt app/src/main/java/com/g1/sketchbook/vector/VectorEditorState.kt app/src/test/java/com/g1/sketchbook/vector/VectorEditorStateTest.kt
git commit -m "feat(vector): add appearance-aware editor history"
```

### Task 10: Canvas interaction reducer and Android host

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasInteraction.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasHost.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorCanvasInteractionTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`

**Interfaces:**
- Consumes: editor state, selection/transform engines, renderer.
- Produces: `SelectionHandle`, `hitSelectionHandle`, `CanvasGesture`, and `VectorCanvasHost.bind(state, profiles)`.

- [ ] **Step 1: Write failing handle and tool-decision tests**

```kotlin
@Test fun rotationHandleWinsBeforeBoundingBoxBody() {
    val overlay = SelectionOverlay(Bounds(20f, 20f, 120f, 120f), rotationHandle = Point(70f, 0f), handleRadius = 12f)
    assertEquals(SelectionHandle.ROTATE, hitSelectionHandle(Point(70f, 4f), overlay))
}

@Test fun penSecondPointerCancelsDraftAndStartsViewportGesture() {
    val result = reduceCanvasInput(CanvasInput.PointerAdded(pointerCount = 2), InteractionState(tool = VectorTool.PEN, draftPathActive = true))
    assertFalse(result.state.draftPathActive)
    assertEquals(CanvasGesture.VIEWPORT, result.state.gesture)
}

@Test fun eraserGestureReturnsUniqueIds() {
    assertEquals(setOf("a", "b"), uniqueEraseIds(listOf("a", "a", "b")))
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorCanvasInteractionTest" --no-daemon`

Expected: compilation fails for interaction symbols.

- [ ] **Step 3: Implement reducer and host binding**

```kotlin
enum class SelectionHandle { BODY, TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, ROTATE, NONE }
enum class CanvasGesture { NONE, DRAW, ERASE, MOVE_SELECTION, SCALE_SELECTION, ROTATE_SELECTION, VIEWPORT, LASSO }
data class SelectionOverlay(val bounds: Bounds, val rotationHandle: Point, val handleRadius: Float)
sealed interface CanvasInput { data class PointerAdded(val pointerCount: Int) : CanvasInput }
data class InteractionState(
    val tool: VectorTool,
    val draftPathActive: Boolean = false,
    val gesture: CanvasGesture = CanvasGesture.NONE,
)
data class InteractionResult(val state: InteractionState)

fun reduceCanvasInput(input: CanvasInput, state: InteractionState): InteractionResult = when (input) {
    is CanvasInput.PointerAdded -> if (input.pointerCount >= 2) {
        InteractionResult(state.copy(draftPathActive = false, gesture = CanvasGesture.VIEWPORT))
    } else InteractionResult(state)
}

fun uniqueEraseIds(hitIds: List<String>): Set<String> = hitIds.toSet()

class VectorCanvasHost(context: Context) : View(context) {
    private var editorState: VectorEditorState? = null
    private var profilesProvider: () -> Map<String, VectorBrushProfile> = { emptyMap() }
    private var collectJob: Job? = null
    private var currentSnapshot: VectorEditorSnapshot? = null

    fun bind(state: VectorEditorState, profiles: () -> Map<String, VectorBrushProfile>) {
        editorState = state
        profilesProvider = profiles
        if (isAttachedToWindow) startCollecting()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            editorState?.snapshot?.collect { currentSnapshot = it; invalidate() }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startCollecting() }
    override fun onDetachedFromWindow() { collectJob?.cancel(); collectJob = null; super.onDetachedFromWindow() }
}
```

The host maps screen/canvas coordinates with the existing `disp/inv/userM` pattern. One finger follows the active tool; two fingers always control viewport except when both started on a selected transform. On gesture completion, dispatch exactly one editor command. Draw selection overlay in screen space after document rendering. Keep `VectorBrushView` as a deprecated delegating wrapper until Task 13 removes its call sites.

- [ ] **Step 4: Run interaction tests and compile Android code**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorCanvasInteractionTest" :app:compileDebugKotlin --no-daemon`

Expected: reducer tests pass and Android host compiles.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorCanvasInteraction.kt app/src/main/java/com/g1/sketchbook/vector/VectorCanvasHost.kt app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt app/src/test/java/com/g1/sketchbook/vector/VectorCanvasInteractionTest.kt
git commit -m "feat(vector): bridge editor state to canvas gestures"
```

### Task 11: Adaptive editor shell and tool rail

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorEditorLayout.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorToolRail.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorEditorLayoutTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`

**Interfaces:**
- Consumes: `VectorEditorState`, `VectorCanvasHost`.
- Produces: `PanelPlacement`, `choosePanelPlacement`, `VectorEditorLayout`, `VectorTopBar`, `VectorToolRail`.

- [ ] **Step 1: Write failing layout-breakpoint tests**

```kotlin
@Test fun wideLandscapeUsesRightPanel() {
    assertEquals(PanelPlacement.RIGHT, choosePanelPlacement(widthDp = 900f, heightDp = 600f))
}

@Test fun portraitAndNarrowLandscapeUseBottomPanel() {
    assertEquals(PanelPlacement.BOTTOM, choosePanelPlacement(widthDp = 700f, heightDp = 1000f))
    assertEquals(PanelPlacement.BOTTOM, choosePanelPlacement(widthDp = 700f, heightDp = 400f))
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorEditorLayoutTest" --no-daemon`

Expected: compilation fails for layout policy.

- [ ] **Step 3: Implement approved adaptive shell**

```kotlin
enum class PanelPlacement { RIGHT, BOTTOM }
fun choosePanelPlacement(widthDp: Float, heightDp: Float): PanelPlacement =
    if (widthDp >= 720f && widthDp > heightDp) PanelPlacement.RIGHT else PanelPlacement.BOTTOM
```

Use `BoxWithConstraints`. Right panel width is `min(336.dp, maxWidth * .32f)` with internal scrolling. Bottom sheet starts at 42% of height, supports collapsed/expanded anchors without a new dependency, and keeps Fill/Stroke swatches plus brush preview visible when collapsed. The top bar contains back, title, Undo, Redo, divider, and export. The left rail contains Select, Pen, Eraser, Hand with 48dp minimum touch targets and selected navy treatment.

```kotlin
@Composable
fun VectorEditorLayout(
    title: String,
    state: VectorEditorState,
    profiles: List<VectorBrushProfile>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImportArt: () -> Unit,
    onImportPattern: () -> Unit,
)

@Composable
fun VectorToolRail(selected: VectorTool, compact: Boolean, onSelect: (VectorTool) -> Unit)
```

- [ ] **Step 4: Compile and run layout tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorEditorLayoutTest" :app:compileDebugKotlin --no-daemon`

Expected: layout tests pass and Compose compilation succeeds.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorEditorLayout.kt app/src/main/java/com/g1/sketchbook/vector/VectorToolRail.kt app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt app/src/test/java/com/g1/sketchbook/vector/VectorEditorLayoutTest.kt
git commit -m "feat(vector): add adaptive path editor shell"
```

### Task 12: Appearance panel and brush library

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorAppearancePanel.kt`
- Create: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushLibrary.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/AppearanceProjectionTest.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorEditorLayout.kt`

**Interfaces:**
- Consumes: editor snapshot/commands and typed repository.
- Produces: `projectAppearance`, `AppearanceProjection`, `VectorAppearancePanel`, `VectorBrushLibrary`.

- [ ] **Step 1: Write failing selection/default/mixed projection tests**

```kotlin
@Test fun noSelectionProjectsFutureDefaults() {
    val snapshot = VectorEditorSnapshot(VectorDocument(objects = emptyList()), selectedIds = emptySet(), defaultAppearance = appearance(width = 13f))
    assertEquals(13f, projectAppearance(snapshot).strokeWidth.value)
    assertEquals("새 패스 스타일", projectAppearance(snapshot).title)
}

@Test fun oneSelectionProjectsThatPath() {
    val snapshot = VectorEditorSnapshot(VectorDocument(objects = listOf(editablePath("a", width = 7f))), selectedIds = setOf("a"))
    assertEquals(7f, projectAppearance(snapshot).strokeWidth.value)
    assertEquals("패스", projectAppearance(snapshot).title)
}

@Test fun differentSelectedWidthsProjectMixedValue() {
    val snapshot = VectorEditorSnapshot(VectorDocument(objects = listOf(editablePath("a", width = 7f), editablePath("b", width = 11f))), selectedIds = setOf("a", "b"))
    assertTrue(projectAppearance(snapshot).strokeWidth.mixed)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.AppearanceProjectionTest" --no-daemon`

Expected: compilation fails for Appearance projection.

- [ ] **Step 3: Implement the approved inspector and library**

```kotlin
data class ProjectedValue<T>(val value: T, val mixed: Boolean = false)
data class AppearanceProjection(
    val title: String,
    val fillEnabled: ProjectedValue<Boolean>, val fillColor: ProjectedValue<Long>,
    val strokeEnabled: ProjectedValue<Boolean>, val strokeColor: ProjectedValue<Long>,
    val strokeWidth: ProjectedValue<Float>, val cap: ProjectedValue<VectorCap>,
    val join: ProjectedValue<VectorJoin>, val brush: ProjectedValue<BrushStyle>,
)

@Composable
fun VectorAppearancePanel(
    projection: AppearanceProjection,
    onEdit: (AppearanceEdit) -> Unit,
    onGestureStart: () -> Unit,
    onGestureCommit: () -> Unit,
    onOpenBrushLibrary: () -> Unit,
    onToggleClosed: () -> Unit,
)

@Composable
fun VectorBrushLibrary(
    profiles: List<VectorBrushProfile>,
    selected: BrushStyle,
    onSelect: (BrushStyle) -> Unit,
    onImportArt: () -> Unit,
    onImportPattern: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
)
```

Match the approved mockups: preview/header row, overlapping Fill/Stroke squares, separate toggles, width value and slider, segmented Cap/Join, Basic/Art/Pattern tabs, actual curved brush preview, and selected/default explanatory copy. Disable Basic-only Cap/Join when Art/Pattern is active without clearing their values. Import Art using Art normalization and Pattern using existing pattern parsing. Missing profiles show an inline warning and Basic fallback preview.

Add a `패스 닫기`/`패스 열기` action bound to `ChangeClosedState`; it is enabled only for selected editable paths and converts selected Legacy objects before applying the command.

- [ ] **Step 4: Run projection tests and compile Compose UI**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.AppearanceProjectionTest" :app:compileDebugKotlin --no-daemon`

Expected: projection tests pass and the panel/library compile.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector/VectorAppearancePanel.kt app/src/main/java/com/g1/sketchbook/vector/VectorBrushLibrary.kt app/src/main/java/com/g1/sketchbook/vector/VectorEditorLayout.kt app/src/test/java/com/g1/sketchbook/vector/AppearanceProjectionTest.kt
git commit -m "feat(vector): add path appearance inspector"
```

### Task 13: Screen integration, autosave, export, and legacy entry-point cleanup

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorCanvasHost.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorBrushView.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/vector/VectorSvgExport.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`
- Create: `app/src/test/java/com/g1/sketchbook/vector/VectorEditorIntegrationTest.kt`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: final `VectorCanvasScreen(bookId, book, myUid, onBack)` behavior and one v2 autosave/export path.

- [ ] **Step 1: Write failing pure integration tests**

```kotlin
@Test fun openingLegacyThenLeavingWithoutCommandsProducesNoSave() {
    val state = VectorEditorState(legacyPageAsDocument(legacyPage()))
    var commits = 0
    state.onDocumentCommitted = { commits++ }
    state.select(setOf(state.snapshot.value.document.objects.first().id))
    state.setTool(VectorTool.HAND)
    assertEquals(0, commits)
}

@Test fun firstAppearanceEditProducesOneV2CommitAndKeepsOtherLegacyObjects() {
    val state = VectorEditorState(legacyPageAsDocument(twoStrokeLegacyPage()))
    val first = state.snapshot.value.document.objects.first().id
    var committed: VectorDocument? = null
    state.onDocumentCommitted = { committed = it }
    state.select(setOf(first)); state.applyAppearance(AppearanceEdit.FillEnabled(true))
    assertIs<EditablePathObject>(committed!!.objects[0])
    assertIs<LegacyStrokeObject>(committed!!.objects[1])
}
```

- [ ] **Step 2: Run integration tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.g1.sketchbook.vector.VectorEditorIntegrationTest" --no-daemon`

Expected: tests fail until final state callbacks and integration are wired.

- [ ] **Step 3: Replace old screen state with the new editor flow**

`VectorCanvasScreen` must:

```kotlin
val document = remember(bookId) { repo.loadVectorDocument(bookId) ?: VectorDocument(objects = emptyList()) }
val editor = remember(bookId) { VectorEditorState(document) }
editor.onDocumentCommitted = { changed ->
    saveVectorDocumentSynced(scope, repo, backup, myUid, bookId, changed)
}
```

Load typed profiles, bind `VectorCanvasHost`, route Undo/Redo/tool/Appearance actions, and export either selected objects or all content with `vectorDocumentToSvg`. Back navigation flushes only a pending committed command; merely opening/panning/selecting does not write v2. Remove old `VectorBrushView` call sites, then either delete the class if unused or leave a deprecated no-call-site adapter for one release.

- [ ] **Step 4: Run all tests and assemble**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

Expected: all unit tests pass and debug APK assembles.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/vector app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookSync.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt app/src/test/java/com/g1/sketchbook/vector/VectorEditorIntegrationTest.kt
git commit -m "feat(vector): integrate appearance path editor"
```

### Task 14: Safety verification, previews, lint, and handoff

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/preview/FlowPreviews.kt`
- Modify: `PROGRESS.md`
- Test only: all vector, backup, and app tests

**Interfaces:**
- Consumes: completed editor.
- Produces: reproducible verification evidence and portrait/landscape previews.

- [ ] **Step 1: Add real composable previews for both approved layouts**

Add previews at `833x1280` and `1280x800` using the real `VectorEditorLayout`, a sample selected flower-like path, and an in-memory `VectorEditorState`. Preview code must not access files or Firebase.

```kotlin
@Preview(widthDp = 833, heightDp = 1280, showBackground = true)
@Composable private fun VectorEditorPortraitPreview() = PreviewTheme { VectorEditorPreviewSample() }

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun VectorEditorLandscapePreview() = PreviewTheme { VectorEditorPreviewSample() }
```

- [ ] **Step 2: Run the complete automated verification suite**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`

Expected: build successful, zero test failures, zero lint errors. Existing lint warnings may remain only if their count does not increase.

- [ ] **Step 3: Perform explicit v1 immutability verification**

Prepare a test vector book with `vector_canvas.json`, record SHA-256 and mtime, then execute these actions on an emulator/device: open, select, pan, zoom, switch tools, and leave without an edit. Record that both values are unchanged and that neither local `vector_canvas_v2.json` nor remote `vectorCanvasV2` was created. Then change one Appearance value and verify v1 remains unchanged while valid v2 is created.

- [ ] **Step 4: Perform the interaction matrix**

Verify portrait bottom sheet collapse/expand, landscape right inspector scroll, small-phone touch targets, S Pen drawing, two-finger viewport gestures, one-finger Hand pan, topmost selection, lasso multi-select, move, proportional/non-proportional scale, rotate, Basic/Art/Pattern differences, missing-profile fallback, selected/default inspector modes, Undo/Redo, selected/all SVG export, and Firebase v1/v2 round trips.

- [ ] **Step 5: Update progress with exact evidence and remaining blockers**

Record test count, lint error/warning count, APK path, manual devices/orientations tested, v1 hashes before/after, and any unverified hardware behavior in `PROGRESS.md`. Do not claim a manual check that was not run.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/g1/sketchbook/preview/FlowPreviews.kt PROGRESS.md
git commit -m "test(vector): verify appearance editor rollout"
```

## Final Review Checklist

- [ ] Every spec requirement maps to Tasks 1–14.
- [ ] Existing `vector_canvas.json` and remote `vectorCanvas` are never written by v2 paths.
- [ ] Legacy rendering tests remain green before and after v2 integration.
- [ ] Basic, Art, and Pattern rendering share renderer, hit-test, bounds, preview, and export routing.
- [ ] Selection/default Appearance semantics are covered by unit tests.
- [ ] Every editor mutation is represented by one reversible command.
- [ ] Opening, viewing, selecting, panning, or zooming creates no v2 save.
- [ ] Portrait and landscape use the approved adaptive panel placements.
- [ ] Full tests, APK assembly, lint, and manual safety checks are recorded before release claims.
