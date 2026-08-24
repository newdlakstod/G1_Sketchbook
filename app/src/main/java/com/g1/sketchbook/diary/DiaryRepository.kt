package com.g1.sketchbook.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Personal picture-diary: one page per calendar day, stored locally as PNG
 * (filesDir/diary/yyyy-MM-dd.png). Today is editable; past days are locked (the "midnight archive").
 */
class DiaryRepository(context: Context) {
    private val dir = File(context.filesDir, "diary").apply { mkdirs() }
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun today(): String = fmt.format(java.util.Date())

    private fun file(date: String) = File(dir, "$date.png")
    fun hasEntry(date: String) = file(date).exists()

    fun load(date: String): Bitmap? =
        file(date).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun loadThumb(date: String, reqPx: Int = 160): Bitmap? {
        val f = file(date); if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqPx) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun save(date: String, bmp: Bitmap) {
        FileOutputStream(file(date)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 로컬에 그림이 있는 모든 날짜("yyyy-MM-dd") — 백업 동기화가 "이 기기에만 있고 아직 클라우드에
     *  안 올라간 일기"를 찾을 때 쓴다(달력 UI는 안 씀, 그건 월 단위로 하루씩 hasEntry로 확인). */
    fun listDates(): List<String> = dir.listFiles { f -> f.name.endsWith(".png") }
        ?.map { it.name.removeSuffix(".png") } ?: emptyList()

    /** 해당 날짜 파일이 마지막으로 저장된 시각 — 파일시스템 mtime. 항목이 없으면 0. */
    fun updatedAt(date: String): Long = file(date).lastModified()
}
