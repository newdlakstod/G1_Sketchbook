package com.g1.sketchbook.vector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VectorTool { SELECT, PEN, ERASER, HAND }

sealed interface AppearanceEdit {
    data class FillEnabled(val enabled: Boolean) : AppearanceEdit
    data class FillColor(val color: Long) : AppearanceEdit
    data class StrokeEnabled(val enabled: Boolean) : AppearanceEdit
    data class StrokeColor(val color: Long) : AppearanceEdit
    data class StrokeWidth(val width: Float) : AppearanceEdit
    data class Cap(val cap: VectorCap) : AppearanceEdit
    data class Join(val join: VectorJoin) : AppearanceEdit
    data class Brush(val brush: BrushStyle) : AppearanceEdit
}

data class VectorEditorSnapshot(
    val document: VectorDocument,
    val selectedIds: Set<String> = emptySet(),
    val tool: VectorTool = VectorTool.SELECT,
    val defaultAppearance: PathAppearance = PathAppearance(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class VectorEditorState(initialDocument: VectorDocument) {
    private data class HistoryEntry(val before: VectorDocument, val after: VectorDocument)
    private data class AppearanceGesture(val before: VectorDocument, val selectedIds: Set<String>, var current: VectorDocument)

    private val _snapshot = MutableStateFlow(VectorEditorSnapshot(document = initialDocument))
    val snapshot: StateFlow<VectorEditorSnapshot> = _snapshot.asStateFlow()
    var onDocumentCommitted: ((VectorDocument) -> Unit)? = null

    private val undo = ArrayDeque<HistoryEntry>()
    private val redo = ArrayDeque<HistoryEntry>()
    private var appearanceGesture: AppearanceGesture? = null

    fun select(ids: Set<String>) {
        updateSnapshot(selectedIds = ids.intersect(_snapshot.value.document.objects.mapTo(hashSetOf()) { it.id }))
    }

    fun setTool(tool: VectorTool) = updateSnapshot(tool = tool)

    fun applyAppearance(edit: AppearanceEdit) {
        if (_snapshot.value.selectedIds.isEmpty()) {
            val updated = applyAppearanceEdit(_snapshot.value.defaultAppearance, edit)
            if (updated != _snapshot.value.defaultAppearance) updateSnapshot(defaultAppearance = updated)
        } else {
            dispatch(ChangeAppearance(_snapshot.value.selectedIds, edit))
        }
    }

    fun beginAppearanceGesture() {
        if (appearanceGesture != null || _snapshot.value.selectedIds.isEmpty()) return
        val before = _snapshot.value.document
        appearanceGesture = AppearanceGesture(before, _snapshot.value.selectedIds, before)
    }

    fun previewAppearance(edit: AppearanceEdit) {
        val gesture = appearanceGesture ?: return applyAppearance(edit)
        val updated = ChangeAppearance(gesture.selectedIds, edit).apply(gesture.current)
        gesture.current = updated
        updateSnapshot(document = updated)
    }

    fun commitAppearanceGesture() {
        val gesture = appearanceGesture ?: return
        appearanceGesture = null
        if (gesture.current != gesture.before) commit(gesture.before, gesture.current)
        else updateSnapshot(document = gesture.before)
    }

    fun cancelAppearanceGesture() {
        val gesture = appearanceGesture ?: return
        appearanceGesture = null
        updateSnapshot(document = gesture.before)
    }

    fun dispatch(command: EditorCommand) {
        if (appearanceGesture != null) return
        val before = _snapshot.value.document
        val after = command.apply(before)
        if (after != before) commit(before, after)
    }

    fun undo() {
        val entry = undo.removeLastOrNull() ?: return
        redo.addLast(entry)
        updateSnapshot(document = entry.before, selectedIds = sanitizedSelection(_snapshot.value.selectedIds, entry.before))
    }

    fun redo() {
        val entry = redo.removeLastOrNull() ?: return
        undo.addLast(entry)
        updateSnapshot(document = entry.after, selectedIds = sanitizedSelection(_snapshot.value.selectedIds, entry.after))
    }

    private fun commit(before: VectorDocument, after: VectorDocument) {
        undo.addLast(HistoryEntry(before, after))
        redo.clear()
        updateSnapshot(document = after, selectedIds = sanitizedSelection(_snapshot.value.selectedIds, after))
        onDocumentCommitted?.invoke(after)
    }

    private fun updateSnapshot(
        document: VectorDocument = _snapshot.value.document,
        selectedIds: Set<String> = _snapshot.value.selectedIds,
        tool: VectorTool = _snapshot.value.tool,
        defaultAppearance: PathAppearance = _snapshot.value.defaultAppearance,
    ) {
        _snapshot.value = VectorEditorSnapshot(document, selectedIds, tool, defaultAppearance, undo.isNotEmpty(), redo.isNotEmpty())
    }

    private fun sanitizedSelection(ids: Set<String>, document: VectorDocument): Set<String> =
        ids.intersect(document.objects.mapTo(hashSetOf()) { it.id })
}
