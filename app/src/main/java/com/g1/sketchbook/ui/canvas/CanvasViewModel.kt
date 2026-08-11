package com.g1.sketchbook.ui.canvas

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.data.StrokeEvent
import com.g1.sketchbook.data.model.Stroke
import com.g1.sketchbook.work.archiveDayIfNeeded
import com.g1.sketchbook.work.dayKey
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyedStroke(val key: String, val stroke: Stroke)

class CanvasViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SketchApp).graph
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon"

    /** Today's local date; each day is its own canvas. */
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private var roomId: String = ""
    private var strokesJob: Job? = null
    private var liveJob: Job? = null

    /** Finished strokes from everyone, keyed by push id. */
    val strokes: SnapshotStateList<KeyedStroke> = mutableStateListOf()

    /** In-progress strokes of other users (uid -> stroke). */
    var liveOthers by mutableStateOf<Map<String, Stroke>>(emptyMap())
        private set

    /** The stroke the local user is currently drawing (normalized 0..1 points, flat). */
    val currentPoints: SnapshotStateList<Float> = mutableStateListOf()

    // Tools
    var color by mutableStateOf(0xFF1A1A2EL); private set
    var strokeWidthPx by mutableStateOf(8f); private set
    var erasing by mutableStateOf(false); private set

    private val ownKeys = ArrayDeque<String>()
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    private var lastLiveWriteMs = 0L

    fun bind(roomId: String) {
        if (this.roomId == roomId && strokesJob != null) return
        this.roomId = roomId
        strokes.clear()
        strokesJob?.cancel()
        liveJob?.cancel()

        strokesJob = viewModelScope.launch {
            graph.roomRepository.observeStrokes(roomId, date).collect { event ->
                when (event) {
                    is StrokeEvent.Added ->
                        if (strokes.none { it.key == event.key }) {
                            strokes.add(KeyedStroke(event.key, event.stroke))
                        }
                    is StrokeEvent.Removed ->
                        strokes.removeAll { it.key == event.key }
                }
            }
        }
        liveJob = viewModelScope.launch {
            graph.roomRepository.observeLive(roomId, date, uid).collect { liveOthers = it }
        }
        // Catch up any day that a midnight background run may have missed (phone off, Doze, ...).
        viewModelScope.launch {
            runCatching { archiveDayIfNeeded(graph, roomId, dayKey(-1)) }
        }
    }

    fun chooseColor(argb: Long) { color = argb; erasing = false }
    fun chooseWidth(px: Float) { strokeWidthPx = px }
    fun toggleErase() { erasing = !erasing }

    fun onDragStart(pos: Offset, canvasSize: Offset) {
        currentPoints.clear()
        addPoint(pos, canvasSize)
    }

    fun onDrag(pos: Offset, canvasSize: Offset) {
        addPoint(pos, canvasSize)
        val now = System.currentTimeMillis()
        if (now - lastLiveWriteMs > LIVE_THROTTLE_MS) {
            lastLiveWriteMs = now
            graph.roomRepository.updateLive(roomId, date, uid, buildStroke(canvasSize))
        }
    }

    fun onDragEnd(canvasSize: Offset) {
        if (currentPoints.size >= 2) {
            val key = graph.roomRepository.pushStroke(roomId, date, buildStroke(canvasSize))
            ownKeys.addLast(key)
        }
        currentPoints.clear()
        graph.roomRepository.clearLive(roomId, date, uid)
    }

    fun undo() {
        val key = ownKeys.removeLastOrNull() ?: return
        strokes.removeAll { it.key == key }
        graph.roomRepository.removeStroke(roomId, date, key)
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { graph.roomRepository.clearDay(roomId, date) }
            ownKeys.clear()
            strokes.clear()
        }
    }

    /** Saves the rendered canvas as today's permanent gallery snapshot. */
    fun saveToGallery(bitmap: Bitmap) {
        saving = true
        viewModelScope.launch {
            runCatching { graph.archiveRepository.saveDaily(roomId, date, bitmap) }
                .onSuccess { message = "오늘 그림을 갤러리에 저장했어요 ✨" }
                .onFailure { message = "저장 실패: ${it.message}" }
            saving = false
        }
    }

    private fun addPoint(pos: Offset, canvasSize: Offset) {
        if (canvasSize.x <= 0f || canvasSize.y <= 0f) return
        currentPoints.add((pos.x / canvasSize.x).coerceIn(0f, 1f))
        currentPoints.add((pos.y / canvasSize.y).coerceIn(0f, 1f))
    }

    private fun buildStroke(canvasSize: Offset): Stroke = Stroke(
        uid = uid,
        color = if (erasing) ERASE_COLOR else color,
        width = if (canvasSize.x > 0) strokeWidthPx / canvasSize.x else 0.008f,
        erase = erasing,
        points = currentPoints.toList(),
    )

    override fun onCleared() {
        strokesJob?.cancel()
        liveJob?.cancel()
        if (roomId.isNotEmpty()) graph.roomRepository.clearLive(roomId, date, uid)
    }

    companion object {
        const val ERASE_COLOR = 0xFFFDF7F4L // matches light canvas background
        private const val LIVE_THROTTLE_MS = 45L
    }
}
