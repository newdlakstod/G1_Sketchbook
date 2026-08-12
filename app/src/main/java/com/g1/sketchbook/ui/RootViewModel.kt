package com.g1.sketchbook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RootState(
    val user: FirebaseUser? = null,
    val nickname: String? = null,
    val needsNickname: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val avatar: String = "🦆",
    val tab: Int = 2, // Home is the centre tab
    val openBookId: String? = null, // when set, a sketchbook canvas is shown full-screen
    val uid: String? = null,        // Firebase uid, needed for shared sessions
    val shareCode: String? = null,  // when set, a shared "draw together" session is shown
    val shareIsHost: Boolean = false,
)

/** Top-level app state for Phase 1: auth, nickname onboarding, theme, and bottom-tab selection. */
class RootViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SketchApp).graph

    private val _state = MutableStateFlow(
        RootState(
            user = graph.authClient.currentUser,
            nickname = graph.sessionStore.nickname,
            needsNickname = graph.authClient.currentUser != null && graph.sessionStore.nickname.isNullOrBlank(),
            theme = graph.sessionStore.themeMode.toThemeMode(),
            avatar = graph.sessionStore.avatar,
            uid = graph.authClient.currentUser?.uid,
        )
    )
    val state: StateFlow<RootState> = _state.asStateFlow()

    fun signIn() {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            graph.authClient.signIn().fold(
                onSuccess = { user ->
                    val nick = graph.sessionStore.nickname
                    _state.value = _state.value.copy(
                        user = user, busy = false, uid = user.uid,
                        needsNickname = nick.isNullOrBlank(), nickname = nick,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "로그인 실패")
                },
            )
        }
    }

    fun saveNickname(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        graph.sessionStore.nickname = n
        _state.value = _state.value.copy(nickname = n, needsNickname = false)
    }

    fun signOut() {
        graph.authClient.signOut()
        graph.sessionStore.currentRoomId = null
        _state.value = _state.value.copy(user = null, needsNickname = false)
    }

    fun selectTab(i: Int) { _state.value = _state.value.copy(tab = i) }
    fun openBook(id: String) { _state.value = _state.value.copy(openBookId = id) }
    fun closeBook() { _state.value = _state.value.copy(openBookId = null) }
    fun openShare(code: String, isHost: Boolean) { _state.value = _state.value.copy(shareCode = code, shareIsHost = isHost) }
    fun closeShare() { _state.value = _state.value.copy(shareCode = null) }

    fun setTheme(mode: ThemeMode) {
        graph.sessionStore.themeMode = mode.name.lowercase()
        _state.value = _state.value.copy(theme = mode)
    }

    fun setAvatar(emoji: String) {
        graph.sessionStore.avatar = emoji
        _state.value = _state.value.copy(avatar = emoji)
    }

    private fun String.toThemeMode() = when (this) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }
}
