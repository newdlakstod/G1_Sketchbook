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
 * JPEG 변환으로 투명 배경이 검게 굳은 레거시 다이어리인지 찾고, 복구할 픽셀만 표시한다.
 *
 * 정상 그림의 검은 선까지 지우지 않도록 다음 조건을 모두 만족할 때만 손상으로 본다.
 * 1. 근검정 픽셀이 이미지 바깥 가장자리와 연결되어 있다.
 * 2. 그 연결 영역이 전체 픽셀의 15% 이상이다.
 * 3. 적어도 한 행에서 폭의 70% 이상을 덮는 넓은 검정 띠가 있다.
 */
internal fun edgeConnectedBlackCorruptionMask(
    pixels: IntArray,
    width: Int,
    height: Int,
): BooleanArray? {
    if (width <= 0 || height <= 0 || pixels.size != width * height) return null

    fun isNearBlack(pixel: Int): Boolean {
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        return red <= 56 && green <= 56 && blue <= 56
    }

    val connected = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    var head = 0
    var tail = 0

    fun enqueue(index: Int) {
        if (!connected[index] && isNearBlack(pixels[index])) {
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

    val largeArea = tail >= pixels.size * 0.15f
    val wideBand = rowCounts.any { it >= width * 0.70f }
    return connected.takeIf { largeArea && wideBand }
}

/**
 * Personal picture-diary: one page per calendar day, stored locally as PNG
 * (filesDir/diary/yyyy-MM-dd.png). Today is editable; past days are locked (the "midnight archive").
 */
class DiaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "diary").apply { mkdirs() }
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val recoveryLock = Any()
    private val recoveryChecked = mutableSetOf<String>()
    private val paperTexture by lazy {
        BitmapFactory.decodeResource(appContext.resources, R.drawable.paper_watercolor)
    }

    fun today(): String = fmt.format(java.util.Date())

    private fun file(date: String) = File(dir, "$date.png")
    private fun contentFile(date: String) = File(dir, diaryContentFileName(date))
    fun hasEntry(date: String) = file(date).exists()
    fun hasContent(date: String) = contentFile(date).exists()

    fun load(date: String): Bitmap? = synchronized(recoveryLock) {
        val source = decodeMutable(file(date)) ?: return@synchronized null
        if (!recoveryChecked.add(date)) return@synchronized source
        recoverLegacyBlackComposite(date, source)
    }

    fun loadContent(date: String): Bitmap? =
        contentFile(date).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun loadThumb(date: String, reqPx: Int = 160): Bitmap? {
        val f = file(date); if (!f.exists()) return null
        ensureLegacyCompositeRecovered(date)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqPx) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
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

    private fun decodeMutable(file: File): Bitmap? =
        file.takeIf { it.exists() }?.let {
            BitmapFactory.decodeFile(it.absolutePath, BitmapFactory.Options().apply { inMutable = true })
        }

    /** 달력 썸네일은 원래 축소 디코드만 하지만, 아직 검사하지 않은 레거시 파일은 처음 한 번 원본을
     * 읽어 복구·재저장한 뒤 축소본을 만든다. 복구된 파일의 mtime이 갱신되어 다음 계정 동기화에서
     * 검은 원격 사본도 정상 PNG로 덮어쓴다. */
    private fun ensureLegacyCompositeRecovered(date: String) {
        synchronized(recoveryLock) {
            if (date in recoveryChecked) return
            val source = decodeMutable(file(date)) ?: return
            recoveryChecked.add(date)
            val recovered = recoverLegacyBlackComposite(date, source)
            recovered.recycle()
        }
    }

    private fun recoverLegacyBlackComposite(date: String, source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val mask = edgeConnectedBlackCorruptionMask(pixels, source.width, source.height) ?: return source

        val content = decodeMutable(contentFile(date))
        val matchingContent = content?.takeIf {
            it.width == source.width && it.height == source.height
        }
        val repaired = renderPaper(source.width, source.height)
        if (matchingContent != null) {
            // 투명 그림 레이어가 남아 있으면 배경을 다시 합성하는 것이 가장 정확하며,
            // 검은 선도 손상 배경과 구분할 필요 없이 그대로 보존된다.
            Canvas(repaired).drawBitmap(matchingContent, 0f, 0f, null)
        } else {
            // 레이어가 없던 구버전 파일은 손상으로 확정된 근검정 픽셀만 투명하게 만든다.
            for (index in pixels.indices) {
                if (mask[index]) pixels[index] = 0
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            Canvas(repaired).drawBitmap(source, 0f, 0f, null)
        }
        content?.recycle()
        source.recycle()
        save(date, repaired)
        return repaired
    }

    /** BrushView와 같은 cover-fit 규칙으로 종이 질감을 그려, 복구한 썸네일과 편집 화면의 종이가
     * 서로 다르게 보이지 않게 한다. */
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
