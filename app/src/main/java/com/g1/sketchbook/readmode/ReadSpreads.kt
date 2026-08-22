package com.g1.sketchbook.readmode

/** Sentinel page index meaning "the sketchbook's own cover art", used only in landscape spreads —
 *  real pages are always >= 0. */
const val COVER_PAGE = -1

/** Computes which page indices are shown together in each "spread" of the reader.
 *  - Portrait: one real page per spread.
 *  - Landscape: the cover pairs with page 0, then real pages pair up two at a time — i.e.
 *    "표지-1, 2-3, 4-5, ..." in 1-indexed display terms. If [pageCount] is even, the very last
 *    spread ends up with only one real page (no partner) — shown alone rather than paired with
 *    nothing. Not expected to happen with this app's fixed 15-page sketchbooks, but a plain
 *    [pageCount] of any size should still produce a sane layout. */
fun buildSpreads(pageCount: Int, landscape: Boolean): List<List<Int>> {
    require(pageCount > 0) { "pageCount must be positive" }
    if (!landscape) return (0 until pageCount).map { listOf(it) }
    val spreads = mutableListOf(listOf(COVER_PAGE, 0))
    var i = 1
    while (i < pageCount) {
        val right = i + 1
        spreads += if (right < pageCount) listOf(i, right) else listOf(i)
        i += 2
    }
    return spreads
}

/** Finds which spread contains [page], so read mode can open on the spread the user was already
 *  editing instead of always starting at the beginning. Falls back to the first spread if [page]
 *  isn't in any spread (shouldn't happen in practice, but a safe default beats a crash). */
fun spreadIndexForPage(spreads: List<List<Int>>, page: Int): Int {
    val found = spreads.indexOfFirst { page in it }
    return if (found >= 0) found else 0
}
