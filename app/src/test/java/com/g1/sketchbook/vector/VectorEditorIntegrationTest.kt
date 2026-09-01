package com.g1.sketchbook.vector

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VectorEditorIntegrationTest {
    @Test fun openingSelectingToolsAndViewportStateProduceNoSave() {
        val state = VectorEditorState(legacyPageAsDocument(VectorPage(listOf(legacyLine("a").stroke))))
        var commits = 0
        state.onDocumentCommitted = { commits++ }

        state.select(setOf(state.snapshot.value.document.objects.single().id))
        state.setTool(VectorTool.HAND)
        state.setTool(VectorTool.SELECT)

        assertEquals(0, commits)
        assertFalse(state.snapshot.value.canUndo)
    }

    @Test fun firstSelectedLegacyAppearanceEditCommitsOnlyTheSelectedObject() {
        val state = VectorEditorState(VectorDocument(objects = listOf(legacyLine("a"), legacyLine("b"))))
        var commits = 0
        state.onDocumentCommitted = { commits++ }

        state.select(setOf("a"))
        state.applyAppearance(AppearanceEdit.FillEnabled(false))

        assertEquals(1, commits)
        assertIs<EditablePathObject>(state.snapshot.value.document.objects[0])
        assertIs<LegacyStrokeObject>(state.snapshot.value.document.objects[1])
    }

    @Test fun oneCompletedDrawCommandProducesExactlyOneSave() {
        val state = VectorEditorState(VectorDocument(objects = emptyList()))
        var commits = 0
        state.onDocumentCommitted = { commits++ }

        state.dispatch(AddObject(editablePath("drawn")))

        assertEquals(1, commits)
        assertEquals(listOf("drawn"), state.snapshot.value.document.objects.map { it.id })
    }

    @Test fun exportUsesSelectedRenderedBoundsOrAllRenderedBounds() {
        val document = VectorDocument(objects = listOf(
            editableLine("left", Point(0f, 0f), Point(10f, 0f)),
            editableLine("right", Point(100f, 20f), Point(130f, 20f)),
        ))

        val selected = vectorExportBounds(document, setOf("right"))
        val all = vectorExportBounds(document, emptySet())

        assertNotNull(selected)
        assertNotNull(all)
        assertEquals(96f, selected.minX)
        assertEquals(134f, selected.maxX)
        assertEquals(-4f, all.minY)
        assertEquals(134f, all.maxX)
    }

    @Test fun loadingLegacyWithoutACommandLeavesV1AndV2FilesUntouched() {
        val root = createTempDirectory("vector-integration-").toFile()
        val v1 = File(root, "vector_canvas.json").apply {
            writeText(VectorPage(listOf(legacyLine("saved").stroke)).toJson())
            setLastModified(246810L)
        }
        val store = VectorDocumentStore(root)
        val state = VectorEditorState(store.load()!!)

        state.select(setOf(state.snapshot.value.document.objects.single().id))
        state.setTool(VectorTool.HAND)

        assertEquals(246810L, v1.lastModified())
        assertNull(File(root, "vector_canvas_v2.json").takeIf { it.exists() })
    }
}
