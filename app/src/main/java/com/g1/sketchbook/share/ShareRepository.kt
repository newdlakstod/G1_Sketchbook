package com.g1.sketchbook.share

import com.g1.sketchbook.data.await
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.random.Random

/**
 * Real-time "draw together" sessions on Firebase Realtime Database (free tier, no Storage).
 *
 * Layout:
 * ```
 * shareSessions/{CODE}/
 *   host: uid
 *   createdAt: ts
 *   slots/{uid}/  { name, role, currentPage, snapshots/{pageIndex}(base64), updatedAt }
 * ```
 * Each participant keeps their own canvas; we sync a downscaled Base64 snapshot per page whenever
 * that page changes (stroke end or page switch), keyed by page index — so others can browse any of
 * a participant's [com.g1.sketchbook.sketchbook.MAX_PAGES] pages, not just whichever one they're
 * currently on. `currentPage` says which one is "live" (drives the default view). Max [MAX_SLOTS]
 * participants.
 */
class ShareRepository {

    private val root = FirebaseDatabase.getInstance().reference.child("shareSessions")

    /** One participant's latest state within a session. */
    data class Slot(
        val uid: String,
        val name: String,
        val role: String,
        val currentPage: Int,          // page they're actively drawing right now (the "live" view)
        val snapshots: Map<Int, String>, // pageIndex -> Base64 JPEG, only pages they've pushed so far
        val updatedAt: Long,
    )

    data class SessionState(
        val exists: Boolean,
        val slots: List<Slot>,
    )

    /** Creates a fresh session with the caller as host, returning the invite code. */
    suspend fun createSession(uid: String, name: String): String {
        val code = reserveCode()
        val node = root.child(code)
        node.child("host").setValue(uid).await()
        node.child("createdAt").setValue(ServerValue.TIMESTAMP).await()
        writeSlot(code, uid, name, role = "host")
        return code
    }

    /** Joins an existing session as guest. Fails if it doesn't exist or is already full. */
    suspend fun joinSession(code: String, uid: String, name: String): Result<Unit> {
        val normalized = code.trim().uppercase()
        val snap = root.child(normalized).get().await()
        if (!snap.exists()) return Result.failure(IllegalStateException("세션을 찾을 수 없어요. 코드를 확인해 주세요."))
        val slots = snap.child("slots")
        val already = slots.hasChild(uid)
        if (!already && slots.childrenCount >= MAX_SLOTS) return Result.failure(IllegalStateException("세션 정원(${MAX_SLOTS}명)이 가득 찼어요."))
        writeSlot(normalized, uid, name, role = "guest")
        return Result.success(Unit)
    }

    /** Pushes the caller's latest canvas snapshot for [page] (fire-and-forget) — also marks [page]
     *  as their current/"live" page so viewers who haven't pinned a specific page see this one. */
    fun pushSnapshot(code: String, uid: String, page: Int, base64: String) {
        val slot = root.child(code).child("slots").child(uid)
        slot.updateChildren(mapOf(
            "currentPage" to page,
            "snapshots/$page" to base64,
            "updatedAt" to ServerValue.TIMESTAMP,
        ))
    }

    /** Emits the session's participants whenever anything changes. */
    fun observeSession(code: String): Flow<SessionState> = callbackFlow {
        val ref = root.child(code)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val slots = snapshot.child("slots").children.mapNotNull { c ->
                    val uid = c.key ?: return@mapNotNull null
                    val snapshots = c.child("snapshots").children.mapNotNull { sc ->
                        val pageIndex = sc.key?.toIntOrNull() ?: return@mapNotNull null
                        val b64 = sc.getValue(String::class.java) ?: return@mapNotNull null
                        pageIndex to b64
                    }.toMap()
                    Slot(
                        uid = uid,
                        name = c.child("name").getValue(String::class.java) ?: "친구",
                        role = c.child("role").getValue(String::class.java) ?: "guest",
                        currentPage = c.child("currentPage").getValue(Int::class.java) ?: 0,
                        snapshots = snapshots,
                        updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: 0L,
                    )
                }
                trySend(SessionState(exists = snapshot.exists(), slots = slots))
            }
            override fun onCancelled(error: DatabaseError) { /* keep last state */ }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private suspend fun writeSlot(code: String, uid: String, name: String, role: String) {
        root.child(code).child("slots").child(uid).updateChildren(
            mapOf("name" to name, "role" to role, "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    /** Finds an unused 6-char code (skips look-alike characters). */
    private suspend fun reserveCode(): String {
        repeat(8) {
            val code = (1..6).map { ALPHABET.random() }.joinToString("")
            if (!root.child(code).get().await().exists()) return code
        }
        // Extremely unlikely fallback: append randomness.
        return (1..6).map { ALPHABET.random() }.joinToString("") + Random.nextInt(10)
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val MAX_SLOTS = 4
    }
}
