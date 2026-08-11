package com.g1.sketchbook.data

import android.graphics.Bitmap
import android.os.Build
import com.g1.sketchbook.data.model.ArchiveEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream

/**
 * Persists the *finished* daily canvas as a single compressed WebP so storage stays tiny,
 * while the live per-stroke data can be thrown away. This is what makes the gallery permanent
 * ("휘발되지 않는") even though each day's live canvas is cheap and disposable.
 */
class ArchiveRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun saveDaily(roomId: String, date: String, bitmap: Bitmap): ArchiveEntry {
        val scaled = downscale(bitmap, MAX_DIMEN)
        val bytes = compressWebp(scaled, QUALITY)

        val ref = storage.reference.child("archive/$roomId/$date.webp")
        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await().toString()

        val user = auth.currentUser
        val entry = ArchiveEntry(
            date = date,
            url = url,
            savedBy = user?.uid.orEmpty(),
            savedByName = user?.displayName ?: "익명",
            savedAt = System.currentTimeMillis(),
        )
        db.getReference("rooms").child(roomId).child("archive").child(date).setValue(entry).await()
        return entry
    }

    fun observeArchive(roomId: String): Flow<List<ArchiveEntry>> = callbackFlow {
        val ref = db.getReference("rooms").child(roomId).child("archive")
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
        private const val MAX_DIMEN = 1080
        private const val QUALITY = 70
    }
}
