package com.g1.sketchbook.readmode

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    BackHandler { onClose(currentPage) }

    // 페이지를 넘기려고 화면 가장자리 가까이서 드래그를 시작하면, 안드로이드 제스처 내비게이션이
    // PageCurl보다 먼저 그 터치를 "뒤로가기 스와이프"로 채가서 읽기모드가 자꾸 닫혔다. 이 화면 전체를
    // 시스템 제스처 제외 영역으로 등록해 가장자리 터치도 PageCurl이 온전히 받게 한다(전체화면 모달
    // 전용 문제라 아래 [ReadingPane]이 아니라 여기서만 처리).
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
        ReadingPane(repo, book, currentPage, onPageChanged = { currentPage = it }, modifier = Modifier.fillMaxSize())
        CloseButton(onClick = { onClose(currentPage) })
    }
}

/** 페이지 커얼 뷰어의 공통 핵심 — 전체화면 모달([ReadModeScreen])과 홈 화면 가로모드의 읽기 패널
 *  (MainScreen.kt의 HomeReadingPane) 양쪽이 공유한다. 뒤로가기/닫기 버튼/제스처 제외 영역처럼
 *  "전체화면 모달"에만 해당하는 것은 여기 없고 호출부가 각자 갖춘다. */
@Composable
fun ReadingPane(
    /** null이면(프리뷰 등 실제 저장소가 없는 상황) 정적 종이 이미지로 대신 그린다 — 아래 참고. */
    repo: SketchbookRepository?,
    book: Sketchbook,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paper = remember(context, book.bgKey) {
        BitmapFactory.decodeResource(context.resources, bgDrawable(book.bgKey))
    }
    if (repo == null) {
        // PageCurl은 OpenGL 서피스(GLSurfaceView)라 Compose 프리뷰(레이아웃 도구가 실제 GL 컨텍스트를
        // 못 그림)에서는 항상 비어 보인다 — 이 자리에 종이 텍스처를 정적 이미지로 대신 그려서, 최소한
        // 크기·비율·라운드 코너 같은 레이아웃은 프리뷰에서도 확인할 수 있게 한다(2026-08-29).
        // PageCurl은 가로모드에서 항상 TwoPageSpread(펼친 책처럼 좌우 두 쪽)로 그린다(PageCurl.kt의
        // layoutMode 분기) — 0페이지만 단독으로 오른쪽에 오고(왼쪽은 빈 자리), 그 뒤로는 (1,2)
        // (3,4)... 짝을 지어 펼침면을 이룬다(PageBookState.spreadStart/stableSelection과 동일 규칙).
        // 이전 버전은 한 쪽만 꽉 채워 그려서 실제와 다르게 보였다(재요청 — "두 쪽으로 보여야지").
        val density = LocalDensity.current
        val pageIndex = normalizeReadPage(currentPage, book.pageCount)
        val spreadStart = when {
            pageIndex == 0 -> 0
            pageIndex % 2 == 0 -> pageIndex - 1
            else -> pageIndex
        }
        val leftPage = if (spreadStart == 0) null else spreadStart
        val rightPage = if (spreadStart == 0) 0 else (spreadStart + 1).takeIf { it < book.pageCount }
        BoxWithConstraints(modifier) {
            val halfWPx = with(density) { (maxWidth / 2).roundToPx() }.coerceAtLeast(1)
            val hPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val staticHalf = remember(paper, halfWPx, hPx) { composePageBitmap(content = null, paper = paper, width = halfWPx, height = hPx) }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (leftPage != null) Image(staticHalf.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (rightPage != null) Image(staticHalf.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
            }
            Text(
                "${pageIndex + 1} / ${book.pageCount} (프리뷰 — 실제 페이지 내용 없음)",
                fontSize = 11.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = { if (spreadStart > 0) onPageChanged((spreadStart - 2).coerceAtLeast(0)) },
                    modifier = Modifier.clip(CircleShape).background(Color(0x99000000))) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 펼침면", tint = Color.White)
                }
                IconButton(onClick = { if (rightPage != null || leftPage != null) onPageChanged(if (spreadStart == 0) 1 else spreadStart + 2) },
                    modifier = Modifier.clip(CircleShape).background(Color(0x99000000))) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 펼침면", tint = Color.White)
                }
            }
        }
        return
    }
    var errorMessage by remember(book.id) { mutableStateOf<String?>(null) }
    val source = remember(repo, book.id, book.pageCount, book.sizeKey, paper) {
        SketchbookPageSource(
            repo = repo,
            bookId = book.id,
            pageCount = book.pageCount,
            pageAspectRatio = book.size.ratio,
            paper = paper,
        )
    }
    Box(modifier) {
        PageCurl(
            source = source,
            pageIndex = normalizeReadPage(currentPage, book.pageCount),
            modifier = Modifier.fillMaxSize(),
            onPageChanged = {
                onPageChanged(it)
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
