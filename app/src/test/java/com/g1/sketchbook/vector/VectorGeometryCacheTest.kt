package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class VectorGeometryCacheTest {
    @Test fun cacheReusesUnchangedImmutableSnapshotAndReplacesOnlyChangedObject() {
        val cache = VectorGeometryCache()
        val first = editablePath("first")
        val second = editablePath("second")

        val cachedFirst = cache.geometryFor(first, emptyMap())
        val cachedSecond = cache.geometryFor(second, emptyMap())
        assertSame(cachedFirst, cache.geometryFor(first, emptyMap()))

        val changedFirst = first.copy(appearance = appearance(width = 12f))
        val recachedFirst = cache.geometryFor(changedFirst, emptyMap())

        assertNotEquals(cachedFirst, recachedFirst)
        assertSame(cachedSecond, cache.geometryFor(second, emptyMap()))
        assertEquals(2, cache.size)
    }

    @Test fun profileContentSnapshotInvalidatesOnlyObjectsUsingThatProfile() {
        val cache = VectorGeometryCache()
        val profiled = editablePath("profiled", brush = BrushStyle(VectorBrushKind.PATTERN, "p"))
        val basic = editablePath("basic")
        val before = PatternBrushProfile("p", "p", listOf(listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f))), 8f, 8f)
        val after = before.copy(sizePx = 16f)
        val oldProfiled = cache.geometryFor(profiled, mapOf("p" to before))
        val oldBasic = cache.geometryFor(basic, emptyMap())

        val newProfiled = cache.geometryFor(profiled, mapOf("p" to after))

        assertNotEquals(oldProfiled, newProfiled)
        assertSame(oldBasic, cache.geometryFor(basic, emptyMap()))
        assertEquals(2, cache.size)
    }
}
