package com.g1.sketchbook.ui

import android.graphics.Rect
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize

/**
 * 화면 가장자리 가까이서 드래그를 시작하면 안드로이드 제스처 내비게이션이 그 터치를 시스템
 * 뒤로가기 스와이프로 먼저 채간다 — 읽기모드(ReadModeScreen)에서 페이지를 넘기려던 드래그가 자꾸
 * 화면을 닫아버려서 처음 발견됐던 문제(2026-08-20)와 같은 종류인데, 그리기 화면의 버튼바를 좌/우
 * 가장자리로 끌어 도킹하려는 드래그, 그리고 캔버스 가장자리까지 붙여 그리는 일반적인 드로잉 자체도
 * 똑같이 영향을 받는다("버튼바가 좌우에 안 붙는다"는 문제의 원인 중 하나로 확인됨, 2026-08-29).
 * 이 Modifier를 전체화면 루트에 붙이면 그 영역 전체를 시스템 제스처 제외 구역으로 등록해서, 그
 * 영역 안의 터치는 앱(Compose pointerInput)이 온전히 먼저 받는다.
 */
@Composable
fun Modifier.excludeSystemGestureEdges(): Modifier {
    val view = LocalView.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(view, boxSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && boxSize.width > 0 && boxSize.height > 0) {
            view.systemGestureExclusionRects = listOf(Rect(0, 0, boxSize.width, boxSize.height))
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) view.systemGestureExclusionRects = emptyList()
        }
    }
    return this.onSizeChanged { boxSize = it }
}
