package com.g1.sketchbook.data

import com.g1.sketchbook.backup.BackupRepository
import com.g1.sketchbook.backup.RemoteColorLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Pairs each [SessionStore.libraries] mutation with a matching [BackupRepository] push, so every
 *  screen that creates/edits/deletes a color library stays in sync without duplicating the push
 *  call — mirrors [com.g1.sketchbook.sketchbook.createSynced] and friends for sketchbooks. [uid]
 *  blank means "not signed in": the local mutation still happens, the push is just skipped.
 *  Pushes run fire-and-forget on [scope]. Returns the updated library list so callers can update
 *  their own Compose state in the same line. */
fun createLibrarySynced(scope: CoroutineScope, session: SessionStore, backup: BackupRepository, uid: String, name: String): List<ColorLibrary> {
    val next = addLibrary(session.libraries, name)
    session.libraries = next
    pushLibraryIfSignedIn(scope, backup, uid, next, next.lastOrNull()?.id)
    return next
}

fun renameLibrarySynced(scope: CoroutineScope, session: SessionStore, backup: BackupRepository, uid: String, id: String, name: String): List<ColorLibrary> {
    val next = renameLibrary(session.libraries, id, name)
    session.libraries = next
    pushLibraryIfSignedIn(scope, backup, uid, next, id)
    return next
}

fun updateLibraryColorSynced(scope: CoroutineScope, session: SessionStore, backup: BackupRepository, uid: String, id: String, index: Int, color: Long): List<ColorLibrary> {
    val next = updateLibraryColor(session.libraries, id, index, color)
    session.libraries = next
    pushLibraryIfSignedIn(scope, backup, uid, next, id)
    return next
}

/** 툼스톤으로 지운다 — 하드 삭제하면 다른 기기가 "원래 없었음"으로 잘못 읽고 되살린다
 *  ([BackupRepository.deleteColorLibrary]와 동일 이유). 삭제한 라이브러리가 활성 상태였다면
 *  activeLibraryIds에서도 제거한다. */
fun removeLibrarySynced(scope: CoroutineScope, session: SessionStore, backup: BackupRepository, uid: String, id: String): List<ColorLibrary> {
    val next = removeLibrary(session.libraries, id)
    session.libraries = next
    if (id in session.activeLibraryIds) session.activeLibraryIds = session.activeLibraryIds - id
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteColorLibrary(uid, id, System.currentTimeMillis()) }
    return next
}

private fun pushLibraryIfSignedIn(scope: CoroutineScope, backup: BackupRepository, uid: String, libraries: List<ColorLibrary>, id: String?) {
    if (uid.isBlank() || id == null) return
    val lib = libraries.firstOrNull { it.id == id } ?: return
    scope.launch(Dispatchers.IO) {
        backup.pushColorLibrary(uid, RemoteColorLibrary(lib.id, lib.name, lib.colors, System.currentTimeMillis(), false))
    }
}
