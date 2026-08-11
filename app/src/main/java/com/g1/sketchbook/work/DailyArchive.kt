package com.g1.sketchbook.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.g1.sketchbook.Graph
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.ui.canvas.renderStrokesToBitmap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Local date key like the canvas uses, offset by [offsetDays] (e.g. -1 = yesterday). */
fun dayKey(offsetDays: Int = 0): String {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
}

/**
 * Renders [date]'s strokes into the permanent gallery snapshot, unless that day is empty or
 * already archived. Safe to call repeatedly — this is what makes the daily archive reliable
 * even when a midnight background run is missed (Doze, phone off, etc.): the next time the
 * room opens, the catch-up call finishes the job.
 */
suspend fun archiveDayIfNeeded(graph: Graph, roomId: String, date: String): Boolean {
    if (graph.archiveRepository.hasArchive(roomId, date)) return false
    val strokes = graph.roomRepository.getStrokesOnce(roomId, date)
    if (strokes.isEmpty()) return false
    val bitmap = renderStrokesToBitmap(strokes, width = 1080, height = 1920)
    graph.archiveRepository.saveDaily(roomId, date, bitmap)
    return true
}

/**
 * Fires around local midnight, archives *yesterday* for the room the user is currently in,
 * then re-schedules itself for the next midnight. Using a self-rescheduling one-time worker
 * (instead of a periodic one) lets us align to the calendar day boundary.
 */
class DailyArchiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as SketchApp).graph
        val roomId = graph.sessionStore.currentRoomId
        if (roomId != null) {
            runCatching { archiveDayIfNeeded(graph, roomId, dayKey(-1)) }
        }
        // Always line up the next midnight, whatever happened above.
        ArchiveScheduler.schedule(applicationContext)
        return Result.success()
    }
}

object ArchiveScheduler {
    private const val UNIQUE_WORK = "daily-archive"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyArchiveWorker>()
            .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun millisUntilNextMidnight(): Long {
        val now = Calendar.getInstance()
        val nextMidnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (nextMidnight.timeInMillis - Date().time).coerceAtLeast(0)
    }
}
