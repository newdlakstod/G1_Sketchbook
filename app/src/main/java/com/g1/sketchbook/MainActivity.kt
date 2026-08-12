package com.g1.sketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.NicknameScreen
import com.g1.sketchbook.ui.RootViewModel
import com.g1.sketchbook.ui.SplashScreen
import com.g1.sketchbook.ui.main.MainScreen
import com.g1.sketchbook.ui.theme.G1Theme
import kotlinx.coroutines.delay

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
    G1Theme(mode = state.theme) {
        var splash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(1200); splash = false }
        when {
            splash -> SplashScreen()
            state.user == null -> LoginScreen(busy = state.busy, error = state.error, onSignIn = vm::signIn)
            state.needsNickname -> NicknameScreen(onSave = vm::saveNickname)
            else -> MainScreen(
                nickname = state.nickname ?: "친구",
                tab = state.tab,
                theme = state.theme,
                onTab = vm::selectTab,
                onTheme = vm::setTheme,
                onSignOut = vm::signOut,
                onRename = vm::saveNickname,
            )
        }
    }
}
