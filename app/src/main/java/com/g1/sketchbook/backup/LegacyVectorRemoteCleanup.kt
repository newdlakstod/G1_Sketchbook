package com.g1.sketchbook.backup

internal data class LegacyVectorRemoteCleanup(
    val bookIds: Set<String>,
    val removeStampBrushes: Boolean,
)

internal fun legacyVectorRemoteCleanup(
    books: List<RemoteSketchbook>,
    hasStampBrushes: Boolean,
): LegacyVectorRemoteCleanup = LegacyVectorRemoteCleanup(
    bookIds = books.filter { it.legacyVector }.mapTo(linkedSetOf()) { it.id },
    removeStampBrushes = hasStampBrushes,
)

internal fun nonVectorRemoteBooks(books: List<RemoteSketchbook>): List<RemoteSketchbook> =
    books.filterNot { it.legacyVector }
