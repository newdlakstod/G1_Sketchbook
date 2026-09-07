package com.gdo.pagecurl

internal class TextureCache<T>(
    private val maxEntries: Int,
    private val onEvict: (T) -> Unit,
) {
    private val entries = LinkedHashMap<Int, T>(maxEntries, 0.75f, true)

    init {
        require(maxEntries > 0)
    }

    val size: Int get() = entries.size

    operator fun get(index: Int): T? = entries[index]

    fun put(index: Int, value: T) {
        entries.put(index, value)?.let(onEvict)
        if (entries.size > maxEntries) {
            val eldest = entries.entries.iterator().next()
            entries.remove(eldest.key)
            onEvict(eldest.value)
        }
    }

    fun clear() {
        entries.values.forEach(onEvict)
        entries.clear()
    }
}
