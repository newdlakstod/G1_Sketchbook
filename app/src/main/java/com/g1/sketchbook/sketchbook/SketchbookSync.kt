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
fun createSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, name: String, sizeKey: String, bgKey: String, vector: Boolean = false): Sketchbook {
    val book = repo.create(name, sizeKey, bgKey, vector = vector)
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
        // 표지 파일은 방금 지워져서 coverUpdatedAt은 0이다 — removeCover가 막 갱신한 book 메타의
        // updatedAt을 툼스톤 시각으로 쓴다.
        val book = repo.get(id)
        book?.let { backup.pushSketchbookMeta(uid, it) }
        backup.deleteSketchbookCover(uid, id, book?.updatedAt ?: System.currentTimeMillis())
    }
}

/** 페이지 순서 바꾸기는 파일을 통째로 뒤섞는다(비게 된 인덱스의 파일은 삭제) — 로컬만 바꾸고 끝내면
 *  다른 기기가 다음 동기화 때 "로컬엔 없고 원격엔 있는 페이지"로 보고 옛 그림을 되살려버린다. 그래서
 *  순서를 바꾼 뒤 이 책의 페이지 전체 상태를 다시 올린다(없는 인덱스는 원격에서도 지움). */
fun reorderPagesSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String, order: List<Int>, pageCount: Int) {
    repo.applyPageOrder(id, order)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        for (index in 0 until pageCount) {
            val bmp = repo.loadPage(id, index)
            if (bmp != null) backup.pushSketchbookPage(uid, id, index, bmp, repo.pageUpdatedAt(id, index))
            else backup.deleteSketchbookPage(uid, id, index)
        }
    }
}

// 공유 스케치북은 그림이 아니라 "참여 중" 기록만 있는 별도 계정 동기화 대상이라(reconcileSharedBooks
// 참고), 코드로 그 기록을 툼스톤한다 — 개인 스케치북과 같은 id 기반 tombstone을 쓰면 다른 기기가
// 다음 동기화 때 이 코드를 다시 못 찾아 못 지운다.
fun deleteSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    val book = repo.get(id)
    repo.delete(id)
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) {
        val code = book?.code
        if (book?.shared == true && code != null) backup.deleteSharedBookRef(uid, code) else backup.deleteSketchbook(uid, id)
    }
}

/** repo.savePage는 PNG 인코딩+디스크 쓰기라 매 붓질(onStrokeEnd)마다 호출되면 메인 스레드에서
 *  체감될 만큼 느리다 — 반드시 백그라운드 스레드에서 돌려야 한다(동기화 붙이기 전엔 항상 그랬음). */
fun savePageSynced(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, bookId: String, index: Int, bmp: Bitmap) {
    scope.launch(Dispatchers.IO) {
        repo.savePage(bookId, index, bmp)
        if (uid.isNotBlank()) backup.pushSketchbookPage(uid, bookId, index, bmp, repo.pageUpdatedAt(bookId, index))
    }
}

private fun pushMetaIfSignedIn(scope: CoroutineScope, repo: SketchbookRepository, backup: BackupRepository, uid: String, id: String) {
    if (uid.isBlank()) return
    scope.launch(Dispatchers.IO) { repo.get(id)?.let { backup.pushSketchbookMeta(uid, it) } }
}
