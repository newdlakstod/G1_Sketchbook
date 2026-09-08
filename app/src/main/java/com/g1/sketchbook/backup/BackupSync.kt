package com.g1.sketchbook.backup

import android.content.Context
import com.g1.sketchbook.brush.BrushType
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.data.ColorLibrary
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.diary.DiaryRepository
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository

/** Runs one full reconcile pass: pulls everything from the cloud, compares against local state
 *  item-by-item via [decideSyncAction], and applies whichever side is newer. Called on sign-in and
 *  whenever the app returns to the foreground (see MainActivity's lifecycle observer) — this is
 *  what makes phone/tablet sync "automatic" without a manual button. */
suspend fun reconcileBackup(context: Context, uid: String, backup: BackupRepository) {
    val sketchbookRepo = SketchbookRepository(context)
    val diaryRepo = DiaryRepository(context)
    val session = SessionStore(context)
    backup.removeLegacyVectorData(uid)
    val remote = backup.pullAll(uid)

    reconcileSketchbooks(sketchbookRepo, backup, uid, nonVectorRemoteBooks(remote.sketchbooks))
    reconcileDiary(diaryRepo, backup, uid, remote.diary)
    reconcileSettings(session, backup, uid, remote.settings)
    reconcileSharedBooks(sketchbookRepo, backup, uid, remote.sharedBooks)
    reconcileColorLibraries(session, backup, uid, remote.colorLibraries)
}

/** 공유 스케치북은 그림이 아니라 "참여 중"이라는 사실만 동기화한다(계정의 다른 기기에 같은 코드의
 *  로컬 카드를 자동으로 만들어줌) — 실제 그림은 이미 ShareRepository의 실시간 세션이 기기와 무관하게
 *  공유한다. 코드가 키라 last-write-wins 타임스탬프 비교가 필요 없고, 존재/툼스톤 두 상태만 있다. */
private fun reconcileSharedBooks(repo: SketchbookRepository, backup: BackupRepository, uid: String, remote: List<RemoteSharedBookRef>) {
    val local = repo.list().filter { it.shared && it.code != null }
    val remoteByCode = remote.associateBy { it.code }
    // 이번 패스에서 툼스톤 때문에 방금 지운 코드는 아래 두 번째 루프에서 다시 안 올린다 — local은
    // 지우기 전 스냅샷이라 그대로 두면 방금 지운 걸 바로 되살려 올리게 된다.
    val justDeleted = mutableSetOf<String>()

    for (ref in remote) {
        val existing = local.firstOrNull { it.code == ref.code }
        if (ref.deleted) {
            if (existing != null) { repo.delete(existing.id); justDeleted += ref.code }
        } else if (existing == null) {
            repo.create(ref.name, ref.sizeKey, ref.bgKey, shared = true, code = ref.code)
        }
    }
    for (book in local) {
        val code = book.code ?: continue
        if (code in justDeleted) continue
        val ref = remoteByCode[code]
        if (ref == null || ref.deleted) backup.pushSharedBookRef(uid, code, book.name, book.sizeKey, book.bgKey, book.createdAt)
    }
}

/** [SessionStore.libraries]와 원격 `colorLibraries`를 맞춘다 — [reconcileSharedBooks]와 같은
 *  툼스톤 방식: 원격에만 있고 로컬에 없으면 받아서 추가, 원격에서 지워졌으면(deleted=true) 로컬
 *  에서도 삭제, 양쪽에 다 있는데 이름/색이 다르면(다른 기기에서 수정 후 푸시된 경우) 원격 내용으로
 *  덮어쓴다 — 모든 편집이 [com.g1.sketchbook.data.ColorLibrarySync]를 통해 즉시(디바운스 후) 푸시
 *  되므로 원격을 신뢰해도 안전하다, 로컬에만 있거나(또는 원격이 이미 지운 걸 로컬은 아직 갖고
 *  있으면) 원격에 올린다. */
private fun reconcileColorLibraries(session: SessionStore, backup: BackupRepository, uid: String, remote: List<RemoteColorLibrary>) {
    val local = session.libraries
    val remoteById = remote.associateBy { it.id }
    val localIds = local.map { it.id }.toSet()
    var next = local

    for (r in remote) {
        if (r.deleted) {
            if (r.id in localIds) next = next.filter { it.id != r.id }
        } else if (r.id !in localIds) {
            next = next + ColorLibrary(r.id, r.name, r.colors)
        } else {
            next = next.map { if (it.id == r.id) ColorLibrary(r.id, r.name, r.colors) else it }
        }
    }
    if (next != local) session.libraries = next

    // 원격 삭제가 방금 위에서 반영됐다면(next에서 사라짐) activeLibraryIds에 남은 참조도 같이 정리
    // 한다 — 안 그러면 라이브러리 관리 팝업의 "N/3 선택됨" 카운트가 다음 수동 토글 전까지 틀어진다.
    val prunedActive = session.activeLibraryIds.filter { id -> next.any { it.id == id } }
    if (prunedActive != session.activeLibraryIds) session.activeLibraryIds = prunedActive

    for (library in next) {
        val r = remoteById[library.id]
        if (r == null || r.deleted) {
            backup.pushColorLibrary(uid, RemoteColorLibrary(library.id, library.name, library.colors, System.currentTimeMillis(), false))
        }
    }
}

private fun reconcileSketchbooks(repo: SketchbookRepository, backup: BackupRepository, uid: String, remote: List<RemoteSketchbook>) {
    val local = repo.list().filter { !it.shared }
    val remoteById = remote.associateBy { it.id }
    val allIds = (local.map { it.id } + remote.map { it.id }).toSet()

    for (id in allIds) {
        val l = local.firstOrNull { it.id == id }
        val r = remoteById[id]

        when (decideSyncAction(l?.updatedAt, r?.updatedAt, r?.deleted ?: false)) {
            SyncAction.DELETE_LOCAL -> repo.delete(id)
            SyncAction.PULL -> if (r != null) {
                repo.upsert(Sketchbook(id, r.name, r.sizeKey, r.bgKey, r.createdAt, r.pageCount, r.fav,
                    coverColor = r.coverColor, updatedAt = r.updatedAt))
            }
            SyncAction.PUSH -> if (l != null) backup.pushSketchbookMeta(uid, l)
            SyncAction.NOOP -> {}
        }
        if (r?.deleted == true) continue // just tombstoned/deleted locally above — nothing else to sync for it

        // mtime > 0 은 파일 존재 확인 그 자체다(없으면 File.lastModified()가 0) — 있는지 보려고
        // 표지 비트맵을 통째로 디코드할 이유가 없다.
        val localCoverAt = repo.coverUpdatedAt(id).takeIf { it > 0L }
        when (decideSyncAction(localCoverAt, r?.coverUpdatedAt, r?.coverRemoved ?: false)) {
            SyncAction.DELETE_LOCAL -> repo.removeCover(id)
            // 받아온 표지 파일의 mtime을 원격 타임스탬프로 되돌려 찍는다 — 안 그러면 "방금 저장 =
            // 지금"이라 다음 동기화가 이걸 곧바로 되밀어 올린다(핑퐁 + 매번 JPEG 재인코딩).
            SyncAction.PULL -> if (r?.coverBase64 != null && r.coverUpdatedAt != null) {
                backup.decodeImage(r.coverBase64)?.let { repo.saveCover(id, it); repo.setCoverUpdatedAt(id, r.coverUpdatedAt) }
            }
            SyncAction.PUSH -> repo.loadCover(id)?.let { backup.pushSketchbookCover(uid, id, it, repo.coverUpdatedAt(id)) }
            SyncAction.NOOP -> {}
        }

        val pageCount = maxOf(l?.pageCount ?: 0, r?.pageCount ?: MAX_PAGES)
        for (index in 0 until pageCount) {
            val localPageAt = repo.pageUpdatedAt(id, index).takeIf { it > 0L }
            val remotePage = r?.pages?.get(index)
            when (decideSyncAction(localPageAt, remotePage?.first)) {
                SyncAction.PULL -> if (remotePage != null) {
                    backup.decodeImage(remotePage.second)?.let { repo.savePage(id, index, it); repo.setPageUpdatedAt(id, index, remotePage.first) }
                }
                SyncAction.PUSH -> repo.loadPage(id, index)?.let { backup.pushSketchbookPage(uid, id, index, it, repo.pageUpdatedAt(id, index)) }
                else -> {}
            }
        }
    }
}

private fun reconcileDiary(repo: DiaryRepository, backup: BackupRepository, uid: String, remote: Map<String, RemoteDiaryDay>) {
    val allDates = (repo.listDates() + remote.keys).toSet()
    val rollbackUnsafeRecovery = repo.needsUnsafeRecoveryRollback()
    val today = repo.today()
    for (date in allDates) {
        val localAt = if (repo.hasEntry(date)) repo.updatedAt(date) else null
        val remoteDay = remote[date]
        when (decideDiarySyncAction(localAt, remoteDay?.updatedAt, rollbackUnsafeRecovery, date < today)) {
            // 합성 이미지(image)와 별도 필기 레이어(content)를 같이 당겨온다 — content가 없으면(옛
            // 기기가 이 기능 이전 버전으로 올렸거나 그 기기에서도 아직 없던 날) 그냥 건너뛴다. 예전엔
            // content를 아예 동기화 안 해서, 폰에서 그린 오늘 일기를 태블릿에서 열면 합성 이미지만
            // 받아오고(hasEntry=true) content는 영영 안 생겨서(hasContent=false) "투명 배경 PNG로
            // 다운로드"가 그 기기에서는 항상 "기존 일기는 지원 안 해요" 오류만 떴다(2026-08-30,
            // "오늘일기 저장하기에서 투명배경 저장 계속 오류" 리포트로 원인 확정).
            SyncAction.PULL -> if (remoteDay != null) {
                backup.decodeImage(remoteDay.image)?.let { repo.save(date, it); repo.setUpdatedAt(date, remoteDay.updatedAt) }
                remoteDay.contentBase64?.let { b64 -> backup.decodeImage(b64)?.let { repo.saveContent(date, it) } }
            }
            SyncAction.PUSH -> repo.load(date)?.let { backup.pushDiaryDay(uid, date, it, repo.updatedAt(date), repo.loadContent(date)) }
            SyncAction.DELETE_LOCAL, SyncAction.NOOP -> {} // diary has no delete feature — tombstones never occur
        }
    }
    if (rollbackUnsafeRecovery) repo.markUnsafeRecoveryRollbackComplete()
}

/** Settings always "exist" locally (SessionStore has defaults from the start), so unlike sketchbook
 *  pages/covers there's no PUSH-if-local-exists-and-remote-doesn't case to weigh — this is simpler
 *  than [decideSyncAction]: seed the cloud on first ever sync, otherwise pull only if the cloud is
 *  strictly newer than what we last synced from (frequent local changes — brush size, gestures —
 *  are pushed by the ON_STOP catch-all in MainActivity, not tracked field-by-field here). */
private fun reconcileSettings(session: SessionStore, backup: BackupRepository, uid: String, remote: RemoteSettings?) {
    if (remote == null) {
        syncSettingsUp(session, backup, uid)
    } else if (remote.updatedAt > session.settingsSyncedAt) {
        applyRemoteSettings(session, backup, remote)
    }
}

/** Pushes the current settings snapshot (including the avatar image, if any) and records the sync
 *  point. Called both by [reconcileSettings] and directly by RootViewModel/MainActivity whenever a
 *  setting changes that should sync right away (theme, avatar) or when the app backgrounds. */
fun syncSettingsUp(session: SessionStore, backup: BackupRepository, uid: String) {
    val now = System.currentTimeMillis()
    val record = RemoteSettings(
        nickname = session.nickname, themeMode = session.themeMode,
        activeLibraryIds = session.activeLibraryIds, quickFavorites = session.quickFavorites,
        gesture2Tap = session.twoFingerTapAction.name, gesture3Tap = session.threeFingerTapAction.name,
        gestureLongPress = session.longPressAction.name, largeCovers = session.largeCovers,
        brushColor = session.brushColor,
        brushSizes = BrushType.entries.associate { it.name to session.brushSize(it) },
        brushOpacities = BrushType.entries.associate { it.name to session.brushOpacity(it) },
        eraserSize = session.eraserSize, eraserOpacity = session.eraserOpacity, eraserBlur = session.eraserBlur,
        avatarBase64 = null, updatedAt = now,
    )
    backup.pushSettings(uid, record, session.loadAvatarImage())
    session.settingsSyncedAt = now
}

private fun applyRemoteSettings(session: SessionStore, backup: BackupRepository, r: RemoteSettings) {
    if (r.nickname != null) session.nickname = r.nickname
    session.themeMode = r.themeMode
    // 원격 activeLibraryIds가 비어있는 건 "사용자가 정말 0개를 골랐음"과 "이 기능 이전 버전이
    // 마지막으로 쓴 계정이라 이 키 자체가 없었음"을 구분할 수 없다 — 후자인 채로 덮어쓰면 새 기기/
    // 재설치가 팔레트를 통째로 비운 채 시작한다(quickFavorites가 바로 아래서 이미 같은 이유로
    // size 체크하는 것과 동일한 방어). 빈 값은 무시하고 로컬 기본값을 유지한다.
    if (r.activeLibraryIds.isNotEmpty()) session.activeLibraryIds = r.activeLibraryIds
    if (r.quickFavorites.size == SessionStore.QuickFavoritesCount) session.quickFavorites = r.quickFavorites
    session.twoFingerTapAction = runCatching { GestureAction.valueOf(r.gesture2Tap) }.getOrDefault(GestureAction.NONE)
    session.threeFingerTapAction = runCatching { GestureAction.valueOf(r.gesture3Tap) }.getOrDefault(GestureAction.NONE)
    session.longPressAction = runCatching { GestureAction.valueOf(r.gestureLongPress) }.getOrDefault(GestureAction.NONE)
    session.largeCovers = r.largeCovers
    session.brushColor = r.brushColor
    BrushType.entries.forEach { t ->
        r.brushSizes[t.name]?.let { session.setBrushSize(t, it) }
        r.brushOpacities[t.name]?.let { session.setBrushOpacity(t, it) }
    }
    session.eraserSize = r.eraserSize
    session.eraserOpacity = r.eraserOpacity
    session.eraserBlur = r.eraserBlur
    r.avatarBase64?.let { b64 -> backup.decodeImage(b64)?.let { session.saveAvatarImage(it) } }
    session.settingsSyncedAt = r.updatedAt
}
