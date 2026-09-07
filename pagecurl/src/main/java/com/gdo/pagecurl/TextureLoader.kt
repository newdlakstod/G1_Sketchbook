package com.gdo.pagecurl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

internal fun validateTextureUpload(
    label: String,
    texture: Int,
    glError: Int,
    deleteTexture: (Int) -> Unit,
) {
    if (glError == GLES30.GL_NO_ERROR) return
    deleteTexture(texture)
    error("OpenGL texture upload failed for $label with error 0x${glError.toString(16)}")
}

internal object TextureLoader {
    fun upload(bitmap: Bitmap, label: String, maxTextureSize: Int): Int {
        require(!bitmap.isRecycled) { "$label bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "$label bitmap has no pixels" }
        require(bitmap.width <= maxTextureSize && bitmap.height <= maxTextureSize) {
            "$label bitmap ${bitmap.width}x${bitmap.height} exceeds GL texture limit $maxTextureSize"
        }
        val ids = IntArray(1)
        var ownsTexture = true
        try {
            GLES30.glGenTextures(1, ids, 0)
            check(ids[0] != 0) { "Unable to allocate GL texture for $label" }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR_MIPMAP_LINEAR,
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR,
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE,
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE,
            )
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            validateTextureUpload(label, ids[0], GLES30.glGetError()) { texture ->
                ownsTexture = false
                GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            return ids[0]
        } catch (failure: Throwable) {
            if (ownsTexture && ids[0] != 0) GLES30.glDeleteTextures(1, ids, 0)
            throw IllegalStateException("Failed to upload texture for $label", failure)
        }
    }
}
