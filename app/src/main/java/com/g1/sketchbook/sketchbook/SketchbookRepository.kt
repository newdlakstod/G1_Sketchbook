package com.g1.sketchbook.sketchbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** A canvas size option. Paper sizes are millimetres; device sizes are pixels. */
data class CanvasSize(val key: String, val label: String, val w: Int, val h: Int) {
    val ratio: Float get() = w.toFloat() / h.toFloat()
    val isPaper: Boolean get() = key == "a5" || key == "a4" || key == "a3"
    /** Actual canvas resolution: paper mm -> px at [dpi]; devices are already px. */
    fun pxW(dpi: Int = 200): Int = if (isPaper) Math.round(w / 25.4 * dpi).toInt() else w
    fun pxH(dpi: Int = 200): Int = if (isPaper) Math.round(h / 25.4 * dpi).toInt() else h
}

/** A paper background option (maps to a drawable by [key] in the UI layer). */
data class Background(val key: String, val label: String)

object Catalog {
    val sizes = listOf(
        CanvasSize("a5", "A5", 148, 210),
        CanvasSize("a4", "A4", 210, 297),
        CanvasSize("a3", "A3", 297, 420),
        CanvasSize("desktop", "데스크톱", 1920, 1080),
        CanvasSize("mobile", "모바일", 390, 844),
        CanvasSize("tablet", "태블릿", 810, 1080),
    )
    val backgrounds = listOf(
        Background("watercolor", "수채화용지"),
        Background("drawing", "도화지"),
        Background("canvas", "캔버스면"),
        Background("recycled", "재생지"),
        Background("kraft", "크라프트지"),
    )
    fun size(key: String) = sizes.firstOrNull { it.key == key } ?: sizes[1]
    fun background(key: String) = backgrounds.firstOrNull { it.key == key } ?: backgrounds[0]
}

data class Sketchbook(
    val id: String,
    val name: String,
    val sizeKey: String,
    val bgKey: String,
    val createdAt: Long,
    val pageCount: Int,
    val fav: Boolean = false,
    val shared: Boolean = false,   // a "draw together" book, grouped separately
    val code: String? = null,      // invite/session code for shared books
) {
    val size get() = Catalog.size(sizeKey)
}

const val MAX_PAGES = 15

/**
 * Local-first persistence for personal sketchbooks: metadata in SharedPreferences (JSON), page
 * images as PNG files under filesDir/sketchbooks/{id}/page_{i}.png. (Sharing/realtime comes later.)
 */
class SketchbookRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("g1_sketchbooks", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "sketchbooks").apply { mkdirs() }

    fun list(): List<Sketchbook> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sketchbook(o.getString("id"), o.getString("name"), o.getString("size"),
                    o.getString("bg"), o.optLong("createdAt"), o.optInt("pages", 1), o.optBoolean("fav", false),
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null })
            }.sortedWith(compareByDescending<Sketchbook> { it.fav }.thenByDescending { it.createdAt })
        }.getOrDefault(emptyList())
    }

    fun get(id: String) = list().firstOrNull { it.id == id }

    fun create(name: String, sizeKey: String, bgKey: String, shared: Boolean = false, code: String? = null): Sketchbook {
        val fallback = if (shared) "공유 스케치북" else "우리 스케치북"
        val sb = Sketchbook(newId(), name.ifBlank { fallback }, sizeKey, bgKey, System.currentTimeMillis(), 1,
            fav = false, shared = shared, code = code)
        save(list() + sb)
        File(root, sb.id).mkdirs()
        return sb
    }

    fun setPageCount(id: String, count: Int) {
        save(list().map { if (it.id == id) it.copy(pageCount = count.coerceIn(1, MAX_PAGES)) else it })
    }

    fun toggleFav(id: String) {
        save(list().map { if (it.id == id) it.copy(fav = !it.fav) else it })
    }

    fun delete(id: String) {
        save(list().filter { it.id != id })
        File(root, id).deleteRecursively()
    }

    fun pageFile(id: String, index: Int): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "page_$index.png")
    }

    fun loadPage(id: String, index: Int): Bitmap? {
        val f = pageFile(id, index)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun savePage(id: String, index: Int, bmp: Bitmap) {
        FileOutputStream(pageFile(id, index)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun save(books: List<Sketchbook>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: ""))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun newId(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { a[Random.nextInt(a.length)] }.joinToString("")
    }

    companion object { private const val KEY = "books" }
}
