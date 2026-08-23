package com.g1.sketchbook.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.g1.sketchbook.ui.LoginScreen
import com.g1.sketchbook.ui.NicknameDialog
import com.g1.sketchbook.ui.SplashScreen
import com.g1.sketchbook.ui.theme.DaymoryTheme
import com.g1.sketchbook.ui.theme.ThemeMode

private const val PREVIEW_WIDTH = 475
private const val PREVIEW_HEIGHT = 751

@Preview(name = "01 Splash", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SplashPreview() = PreviewTheme { SplashScreen(onEnter = {}) }

@Preview(name = "02 Login", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun LoginPreview() = PreviewTheme { LoginScreen(busy = false, error = null, onSignIn = {}) }

@Preview(name = "03 Login - loading", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun LoginLoadingPreview() = PreviewTheme {
    LoginScreen(busy = true, error = null, onSignIn = {})
}

@Preview(name = "04 Login - error", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun LoginErrorPreview() = PreviewTheme {
    LoginScreen(busy = false, error = "로그인 상태를 확인하지 못했어요.", onSignIn = {})
}

@Preview(name = "05 Nickname dialog", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun NicknameDialogPreview() = PreviewTheme {
    LoginScreen(busy = false, error = null, onSignIn = {})
    NicknameDialog(onCancel = {}, onConfirm = {})
}

@Preview(name = "06 Splash - dark", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun SplashDarkPreview() = PreviewTheme(mode = ThemeMode.DARK) { SplashScreen(onEnter = {}) }

@Preview(name = "07 Login - dark", showBackground = true, widthDp = PREVIEW_WIDTH, heightDp = PREVIEW_HEIGHT)
@Composable
private fun LoginDarkPreview() = PreviewTheme(mode = ThemeMode.DARK) {
    LoginScreen(busy = false, error = null, onSignIn = {})
}

@Preview(name = "08 Splash - landscape", showBackground = true, widthDp = PREVIEW_HEIGHT, heightDp = PREVIEW_WIDTH)
@Composable
private fun SplashLandscapePreview() = PreviewTheme { SplashScreen(onEnter = {}) }

@Preview(name = "09 Login - landscape", showBackground = true, widthDp = PREVIEW_HEIGHT, heightDp = PREVIEW_WIDTH)
@Composable
private fun LoginLandscapePreview() = PreviewTheme { LoginScreen(busy = false, error = null, onSignIn = {}) }

@Composable
private fun PreviewTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    DaymoryTheme(mode = mode, content = content)
}
