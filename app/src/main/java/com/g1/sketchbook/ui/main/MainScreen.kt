package com.g1.sketchbook.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.sketchbook.Catalog
import com.g1.sketchbook.sketchbook.DefaultSketchbookCoverColor
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookCover
import com.g1.sketchbook.sketchbook.SketchbookCoverShape
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.sketchbook.decodeCoverBitmap
import com.g1.sketchbook.readmode.ReadingPane
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    nickname: String,
    avatarVersion: Int,
    tab: Int,
    theme: ThemeMode,
    myUid: String,
    /** Bumped by RootViewModel after each successful background sync — the tabs that cache a list
     *  behind a local `refresh` counter re-read from disk when this changes. */
    syncGeneration: Int = 0,
    onTab: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onRename: (String) -> Unit,
    onSetAvatarImage: (Bitmap) -> Unit,
    onOpenBook: (String) -> Unit,
    /** 목록/공유 탭 3열 페이지 썸네일 더블탭 전용 — 그 페이지를 펼친 채로 바로 스케치 모드에 들어간다.
     *  안 넘기면(프리뷰 등) 그냥 onOpenBook으로 대체(페이지 지정 없이 열림). */
    onOpenBookAtPage: (String, Int) -> Unit = { id, _ -> onOpenBook(id) },
    onOpenDiary: (String) -> Unit,
    onOpenCalendar: (Int, Int) -> Unit,
    previewBooks: List<Sketchbook>? = null,
    previewDiaryDates: Set<String>? = null,
) {
    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> HomeTab(
                onOpenBook = onOpenBook,
                myUid = myUid,
                syncGeneration = syncGeneration,
                previewBooks = previewBooks,
            )
            1 -> com.g1.sketchbook.sketchbook.SketchbookTab(
                nickname = nickname, myUid = myUid, onOpenBook = onOpenBook, onOpenBookAtPage = onOpenBookAtPage,
                syncGeneration = syncGeneration,
                previewBooks = previewBooks,
            )
            2 -> com.g1.sketchbook.sketchbook.SketchbookTab(
                nickname = nickname, myUid = myUid, onOpenBook = onOpenBook, onOpenBookAtPage = onOpenBookAtPage,
                initialShowShared = true, syncGeneration = syncGeneration,
                previewBooks = previewBooks,
            )
            3 -> com.g1.sketchbook.diary.DiaryCalendarScreen(
                onOpenDiary = onOpenDiary,
                onOpenCalendar = onOpenCalendar,
                previewMarkedDates = previewDiaryDates,
            )
            else -> SettingsTab(nickname, avatarVersion, theme, onTheme, onSignOut, onRename, onSetAvatarImage)
        }
    }
    MainTabLayout(tab, onTab) { content() }
}

@Composable
private fun HomeTab(
    onOpenBook: (String) -> Unit,
    myUid: String,
    syncGeneration: Int = 0,
    previewBooks: List<Sketchbook>? = null,
) {
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    val scope = rememberCoroutineScope()
    val backup = remember { com.g1.sketchbook.backup.BackupRepository() }
    var refresh by remember { mutableStateOf(0) }
    // 백그라운드 동기화가 파일을 직접 써서 Compose가 모르므로, 동기화가 끝나면 목록을 다시 읽는다.
    LaunchedEffect(syncGeneration) { if (syncGeneration > 0) refresh++ }
    val allBooks = previewBooks ?: remember(repo, refresh) { repo!!.list() }
    // 우상단 개인/공유 아이콘 버튼으로 노트를 전환해서 봄 — 스케치북 리스트 탭의 개인/공유받음
    // 필터와 같은 개념, 홈 캐러셀에도 적용.
    var showShared by remember { mutableStateOf(false) }
    val books = remember(allBooks, showShared) { allBooks.filter { it.shared == showShared } }
    // 표지 길게 눌러 수정 — 목록탭(SketchbookListScreen)의 CoverCard와 같은 다이얼로그를 재사용.
    var editing by remember { mutableStateOf<Sketchbook?>(null) }
    var pendingDelete by remember { mutableStateOf<Sketchbook?>(null) }
    // 가로모드 전용: 2열은 읽기모드(선택한 표지를 그 자리에서 페이지 넘겨 봄), 3열은 표지리스트
    // (탭해서 2열의 읽기 대상을 바꿈) — 세로모드(휴대폰)는 기존 캐러셀 그대로 유지한다(2026-08-29).
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBook = books.firstOrNull { it.id == selectedBookId } ?: books.firstOrNull()
    var selectedReadPage by remember(selectedBook?.id) { mutableStateOf(0) }
    MainTabPage(
        title = "Draw your time",
        contentSidePadding = 0.dp,
        actions = {
            IconButton(onClick = { showShared = false }) {
                Icon(Icons.Filled.Person, "개인",
                    tint = if (!showShared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showShared = true }) {
                Icon(Icons.Filled.Groups, "공유",
                    tint = if (showShared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        sidePanel = {
            CoverListPanel(
                books, selectedBook?.id, repo,
                onSelect = { selectedBookId = it.id },
                onLongPress = { editing = it },
            )
        },
    ) {
        // 홈 캐러셀/읽기 패널은 공통 헤더 여백과 달리 화면 양쪽 끝까지 사용한다.
        Box(Modifier.fillMaxSize()) {
            if (books.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = Dimens.Screen.sideMargin),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (showShared) "아직 공유받은 스케치북이 없어요" else "아직 스케치북이 없어요",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("List 탭에서 첫 스케치북을 만들어보세요.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            } else if (landscape) {
                val book = selectedBook
                if (book != null) {
                    // PageCurl(GLSurfaceView 기반)은 fillMaxSize를 줘도 페이지 비율을 유지한 채
                    // 내부에서 알아서 레터박스를 둔다 — 라운드 코너를 바깥(꽉 찬) 상자에 주면 종이
                    // 자체는 네모난 채로 그 안 여백에 떠 있는 것처럼 보였다(2026-08-29). 책 비율에
                    // 맞는 크기로 직접 감싸서 라운드 코너가 종이 가장자리에 딱 맞게 한다. 여백 색은
                    // 종이 톤을 흉내내려고 몇 번 시도했었는데(고정 베이지 → 텍스처 평균색), 질감 없는
                    // 단색은 실제 텍스처 있는 종이 옆에서 아무리 색을 맞춰도 이음새가 보였다 — 홈 탭이
                    // 원래 쓰는 배경색을 그대로 써서 "종이처럼 보이게" 흉내내지 않고 그냥 이 패널의
                    // 나머지 부분(3열 등)과 자연스럽게 이어지는 UI 배경으로 두기로 함(재요청).
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center,
                    ) {
                        // 이 블록은 항상 landscape일 때만 그려지고, PageCurl은 landscape에서 항상
                        // 좌우 두 쪽(TwoPageSpread)으로 그려서 실제 표시 비율이 페이지 하나의 2배로
                        // 넓다 — 여기서 상자를 페이지 1장 비율로만 잡으면 PageCurl이 그 안에서 또
                        // 한 번 축소해 레터박스가 이중으로 생겼다(2026-08-29, "두 쪽으로 보여야지").
                        val ratio = book.size.ratio * 2f
                        val w = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
                        val h = w / ratio
                        ReadingPane(
                            repo, book, selectedReadPage, onPageChanged = { selectedReadPage = it },
                            modifier = Modifier.width(w).height(h).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    // 3열 표지리스트를 탭하면 여기서 읽을 뿐 — 그리기는 이 별도 버튼으로만 들어간다
                    // (탭=읽기, 그리기 진입은 명시적 버튼으로 분리하기로 결정, 2026-08-29). 텍스트 없이
                    // 아이콘만(2026-08-29, 재요청).
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .bounceClick { onOpenBook(book.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Edit, "그리기", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                HomeCarousel(books, repo, onOpenBook, onLongPress = { editing = it })
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("스케치북 삭제") },
            text = { Text("'${target.name}' 을(를) 삭제할까요?\n안에 그린 그림도 함께 사라지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { repo?.let { r -> com.g1.sketchbook.sketchbook.deleteSynced(scope, r, backup, myUid, target.id) }; refresh++; pendingDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    editing?.let { target ->
        // books에서 최신 상태를 다시 찾아 쓴다 — 즐겨찾기 토글 등으로 갱신돼도 다이얼로그가 스냅샷에
        // 머무르지 않도록(목록탭 EditCoverDialog 호출부와 동일 패턴).
        val current = allBooks.firstOrNull { it.id == target.id } ?: target
        com.g1.sketchbook.sketchbook.EditCoverDialog(
            book = current,
            repo = repo,
            onCancel = { editing = null },
            onSave = { name, newCover, removeCover, newColor ->
                repo?.let { r ->
                    com.g1.sketchbook.sketchbook.renameSynced(scope, r, backup, myUid, current.id, name)
                    if (newCover != null) com.g1.sketchbook.sketchbook.saveCoverSynced(scope, r, backup, myUid, current.id, newCover)
                    else if (removeCover) com.g1.sketchbook.sketchbook.removeCoverSynced(scope, r, backup, myUid, current.id)
                    com.g1.sketchbook.sketchbook.setCoverColorSynced(scope, r, backup, myUid, current.id, newColor)
                }
                refresh++; editing = null
            },
            onToggleFav = { repo?.let { r -> com.g1.sketchbook.sketchbook.toggleFavSynced(scope, r, backup, myUid, current.id) }; refresh++ },
            onDelete = { editing = null; pendingDelete = current },
        )
    }
}

/** 가로모드 홈 화면 3열(서브패널) 전용 표지리스트 — 표지를 크게 세로로 나열해 아래로 스크롤하는
 *  방식(2026-08-29, 처음엔 작은 썸네일+옆에 제목이었는데 "표지는 크게, 제목/주석은 표지 아래로"
 *  요청으로 다시 그림). 탭하면 2열의 읽기 대상을 바꾸고(선택 표시는 굵은 테두리), 길게 누르면 표지
 *  수정 다이얼로그가 뜬다(캐러셀의 long-press와 동일 동작). 세로모드는 여전히 [HomeCarousel]을 쓴다. */
@Composable
private fun CoverListPanel(
    books: List<Sketchbook>, selectedId: String?, repo: SketchbookRepository?,
    onSelect: (Sketchbook) -> Unit, onLongPress: (Sketchbook) -> Unit,
) {
    if (books.isEmpty()) return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        itemsIndexed(books, key = { _, b -> b.id }) { _, book ->
            val selected = book.id == selectedId
            var cover by remember(book.id, book.coverVersion) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(book.id, book.coverVersion, repo) { cover = withContext(Dispatchers.IO) { repo?.loadCoverThumb(book.id) } }
            val stackColor = if (cover != null) Color.Black else (book.coverColor?.let { Color(it) } ?: DefaultSketchbookCoverColor)
            Column(Modifier.fillMaxWidth().bounceClick(onLongClick = { onLongPress(book) }) { onSelect(book) }) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(Dimens.Home.coverRatio)
                        .clip(SketchbookCoverShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, SketchbookCoverShape),
                ) {
                    SketchbookCover(
                        modifier = Modifier.fillMaxSize(),
                        coverColor = stackColor,
                        coverImage = cover?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) },
                    ) {}
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    book.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                val bgLabel = Catalog.backgrounds.firstOrNull { it.key == book.bgKey }?.label ?: book.bgKey
                Text(
                    "${book.dateLabel} · ${book.size.label} · $bgLabel",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Swipeable carousel of sketchbook covers — the focused (centred) one is largest, neighbours peek
 *  in smaller and dimmer on either side. Tapping any cover opens it, long-pressing edits it (same
 *  affordance as the list tab's CoverCard). Below the covers, a single title block always describes
 *  whichever book is currently centred (2026-08-20, 시안 참고: G1_BOOKLOG_rev1의 HomeScreen.kt
 *  ReadingPagerCarousel — LazyRow+snap fling으로 손을 떼기 전까지 관성이 끊기지 않게 하고, 가운데
 *  인덱스 하나만 계산해서 그 아래 타이틀·점 인디케이터를 그리는 구조를 그대로 가져왔다). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCarousel(books: List<Sketchbook>, repo: SketchbookRepository?, onOpen: (String) -> Unit, onLongPress: (Sketchbook) -> Unit) {
    val listState = rememberLazyListState()
    // HorizontalPager는 한 장을 넘기고 나면 관성이 그 장에서 뚝 끊기는 느낌이 있었다(손을 떼기 전까지
    // 부드럽게 이어지지 않음) — LazyRow + snap fling behavior는 손을 뗄 때까지 관성 스크롤이 자연스럽게
    // 이어지다가, 멈추는 순간에만 가장 가까운 표지로 스냅한다(참고 프로젝트와 동일 조합).
    val snapFling = rememberSnapFlingBehavior(listState)
    // 지금 화면 가운데에 가장 가까운 표지의 인덱스 — 아래 타이틀·점 인디케이터가 이 값 하나만 본다.
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - viewCenter) }?.index ?: 0
        }
    }
    val centeredBook = books.getOrNull(centeredIndex)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Side padding sized so the focused cover gets its full spec width, with whatever room is
            // left over used to peek the neighbours (never negative, even on narrow phones).
            val peek = ((maxWidth - Dimens.Home.carouselCenterW) / 2).coerceAtLeast(20.dp)
            // 그림자가 위아래로 잘려 보인다는 재현 리포트(2026-08-29) — Row 자신의 높이를 아이템에게
            // (fillMaxHeight 등으로) 기대게 하면 부모 weight(1f) 영역·스크롤 타이밍에 따라 값이 흔들려
            // 잘렸다. wrapContentHeight로 Row가 아이템의 진짜 콘텐츠 높이만큼만 갖게 하고, 아이템 쪽도
            // 고정 크기로 만들어(아래 itemsIndexed 블록 참고) 그 콘텐츠 높이 자체를 흔들리지 않게
            // 고정했다. TopCenter로 붙여서 표지를 위로 올린다 — 남는 공간은 전부 아래(타이틀 쪽)로 간다.
            LazyRow(
                state = listState,
                flingBehavior = snapFling,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().wrapContentHeight().align(Alignment.TopCenter),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = peek),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
                    // 화면 중심에서 얼마나 떨어져 있는지(0=한가운데, 1=옆으로 완전히 밀려남) — 매
                    // 프레임 레이아웃 정보에서 다시 계산해서 표지 크기·그림자·흐림 정도에 반영한다.
                    val distance by remember(index) {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val viewCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (item != null) {
                                val itemCenter = item.offset + item.size / 2f
                                val halfViewport = (info.viewportEndOffset - info.viewportStartOffset) / 2f
                                (kotlin.math.abs(itemCenter - viewCenter) / halfViewport).coerceIn(0f, 1f)
                            } else 1f
                        }
                    }
                    // 표지 레이아웃 크기는 항상 고정(기존 스펙 사이즈, Dimens.Home.carouselCenterW/H)
                    // — distance에 따라 실제 레이아웃 폭까지 바뀌면 그 폭이 다시 LazyRow의 아이템
                    // 위치(따라서 distance 자신)에 영향을 주는 자기참조 루프가 생겨 스크롤이 불안정해
                    // 지고 가운데가 확실히 커 보이지도 않았다(2026-08-20). 대신 scale 변환으로만
                    // 가운데를 키운다 — 레이아웃 크기는 그대로 두고 화면에 그려지는 크기만 바뀐다.
                    val w = Dimens.Home.carouselCenterW
                    val h = Dimens.Home.carouselCenterH
                    val sideScale = Dimens.Home.carouselSideW / Dimens.Home.carouselCenterW
                    val scale = androidx.compose.ui.util.lerp(1f, sideScale, distance)
                    val fade = 1f - distance * 0.5f
                    // 예전엔 elevation을 distance로 매 프레임 다시 계산했다(가까울수록 진하게) — 그런데
                    // Android의 elevation 그림자는 RenderNode의 Z값이 바뀔 때마다 다시 그려지고, 스크롤 중
                    // 매 프레임 값이 바뀌면 그 다시-그리기가 프레임마다 못 따라가면서 스크롤이 멈추고 나서야
                    // (Z값이 더 이상 안 바뀌고 나서야) 그림자가 "뜨는" 것처럼 보이는 재현 리포트가 있었다
                    // (2026-08-29). elevation을 고정값으로 둬서 Z값이 스크롤 내내 안 바뀌게 하면 그림자가
                    // 처음부터 끝까지 계속 떠 있다 — 원근감(가까울수록 크게/진하게)은 scale·alpha만으로도
                    // 충분히 표현된다.
                    val elevation = 12.dp
                    val coverShape = SketchbookCoverShape
                    // 갤러리에서 고른 표지 이미지가 있으면 그걸, 없으면 (커스텀 지정 시) coverColor, 그것도
                    // 없으면 기본색을 보여준다(목록탭 CoverCard와 동일). coverVersion을 키에 넣어야 같은 id라도
                    // 표지 사진이 바뀌면 다시 읽어온다.
                    var cover by remember(book.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(book.id, book.coverVersion, repo) { cover = withContext(Dispatchers.IO) { repo?.loadCoverThumb(book.id) } }
                    // 표지가 사진이면 두께 스택은 검정으로 고정 — book.coverColor는 사진 적용 전에
                    // 마지막으로 골랐던(또는 기본) 색이 그대로 남아있는 필드라, 사진 표지에 그 색을
                    // 그대로 쓰면 "직전 표지 색"이 두께 부분에서만 새어나오는 것처럼 보였다.
                    val stackColor = if (cover != null) Color.Black else (book.coverColor?.let { Color(it) } ?: DefaultSketchbookCoverColor)
                    // 옆(비-중심) 표지는 그림자가 잘려 보이다가 가운데로 오면 안 잘린 표지로
                    // "바뀌는" 것처럼 보인다는 재현 리포트(2026-08-29) — 원인은 scale()이 만드는
                    // graphicsLayer 안에 elevation 그림자(RenderNode 기반)가 들어있던 구조였다.
                    // scale이 1이 아닌 동안은 그 축소 레이어 안의 그림자가 온전히 다시 그려지지
                    // 않고, scale이 정확히 1로 돌아오는 가운데 위치에서만 제대로 그려졌다. scale()
                    // 대신 표지·그림자 크기 자체를 scaledW/scaledH로 직접 계산해서 그림자를 그리는
                    // Box가 항상 "지금 실제 보여야 할 크기"이게 만든다 — 별도 축소 레이어를 더 거치지
                    // 않으니 이 상호작용 자체가 사라진다. 바깥 아이템 슬롯 크기(w+4dp+shadowSlack)는
                    // scale과 무관하게 이미 고정값이라(아래) LazyRow 자기참조 루프 걱정도 없다.
                    val scaledW = w * scale
                    val scaledH = h * scale
                    // 예전엔 이 Box를 fillMaxHeight()짜리 바깥 Box로 한 번 더 감쌌었는데, 그 바깥 Box의
                    // 실제 높이는 LazyRow가 그 순간에 스스로에게 부여한 높이를 그대로 물려받는 값이라
                    // (wrapContentHeight를 걸어도 Row 안 아이템이 fillMaxHeight를 쓰는 한 완전히
                    // 고정되지 않는다) 스크롤 중 레이아웃이 다시 계산되는 타이밍에 따라 그 값이 흔들려서,
                    // 처음 진입했을 때나 스크롤 도중엔 잘리다가 스크롤이 멈추고 나서야(재측정 후) 안
                    // 잘린 것처럼 보이는 재발 리포트가 있었다(2026-08-29). 아이템 크기를 아예 w/h/
                    // shadowSlack만으로 정해지는 고정값으로 만들어(부모 높이에 전혀 기대지 않음) 이
                    // 흔들림 자체를 없앤다 — 세로 정렬은 LazyRow의 verticalAlignment가 맡는다.
                    val shadowSlack = 28.dp
                    Box(
                        Modifier.width(w + 4.dp + shadowSlack).height(h + 4.dp + shadowSlack)
                            .bounceClick(onLongClick = { onLongPress(book) }) { onOpen(book.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // 그림자 전용 레이어 — 표지 그림(아래 SketchbookCover에만 건 alpha(fade))과 완전히
                        // 분리해서 항상 100% 밝기로 그린다. 예전엔 이 shadow가 표지 그림과 같은 alpha(fade)
                        // 안에 있어서, 옆(비-중심)으로 갈수록 alpha가 최대 50%까지 떨어지며 그림자도 같이
                        // 옅어져 크림색 배경 위에서 거의 안 보였다 — "옆 표지는 그림자가 잘려 보이다가
                        // 가운데로 오면서 안 잘린 표지로 바뀐다"는 재현 리포트(2026-08-29, 스크린샷으로
                        // 원인 확정)의 진짜 원인이었다. 잘린 게 아니라 흐려서 안 보인 것 — 그림자를 별도
                        // 레이어로 떼어내 fade의 영향 밖에 두면 옆 표지도 가운데와 똑같은 밝기의 그림자를
                        // 갖는다.
                        Box(Modifier.width(scaledW).height(scaledH)
                            .shadow(elevation, coverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black))
                        // 책 두께 스택(아래 두 겹)도 같은 이유로 옆 표지에서 옅어져 있다 없다 하는
                        // 재현 리포트(2026-08-29) — 그림자와 똑같이 alpha(fade) 안에 있었던 게 원인이라,
                        // 이 바깥 Box에서는 alpha를 떼고 표지 그림(SketchbookCover)에만 개별로 건다.
                        // 두께 스택·그림자는 이제 항상 100% 밝기, 실제 표지 그림만 옆으로 갈수록 흐려진다.
                        Box(Modifier.width(scaledW + 4.dp).height(scaledH + 4.dp)) {
                            // 책처럼 두께감 있게 — 표지 뒤로 살짝 어긋난 종이 스택 2겹.
                            Box(Modifier.width(scaledW).height(scaledH).offset(x = 4.dp, y = 4.dp).clip(coverShape)
                                .background(stackColor.copy(alpha = 0.5f)))
                            Box(Modifier.width(scaledW).height(scaledH).offset(x = 2.dp, y = 2.dp).clip(coverShape)
                                .background(stackColor.copy(alpha = 0.75f)))
                            // 실제 앞표지는 공용 컴포넌트가 기본색과 어두운 책등을 함께 그립니다.
                            SketchbookCover(
                                modifier = Modifier.width(scaledW).height(scaledH).alpha(fade),
                                coverColor = stackColor,
                                coverImage = cover?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) },
                            ) {
                                if (book.shared) {
                                    Text("🤝", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp).background(Color(0x33000000), CircleShape).padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 가운데 표지 하나만의 정보 — 위: 스케치북 이름(큰 타이틀), 아래: 생성일·캔버스 사이즈·배경명
        // (작은 타이틀). 캐러셀이 넘어갈 때마다 centeredIndex가 바뀌면서 이 블록만 다시 그려진다.
        Spacer(Modifier.height(14.dp))
        Text(
            centeredBook?.name ?: "", fontSize = Dimens.Home.carouselTitleSp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            centeredBook?.let { b ->
                val bgLabel = Catalog.backgrounds.firstOrNull { it.key == b.bgKey }?.label ?: b.bgKey
                "${b.dateLabel} · ${b.size.label} · $bgLabel"
            } ?: "",
            fontSize = Dimens.Home.carouselSubtitleSp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        // 점 인디케이터 — 지금 몇 번째 스케치북인지 한눈에.
        if (books.size > 1) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(books.size) { i ->
                    val selected = i == centeredIndex
                    Box(
                        Modifier.size(if (selected) 8.dp else 5.dp).clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingsTab(nickname: String, avatarVersion: Int, theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
                        onSignOut: () -> Unit, onRename: (String) -> Unit, onSetAvatarImage: (Bitmap) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    var gesture2Tap by remember { mutableStateOf(session.twoFingerTapAction) }
    var gesture3Tap by remember { mutableStateOf(session.threeFingerTapAction) }
    var gestureLongPress by remember { mutableStateOf(session.longPressAction) }
    // 계정 이미지 — 갤러리에서 골라 512px로 다운샘플 디코드 후 저장(표지 사진 선택과 동일 패턴).
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeCoverBitmap(context, uri, 512) }
            bmp?.let(onSetAvatarImage)
        }
    }
    // 가로모드 3열(서브패널)은 이 탭에선 내용 없이 레이아웃만 — sidePanel 생략(기본값 null).
    MainTabPage(
        title = "Setting",
        modifier = Modifier.verticalScroll(rememberScrollState()),
        contentFillsRemaining = false,
        actions = {
            // 화면 테마 — 라이트/다크 토글 스위치(시안처럼). "시스템" 값이면 지금 실제로 보이는
            // 쪽(라이트/다크)을 스위치 상태로 보여주되, 건드리는 순간부터는 명시적 라이트/다크로
            // 고정된다(토글 하나로 3단계를 표현할 수 없어서 — 다시 "시스템 따라가기"로 되돌리는
            // 별도 진입점은 없음).
            val isDark = when (theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            Switch(
                checked = isDark,
                onCheckedChange = { onTheme(if (it) ThemeMode.DARK else ThemeMode.LIGHT) },
                thumbContent = {
                    Icon(
                        if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode, null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedIconColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) {
        SettingLabel("프로필")
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Avatar(avatarVersion, 56.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("별명", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(nickname, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("계정 이미지를 눌러 갤러리에서 선택", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "별명 수정") }
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingLabel("제스처")
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp)) {
                GestureActionRow("두 손가락 탭", gesture2Tap) { gesture2Tap = it; session.twoFingerTapAction = it }
                Spacer(Modifier.height(16.dp))
                GestureActionRow("세 손가락 탭", gesture3Tap) { gesture3Tap = it; session.threeFingerTapAction = it }
                Spacer(Modifier.height(16.dp))
                GestureActionRow("화면 길게 누르기", gestureLongPress) { gestureLongPress = it; session.longPressAction = it }
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingLabel("정보")
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🦆", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daymory", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("아날로그 감성 스케치북", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("v${com.g1.sketchbook.BuildConfig.VERSION_NAME}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small) { Text("로그아웃") }
    }

    if (editing) {
        var name by remember { mutableStateOf(nickname) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("별명 수정") },
            text = {
                OutlinedTextField(name, { name = it.take(16) }, singleLine = true,
                    label = { Text("별명") }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(name); editing = false }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

private fun gestureActionLabel(a: GestureAction) = when (a) {
    GestureAction.NONE -> "없음"
    GestureAction.UNDO -> "뒤로가기"
    GestureAction.REDO -> "앞으로가기"
    GestureAction.EYEDROP -> "색상 스포이드"
    GestureAction.TOGGLE_TOOLBARS -> "브러시바 최소화/펼치기"
}

private fun gestureActionIcon(a: GestureAction): ImageVector = when (a) {
    GestureAction.NONE -> Icons.Filled.Block
    GestureAction.UNDO -> Icons.AutoMirrored.Filled.Undo
    GestureAction.REDO -> Icons.AutoMirrored.Filled.Redo
    GestureAction.EYEDROP -> Icons.Filled.Colorize
    GestureAction.TOGGLE_TOOLBARS -> Icons.Filled.UnfoldLess
}

/** One gesture's mapping: a label plus a row of circular icon buttons (시안: Procreate 제스처 설정
 *  화면처럼 원형 버튼 + 아래 라벨, 선택된 것만 굵은 테두리). 5개가 좁은 화면에서 다 안 들어갈 수
 *  있어 가로 스크롤 허용(시안도 마지막 항목이 화면 밖으로 살짝 잘려 스크롤되는 형태였음). */
@Composable
private fun GestureActionRow(label: String, selected: GestureAction, onSelect: (GestureAction) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GestureAction.entries.forEach { a -> GestureCircleButton(a, selected == a) { onSelect(a) } }
        }
    }
}

@Composable
private fun GestureCircleButton(action: GestureAction, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(gestureActionIcon(action), gestureActionLabel(action), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            gestureActionLabel(action), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 계정 이미지 — 한 번이라도 갤러리에서 골랐으면 그 사진(원형 크롭), 아니면 로그인 실루엣
 *  아이콘(동그란 머리+몸통, [Icons.Filled.Person])을 기본값으로 보여준다. [avatarVersion]이
 *  바뀔 때만 파일을 다시 읽는다(스케치북 표지의 coverVersion과 같은 캐시무효화 패턴). */
@Composable
private fun Avatar(avatarVersion: Int, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(avatarVersion) { bmp = withContext(Dispatchers.IO) { SessionStore(context).loadAvatarImage() } }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), "계정 이미지", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Person, "기본 계정 이미지", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.7f))
        }
    }
}
