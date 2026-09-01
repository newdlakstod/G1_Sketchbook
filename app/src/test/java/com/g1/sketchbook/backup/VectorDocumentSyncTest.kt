package com.g1.sketchbook.backup

import com.g1.sketchbook.vector.VectorDocument
import com.g1.sketchbook.vector.encodeVectorDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorDocumentSyncTest {
    @Test fun v1OnlyRemoteNeverCausesAV2PushUntilLocalV2Exists() {
        assertEquals(SyncAction.NOOP, decideVectorDocumentSyncAction(localV2At = null, remoteV2At = null))
    }

    @Test fun validRemoteV2PullUsesItsTimestamp() {
        assertEquals(SyncAction.PULL, decideVectorDocumentSyncAction(localV2At = null, remoteV2At = 20L))
    }

    @Test fun invalidRemoteV2DoesNotAuthorizeOverwritingLocalOrV1() {
        assertEquals(
            SyncAction.NOOP,
            decideVectorDocumentSyncAction(localV2At = 10L, remoteV2At = 20L, remoteV2Valid = false),
        )
    }

    @Test fun validLocalV2PushesOnlyItsSiblingPayload() {
        assertEquals(SyncAction.PUSH, decideVectorDocumentSyncAction(localV2At = 30L, remoteV2At = 20L))
    }

    @Test fun v2RemoteWriteUsesOnlySiblingPathAndPayload() {
        val write = vectorDocumentRemoteWrite("book-7", "{\"version\":2,\"objects\":[]}", 20L)

        assertEquals("sketchbooks/book-7/vectorCanvasV2", write.path)
        assertEquals(setOf("updatedAt", "document"), write.payload.keys)
        assertEquals(20L, write.payload["updatedAt"])
        assertEquals("{\"version\":2,\"objects\":[]}", write.payload["document"])
        assertFalse(write.path.endsWith("/vectorCanvas"))
        assertFalse("strokes" in write.payload)
    }

    @Test fun v1OnlyStateLeavesTheV2StoreAndSiblingPushUntouched() {
        val writes = mutableListOf<Pair<VectorDocument, Long>>()
        val pushes = mutableListOf<Pair<String, Long>>()

        assertEquals(
            SyncAction.NOOP,
            reconcileVectorDocument(
                localV2At = null,
                remoteV2 = null,
                loadLocalV2 = { error("v1 fallback must not be loaded") },
                saveLocal = { document, updatedAt -> writes += document to updatedAt },
                pushRemote = { json, updatedAt -> pushes += json to updatedAt },
            ),
        )
        assertTrue(writes.isEmpty())
        assertTrue(pushes.isEmpty())
    }

    @Test fun validRemoteV2PullsIntoTheV2StoreWithTheRemoteTimestamp() {
        val remoteDocument = VectorDocument(objects = emptyList())
        val writes = mutableListOf<Pair<VectorDocument, Long>>()

        assertEquals(
            SyncAction.PULL,
            reconcileVectorDocument(
                localV2At = null,
                remoteV2 = RemoteVectorDocument(20L, encodeVectorDocument(remoteDocument)),
                loadLocalV2 = { error("pull must not load local data") },
                saveLocal = { document, updatedAt -> writes += document to updatedAt },
                pushRemote = { _, _ -> error("pull must not push") },
            ),
        )
        assertEquals(listOf(remoteDocument to 20L), writes)
    }

    @Test fun invalidRemoteV2ChangesNeitherTheLocalV2StoreNorSiblingPayload() {
        var localLoaded = false
        var localSaved = false
        var pushed = false

        assertEquals(
            SyncAction.NOOP,
            reconcileVectorDocument(
                localV2At = 10L,
                remoteV2 = RemoteVectorDocument(20L, "{broken"),
                loadLocalV2 = { localLoaded = true; VectorDocument(objects = emptyList()) },
                saveLocal = { _, _ -> localSaved = true },
                pushRemote = { _, _ -> pushed = true },
            ),
        )
        assertTrue(!localLoaded && !localSaved && !pushed)
    }

    @Test fun remoteV2WithMissingTimestampIsATotalNoop() {
        assertRemoteV2IsNoop(RemoteVectorDocument(0L, encodeVectorDocument(VectorDocument(objects = emptyList()))))
    }

    @Test fun remoteV2WithMissingDocumentIsATotalNoop() {
        assertRemoteV2IsNoop(RemoteVectorDocument(20L, ""))
    }

    @Test fun corruptDedicatedV2DoesNotPushAV1Fallback() {
        var dedicatedV2Loaded = false
        var pushed = false

        assertEquals(
            SyncAction.NOOP,
            reconcileVectorDocument(
                localV2At = 30L,
                remoteV2 = null,
                loadLocalV2 = { dedicatedV2Loaded = true; null },
                saveLocal = { _, _ -> error("invalid local v2 must not be saved") },
                pushRemote = { _, _ -> pushed = true },
            ),
        )
        assertTrue(dedicatedV2Loaded)
        assertFalse(pushed)
    }

    @Test fun validLocalV2PushesTheV2DocumentPayloadOnly() {
        val localDocument = VectorDocument(objects = emptyList())
        val pushes = mutableListOf<Pair<String, Long>>()

        assertEquals(
            SyncAction.PUSH,
            reconcileVectorDocument(
                localV2At = 30L,
                remoteV2 = RemoteVectorDocument(20L, encodeVectorDocument(VectorDocument(objects = emptyList()))),
                loadLocalV2 = { localDocument },
                saveLocal = { _, _ -> error("push must not save locally") },
                pushRemote = { json, updatedAt -> pushes += json to updatedAt },
            ),
        )
        assertEquals(listOf(encodeVectorDocument(localDocument) to 30L), pushes)
    }

    private fun assertRemoteV2IsNoop(remoteV2: RemoteVectorDocument) {
        var localLoaded = false
        var localSaved = false
        var pushed = false

        assertEquals(
            SyncAction.NOOP,
            reconcileVectorDocument(
                localV2At = 10L,
                remoteV2 = remoteV2,
                loadLocalV2 = { localLoaded = true; VectorDocument(objects = emptyList()) },
                saveLocal = { _, _ -> localSaved = true },
                pushRemote = { _, _ -> pushed = true },
            ),
        )
        assertFalse(localLoaded || localSaved || pushed)
    }
}
