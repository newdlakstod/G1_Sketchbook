package com.g1.sketchbook.vector

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VectorDocumentStoreTest {
    @Test fun savingV2LeavesLegacyBytesAndMtimeUnchanged() {
        val root = createTempDir(prefix = "vector-store-")
        val v1 = File(root, "vector_canvas.json").apply {
            writeText("legacy bytes")
            setLastModified(123456L)
        }

        VectorDocumentStore(root).saveV2(VectorDocument(objects = emptyList()))

        assertEquals("legacy bytes", v1.readText())
        assertEquals(123456L, v1.lastModified())
        assertNotNull(decodeVectorDocument(File(root, "vector_canvas_v2.json").readText()))
    }

    @Test fun loadUsesPreviousValidV2WhenInterruptedReplacementLeavesPrimaryMissing() {
        val root = createTempDir(prefix = "vector-store-")
        val v1 = File(root, "vector_canvas.json").apply {
            writeText("interrupted legacy source")
            setLastModified(765432L)
        }
        val old = VectorDocument(objects = listOf(editablePath("old")))
        val new = VectorDocument(objects = listOf(editablePath("new")))
        VectorDocumentStore(root).saveV2(old)

        assertFailsWith<IllegalStateException> {
            VectorDocumentStore(root, InterruptingFileSystem()).saveV2(new)
        }

        assertEquals(old, VectorDocumentStore(root).load())
        assertNull(File(root, "vector_canvas_v2.json").takeIf { it.exists() })
        assertEquals(old, decodeVectorDocument(File(root, "vector_canvas_v2.previous").readText()))
        assertEquals("interrupted legacy source", v1.readText())
        assertEquals(765432L, v1.lastModified())
    }

    @Test fun replacementFailureRestoresPriorV2AndNeverAcceptsPartialNewData() {
        val root = createTempDir(prefix = "vector-store-")
        val v1 = File(root, "vector_canvas.json").apply {
            writeText("legacy source")
            setLastModified(456789L)
        }
        val old = VectorDocument(objects = listOf(editablePath("old")))
        val new = VectorDocument(objects = listOf(editablePath("new")))
        VectorDocumentStore(root).saveV2(old)

        assertFailsWith<IllegalStateException> {
            VectorDocumentStore(root, FinalMoveFailureFileSystem()).saveV2(new)
        }

        assertEquals(old, VectorDocumentStore(root).load())
        assertEquals(old, decodeVectorDocument(File(root, "vector_canvas_v2.json").readText()))
        assertFalse(File(root, "vector_canvas_v2.json.tmp").exists())
        assertEquals("legacy source", v1.readText())
        assertEquals(456789L, v1.lastModified())
    }

    private class FinalMoveFailureFileSystem : DefaultVectorDocumentFileSystem() {
        override fun moveAtomically(source: File, target: File): Boolean =
            if (source.name.endsWith(".tmp") && target.name == "vector_canvas_v2.json") false
            else super.moveAtomically(source, target)
    }

    private class InterruptingFileSystem : DefaultVectorDocumentFileSystem() {
        override fun moveAtomically(source: File, target: File): Boolean =
            if ((source.name.endsWith(".tmp") && target.name == "vector_canvas_v2.json") ||
                (source.name.endsWith(".previous") && target.name == "vector_canvas_v2.json")) false
            else super.moveAtomically(source, target)
    }
}
