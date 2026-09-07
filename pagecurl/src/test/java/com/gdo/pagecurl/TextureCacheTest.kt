package com.gdo.pagecurl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextureCacheTest {
    @Test
    fun sixthTextureEvictsTheLeastRecentlyUsedEntry() {
        val evicted = mutableListOf<Int>()
        val cache = TextureCache<Int>(maxEntries = 5, onEvict = evicted::add)
        repeat(5) { cache.put(it, it + 100) }
        cache[0]
        cache.put(5, 105)
        assertEquals(5, cache.size)
        assertEquals(listOf(101), evicted)
        assertNull(cache[1])
        assertEquals(100, cache[0])
    }

    @Test
    fun clearEvictsEveryStoredTextureExactlyOnce() {
        val evicted = mutableListOf<Int>()
        val cache = TextureCache<Int>(2, evicted::add)
        cache.put(0, 10)
        cache.put(1, 11)
        cache.clear()
        assertEquals(listOf(10, 11), evicted)
        assertEquals(0, cache.size)
    }

    @Test
    fun replacingAnIndexEvictsItsPreviousTexture() {
        val evicted = mutableListOf<Int>()
        val cache = TextureCache<Int>(2, evicted::add)
        cache.put(0, 10)

        cache.put(0, 20)

        assertEquals(listOf(10), evicted)
        assertEquals(20, cache[0])
        assertEquals(1, cache.size)
    }
}
