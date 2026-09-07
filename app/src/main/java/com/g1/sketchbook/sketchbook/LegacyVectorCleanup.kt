package com.g1.sketchbook.sketchbook

import android.content.Context
import org.json.JSONArray
import java.io.File

internal data class LegacyVectorBookFilter(
    val retainedJson: String,
    val vectorBookIds: Set<String>,
)

internal fun filterLegacyVectorBooks(raw: String): LegacyVectorBookFilter? = runCatching {
    val source = JSONArray(raw)
    val retained = JSONArray()
    val vectorIds = linkedSetOf<String>()
    for (index in 0 until source.length()) {
        val book = source.getJSONObject(index)
        if (book.opt("vector") == true) {
            book.optString("id").takeIf(::isSafeLegacyBookId)?.let(vectorIds::add)
        } else {
            retained.put(book)
        }
    }
    LegacyVectorBookFilter(retained.toString(), vectorIds)
}.getOrNull()

internal fun isSafeLegacyBookId(id: String): Boolean =
    id.isNotBlank() && id != "." && id != ".." && '/' !in id && '\\' !in id

/** Removes the retired vector mode's local data before normal repositories can expose it. */
internal class LegacyVectorCleanup(private val context: Context) {
    private val booksPrefs = context.getSharedPreferences(BOOKS_PREFS, Context.MODE_PRIVATE)
    private val sketchbookRoot = File(context.filesDir, "sketchbooks")

    fun run() {
        val previousPending = booksPrefs.getStringSet(PENDING_IDS, emptySet()).orEmpty()
            .filterTo(linkedSetOf(), ::isSafeLegacyBookId)
        val filtered = booksPrefs.getString(BOOKS_KEY, null)?.let(::filterLegacyVectorBooks)
        val pending = previousPending + filtered.orEmptyIds()

        if (filtered != null && filtered.retainedJson != booksPrefs.getString(BOOKS_KEY, null)) {
            booksPrefs.edit()
                .putString(BOOKS_KEY, filtered.retainedJson)
                .putStringSet(PENDING_IDS, pending)
                .commit()
        } else if (pending != previousPending) {
            booksPrefs.edit().putStringSet(PENDING_IDS, pending).commit()
        }

        val failed = pending.filterTo(linkedSetOf()) { id ->
            val directory = File(sketchbookRoot, id)
            directory.exists() && !directory.deleteRecursively()
        }
        booksPrefs.edit().putStringSet(PENDING_IDS, failed).apply()

        File(context.filesDir, "vector_brushes").deleteRecursively()
        File(context.filesDir, "vector_brushes_v2").deleteRecursively()
        context.getSharedPreferences(STAMP_BRUSH_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun LegacyVectorBookFilter?.orEmptyIds(): Set<String> = this?.vectorBookIds.orEmpty()

    private companion object {
        const val BOOKS_PREFS = "g1_sketchbooks"
        const val BOOKS_KEY = "books"
        const val PENDING_IDS = "retired_vector_book_ids"
        const val STAMP_BRUSH_PREFS = "g1_stamp_brushes"
    }
}
