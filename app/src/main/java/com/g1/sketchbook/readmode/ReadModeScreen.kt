package com.g1.sketchbook.readmode

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
import androidx.compose.ui.unit.dp
import com.gdo.pagecurl.PageCurl
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository

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
    val source = remember(repo, book.id, book.pageCount, book.sizeKey) {
        SketchbookPageSource(
            repo = repo,
            bookId = book.id,
            pageCount = book.pageCount,
            pageAspectRatio = book.size.ratio,
        )
    }

    BackHandler { onClose(currentPage) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
