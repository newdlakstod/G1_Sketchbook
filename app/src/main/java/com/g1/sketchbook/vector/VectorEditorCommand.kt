package com.g1.sketchbook.vector

sealed interface EditorCommand {
    fun apply(document: VectorDocument): VectorDocument
    fun revert(document: VectorDocument): VectorDocument
}

data class AddObject(val objectPath: VectorObject) : EditorCommand {
    override fun apply(document: VectorDocument): VectorDocument =
        if (document.objects.any { it.id == objectPath.id }) document else document.copy(objects = document.objects + objectPath)
    override fun revert(document: VectorDocument): VectorDocument = document.copy(objects = document.objects.filterNot { it.id == objectPath.id })
}

data class DeleteObjects(val ids: Set<String>) : EditorCommand {
    override fun apply(document: VectorDocument): VectorDocument = document.copy(objects = document.objects.filterNot { it.id in ids })
    override fun revert(document: VectorDocument): VectorDocument = document
}

data class TransformObjects(val ids: Set<String>, val transform: SelectionTransform) : EditorCommand {
    override fun apply(document: VectorDocument): VectorDocument = document.copy(objects = transformObjects(document.objects, ids, transform))
    override fun revert(document: VectorDocument): VectorDocument = document
}

data class ChangeAppearance(val ids: Set<String>, val edit: AppearanceEdit) : EditorCommand {
    override fun apply(document: VectorDocument): VectorDocument = document.copy(objects = document.objects.map { objectPath ->
        if (objectPath.id !in ids) objectPath else changeObjectAppearance(objectPath, edit)
    })
    override fun revert(document: VectorDocument): VectorDocument = document
}

data class ChangeClosedState(val ids: Set<String>, val closed: Boolean) : EditorCommand {
    override fun apply(document: VectorDocument): VectorDocument = document.copy(objects = document.objects.map { objectPath ->
        if (objectPath is EditablePathObject && objectPath.id in ids) objectPath.copy(geometry = objectPath.geometry.copy(closed = closed)) else objectPath
    })
    override fun revert(document: VectorDocument): VectorDocument = document
}

private fun changeObjectAppearance(objectPath: VectorObject, edit: AppearanceEdit): VectorObject = when (objectPath) {
    is EditablePathObject -> objectPath.copy(appearance = applyAppearanceEdit(objectPath.appearance, edit))
    is LegacyStrokeObject -> {
        val editable = convertLegacyObject(objectPath)
        val updated = applyAppearanceEdit(editable.appearance, edit)
        if (updated == editable.appearance) objectPath else editable.copy(appearance = updated)
    }
}

fun applyAppearanceEdit(appearance: PathAppearance, edit: AppearanceEdit): PathAppearance = when (edit) {
    is AppearanceEdit.FillEnabled -> appearance.copy(fill = appearance.fill.copy(enabled = edit.enabled))
    is AppearanceEdit.FillColor -> appearance.copy(fill = appearance.fill.copy(color = edit.color))
    is AppearanceEdit.StrokeEnabled -> appearance.copy(stroke = appearance.stroke.copy(enabled = edit.enabled))
    is AppearanceEdit.StrokeColor -> appearance.copy(stroke = appearance.stroke.copy(color = edit.color))
    is AppearanceEdit.StrokeWidth -> appearance.copy(stroke = appearance.stroke.copy(width = edit.width.takeIf { it.isFinite() }?.coerceIn(0f, 10_000f) ?: appearance.stroke.width))
    is AppearanceEdit.Cap -> appearance.copy(stroke = appearance.stroke.copy(cap = edit.cap))
    is AppearanceEdit.Join -> appearance.copy(stroke = appearance.stroke.copy(join = edit.join))
    is AppearanceEdit.Brush -> appearance.copy(brush = edit.brush)
}
