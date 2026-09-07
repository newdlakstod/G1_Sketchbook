package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureLoaderTest {
    @Test
    fun glUploadErrorDeletesAllocatedTextureAndThrows() {
        var deletedTexture = 0

        val failure = assertThrows(IllegalStateException::class.java) {
            validateTextureUpload(
                label = "page 2",
                texture = 41,
                glError = 0x0502,
                deleteTexture = { deletedTexture = it },
            )
        }

        assertEquals(41, deletedTexture)
        assertTrue(failure.message.orEmpty().contains("0x502"))
    }
}
