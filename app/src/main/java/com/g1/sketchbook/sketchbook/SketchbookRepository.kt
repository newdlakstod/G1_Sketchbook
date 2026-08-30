package com.g1.sketchbook.sketchbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import com.g1.sketchbook.vector.VectorPage
import com.g1.sketchbook.vector.renderVectorPage
import com.g1.sketchbook.vector.toJson
import com.g1.sketchbook.vector.vectorPageFromJson
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
    val coverColor: Long? = null,  // custom solid cover colour (ARGB); null = default yellow
    /** 표지 이미지 파일이 바뀔 때마다 올라간다 — id는 그대로라 LaunchedEffect(book.id)만으론 새
     *  파일을 다시 읽어오지 않으므로, 이 값을 키에 함께 넣어 캐시를 무효화한다. */
    val coverVersion: Int = 0,
    /** 처음부터 벡터(획 점 목록)로 그리는 스케치북 — [shared]와 동시에 켜지지 않는다(생성 마법사가
     *  그 조합을 만들지 않음). true면 페이지는 `page_{i}.png`가 아니라 `page_{i}.json`에 저장되고,
     *  [SketchbookRepository.loadVectorPage]/[saveVectorPage]로 읽고 쓴다. */
    val vector: Boolean = false,
    /** 무한 캔버스 여부(벡터 책 전용, [vector]=true일 때만 의미 있음) — true면 [vectorCanvasW]/
     *  [vectorCanvasH]는 항상 null. 페이지 개념이 없는 벡터 책 하나가 곧 캔버스 한 장이다. */
    val vectorInfinite: Boolean = false,
    /** 커스텀(고정) 캔버스의 논리 가로·세로 — [vectorInfinite]=false일 때만 값이 있음. */
    val vectorCanvasW: Int? = null,
    val vectorCanvasH: Int? = null,
    /** 메타(이름/즐겨찾기/표지색/표지버전)가 마지막으로 바뀐 시각 — 구글 계정 백업 동기화의
     *  last-write-wins 비교에 쓰인다. 새로 만들 때(create)는 기본값(호출 시점)이 곧 맞는 값이라
     *  따로 안 넘겨도 된다. */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val size get() = Catalog.size(sizeKey)
    /** "2026.08.16" — shown under cover thumbnails (home carousel, sketchbook list). */
    val dateLabel: String get() = java.text.SimpleDateFormat(
        "yyyy.MM.dd",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(createdAt))
}

const val MAX_PAGES = 15

/**
 * Local-first persistence for personal sketchbooks: metadata in SharedPreferences (JSON), page
 * images as PNG files under filesDir/sketchbooks/{id}/page_{i}.png.
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
                    o.optBoolean("shared", false), o.optString("code", "").ifBlank { null },
                    o.optLong("coverColor", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }, o.optInt("coverVer", 0),
                    o.optBoolean("vector", false),
                    vectorInfinite = o.optBoolean("vectorInfinite", false),
                    vectorCanvasW = o.optInt("vectorCanvasW", -1).takeIf { it > 0 },
                    vectorCanvasH = o.optInt("vectorCanvasH", -1).takeIf { it > 0 },
                    updatedAt = o.optLong("updatedAt", o.optLong("createdAt")))
            }.sortedWith(compareByDescending<Sketchbook> { it.fav }.thenByDescending { it.createdAt })
        }.getOrDefault(emptyList())
    }

    fun get(id: String) = list().firstOrNull { it.id == id }

    fun create(
        name: String, sizeKey: String, bgKey: String, shared: Boolean = false, code: String? = null,
        vector: Boolean = false, vectorInfinite: Boolean = false, vectorCanvasW: Int? = null, vectorCanvasH: Int? = null,
    ): Sketchbook {
        val fallback = if (shared) "공유 스케치북" else if (vector) "벡터 스케치북" else "우리 스케치북"
        // A sketchbook is a fixed MAX_PAGES-page notebook from the start (like a physical one) —
        // pages aren't added/removed later, just navigated. Blank pages are lazy (no file until drawn on).
        // (벡터 책은 pageCount를 안 쓰지만, 필드 자체는 다른 책들과 공유하는 구조라 그대로 채워 넣는다.)
        val sb = Sketchbook(newId(), name.ifBlank { fallback }, sizeKey, bgKey, System.currentTimeMillis(), MAX_PAGES,
            fav = false, shared = shared, code = code, vector = vector,
            vectorInfinite = vectorInfinite, vectorCanvasW = vectorCanvasW, vectorCanvasH = vectorCanvasH)
        save(list() + sb)
        File(root, sb.id).mkdirs()
        return sb
    }

    fun toggleFav(id: String) {
        save(list().map { if (it.id == id) it.copy(fav = !it.fav, updatedAt = System.currentTimeMillis()) else it })
    }

    /** 표지 길게 눌러 수정하기 — 이름만 바꾼다(사이즈·종이 재질은 이미 그려둔 페이지에 쓰이므로 제외). */
    fun rename(id: String, name: String) {
        save(list().map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, updatedAt = System.currentTimeMillis()) else it })
    }

    fun delete(id: String) {
        save(list().filter { it.id != id })
        File(root, id).deleteRecursively()
    }

    /** [book]을 그대로 넣는다(같은 id가 있으면 교체, 없으면 추가) — [create]와 달리 새 id를 만들지
     *  않는다. 구글 계정 백업에서 다른 기기가 만든 스케치북을 복원할 때 씀 — 클라우드의 id를
     *  그대로 로컬 id로 써야 다음 동기화 때도 같은 항목으로 계속 매칭된다. */
    fun upsert(book: Sketchbook) {
        val current = list()
        val next = if (current.any { it.id == book.id }) current.map { if (it.id == book.id) book else it } else current + book
        save(next)
        File(root, book.id).mkdirs()
    }

    fun pageFile(id: String, index: Int): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "page_$index.png")
    }

    fun loadPage(id: String, index: Int): Bitmap? {
        val f = pageFile(id, index)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Downsampled page image for the page-list panel — cheap to decode 15 of at once. */
    fun loadPageThumb(id: String, index: Int, reqPx: Int = 160): Bitmap? {
        val f = pageFile(id, index); if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqPx) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun savePage(id: String, index: Int, bmp: Bitmap) {
        FileOutputStream(pageFile(id, index)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 페이지 파일이 마지막으로 저장된 시각 — 파일시스템 mtime을 그대로 씀(별도 타임스탐프 저장 불
     *  필요). 안 그려진 페이지는 0을 반환한다(파일이 없으면 File.lastModified()는 0). */
    fun pageUpdatedAt(id: String, index: Int): Long = pageFile(id, index).lastModified()

    /** PULL로 받아 저장한 페이지에 **원격 타임스탬프**를 다시 찍는다 — 안 그러면 방금 저장한 mtime이
     *  "지금"이라 항상 원격보다 최신으로 보여서, 다음 동기화가 곧바로 그걸 되밀어 올린다(핑퐁). */
    fun setPageUpdatedAt(id: String, index: Int, timestamp: Long) { pageFile(id, index).setLastModified(timestamp) }

    private fun vectorPageFile(id: String, index: Int): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "page_$index.json")
    }

    fun loadVectorPage(id: String, index: Int): VectorPage? {
        val f = vectorPageFile(id, index)
        if (!f.exists()) return null
        return vectorPageFromJson(f.readText())
    }

    /** JSON(진짜 저장 데이터)과 함께, 같은 인덱스의 `page_{i}.png`에 렌더링한 비트맵도 같이 써서
     *  [loadPageThumb]/[loadPage] 등 기존 PNG 전용 썸네일 경로가 벡터 페이지에도 그대로 통한다 —
     *  `PagePanel`(3열 페이지 목록) 등 다른 화면을 벡터 인지하게 고칠 필요가 없다. PNG는 순수
     *  캐시라 JSON만 진짜 상태다. */
    fun saveVectorPage(id: String, index: Int, page: VectorPage) {
        vectorPageFile(id, index).writeText(page.toJson())
        FileOutputStream(pageFile(id, index)).use {
            renderVectorPage(page, Catalog.size("vector").pxW()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    fun vectorPageUpdatedAt(id: String, index: Int): Long = vectorPageFile(id, index).lastModified()

    fun setVectorPageUpdatedAt(id: String, index: Int, timestamp: Long) { vectorPageFile(id, index).setLastModified(timestamp) }

    /** 페이지 순서 바꾸기(길게 눌러 드래그) — [order]\[새 위치\] = 그 자리에 와야 할 예전 인덱스.
     *  파일을 직접 맞바꿔서 반영하므로 다른 코드는 그대로 인덱스로 읽기만 하면 된다. 중간에 원본을
     *  덮어쓰지 않도록 전부 임시파일로 옮긴 뒤 최종 위치에 다시 쓴다. */
    fun applyPageOrder(id: String, order: List<Int>) {
        val dir = File(root, id).apply { mkdirs() }
        val temps = order.mapIndexed { newIndex, oldIndex ->
            val src = pageFile(id, oldIndex)
            if (!src.exists()) null else File(dir, "reorder_tmp_$newIndex.png").also { src.copyTo(it, overwrite = true) }
        }
        for (i in order.indices) pageFile(id, i).delete()
        temps.forEachIndexed { newIndex, tmp -> tmp?.let { it.copyTo(pageFile(id, newIndex), overwrite = true); it.delete() } }
    }

    /** 표지 디자인용 갤러리 이미지 — 페이지 그림과 별개로 book 폴더에 한 장만 둔다(없으면 기본색 표지). */
    private fun coverFile(id: String): File {
        val dir = File(root, id).apply { mkdirs() }
        return File(dir, "cover.jpg")
    }

    fun loadCover(id: String): Bitmap? {
        val f = coverFile(id)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** 표지 그리드/캐러셀에 쓸 다운샘플 버전 — 원본 갤러리 사진 그대로 들고 있으면 목록에서 무겁다. */
    fun loadCoverThumb(id: String, reqPx: Int = 400): Bitmap? {
        val f = coverFile(id); if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqPx) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** [bmp]는 미리 표지 비율로 크롭된 상태로 들어온다(호출부의 `cropToCoverAspect` 참고). */
    fun saveCover(id: String, bmp: Bitmap) {
        FileOutputStream(coverFile(id)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bumpCoverVersion(id)
    }

    fun removeCover(id: String) {
        coverFile(id).delete()
        bumpCoverVersion(id)
    }

    /** 표지 파일이 마지막으로 저장된 시각 — [pageUpdatedAt]과 같은 이유로 mtime을 그대로 씀. */
    fun coverUpdatedAt(id: String): Long = coverFile(id).lastModified()

    /** [setPageUpdatedAt]과 같은 이유 — PULL로 받은 표지에 원격 타임스탬프를 찍어 핑퐁을 막는다. */
    fun setCoverUpdatedAt(id: String, timestamp: Long) { coverFile(id).setLastModified(timestamp) }

    fun setCoverColor(id: String, color: Long?) {
        save(list().map { if (it.id == id) it.copy(coverColor = color, updatedAt = System.currentTimeMillis()) else it })
    }

    /** id는 그대로 유지되는 book 갱신이라 `LaunchedEffect(book.id)`만으론 목록 썸네일이 새 표지
     *  이미지를 다시 읽어오지 않는다 — 이 값을 실제로 바꿔서 캐시를 무효화시킨다. */
    private fun bumpCoverVersion(id: String) {
        save(list().map { if (it.id == id) it.copy(coverVersion = it.coverVersion + 1, updatedAt = System.currentTimeMillis()) else it })
    }

    private fun save(books: List<Sketchbook>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("name", it.name).put("size", it.sizeKey)
                .put("bg", it.bgKey).put("createdAt", it.createdAt).put("pages", it.pageCount).put("fav", it.fav)
                .put("shared", it.shared).put("code", it.code ?: "")
                .put("coverColor", it.coverColor ?: Long.MIN_VALUE).put("coverVer", it.coverVersion)
                .put("vector", it.vector)
                .put("vectorInfinite", it.vectorInfinite)
                .put("vectorCanvasW", it.vectorCanvasW ?: -1).put("vectorCanvasH", it.vectorCanvasH ?: -1)
                .put("updatedAt", it.updatedAt))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun newId(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { a[Random.nextInt(a.length)] }.joinToString("")
    }

    companion object { private const val KEY = "books" }
}
