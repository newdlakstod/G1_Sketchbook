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
    private val root by lazy { FirebaseDatabase.getInstance().reference.child("backups") }

    /** [preserveAlpha]는 스케치북 페이지 전용 — exportContent()로 만든 페이지는 종이 없이 필기만
     *  투명 배경으로 저장되는데, JPEG은 알파 채널이 없어 투명한 곳이 검은색으로 눌러 붙는다(태블릿↔폰
     *  전환 시 배경이 검게 보이던 원인). 표지·일기·아바타는 원래부터 불투명이라 지금처럼 JPEG로 용량을 아낀다. */
    private fun encode(bmp: Bitmap, maxSide: Int = 1800, quality: Int = 90, preserveAlpha: Boolean = false): String {
        val s = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        if (preserveAlpha) scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        else scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
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
                "vector" to book.vector, "vectorInfinite" to book.vectorInfinite,
                "vectorCanvasW" to (book.vectorCanvasW ?: -1), "vectorCanvasH" to (book.vectorCanvasH ?: -1),
            ),
        )
    }

    fun pushSketchbookCover(uid: String, bookId: String, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover")
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp)))
    }

    /** Marks the cover removed instead of deleting the node — same reason [deleteSketchbook] leaves a
     *  tombstone: a hard remove is indistinguishable from "never had a cover" on another device's
     *  next pull, which would push its own stale cover back and resurrect it. */
    fun deleteSketchbookCover(uid: String, bookId: String, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("cover")
            .setValue(mapOf("removed" to true, "updatedAt" to updatedAt))
    }

    fun pushSketchbookPage(uid: String, bookId: String, index: Int, bmp: Bitmap, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("pages").child(index.toString())
            .setValue(mapOf("updatedAt" to updatedAt, "image" to encode(bmp, preserveAlpha = true)))
    }

    /** 벡터 캔버스는 이미 텍스트(JSON)라 base64 인코딩 없이 그대로 올린다 — 이미지보다 훨씬
     *  가볍다. [pushSketchbookPage]와 나란한 벡터 전용 경로. 책 하나당 캔버스 하나라 인덱스가 없다. */
    fun pushVectorCanvas(uid: String, bookId: String, strokesJson: String, updatedAt: Long) {
        root.child(uid).child("sketchbooks").child(bookId).child("vectorCanvas")
            .setValue(mapOf("updatedAt" to updatedAt, "strokes" to strokesJson))
    }

    /** No tombstone needed here (unlike [deleteSketchbookCover]): the only caller is a page reorder,
     *  which immediately re-pushes the full new page set from the same device in the same operation —
     *  there's no window for another device to see a bare absence and misread it. */
    fun deleteSketchbookPage(uid: String, bookId: String, index: Int) {
        root.child(uid).child("sketchbooks").child(bookId).child("pages").child(index.toString()).removeValue()
    }

    /** Tombstones the book instead of removing the node outright — a hard remove would look like
     *  "never existed" to another device's next pull, which would resurrect it as a brand-new push. */
    fun deleteSketchbook(uid: String, bookId: String) {
        root.child(uid).child("sketchbooks").child(bookId)
            .setValue(mapOf("deleted" to true, "updatedAt" to ServerValue.TIMESTAMP))
    }

    /** 공유 스케치북 참여 사실을 계정에 기록 — 그림 데이터는 안 실음(ShareRepository가 이미 실시간
     *  공유). 다른 기기의 다음 동기화가 이 [code]로 로컬 카드를 자동으로 만들어 볼 수 있게 해준다. */
    fun pushSharedBookRef(uid: String, code: String, name: String, sizeKey: String, bgKey: String, createdAt: Long) {
        root.child(uid).child("sharedBooks").child(code)
            .setValue(mapOf("name" to name, "sizeKey" to sizeKey, "bgKey" to bgKey, "createdAt" to createdAt, "deleted" to false))
    }

    /** 툼스톤 — 하드 삭제하면 "원래 없었음"과 구분이 안 돼서 다른 기기가 되살린다(다른 delete* 함수와 동일 이유). */
    fun deleteSharedBookRef(uid: String, code: String) {
        root.child(uid).child("sharedBooks").child(code).setValue(mapOf("deleted" to true))
    }

    /** [contentBmp] is the separate stroke-only layer ([DiaryRepository.loadContent]) — null for a
     *  diary day that predates that feature (or hasn't been redrawn since), same as the local file
     *  possibly not existing. Pushed with [preserveAlpha]=true (like sketchbook pages) since it's a
     *  transparent PNG, not the opaque composite. */
    fun pushDiaryDay(uid: String, date: String, bmp: Bitmap, updatedAt: Long, contentBmp: Bitmap? = null) {
        val payload = mutableMapOf<String, Any?>("updatedAt" to updatedAt, "image" to encode(bmp))
        if (contentBmp != null) payload["content"] = encode(contentBmp, preserveAlpha = true)
        root.child(uid).child("diary").child(date).setValue(payload)
    }

    /** [avatarBmp] is encoded here (mirrors the other pushX functions taking a raw Bitmap) —
     *  [record].avatarBase64 is a read-direction-only field (populated by [pullAll]), ignored here. */
    fun pushSettings(uid: String, record: RemoteSettings, avatarBmp: Bitmap?) {
        val payload = mutableMapOf<String, Any?>(
            "themeMode" to record.themeMode,
            "favoriteColors" to record.favoriteColors, "gesture2Tap" to record.gesture2Tap,
            "gesture3Tap" to record.gesture3Tap, "gestureLongPress" to record.gestureLongPress,
            "largeCovers" to record.largeCovers, "brushColor" to record.brushColor,
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
            val vectorCanvas = c.child("vectorCanvas").let { vc ->
                val updatedAt = vc.child("updatedAt").getValue(Long::class.java)
                val strokes = vc.child("strokes").getValue(String::class.java)
                if (updatedAt != null && strokes != null) updatedAt to strokes else null
            }
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
                coverRemoved = c.child("cover").child("removed").getValue(Boolean::class.java) ?: false,
                pages = pages,
                vector = meta.child("vector").getValue(Boolean::class.java) ?: false,
                vectorInfinite = meta.child("vectorInfinite").getValue(Boolean::class.java) ?: false,
                vectorCanvasW = meta.child("vectorCanvasW").getValue(Int::class.java)?.takeIf { it > 0 },
                vectorCanvasH = meta.child("vectorCanvasH").getValue(Int::class.java)?.takeIf { it > 0 },
                vectorCanvas = vectorCanvas,
            )
        }

        val diary = snap.child("diary").children.mapNotNull { c ->
            val date = c.key ?: return@mapNotNull null
            val updatedAt = c.child("updatedAt").getValue(Long::class.java) ?: return@mapNotNull null
            val image = c.child("image").getValue(String::class.java) ?: return@mapNotNull null
            val content = c.child("content").getValue(String::class.java)
            date to RemoteDiaryDay(updatedAt, image, content)
        }.toMap()

        val s = snap.child("settings")
        val settings = if (!s.exists()) null else RemoteSettings(
            nickname = s.child("nickname").getValue(String::class.java),
            themeMode = s.child("themeMode").getValue(String::class.java) ?: "system",
            favoriteColors = s.child("favoriteColors").children.mapNotNull { it.getValue(Long::class.java) },
            gesture2Tap = s.child("gesture2Tap").getValue(String::class.java) ?: "NONE",
            gesture3Tap = s.child("gesture3Tap").getValue(String::class.java) ?: "NONE",
            gestureLongPress = s.child("gestureLongPress").getValue(String::class.java) ?: "NONE",
            largeCovers = s.child("largeCovers").getValue(Boolean::class.java) ?: true,
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

        val sharedBooks = snap.child("sharedBooks").children.mapNotNull { c ->
            val code = c.key ?: return@mapNotNull null
            RemoteSharedBookRef(
                code = code,
                name = c.child("name").getValue(String::class.java) ?: "공유 스케치북",
                sizeKey = c.child("sizeKey").getValue(String::class.java) ?: "a4",
                bgKey = c.child("bgKey").getValue(String::class.java) ?: "watercolor",
                createdAt = c.child("createdAt").getValue(Long::class.java) ?: 0L,
                deleted = c.child("deleted").getValue(Boolean::class.java) ?: false,
            )
        }

        return RemoteSnapshot(sketchbooks, diary, settings, sharedBooks)
    }
}
