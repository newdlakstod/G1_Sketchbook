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
    /** 벡터 스케치북 여부·페이지(텍스트 그대로, base64 인코딩 없음) — [pages]와 상호 배타적으로
     *  쓰인다: vector=true인 책은 항상 pages가 비어 있고 vectorPages만 쓴다. */
    val vector: Boolean = false,
    val vectorInfinite: Boolean = false,
    val vectorCanvasW: Int? = null,
    val vectorCanvasH: Int? = null,
    /** 벡터 책 하나 = 캔버스 하나이므로 더 이상 페이지 인덱스가 없다. */
    val vectorCanvas: Pair<Long, String>? = null, // (updatedAt, strokes json)
)

/** 공유 스케치북은 실제 그림이 아니라 "이 계정이 이 코드에 참여 중"이라는 사실만 계정 전체에
 *  동기화한다 — 그림 자체는 이미 ShareRepository의 실시간 세션으로 기기와 무관하게 공유되므로,
 *  여기선 다른 기기에도 같은 로컬 카드를 자동으로 만들어주는 데 필요한 정보만 있으면 된다. */
data class RemoteSharedBookRef(
    val code: String, val name: String, val sizeKey: String, val bgKey: String,
    val createdAt: Long, val deleted: Boolean,
)

/** [contentBase64] is the separate stroke-only transparent layer (same file [DiaryRepository.loadContent]
 *  reads/writes locally) — null for days pushed before this existed, or by an older app version. Synced
 *  alongside the composite [image] so "투명 배경 PNG로 다운로드" keeps working after a diary crosses devices,
 *  not just on the device it was drawn on (2026-08-30, was composite-only until now). */
data class RemoteDiaryDay(val updatedAt: Long, val image: String, val contentBase64: String?)

data class RemoteSnapshot(
    val sketchbooks: List<RemoteSketchbook>,
    val diary: Map<String, RemoteDiaryDay>,
    val settings: RemoteSettings?,
    val sharedBooks: List<RemoteSharedBookRef>,
)

data class RemoteSettings(
    val nickname: String?, val themeMode: String, val favoriteColors: List<Long>,
    val gesture2Tap: String, val gesture3Tap: String, val gestureLongPress: String,
    val largeCovers: Boolean, val brushColor: Long,
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
