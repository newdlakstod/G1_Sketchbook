package com.gdo.pagecurl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageShaderContractTest {
    @Test
    fun shaderKeepsOpaqueFaceSelectionAndAddsStaticPaperControls() {
        val vertex = ShaderSources.PAGE_VERTEX
        val fragment = ShaderSources.PAGE_FRAGMENT

        assertTrue(vertex.contains("uPageOffsetX"))
        assertTrue(fragment.contains("gl_FrontFacing ? front : back"))
        assertTrue(fragment.contains("uBlankPage"))
        assertTrue(fragment.contains("uGutterSide"))
        assertFalse(fragment.contains("mix(front, back"))
    }
}
