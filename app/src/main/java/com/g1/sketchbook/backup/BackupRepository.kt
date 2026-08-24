package com.g1.sketchbook.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.g1.sketchbook.data.await
import com.g1.sketchbook.sketchbook.MAX_PAGES
import com.g1.sketchbook.sketchbook.Sketchbook
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Google-account backup: personal sketchbooks, diary, and settings synced across a user's devices
 * via Firebase Realtime Database (base64-encoded JPEGs — no Firebase Storage, see
 * docs/superpowers/specs/2026-08-24-google-account-backup-sync-design.md for why). Shared
 * sketchbooks are NOT covered here — those already sync live via ShareRepository.
 */
class BackupRepository {
    private val root = FirebaseDatabase.getInstance().reference.child("backups")

    private fun encode(bmp: Bitmap, maxSide: Int = 1800, quality: Int = 90): String {
        val s = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeImage(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun pushSketchbookMeta(uid: String, book: Sketchbook) {
        root.child(uid).child("sketchbooks").child(book.id).child("meta").setValue(
            mapOf(
                "name" to book.name, "sizeKey" to book.sizeKey, "bgKey" to book.bgKey,
                "createdAt" to book.createdAt, "pageCount" to book.pageCount, "fav" to book.fav,
                "coverColor" to (book.coverColor ?: Long.MIN_VALUE), "updatedAt" to book.updatedAt,
            ),
        )
    }

    fun pushSketchbookCover(uid: String, bookId: String, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover")
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    fun deleteSketchbookCover(uid: String, bookId: String) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover").removeValue()
    }

    fun pushSketchbookPage(uid: String, bookId: String, index: Int, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("pages").child(index.toString())
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    /** Tombstones the book instead of removing the node outright — a hard remove would look like
     *  "never existed" to another device's next pull, which would resurrect it as a brand-new push. */
    fun deleteSketchbook(uid: String, bookId: String) {
        root.child(uid).child("sketchbooks").child(bookId)
            .setValue(mapOf("deleted" to true, "updatedAt" to ServerValue.TIMESTAMP))
    }

    fun pushDiaryDay(uid: String, date: String, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("diary").child(date)
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    /** [avatarBmp] is encoded here (mirrors the other pushX functions taking a raw Bitmap) —
     *  [record].avatarBase64 is a read-direction-only field (populated by [pullAll]), ignored here. */
    fun pushSettings(uid: String, record: RemoteSettings, avatarBmp: Bitmap?) {
        val payload = mutableMapOf<String, Any?>(
            "themeMode" to record.themeMode,
            "favoriteColors" to record.favoriteColors, "gesture2Tap" to record.gesture2Tap,
            "gesture3Tap" to record.gesture3Tap, "gestureLongPress" to record.gestureLongPress,
            "gridColumns" to record.gridColumns, "brushColor" to record.brushColor,
            "brushSizes" to record.brushSizes, "brushOpacities" to record.brushOpacities,
            "eraserSize" to record.eraserSize, "eraserOpacity" to record.eraserOpacity,
            "eraserBlur" to record.eraserBlur, "updatedAt" to record.updatedAt,
        )
        // updateChildren treats an explicit null as "delete this key", so a device that hasn't set a
        // nickname yet would wipe the one already in the cloud — same reason avatarImage is conditional.
        if (record.nickname != null) payload["nickname"] = record.nickname
        if (avatarBmp != null) payload["avatarImage"] = encode(avatarBmp)
        root.child(uid).child("settings").updateChildren(payload)
    }

    suspend fun pullAll(uid: String): RemoteSnapshot {
        val snap = root.child(uid).get().await()

        val sketchbooks = snap.child("sketchbooks").children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            val meta = c.child("meta")
            val pages = c.child("pages").children.mapNotNull { pc ->
                val idx = pc.key?.toIntOrNull() ?: return@mapNotNull null
                val updatedAt = pc.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
                val image = pc.child("image").getValue(String::class.java) ?: return@mapNotNull null
                idx to (updatedAt to image)
            }.toMap()
            RemoteSketchbook(
                id = id,
                name = meta.child("name").getValue(String::class.java) ?: "",
                sizeKey = meta.child("sizeKey").getValue(String::class.java) ?: "a4",
                bgKey = meta.child("bgKey").getValue(String::class.java) ?: "watercolor",
                createdAt = meta.child("createdAt").getValue(Long::class.java) ?: 0L,
                pageCount = meta.child("pageCount").getValue(Int::class.java) ?: MAX_PAGES,
                fav = meta.child("fav").getValue(Boolean::class.java) ?: false,
                coverColor = meta.child("coverColor").getValue(Long::class.java)?.takeIf { it != Long.MIN_VALUE },
                updatedAt = meta.child("updatedAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
                coverBase64 = c.child("cover").child("image").getValue(String::class.java),
                coverUpdatedAt = c.child("cover").child("updatedAt").getValue(Long::class.java),
                pages = pages,
            )
        }

        val diary = snap.child("diary").children.mapNotNull { c ->
            val date = c.key ?: return@mapNotNull null
            val updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
            val image = c.child("image").getValue(String::class.java) ?: return@mapNotNull null
            date to (updatedAt to image)
        }.toMap()

        val s = snap.child("settings")
        val settings = if (!s.exists()) null else RemoteSettings(
            nickname = s.child("nickname").getValue(String::class.java),
            themeMode = s.child("themeMode").getValue(String::class.java) ?: "system",
            favoriteColors = s.child("favoriteColors").children.mapNotNull { it.getValue(Long::class.java) },
            gesture2Tap = s.child("gesture2Tap").getValue(String::class.java) ?: "NONE",
            gesture3Tap = s.child("gesture3Tap").getValue(String::class.java) ?: "NONE",
            gestureLongPress = s.child("gestureLongPress").getValue(String::class.java) ?: "NONE",
            gridColumns = s.child("gridColumns").getValue(Int::class.java) ?: 3,
            brushColor = s.child("brushColor").getValue(Long::class.java) ?: 0xFF1E2D4CL,
            brushSizes = s.child("brushSizes").children.associate {
                (it.key ?: "") to (it.getValue(Double::class.java)?.toFloat() ?: 0f)
            },
            brushOpacities = s.child("brushOpacities").children.associate {
                (it.key ?: "") to (it.getValue(Double::class.java)?.toFloat() ?: 100f)
            },
            eraserSize = s.child("eraserSize").getValue(Double::class.java)?.toFloat() ?: 10f,
            eraserOpacity = s.child("eraserOpacity").getValue(Double::class.java)?.toFloat() ?: 100f,
            eraserBlur = s.child("eraserBlur").getValue(Double::class.java)?.toFloat() ?: 0f,
            avatarBase64 = s.child("avatarImage").getValue(String::class.java),
            updatedAt = s.child("updatedAt").getValue(Long::class.java) ?: 0L,
        )

        return RemoteSnapshot(sketchbooks, diary, settings)
    }
}
