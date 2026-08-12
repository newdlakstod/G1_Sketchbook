package com.g1.sketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.g1.sketchbook.ui.AppViewModel
import com.g1.sketchbook.ui.HomeScreen
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.canvas.CanvasScreen
import com.g1.sketchbook.ui.gallery.GalleryScreen
import com.g1.sketchbook.ui.theme.G1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            G1Theme {
                // Phase 0: brush-engine playground for tuning the core drawing feel.
                // (The full app flow — AppRoot() — is retained below and restored after Phase 0.)
                com.g1.sketchbook.brush.BrushPlaygroundScreen()
            }
        }
    }
}

@Composable
private fun AppRoot(appVm: AppViewModel = viewModel()) {
    val state by appVm.state.collectAsStateWithLifecycle()
    val members by appVm.members.collectAsStateWithLifecycle()
    val sketchbooks by appVm.sketchbooks.collectAsStateWithLifecycle()
    val recentEntry by appVm.recentEntry.collectAsStateWithLifecycle()

    when {
        state.user == null -> LoginScreen(
            busy = state.busy,
            error = state.error,
            onSignIn = appVm::signIn,
        )

        state.roomId == null -> HomeScreen(
            userName = state.user?.displayName ?: "친구",
            userEmail = state.user?.email ?: "",
            busy = state.busy,
            error = state.error,
            sketchbooks = sketchbooks,
            recentEntry = recentEntry,
            onOpenSketchbook = appVm::openRoom,
            onRemoveSketchbook = appVm::removeSketchbook,
            onCreateRoom = appVm::createRoom,
            onJoinRoom = appVm::joinRoom,
            onSignOut = appVm::signOut,
        )

        else -> {
            val roomId = state.roomId!!
            var showGallery by remember(roomId) { mutableStateOf(false) }
            if (showGallery) {
                // Back from the gallery returns to the canvas, not out of the app.
                BackHandler { showGallery = false }
                GalleryScreen(roomId = roomId, onBack = { showGallery = false })
            } else {
                // Back from the canvas leaves the room to Home, not out of the app.
                BackHandler { appVm.leaveRoom() }
                CanvasScreen(
                    roomId = roomId,
                    members = members,
                    onOpenGallery = { showGallery = true },
                    onLeaveRoom = appVm::leaveRoom,
                )
            }
        }
    }
}
