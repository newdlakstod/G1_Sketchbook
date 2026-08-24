package com.g1.sketchbook.sketchbook

import android.graphics.Bitmap
import com.g1.sketchbook.backup.BackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pairs each [SketchbookRepository] mutation with a matching [BackupRepository] push, so every
 * screen that creates/edits/deletes a personal sketchbook stays in sync without duplicating the
 * push call — the Home tab's cover-edit dialog and the List tab's both call the same
 * SketchbookRepository mutators independently today. [uid] blank means "not signed in": the local
 * mutation still happens, the push is just skipped. Pushes run fire-and-forget on [scope].
 */
fun createSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, name: String, sizeKey: String, bgKey: String): Sketchbook {
    val book = repo.create(name, sizeKey, bgKey)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.pushSketchbookMeta(uid, book) }
    return book
}

fun renameSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, name: String) {
    repo.rename(id, name)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun toggleFavSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.toggleFav(id)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun setCoverColorSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, color: Long?) {
    repo.setCoverColor(id, color)
    pushMetaIfSignedIn(scope, repo, backup, uid, id)
}

fun saveCoverSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, bmp: Bitmap) {
    repo.saveCover(id, bmp)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) }
        repo.loadCover(id)?.let { backup.pushSketchbookCover(uid, id, it, repo.coverUpdatedAt(id)) }
    }
}

fun removeCoverSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.removeCover(id)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) }
        backup.deleteSketchbookCover(uid, id)
    }
}

fun deleteSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    repo.delete(id)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteSketchbook(uid, id) }
}

fun savePageSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, bookId: String, index: Int, bmp: Bitmap) {
    repo.savePage(bookId, index, bmp)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        backup.pushSketchbookPage(uid, bookId, index, bmp, repo.pageUpdatedAt(bookId, index))
    }
}

private fun pushMetaIfSignedIn(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    if (uid.isBlank()) return
    scope.launch(Dispatchers.IO) { repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) } }
}
