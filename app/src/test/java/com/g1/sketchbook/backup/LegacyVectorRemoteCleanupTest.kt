package com.g1.sketchbook.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyVectorRemoteCleanupTest {
    @Test
    fun selectsOnlyExplicitRemoteVectorBooksAndBrushRoot() {
        val bitmap = remoteBook("bitmap", legacyVector = false)
        val vector = remoteBook("vector", legacyVector = true)

        assertEquals(
            LegacyVectorRemoteCleanup(setOf("vector"), removeStampBrushes = true),
            legacyVectorRemoteCleanup(listOf(bitmap, vector), hasStampBrushes = true),
        )
    }

    @Test
    fun filtersRemoteVectorRowsBeforeNormalReconciliation() {
        val remote = listOf(remoteBook("bitmap", false), remoteBook("vector", true))

        assertEquals(listOf("bitmap"), nonVectorRemoteBooks(remote).map { it.id })
    }

    private fun remoteBook(id: String, legacyVector: Boolean) = RemoteSketchbook(
        id = id,
        name = id,
        sizeKey = "a4",
        bgKey = "watercolor",
        createdAt = 1L,
        pageCount = 15,
        fav = false,
        coverColor = null,
        updatedAt = 1L,
        deleted = false,
        coverBase64 = null,
        coverUpdatedAt = null,
        pages = emptyMap(),
        legacyVector = legacyVector,
    )
}
