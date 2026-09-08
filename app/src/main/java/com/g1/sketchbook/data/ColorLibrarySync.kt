package com.g1.sketchbook.data

import com.g1.sketchbook.backup.BackupRepository
import com.g1.sketchbook.backup.RemoteColorLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pairs each [SessionStore.libraries] mutation with a matching [BackupRepository] push, so every
 *  screen that creates/edits/deletes a color library stays in sync without duplicating the push
 *  call — mirrors [com.g1.sketchbook.sketchbook.createSynced] and friends for sketchbooks. [uid]
 *  blank means "not signed in": the local mutation still happens, the push is just skipped.
 *  Pushes run fire-and-forget on [scope], debounced per library id (see [pendingPushJobs]) so a
 *  color-wheel drag or a rename keystroke doesn't push once per sample. Returns the updated
 *  library list so callers can update their own Compose state in the same line. */
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
    // 방금 지운 라이브러리 앞으로 대기 중이던(디바운스된) 색/이름 푸시가 있다면 취소한다 — 안 그러면
    // 그 낡은 푸시가 지금 이 삭제 툼스톤보다 늦게 도착해서 다른 기기에 라이브러리를 되살릴 수 있다.
    pendingPushJobs.remove(id)?.cancel()
    if (uid.isNotBlank()) scope.launch(Dispatchers.IO) { backup.deleteColorLibrary(uid, id, System.currentTimeMillis()) }
    return next
}

/** 라이브러리 id별로 "마지막 예약된 푸시"를 들고 있는 파일 스코프 맵 — 색상휠 드래그(포인터 샘플마다)
 *  나 이름 TextField(키 입력마다) 같은 연타성 편집이 로컬 상태는 매번 즉시 반영하면서도 원격 Firebase
 *  쓰기는 초당 수십~수백 번이 아니라 활동이 멈춘 뒤 한 번만 나가도록 묶는다(DiaryScreens.kt의
 *  pendingCompositeJob과 같은 취소-후-재예약 디바운스 패턴, 2026-09-08). id별로 따로 관리해야
 *  서로 다른 두 라이브러리를 연달아 편집할 때 한쪽의 디바운스가 다른 쪽 대기 중인 푸시를 취소하지
 *  않는다. */
private val pendingPushJobs = mutableMapOf<String, Job>()
private const val PushDebounceMs = 500L

private fun pushLibraryIfSignedIn(scope: CoroutineScope, backup: BackupRepository, uid: String, libraries: List<ColorLibrary>, id: String?) {
    if (uid.isBlank() || id == null) return
    pendingPushJobs[id]?.cancel()
    pendingPushJobs[id] = scope.launch(Dispatchers.IO) {
        delay(PushDebounceMs)
        val lib = libraries.firstOrNull { it.id == id } ?: return@launch
        backup.pushColorLibrary(uid, RemoteColorLibrary(lib.id, lib.name, lib.colors, System.currentTimeMillis(), false))
    }
}
