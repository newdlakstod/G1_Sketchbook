package com.g1.sketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.NicknameDialog
import com.g1.sketchbook.ui.RootViewModel
import com.g1.sketchbook.ui.SplashScreen
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot(vm: RootViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 폰↔태블릿 자동 동기화: 앱이 포그라운드로 올라올 때마다 클라우드 백업을 받아와 병합하고,
    // 백그라운드로 내려갈 때 지금 설정값을 올린다(브러시 색상/굵기처럼 자주 바뀌는 값을 매번
    // 따로 안 올리고 여기서 한 번에 흘려보내는 지점 — GoogleAccountBackupSync 계획 참고).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.syncNow(context)
                Lifecycle.Event.ON_STOP -> vm.flushSettings()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DaymoryTheme(mode = state.theme) {
        var splash by remember { mutableStateOf(true) }
        when {
            splash -> SplashScreen(onEnter = { splash = false })
            state.user == null -> LoginScreen(busy = state.busy, error = state.error, onSignIn = { vm.signIn(context) })
            state.needsNickname -> {
                LoginScreen(busy = false, error = null, onSignIn = {})
                NicknameDialog(onCancel = vm::signOut, onConfirm = vm::saveNickname)
            }
            state.openBookId != null -> com.g1.sketchbook.sketchbook.SketchbookCanvasScreen(
                bookId = state.openBookId!!, startPage = state.openBookPage, myUid = state.uid ?: "", myName = state.nickname ?: "나",
                onBack = vm::closeBook,
            )
            state.openDiaryDate != null -> com.g1.sketchbook.diary.DiaryEditorScreen(
                date = state.openDiaryDate!!, myUid = state.uid ?: "", onBack = vm::closeDiary,
            )
            state.cleanCalendar != null -> com.g1.sketchbook.diary.CleanCalendarScreen(
                year = state.cleanCalendar!!.first, month = state.cleanCalendar!!.second, onBack = vm::closeCleanCalendar,
            )
            else -> MainScreen(
                nickname = state.nickname ?: "친구",
                avatarVersion = state.avatarVersion,
                tab = state.tab,
                theme = state.theme,
                myUid = state.uid ?: "",
                syncGeneration = state.syncGeneration,
                onTab = vm::selectTab,
                onTheme = vm::setTheme,
                onSignOut = vm::signOut,
                onRename = vm::saveNickname,
                onSetAvatarImage = vm::setAvatarImage,
                onOpenBook = vm::openBook,
                onOpenBookAtPage = vm::openBookAtPage,
                onOpenDiary = vm::openDiary,
                onOpenCalendar = vm::openCleanCalendar,
            )
        }
    }
}
