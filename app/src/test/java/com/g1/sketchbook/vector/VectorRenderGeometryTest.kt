package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VectorRenderGeometryTest {
    @Test fun missingProfileFallsBackToBasicWithoutChangingReference() {
        val objectPath = editablePath(brush = BrushStyle(VectorBrushKind.ART, "missing"))

        val geometry = renderedPolygons(objectPath, emptyMap())

        assertTrue(geometry.strokeShapes.flatten().isNotEmpty())
        assertEquals("missing", objectPath.appearance.brush.profileId)
    }

    @Test fun wrongEmptyAndDegenerateProfilesFallBackToBasic() {
        val objectPath = editablePath(brush = BrushStyle(VectorBrushKind.ART, "profile"))
        val pattern = PatternBrushProfile("profile", "wrong", listOf(listOf(Point(0f, 0f))), 8f, 8f)
        val emptyArt = ArtBrushProfile("profile", "empty", emptyList())
        val flatArt = ArtBrushProfile("profile", "flat", listOf(listOf(Point(1f, 0f), Point(1f, 1f))))

        listOf(pattern, emptyArt, flatArt).forEach { profile ->
            assertEquals(RenderRoute.BASIC, renderRoute(objectPath.appearance.brush, mapOf("profile" to profile)))
            assertTrue(renderedPolygons(objectPath, mapOf("profile" to profile)).strokeShapes.flatten().isNotEmpty())
        }

        val patternPath = editablePath(brush = BrushStyle(VectorBrushKind.PATTERN, "pattern"))
        val badPattern = PatternBrushProfile("pattern", "bad", listOf(listOf(Point(0f, 0f), Point(1f, 0f))), 8f, 8f)
        assertEquals(RenderRoute.BASIC, renderRoute(patternPath.appearance.brush, mapOf("pattern" to badPattern)))
        assertTrue(renderedPolygons(patternPath, mapOf("pattern" to badPattern)).strokeShapes.flatten().isNotEmpty())
    }

    @Test fun artAndPatternTakeDifferentRenderRoutes() {
        val art = ArtBrushProfile("a", "art", listOf(listOf(Point(0f, -1f), Point(1f, 1f))))
        val pattern = PatternBrushProfile("p", "pattern", listOf(listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f))))

        assertEquals(RenderRoute.ART, renderRoute(BrushStyle(VectorBrushKind.ART, "a"), mapOf("a" to art)))
        assertEquals(RenderRoute.PATTERN, renderRoute(BrushStyle(VectorBrushKind.PATTERN, "p"), mapOf("p" to pattern)))
    }

    @Test fun transformedExpandedGeometryDrivesBoundsAndHits() {
        val objectPath = editableLine("line", Point(0f, 0f), Point(10f, 0f), width = 2f).copy(
            transform = ObjectTransform(translateX = 20f, translateY = 10f, scaleX = 2f, scaleY = 2f),
        )

        val bounds = renderedObjectBounds(objectPath, emptyMap())!!

        // Round caps are expanded before the persisted scale is applied.
        assertEquals(18f, bounds.minX)
        assertEquals(42f, bounds.maxX)
        assertTrue(pointInRenderedObject(Point(30f, 10f), objectPath, emptyMap(), tolerance = 0f))
        assertFalse(pointInRenderedObject(Point(5f, 0f), objectPath, emptyMap(), tolerance = 0f))
    }

    @Test fun thinOpenPathUsesDocumentSpaceHitTolerance() {
        val thin = editableLine("thin", Point(0f, 0f), Point(10f, 0f), width = 0f)

        assertTrue(pointInRenderedObject(Point(5f, 1.5f), thin, emptyMap(), tolerance = 2f))
        assertFalse(pointInRenderedObject(Point(5f, 2.5f), thin, emptyMap(), tolerance = 2f))
    }

    @Test fun patternHitTestingUsesOnlyFinalStampedExtents() {
        val objectPath = editableLine("pattern", Point(0f, 0f), Point(100f, 0f), width = 8f).copy(
            appearance = appearance(width = 8f).copy(brush = BrushStyle(VectorBrushKind.PATTERN, "p")),
        )
        val profile = PatternBrushProfile(
            "p", "sparse", listOf(listOf(Point(-0.5f, -0.5f), Point(0.5f, -0.5f), Point(0f, 0.5f))),
            spacingPx = 100f, sizePx = 2f,
        )

        assertTrue(pointInRenderedObject(Point(0f, 0f), objectPath, mapOf("p" to profile), tolerance = 20f))
        assertFalse(pointInRenderedObject(Point(50f, 0f), objectPath, mapOf("p" to profile), tolerance = 20f))
    }

    @Test fun cacheKeyChangesForAppearanceButNotViewport() {
        val objectPath = editablePath("a", width = 8f)
        val first = geometryCacheKey(objectPath, profile = null)
        val changed = geometryCacheKey(objectPath.copy(appearance = appearance(width = 12f)), profile = null)

        assertNotEquals(first, changed)
        assertEquals(first, geometryCacheKey(objectPath, profile = null))
    }

    @Test fun legacyDocumentKeepsV1SvgOutputAndOrder() {
        val first = legacyLine("first")
        val editable = editablePath("middle")
        val last = legacyLine("last").copy(stroke = legacyLine("last", width = 6f).stroke)
        val document = VectorDocument(objects = listOf(first, editable, last))
        val region = Bounds(-10f, -10f, 50f, 50f)
        val svg = vectorDocumentToSvg(document, region)

        val firstLegacy = vectorPageToSvg(VectorPage(listOf(first.stroke)), region)
            .substringAfter('>').substringBeforeLast("</svg>")
        val lastLegacy = vectorPageToSvg(VectorPage(listOf(last.stroke)), region)
            .substringAfter('>').substringBeforeLast("</svg>")
        assertTrue(svg.indexOf(firstLegacy) < svg.indexOf("data-role=\"stroke\""))
        assertTrue(svg.indexOf("data-role=\"stroke\"") < svg.indexOf(lastLegacy))
    }
}
