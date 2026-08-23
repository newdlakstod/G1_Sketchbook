package com.g1.sketchbook.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g1.sketchbook.SketchApp
import com.g1.sketchbook.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RootState(
    val user: FirebaseUser? = null,
    val nickname: String? = null,
    val needsNickname: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 계정 이미지 파일이 바뀔 때마다 올라간다 — 스케치북 표지의 coverVersion과 같은 캐시 무효화
     *  용도(파일 경로는 고정이라 이 값이 바뀌어야 Compose가 다시 읽어온다). */
    val avatarVersion: Int = 0,
    val tab: Int = 0, // Home is the first tab
    val openBookId: String? = null, // when set, a sketchbook canvas is shown full-screen
    val openDiaryDate: String? = null, // when set, the diary editor for this date is full-screen
    val cleanCalendar: Pair<Int, Int>? = null, // (year, month) → full-screen clean calendar (slides 3/4)
    val uid: String? = null,        // Firebase uid, needed for shared sketchbooks
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
        _state.value = _state.value.copy(user = null, needsNickname = false)
    }

    fun selectTab(i: Int) { _state.value = _state.value.copy(tab = i) }
    fun openBook(id: String) { _state.value = _state.value.copy(openBookId = id) }
    fun closeBook() { _state.value = _state.value.copy(openBookId = null) }
    fun openDiary(date: String) { _state.value = _state.value.copy(openDiaryDate = date) }
    fun closeDiary() { _state.value = _state.value.copy(openDiaryDate = null) }
    fun openCleanCalendar(year: Int, month: Int) { _state.value = _state.value.copy(cleanCalendar = year to month) }
    fun closeCleanCalendar() { _state.value = _state.value.copy(cleanCalendar = null) }

    fun setTheme(mode: ThemeMode) {
        graph.sessionStore.themeMode = mode.name.lowercase()
        _state.value = _state.value.copy(theme = mode)
    }

    fun setAvatarImage(bmp: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            graph.sessionStore.saveAvatarImage(bmp)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(avatarVersion = _state.value.avatarVersion + 1) }
        }
    }

    private fun String.toThemeMode() = when (this) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }
}
