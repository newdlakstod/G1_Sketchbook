package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceProjectionTest {
    @Test fun noSelectionProjectsFutureDefaults() {
        val snapshot = VectorEditorSnapshot(VectorDocument(objects = emptyList()), defaultAppearance = appearance(13f))
        assertEquals(13f, projectAppearance(snapshot).strokeWidth.value)
        assertEquals("새 패스 스타일", projectAppearance(snapshot).title)
    }

    @Test fun oneSelectionProjectsThatPath() {
        val snapshot = VectorEditorSnapshot(VectorDocument(objects = listOf(editablePath("a", 7f))), selectedIds = setOf("a"))
        assertEquals(7f, projectAppearance(snapshot).strokeWidth.value)
        assertEquals("패스", projectAppearance(snapshot).title)
    }

    @Test fun differentSelectedWidthsProjectMixedValue() {
        val snapshot = VectorEditorSnapshot(VectorDocument(objects = listOf(editablePath("a", 7f), editablePath("b", 11f))), selectedIds = setOf("a", "b"))
        assertTrue(projectAppearance(snapshot).strokeWidth.mixed)
    }

    @Test fun legacyProjectsEffectiveAppearanceWithoutConvertingIt() {
        val legacy = legacyLine("legacy", 6f)
        val projection = projectAppearance(VectorEditorSnapshot(VectorDocument(objects = listOf(legacy)), selectedIds = setOf("legacy")))
        assertEquals(6f, projection.strokeWidth.value)
        assertTrue(projection.hasLegacySelection)
    }

    @Test fun missingProfileFallsBackToBasicPreviewWithoutLosingId() {
        val missing = BrushStyle(VectorBrushKind.ART, "gone")
        val projection = projectAppearance(VectorEditorSnapshot(VectorDocument(objects = listOf(editablePath("a", brush = missing))), selectedIds = setOf("a")))
        assertTrue(projection.missingProfile)
        assertEquals("gone", projection.brush.value.profileId)
        assertEquals(VectorBrushKind.BASIC, projection.previewBrush.kind)
    }

    @Test fun closedProjectionUsesDeterministicMixedToggleTarget() {
        val open = editablePath("open").copy(geometry = editablePath("open").geometry.copy(closed = false))
        val closed = editablePath("closed").copy(geometry = editablePath("closed").geometry.copy(closed = true))
        val projection = projectAppearance(VectorEditorSnapshot(VectorDocument(objects = listOf(open, closed)), selectedIds = setOf("open", "closed")))
        assertTrue(projection.closed.mixed)
        assertTrue(projection.closeTarget)
        assertFalse(projection.closed.value)
    }
}
