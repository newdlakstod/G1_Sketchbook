package com.g1.sketchbook.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.data.model.ArchiveEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SketchApp).graph

    private val _entries = MutableStateFlow<List<ArchiveEntry>>(emptyList())
    val entries: StateFlow<List<ArchiveEntry>> = _entries.asStateFlow()

    fun bind(roomId: String) {
        viewModelScope.launch {
            graph.archiveRepository.observeArchive(roomId).collect { _entries.value = it }
        }
    }
}
