package com.g1.sketchbook.vector

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.g1.sketchbook.backup.BackupRepository
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.saveVectorDocumentSynced
import com.g1.sketchbook.ui.saveSvgToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The sole active vector editor entry point. Documents load v2 first through the repository's
 * fallback boundary, while writes happen only after a reversible editor command commits. */
@Composable
fun VectorCanvasScreen(bookId: String, book: Sketchbook, myUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SketchbookRepository(context) }
    val backup = remember { BackupRepository() }
    val brushRepository = remember { VectorBrushRepository(context) }
    var profiles by remember { mutableStateOf(brushRepository.list()) }
    val profileMap = remember(profiles) { profiles.associateBy { it.id } }
    val document = remember(bookId) { repository.loadVectorDocument(bookId) ?: VectorDocument(objects = emptyList()) }
    val editor = remember(bookId) {
        VectorEditorState(document).also { state ->
            state.onDocumentCommitted = { changed ->
                saveVectorDocumentSynced(scope, repository, backup, myUid, bookId, changed)
            }
        }
    }
    val snapshot by editor.snapshot.collectAsState()
    var libraryOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var importKind by remember { mutableStateOf<VectorBrushKind?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val kind = importKind ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val svg = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val created = svg?.let { text ->
                when (kind) {
                    VectorBrushKind.ART -> brushRepository.importArt("Art brush", text)
                    VectorBrushKind.PATTERN -> brushRepository.importPattern("Pattern brush", text)
                    VectorBrushKind.BASIC -> null
                }
            }
            withContext(Dispatchers.Main) {
                importKind = null
                if (created == null) Toast.makeText(context, "SVG 브러시를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                else profiles = brushRepository.list()
            }
        }
    }

    fun exportCurrentDocument() {
        val region = vectorExportBounds(snapshot.document, snapshot.selectedIds, profileMap)
        if (region == null) {
            Toast.makeText(context, "내보낼 패스가 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        val documentToExport = snapshot.document
        scope.launch(Dispatchers.IO) {
            val status = saveSvgToGallery(context, vectorDocumentToSvg(documentToExport, region, profileMap), book.name)
            withContext(Dispatchers.Main) { Toast.makeText(context, status, Toast.LENGTH_SHORT).show() }
        }
    }

    VectorEditorLayout(
        title = book.name,
        state = editor,
        profiles = profiles,
        onBack = onBack,
        onExport = ::exportCurrentDocument,
        onImportArt = { importKind = VectorBrushKind.ART; importLauncher.launch("image/svg+xml") },
        onImportPattern = { importKind = VectorBrushKind.PATTERN; importLauncher.launch("image/svg+xml") },
        canvas = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { VectorCanvasHost(it).also { host -> host.bind(editor) { profileMap } } },
                update = { host -> host.bind(editor) { profileMap } },
            )
        },
        appearance = {
            val projection = projectAppearance(snapshot, profileMap)
            if (libraryOpen) {
                VectorBrushLibrary(
                    profiles = profiles,
                    selected = projection.brush.value,
                    onSelect = { editor.applyAppearance(AppearanceEdit.Brush(it)); libraryOpen = false },
                    onImportArt = { importKind = VectorBrushKind.ART; importLauncher.launch("image/svg+xml") },
                    onImportPattern = { importKind = VectorBrushKind.PATTERN; importLauncher.launch("image/svg+xml") },
                    onRename = { id, name -> renameId = id; renameDraft = name },
                    onDelete = { id -> brushRepository.delete(id); profiles = brushRepository.list() },
                )
            } else {
                VectorAppearancePanel(
                    projection = projection,
                    onEdit = editor::applyAppearance,
                    onGestureStart = editor::beginAppearanceGesture,
                    onGestureCommit = editor::commitAppearanceGesture,
                    onOpenBrushLibrary = { libraryOpen = true },
                    onToggleClosed = editor::toggleSelectedClosed,
                )
            }
        },
    )

    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("브러시 이름") },
            text = { androidx.compose.material3.TextField(renameDraft, { renameDraft = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { brushRepository.rename(id, renameDraft); profiles = brushRepository.list(); renameId = null }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("취소") } },
        )
    }
}
