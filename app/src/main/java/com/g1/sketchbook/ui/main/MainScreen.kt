package com.g1.sketchbook.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    nickname: String,
    avatar: String,
    tab: Int,
    theme: ThemeMode,
    myUid: String,
    onTab: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onRename: (String) -> Unit,
    onSetAvatar: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenDiary: (String) -> Unit,
    onOpenCalendar: (Int, Int) -> Unit,
    previewBooks: List<Sketchbook>? = null,
    previewDiaryDates: Set<String>? = null,
) {
    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> HomeTab(
                onOpenBook = onOpenBook,
                previewBooks = previewBooks,
            )
            1 -> com.g1.sketchbook.sketchbook.SketchbookTab(
                nickname = nickname, myUid = myUid, onOpenBook = onOpenBook,
                previewBooks = previewBooks,
            )
            2 -> com.g1.sketchbook.sketchbook.SketchbookTab(
                nickname = nickname, myUid = myUid, onOpenBook = onOpenBook,
                initialShowShared = true, previewBooks = previewBooks,
            )
            3 -> com.g1.sketchbook.diary.DiaryCalendarScreen(
                onOpenDiary = onOpenDiary,
                onOpenCalendar = onOpenCalendar,
                previewMarkedDates = previewDiaryDates,
            )
            else -> SettingsTab(nickname, avatar, theme, onTheme, onSignOut, onRename, onSetAvatar)
        }
    }
    MainTabLayout(tab, onTab) { content() }
}

@Composable
private fun HomeTab(
    onOpenBook: (String) -> Unit,
    previewBooks: List<Sketchbook>? = null,
) {
    val context = LocalContext.current
    val repo = if (previewBooks == null) remember(context) { SketchbookRepository(context) } else null
    var refresh by remember { mutableStateOf(0) }
    val allBooks = previewBooks ?: remember(repo, refresh) { repo!!.list() }
    // 우상단 개인/공유 아이콘 버튼으로 노트를 전환해서 봄 — 스케치북 리스트 탭의 개인/공유받음
    // 필터와 같은 개념, 홈 캐러셀에도 적용.
    var showShared by remember { mutableStateOf(false) }
    val books = remember(allBooks, showShared) { allBooks.filter { it.shared == showShared } }
    // 표지 길게 눌러 수정 — 목록탭(SketchbookListScreen)의 CoverCard와 같은 다이얼로그를 재사용.
    var editing by remember { mutableStateOf<Sketchbook?>(null) }
    var pendingDelete by remember { mutableStateOf<Sketchbook?>(null) }
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
    ) {
        // 홈 캐러셀은 공통 헤더 여백과 달리 화면 양쪽 끝까지 사용한다.
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
                TextButton(onClick = { repo?.delete(target.id); refresh++; pendingDelete = null }) {
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
                repo?.rename(current.id, name)
                if (newCover != null) repo?.saveCover(current.id, newCover) else if (removeCover) repo?.removeCover(current.id)
                repo?.setCoverColor(current.id, newColor)
                refresh++; editing = null
            },
            onToggleFav = { repo?.toggleFav(current.id); refresh++ },
            onDelete = { editing = null; pendingDelete = current },
        )
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
            LazyRow(
                state = listState,
                flingBehavior = snapFling,
                modifier = Modifier.fillMaxSize(),
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
                    // Shadow strength follows proximity too — the focused cover "lifts forward", neighbours
                    // recede — so the size shrink alone doesn't have to carry the whole depth illusion.
                    // 12dp로 상한을 낮춤(예전 18dp) — scale/alpha가 만드는 그래픽 레이어는 자기 레이아웃
                    // 크기 밖으로는 그림자를 못 그리는데(아래 shadowSlack 참고), 너무 큰 elevation은
                    // slack을 넉넉히 줘도 여전히 잘릴 수 있어 값 자체도 같이 낮췄다(2026-08-20).
                    val elevation = androidx.compose.ui.unit.lerp(12.dp, 4.dp, distance)
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
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        // scale()/alpha()는 graphicsLayer(오프스크린 레이어)를 만드는데, 그 레이어는 이
                        // Box 자신의 레이아웃 크기로 딱 잘려서 그려진다 — 안쪽 SketchbookCover의 그림자가
                        // w/h 밖으로 번져도 이 바깥 상자 크기(shadowSlack 없이는 겨우 +4dp) 밖으로는 못
                        // 나가 잘렸다. shadowSlack만큼 여유를 주고, 원래 스택 겹침 비주얼은 안쪽 상자에
                        // 그대로 둔 채 가운데 정렬해 넣는다(2026-08-20).
                        val shadowSlack = 16.dp
                        Box(
                            Modifier.width(w + 4.dp + shadowSlack).height(h + 4.dp + shadowSlack)
                                .scale(scale).alpha(fade)
                                .bounceClick(onLongClick = { onLongPress(book) }) { onOpen(book.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(w + 4.dp).height(h + 4.dp)) {
                                // 책처럼 두께감 있게 — 표지 뒤로 살짝 어긋난 종이 스택 2겹.
                                Box(Modifier.width(w).height(h).offset(x = 4.dp, y = 4.dp).clip(coverShape)
                                    .background(stackColor.copy(alpha = 0.5f)))
                                Box(Modifier.width(w).height(h).offset(x = 2.dp, y = 2.dp).clip(coverShape)
                                    .background(stackColor.copy(alpha = 0.75f)))
                                // 실제 앞표지는 공용 컴포넌트가 기본색과 어두운 책등을 함께 그립니다.
                                SketchbookCover(
                                    modifier = Modifier.width(w).height(h)
                                        .shadow(elevation, coverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black),
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
private fun SettingsTab(nickname: String, avatar: String, theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
                        onSignOut: () -> Unit, onRename: (String) -> Unit, onSetAvatar: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var avatarEditing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var gesture2Tap by remember { mutableStateOf(session.twoFingerTapAction) }
    var gesture3Tap by remember { mutableStateOf(session.threeFingerTapAction) }
    var gestureLongPress by remember { mutableStateOf(session.longPressAction) }
    MainTabPage(
        title = "Setting",
        modifier = Modifier.verticalScroll(rememberScrollState()),
        contentFillsRemaining = false,
    ) {
        SettingLabel("프로필")
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { avatarEditing = true }) { Avatar(avatar, 56.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("별명", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(nickname, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("아바타를 눌러 변경", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "별명 수정") }
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingLabel("화면")
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp)) {
                Text("화면 테마", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(theme == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) }, label = { Text("시스템") })
                    FilterChip(theme == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) }, label = { Text("라이트") })
                    FilterChip(theme == ThemeMode.DARK, { onTheme(ThemeMode.DARK) }, label = { Text("다크") })
                }
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

    if (avatarEditing) {
        AlertDialog(
            onDismissRequest = { avatarEditing = false },
            title = { Text("아바타 선택") },
            text = {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🦆", "🐱", "🐸", "🐰", "🐻", "🐥", "🐨", "🦊", "🐼", "🐧", "🐤", "🐢").forEach { e ->
                        Box(Modifier.size(46.dp).clip(CircleShape)
                            .background(if (e == avatar) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSetAvatar(e); avatarEditing = false },
                            contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { avatarEditing = false }) { Text("닫기") } },
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

/** One gesture's mapping: a label plus a chip row of the four possible actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureActionRow(label: String, selected: GestureAction, onSelect: (GestureAction) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GestureAction.entries.forEach { a ->
                FilterChip(
                    selected == a, { onSelect(a) },
                    label = { Icon(gestureActionIcon(a), gestureActionLabel(a), modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun Avatar(emoji: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji.ifBlank { "🦆" }, fontSize = (size.value * 0.52f).sp)
    }
}
