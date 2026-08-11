package com.g1.sketchbook.data

import com.g1.sketchbook.data.model.Member
import com.g1.sketchbook.data.model.RoomMeta
import com.g1.sketchbook.data.model.Stroke
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.random.Random

/** Represents an add/remove of a completed stroke, keyed by its Realtime Database push id. */
sealed interface StrokeEvent {
    data class Added(val key: String, val stroke: Stroke) : StrokeEvent
    data class Removed(val key: String) : StrokeEvent
}

class RoomRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private fun rooms() = db.getReference("rooms")
    private fun room(roomId: String) = rooms().child(roomId)

    private fun dayStrokes(roomId: String, date: String): DatabaseReference =
        room(roomId).child("days").child(date).child("strokes")

    private fun dayLive(roomId: String, date: String): DatabaseReference =
        room(roomId).child("days").child(date).child("live")

    /** Creates a room with a short human-friendly code as its id. Returns the code. */
    suspend fun createRoom(name: String): String {
        val code = generateCode()
        val meta = RoomMeta(
            name = name.ifBlank { "우리 스케치북" },
            createdBy = auth.currentUser?.uid.orEmpty(),
            createdAt = System.currentTimeMillis(),
        )
        room(code).child("meta").setValue(meta).await()
        addSelfAsMember(code)
        return code
    }

    /** Returns true if the room exists (and adds the current user as a member). */
    suspend fun joinRoom(code: String): Boolean {
        val normalized = code.trim().uppercase()
        val snapshot = room(normalized).child("meta").get().await()
        if (!snapshot.exists()) return false
        addSelfAsMember(normalized)
        return true
    }

    suspend fun getRoomName(roomId: String): String? {
        val snapshot = room(roomId).child("meta").child("name").get().await()
        return snapshot.getValue(String::class.java)
    }

    private suspend fun addSelfAsMember(roomId: String) {
        val user = auth.currentUser ?: return
        val member = Member(
            name = user.displayName ?: "익명",
            photoUrl = user.photoUrl?.toString().orEmpty(),
            joinedAt = System.currentTimeMillis(),
        )
        room(roomId).child("meta").child("members").child(user.uid).setValue(member).await()
    }

    fun observeMembers(roomId: String): Flow<List<Member>> = callbackFlow {
        val ref = room(roomId).child("meta").child("members")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.getValue(Member::class.java) })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Streams completed strokes for a given day as they are added/removed. */
    fun observeStrokes(roomId: String, date: String): Flow<StrokeEvent> = callbackFlow {
        val ref = dayStrokes(roomId, date)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val stroke = snapshot.getValue(Stroke::class.java) ?: return
                trySend(StrokeEvent.Added(snapshot.key ?: return, stroke))
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                trySend(StrokeEvent.Removed(snapshot.key ?: return))
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Streams the in-progress strokes of *other* users (uid -> current stroke). */
    fun observeLive(roomId: String, date: String, selfUid: String): Flow<Map<String, Stroke>> = callbackFlow {
        val ref = dayLive(roomId, date)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children
                    .filter { it.key != selfUid }
                    .mapNotNull { child ->
                        child.getValue(Stroke::class.java)?.let { child.key!! to it }
                    }.toMap()
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** One-shot read of a day's finished strokes (used by the daily auto-archiver). */
    suspend fun getStrokesOnce(roomId: String, date: String): List<Stroke> {
        val snapshot = dayStrokes(roomId, date).get().await()
        return snapshot.children.mapNotNull { it.getValue(Stroke::class.java) }
    }

    /** Pushes a finished stroke; returns its key. */
    fun pushStroke(roomId: String, date: String, stroke: Stroke): String {
        val ref = dayStrokes(roomId, date).push()
        ref.setValue(stroke)
        return ref.key!!
    }

    fun removeStroke(roomId: String, date: String, key: String) {
        dayStrokes(roomId, date).child(key).removeValue()
    }

    /** Broadcasts the current (in-progress) stroke so others see it live. */
    fun updateLive(roomId: String, date: String, uid: String, stroke: Stroke) {
        val ref = dayLive(roomId, date).child(uid)
        ref.setValue(stroke)
        // Auto-clean if the user disconnects mid-stroke.
        ref.onDisconnect().removeValue()
    }

    fun clearLive(roomId: String, date: String, uid: String) {
        dayLive(roomId, date).child(uid).removeValue()
    }

    suspend fun clearDay(roomId: String, date: String) {
        room(roomId).child("days").child(date).child("strokes").removeValue().await()
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no confusing chars
        fun generateCode(length: Int = 6): String =
            (1..length).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
