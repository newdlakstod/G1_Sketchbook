package com.g1.sketchbook.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.g1.sketchbook.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

internal fun diaryContentFileName(date: String) = "${date}_content.png"

internal fun diaryDateFromCompositeFile(name: String): String? =
    name.takeIf { it.endsWith(".png") && !it.endsWith("_content.png") }
        ?.removeSuffix(".png")

/**
 * 화면에만 쓸 비파괴 복구본을 만든다. 저장된 합성본은 절대 수정하지 않으며, 큰 가장자리 연결 검정
 * 영역만 종이 픽셀로 바꾼 뒤 별도 필기 레이어를 다시 얹는다. 안전하게 판정할 수 없으면 null이다.
 */
internal fun buildLegacyDiaryPreviewPixels(
    storedComposite: IntArray,
    paperPixels: IntArray,
    contentPixels: IntArray?,
    width: Int,
    height: Int,
): IntArray? {
    if (
        width <= 0 || height <= 0 ||
        storedComposite.size != width * height ||
        paperPixels.size != storedComposite.size ||
        (contentPixels != null && contentPixels.size != storedComposite.size)
    ) return null

    fun isNearBlack(pixel: Int): Boolean {
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        return red <= 56 && green <= 56 && blue <= 56
    }

    val connected = BooleanArray(storedComposite.size)
    val queue = IntArray(storedComposite.size)
    var head = 0
    var tail = 0
    fun enqueue(index: Int) {
        if (!connected[index] && isNearBlack(storedComposite[index])) {
            connected[index] = true
            queue[tail++] = index
        }
    }
    for (x in 0 until width) {
        enqueue(x)
        enqueue((height - 1) * width + x)
    }
    for (y in 1 until height - 1) {
        enqueue(y * width)
        enqueue(y * width + width - 1)
    }

    val rowCounts = IntArray(height)
    while (head < tail) {
        val index = queue[head++]
        val x = index % width
        val y = index / width
        rowCounts[y]++
        if (x > 0) enqueue(index - 1)
        if (x + 1 < width) enqueue(index + 1)
        if (y > 0) enqueue(index - width)
        if (y + 1 < height) enqueue(index + width)
    }
    if (tail < storedComposite.size * 0.15f || rowCounts.none { it >= width * 0.70f }) return null

    val preview = storedComposite.copyOf()
    for (index in preview.indices) {
        if (connected[index]) preview[index] = paperPixels[index]
    }
    contentPixels?.forEachIndexed { index, source ->
        val alpha = source ushr 24 and 0xFF
        if (alpha == 0) return@forEachIndexed
        if (alpha == 0xFF) {
            preview[index] = source
        } else {
            val destination = preview[index]
            val inverse = 0xFF - alpha
            val red = ((source ushr 16 and 0xFF) * alpha + (destination ushr 16 and 0xFF) * inverse + 127) / 255
            val green = ((source ushr 8 and 0xFF) * alpha + (destination ushr 8 and 0xFF) * inverse + 127) / 255
            val blue = ((source and 0xFF) * alpha + (destination and 0xFF) * inverse + 127) / 255
            preview[index] = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
        }
    }
    return preview
}

/**
 * Personal picture-diary: one page per calendar day, stored locally as PNG
 * (filesDir/diary/yyyy-MM-dd.png). Today is editable; past days are locked (the "midnight archive").
 */
class DiaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "diary").apply { mkdirs() }
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val unsafeRecoveryRollbackMarker = File(dir, ".unsafe-black-recovery-rollback-v1")
    private val paperTexture by lazy {
        BitmapFactory.decodeResource(appContext.resources, R.drawable.paper_watercolor)
    }

    fun today(): String = fmt.format(java.util.Date())

    private fun file(date: String) = File(dir, "$date.png")
    private fun contentFile(date: String) = File(dir, diaryContentFileName(date))
    fun hasEntry(date: String) = file(date).exists()
    fun hasContent(date: String) = contentFile(date).exists()

    fun load(date: String): Bitmap? =
        file(date).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun loadContent(date: String): Bitmap? =
        contentFile(date).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** 상세/달력 표시 전용. 원본 파일과 mtime은 건드리지 않는다. */
    fun loadDisplay(date: String, maxSide: Int = 1800): Bitmap? {
        val stored = decodeSampled(file(date), maxSide) ?: return null
        val width = stored.width
        val height = stored.height
        val paper = renderPaper(width, height)
        val loadedContent = decodeSampled(contentFile(date), maxOf(width, height))
        val content = loadedContent?.let {
            if (it.width == width && it.height == height) it
            else Bitmap.createScaledBitmap(it, width, height, true)
        }
        if (content !== loadedContent) loadedContent?.recycle()

        val storedPixels = IntArray(width * height)
        val paperPixels = IntArray(width * height)
        val contentPixels = content?.let { IntArray(width * height) }
        stored.getPixels(storedPixels, 0, width, 0, 0, width, height)
        paper.getPixels(paperPixels, 0, width, 0, 0, width, height)
        contentPixels?.let { content?.getPixels(it, 0, width, 0, 0, width, height) }
        val previewPixels = buildLegacyDiaryPreviewPixels(
            storedPixels, paperPixels, contentPixels, width, height,
        )
        paper.recycle()
        content?.recycle()
        if (previewPixels == null) return stored

        val preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        preview.setPixels(previewPixels, 0, width, 0, 0, width, height)
        stored.recycle()
        return preview
    }

    fun loadThumb(date: String, reqPx: Int = 160): Bitmap? {
        val full = loadDisplay(date, reqPx * 2) ?: return null
        var sample = 1
        while (full.width / (sample * 2) >= reqPx) sample *= 2
        if (sample == 1) return full
        val thumb = Bitmap.createScaledBitmap(full, full.width / sample, full.height / sample, true)
        full.recycle()
        return thumb
    }

    fun save(date: String, bmp: Bitmap) {
        FileOutputStream(file(date)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun saveContent(date: String, bmp: Bitmap) {
        FileOutputStream(contentFile(date)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 로컬에 그림이 있는 모든 날짜("yyyy-MM-dd") — 백업 동기화가 "이 기기에만 있고 아직 클라우드에
     *  안 올라간 일기"를 찾을 때 쓴다(달력 UI는 안 씀, 그건 월 단위로 하루씩 hasEntry로 확인). */
    fun listDates(): List<String> = dir.listFiles()
        ?.mapNotNull { diaryDateFromCompositeFile(it.name) } ?: emptyList()

    /** 해당 날짜 파일이 마지막으로 저장된 시각 — 파일시스템 mtime. 항목이 없으면 0. */
    fun updatedAt(date: String): Long = file(date).lastModified()

    /** PULL로 받아 저장한 일기에 **원격 타임스탬프**를 다시 찍는다 — 안 그러면 방금 저장한 mtime이
     *  "지금"이라 항상 원격보다 최신으로 보여서, 다음 동기화가 곧바로 그걸 되밀어 올린다(핑퐁). */
    fun setUpdatedAt(date: String, timestamp: Long) { file(date).setLastModified(timestamp) }

    fun needsUnsafeRecoveryRollback(): Boolean = !unsafeRecoveryRollbackMarker.exists()

    fun markUnsafeRecoveryRollbackComplete() {
        unsafeRecoveryRollbackMarker.createNewFile()
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun renderPaper(width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFBF6EA.toInt())
        val paper = paperTexture
        val rotate = (paper.width > paper.height) != (width > height)
        val paperWidth = if (rotate) paper.height else paper.width
        val paperHeight = if (rotate) paper.width else paper.height
        val scale = max(width.toFloat() / paperWidth, height.toFloat() / paperHeight)
        val matrix = Matrix().apply {
            postTranslate(-paper.width / 2f, -paper.height / 2f)
            if (rotate) postRotate(90f)
            postScale(scale, scale)
            postTranslate(width / 2f, height / 2f)
        }
        canvas.drawBitmap(paper, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
