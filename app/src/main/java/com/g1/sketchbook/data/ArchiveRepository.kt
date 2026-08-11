package com.g1.sketchbook.data

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.g1.sketchbook.data.model.ArchiveEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream

/**
 * Persists the *finished* daily canvas as a single compressed WebP so each day becomes a
 * permanent gallery snapshot even though the live per-stroke data is cheap and disposable.
 *
 * The snapshot is Base64-encoded and stored directly in Realtime Database — this keeps the
 * whole app on Firebase's free (Spark) plan, which no longer allows Cloud Storage. Images are
 * downscaled + compressed hard so each entry stays small (~tens of KB).
 */
class ArchiveRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private fun archiveRef(roomId: String) =
        db.getReference("rooms").child(roomId).child("archive")

    /** True if this day has already been archived (used to avoid duplicate auto-archiving). */
    suspend fun hasArchive(roomId: String, date: String): Boolean =
        archiveRef(roomId).child(date).get().await().exists()

    suspend fun saveDaily(roomId: String, date: String, bitmap: Bitmap): ArchiveEntry {
        val scaled = downscale(bitmap, MAX_DIMEN)
        val bytes = compressWebp(scaled, QUALITY)
        val image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val user = auth.currentUser
        val entry = ArchiveEntry(
            date = date,
            image = image,
            savedBy = user?.uid.orEmpty(),
            savedByName = user?.displayName ?: "익명",
            savedAt = System.currentTimeMillis(),
        )
        archiveRef(roomId).child(date).setValue(entry).await()
        return entry
    }

    fun observeArchive(roomId: String): Flow<List<ArchiveEntry>> = callbackFlow {
        val ref = archiveRef(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children
                    .mapNotNull { it.getValue(ArchiveEntry::class.java) }
                    .sortedByDescending { it.date }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun downscale(bmp: Bitmap, maxDimen: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= maxDimen) return bmp
        val ratio = maxDimen.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun compressWebp(bmp: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        bmp.compress(format, quality, out)
        return out.toByteArray()
    }

    companion object {
        // Kept modest so the Base64 payload in Realtime Database stays small.
        private const val MAX_DIMEN = 720
        private const val QUALITY = 60
    }
}
