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
    fun isLocked(date: String): Boolean = date != today()

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
}
