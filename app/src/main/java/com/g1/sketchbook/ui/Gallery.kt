package com.g1.sketchbook.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** PNG 한 장을 기기 갤러리(Pictures/G1Sketchbook)에 저장 — 다이어리 다운로드, 스케치북 올가미 선택
 *  영역 저장 등 여러 화면이 공유해서 쓴다. 반환값은 그대로 Toast에 띄우는 사용자용 결과 문구. */
fun saveToGallery(ctx: Context, bmp: Bitmap, name: String): String = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/G1Sketchbook")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "갤러리에 저장했어요 ✨"
        } else "저장 실패"
    } else {
        val dir = java.io.File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "G1Sketchbook").apply { mkdirs() }
        val f = java.io.File(dir, "$name.png")
        java.io.FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "저장됨: ${f.absolutePath}"
    }
} catch (e: Exception) { "저장 실패: ${e.message}" }
