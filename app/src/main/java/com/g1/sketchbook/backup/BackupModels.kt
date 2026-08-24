package com.g1.sketchbook.backup

/** One sketchbook's page/cover/meta as read from Firebase — plain data, no Android/Firebase types,
 *  so [decideSyncAction] can be unit-tested without a device or emulator. */
data class RemoteSketchbook(
    val id: String, val name: String, val sizeKey: String, val bgKey: String,
    val createdAt: Long, val pageCount: Int, val fav: Boolean, val coverColor: Long?,
    val updatedAt: Long, val deleted: Boolean,
    val coverBase64: String?, val coverUpdatedAt: Long?,
    /** 표지 삭제 툼스톤 — 노드를 지우면 "원래 표지가 없었음"과 구분이 안 돼서 다른 기기가 되살린다. */
    val coverRemoved: Boolean = false,
    val pages: Map<Int, Pair<Long, String>>, // index -> (updatedAt, base64)
)

data class RemoteSnapshot(
    val sketchbooks: List<RemoteSketchbook>,
    val diary: Map<String, Pair<Long, String>>, // date -> (updatedAt, base64)
    val settings: RemoteSettings?,
)

data class RemoteSettings(
    val nickname: String?, val themeMode: String, val favoriteColors: List<Long>,
    val gesture2Tap: String, val gesture3Tap: String, val gestureLongPress: String,
    val gridColumns: Int, val brushColor: Long,
    val brushSizes: Map<String, Float>, val brushOpacities: Map<String, Float>,
    val eraserSize: Float, val eraserOpacity: Float, val eraserBlur: Float,
    val avatarBase64: String?, val updatedAt: Long,
)

/** What to do with one synced item (a sketchbook's meta, one page, one cover, one diary day). */
enum class SyncAction { PUSH, PULL, DELETE_LOCAL, NOOP }

/** [localUpdatedAt]/[remoteUpdatedAt] are epoch millis; null means the item doesn't exist on that
 *  side. [remoteDeleted] is only meaningful for sketchbooks (tombstone) — when true every other
 *  field is ignored: the item is deleted locally if we still have it, otherwise left alone. Ties
 *  (equal timestamps) resolve to NOOP — the item is already in sync, so re-uploading it would just
 *  re-encode and re-send identical bytes on every single reconcile pass. */
fun decideSyncAction(localUpdatedAt: Long?, remoteUpdatedAt: Long?, remoteDeleted: Boolean = false): SyncAction {
    if (remoteDeleted) return if (localUpdatedAt != null) SyncAction.DELETE_LOCAL else SyncAction.NOOP
    return when {
        localUpdatedAt == null && remoteUpdatedAt == null -> SyncAction.NOOP
        localUpdatedAt == null -> SyncAction.PULL
        remoteUpdatedAt == null -> SyncAction.PUSH
        remoteUpdatedAt > localUpdatedAt -> SyncAction.PULL
        remoteUpdatedAt == localUpdatedAt -> SyncAction.NOOP
        else -> SyncAction.PUSH
    }
}
