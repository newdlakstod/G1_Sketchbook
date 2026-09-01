package com.g1.sketchbook.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VectorEditorStateTest {
    @Test fun selectionAndToolChangesNeverCreateHistoryOrCommit() {
        val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"))))
        var commits = 0
        state.onDocumentCommitted = { commits++ }

        state.select(setOf("a"))
        state.setTool(VectorTool.PEN)

        assertEquals(setOf("a"), state.snapshot.value.selectedIds)
        assertEquals(VectorTool.PEN, state.snapshot.value.tool)
        assertFalse(state.snapshot.value.canUndo)
        assertEquals(0, commits)
    }

    @Test fun appearanceEditWithNoSelectionChangesOnlyFutureDefaults() {
        val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"))))
        val before = state.snapshot.value.document

        state.applyAppearance(AppearanceEdit.StrokeWidth(14f))

        assertEquals(before, state.snapshot.value.document)
        assertEquals(14f, state.snapshot.value.defaultAppearance.stroke.width)
        assertFalse(state.snapshot.value.canUndo)
    }

    @Test fun appearanceEditConvertsOnlySelectedLegacyObjectOnActualMutation() {
        val state = VectorEditorState(VectorDocument(objects = listOf(legacyLine("a"), legacyLine("b"))))

        state.select(setOf("a"))
        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[0])
        state.applyAppearance(AppearanceEdit.FillEnabled(false))

        assertIs<EditablePathObject>(state.snapshot.value.document.objects[0])
        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[1])
    }

    @Test fun semanticNoOpAppearanceDoesNotConvertLegacyOrCommit() {
        val state = VectorEditorState(VectorDocument(objects = listOf(legacyLine("a"))))
        var commits = 0
        state.onDocumentCommitted = { commits++ }

        state.select(setOf("a"))
        state.applyAppearance(AppearanceEdit.FillEnabled(true))

        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects.single())
        assertFalse(state.snapshot.value.canUndo)
        assertEquals(0, commits)
    }

    @Test fun sliderPreviewCommitsAsOneUndoStepAndOneCallback() {
        val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"))))
        var commits = 0
        state.onDocumentCommitted = { commits++ }
        state.select(setOf("a")); state.beginAppearanceGesture()

        state.previewAppearance(AppearanceEdit.StrokeWidth(10f))
        state.previewAppearance(AppearanceEdit.StrokeWidth(18f))
        state.commitAppearanceGesture()
        state.undo()

        assertEquals(8f, assertIs<EditablePathObject>(state.snapshot.value.document.objects.single()).appearance.stroke.width)
        assertFalse(state.snapshot.value.canUndo)
        assertEquals(1, commits)
        state.redo()
        assertEquals(18f, assertIs<EditablePathObject>(state.snapshot.value.document.objects.single()).appearance.stroke.width)
    }

    @Test fun transformCommandDispatchesTaskEightEngineAndUndoRedoAreExact() {
        val state = VectorEditorState(VectorDocument(objects = listOf(editableLine("a", Point(0f, 0f), Point(10f, 0f)))) )
        val original = state.snapshot.value.document

        state.select(setOf("a"))
        state.dispatch(TransformObjects(setOf("a"), SelectionTransform(translateX = 4f, pivot = Point(0f, 0f))))

        assertEquals(4f, assertIs<EditablePathObject>(state.snapshot.value.document.objects.single()).geometry.points.first().x)
        state.undo(); assertEquals(original, state.snapshot.value.document)
        state.redo(); assertEquals(4f, assertIs<EditablePathObject>(state.snapshot.value.document.objects.single()).geometry.points.first().x)
    }

    @Test fun deleteSanitizesSelectionAndNewCommandClearsRedo() {
        val state = VectorEditorState(VectorDocument(objects = listOf(editablePath("a"), editablePath("b"))))
        state.select(setOf("a")); state.dispatch(DeleteObjects(setOf("a")))

        assertEquals(emptySet(), state.snapshot.value.selectedIds)
        state.undo(); assertEquals(emptySet(), state.snapshot.value.selectedIds)
        state.dispatch(AddObject(editablePath("c")))

        assertFalse(state.snapshot.value.canRedo)
        assertTrue(state.snapshot.value.document.objects.any { it.id == "c" })
    }

    @Test fun closeActionConvertsOnlySelectedLegacyObjectsAndUndoRestoresThem() {
        val state = VectorEditorState(VectorDocument(objects = listOf(legacyLine("a"), legacyLine("b"))))
        state.select(setOf("a")); state.toggleSelectedClosed()

        assertTrue(assertIs<EditablePathObject>(state.snapshot.value.document.objects[0]).geometry.closed)
        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[1])
        state.undo()
        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[0])
    }
}
