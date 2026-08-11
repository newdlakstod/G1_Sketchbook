package com.g1.sketchbook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.data.model.ArchiveEntry
import com.g1.sketchbook.data.model.Member
import com.g1.sketchbook.data.model.SketchbookRef
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val user: FirebaseUser? = null,
    val roomId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SketchApp).graph

    private val _state = MutableStateFlow(
        AppUiState(
            user = graph.authClient.currentUser,
            roomId = graph.sessionStore.currentRoomId,
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    /** Sketchbooks the user has created/joined, for the home list (most recent first). */
    private val _sketchbooks = MutableStateFlow(graph.sessionStore.sketchbooks())
    val sketchbooks: StateFlow<List<SketchbookRef>> = _sketchbooks.asStateFlow()

    /** Latest saved snapshot of the most-recent sketchbook, for the home preview card. */
    val recentEntry: StateFlow<ArchiveEntry?> = _sketchbooks
        .flatMapLatest { list ->
            val id = list.firstOrNull()?.id
            if (id == null) flowOf(null)
            else graph.archiveRepository.observeArchive(id).map { it.firstOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun refreshSketchbooks() {
        _sketchbooks.value = graph.sessionStore.sketchbooks()
    }

    /** Re-enters a sketchbook from the saved list without needing the code again. */
    fun openRoom(ref: SketchbookRef) {
        graph.sessionStore.currentRoomId = ref.id
        graph.sessionStore.rememberSketchbook(ref) // bump to front
        refreshSketchbooks()
        _state.value = _state.value.copy(roomId = ref.id)
    }

    fun removeSketchbook(id: String) {
        graph.sessionStore.forgetSketchbook(id)
        refreshSketchbooks()
    }

    /** Members of the currently-joined room, for the little presence row. */
    val members: StateFlow<List<Member>> = _state
        .flatMapLatest { s ->
            val id = s.roomId
            if (id == null) emptyFlow() else graph.roomRepository.observeMembers(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun signIn() {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = graph.authClient.signIn()
            _state.value = result.fold(
                onSuccess = { user -> _state.value.copy(user = user, busy = false) },
                onFailure = { e -> _state.value.copy(busy = false, error = e.message ?: "로그인 실패") },
            )
        }
    }

    fun signOut() {
        graph.authClient.signOut()
        graph.sessionStore.currentRoomId = null
        _state.value = AppUiState()
    }

    fun createRoom(name: String) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { graph.roomRepository.createRoom(name) }
                .onSuccess { code ->
                    graph.sessionStore.currentRoomId = code
                    graph.sessionStore.rememberSketchbook(
                        SketchbookRef(code, name.ifBlank { "우리 스케치북" })
                    )
                    refreshSketchbooks()
                    _state.value = _state.value.copy(roomId = code, busy = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "방 생성 실패")
                }
        }
    }

    fun joinRoom(code: String) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { graph.roomRepository.joinRoom(code) }
                .onSuccess { exists ->
                    if (exists) {
                        val normalized = code.trim().uppercase()
                        graph.sessionStore.currentRoomId = normalized
                        val name = runCatching { graph.roomRepository.getRoomName(normalized) }
                            .getOrNull().orEmpty().ifBlank { normalized }
                        graph.sessionStore.rememberSketchbook(SketchbookRef(normalized, name))
                        refreshSketchbooks()
                        _state.value = _state.value.copy(roomId = normalized, busy = false)
                    } else {
                        _state.value = _state.value.copy(busy = false, error = "존재하지 않는 방 코드예요")
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "참여 실패")
                }
        }
    }

    fun leaveRoom() {
        graph.sessionStore.currentRoomId = null
        _state.value = _state.value.copy(roomId = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
