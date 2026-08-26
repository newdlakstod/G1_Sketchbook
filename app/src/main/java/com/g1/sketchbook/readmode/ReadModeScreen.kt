package com.g1.sketchbook.readmode

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gdo.pagecurl.PageCurl
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.bgDrawable

internal fun normalizeReadPage(startPage: Int, pageCount: Int): Int {
    require(pageCount > 0) { "pageCount must be positive" }
    return startPage.coerceIn(0, pageCount - 1)
}

/** Full-screen, read-only viewer backed by the shared PageCurl module. */
@Composable
fun ReadModeScreen(
    repo: SketchbookRepository,
    book: Sketchbook,
    startPage: Int,
    onClose: (lastPage: Int) -> Unit,
) {
    var currentPage by rememberSaveable(book.id) {
        mutableIntStateOf(normalizeReadPage(startPage, book.pageCount))
    }
    var errorMessage by remember(book.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val paper = remember(context, book.bgKey) {
        BitmapFactory.decodeResource(context.resources, bgDrawable(book.bgKey))
    }
    val source = remember(repo, book.id, book.pageCount, book.sizeKey, paper) {
        SketchbookPageSource(
            repo = repo,
            bookId = book.id,
            pageCount = book.pageCount,
            pageAspectRatio = book.size.ratio,
            paper = paper,
        )
    }

    BackHandler { onClose(currentPage) }

    // 페이지를 넘기려고 화면 가장자리 가까이서 드래그를 시작하면, 안드로이드 제스처 내비게이션이
    // PageCurl보다 먼저 그 터치를 "뒤로가기 스와이프"로 채가서 읽기모드가 자꾸 닫혔다. 이 화면 전체를
    // 시스템 제스처 제외 영역으로 등록해 가장자리 터치도 PageCurl이 온전히 받게 한다.
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

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxSize = it }) {
        PageCurl(
            source = source,
            pageIndex = currentPage,
            modifier = Modifier.fillMaxSize(),
            onPageChanged = {
                currentPage = it
                errorMessage = null
            },
            onError = { error ->
                Log.e("ReadMode", "Page curl failed", error)
                errorMessage = error.message ?: "페이지를 불러오지 못했어요."
            },
        )
        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        CloseButton(onClick = { onClose(currentPage) })
    }
}

@Composable
private fun BoxScope.CloseButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x66000000)),
    ) {
        Icon(Icons.Filled.Close, "닫기", tint = Color.White)
    }
}
