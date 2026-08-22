package com.g1.sketchbook.readmode.curl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/** Uploads app-supplied page [Bitmap]s as GL textures — adapted from PageCurlDemo's asset-only
 *  `TextureLoader`. This app has no bundled page assets; pages come from `PageTextureProvider`. */
object TextureLoader {
    fun loadBitmap(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        check(ids[0] != 0) { "Unable to allocate GL texture" }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        configureParameters()
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    /** Re-specifies an existing texture's pixel data in place — avoids allocating a fresh texture
     *  id on every page turn. `GLUtils.texImage2D` re-specifies the size too, so this is safe even
     *  when [bitmap]'s dimensions differ from what the texture originally held. */
    fun updateBitmap(textureId: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release(textureId: Int) {
        if (textureId == 0) return
        GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
    }

    private fun configureParameters() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }
}
