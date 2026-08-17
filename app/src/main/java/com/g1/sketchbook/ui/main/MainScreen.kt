package com.g1.sketchbook.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.data.SessionStore
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
    val allBooks = previewBooks ?: remember(repo) { repo!!.list() }
    // 우상단 개인/공유 아이콘 버튼으로 노트를 전환해서 봄 — 스케치북 리스트 탭의 개인/공유받음
    // 필터와 같은 개념, 홈 캐러셀에도 적용.
    var showShared by remember { mutableStateOf(false) }
    val books = remember(allBooks, showShared) { allBooks.filter { it.shared == showShared } }
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
                HomeCarousel(books, repo, onOpenBook)
            }
        }
    }
}

/** Swipeable carousel of sketchbook covers — the focused (centred) one is largest, neighbours peek
 *  in smaller and dimmer on either side. Tapping any cover opens it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeCarousel(books: List<Sketchbook>, repo: SketchbookRepository?, onOpen: (String) -> Unit) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { books.size })
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // Side padding sized so the focused page gets its full spec width, with whatever room is left
    // over used to peek the neighbours (never negative, even on narrow phones).
    val peek = ((maxWidth - Dimens.Home.carouselCenterW) / 2).coerceAtLeast(20.dp)
    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = peek),
        pageSpacing = 16.dp,
        // 기본 fling 동작 그대로 — 빠르게 스와이프하면 여러 장을 계속 넘어가다가, 손가락으로 다시
        // 눌러야 멈춘다(한 장씩만 넘어가게 막았던 예전 pagerSnapDistance 제한을 없앴다).
    ) { page ->
        val distance = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
            .let { if (it < 0) -it else it }.coerceIn(0f, 1f)
        val w = androidx.compose.ui.unit.lerp(Dimens.Home.carouselCenterW, Dimens.Home.carouselSideW, distance)
        val h = androidx.compose.ui.unit.lerp(Dimens.Home.carouselCenterH, Dimens.Home.carouselSideH, distance)
        val fade = 1f - distance * 0.5f
        // Shadow strength follows proximity too — the focused cover "lifts forward", neighbours
        // recede — so the size shrink alone doesn't have to carry the whole depth illusion.
        val elevation = androidx.compose.ui.unit.lerp(18.dp, 4.dp, distance)
        val book = books[page]
        val coverShape = SketchbookCoverShape
        val titleSp = androidx.compose.ui.unit.lerp(Dimens.Home.coverTitleCenterSp, Dimens.Home.coverTitleSideSp, distance)
        val dateSp = androidx.compose.ui.unit.lerp(Dimens.Home.coverDateCenterSp, Dimens.Home.coverDateSideSp, distance)
        // 갤러리에서 고른 표지 이미지가 있으면 그걸, 없으면 (커스텀 지정 시) coverColor, 그것도
        // 없으면 기본색을 보여준다(목록탭 CoverCard와 동일). coverVersion을 키에 넣어야 같은 id라도
        // 표지 사진이 바뀌면 다시 읽어온다.
        var cover by remember(book.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(book.id, book.coverVersion, repo) { cover = withContext(Dispatchers.IO) { repo?.loadCoverThumb(book.id) } }
        val stackColor = book.coverColor?.let { Color(it) } ?: DefaultSketchbookCoverColor
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(fade).bounceClick { onOpen(book.id) }) {
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
                Spacer(Modifier.height(6.dp))
                Text(book.name, fontSize = titleSp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                Text(book.dateLabel, fontSize = dateSp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
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
                    Text("daymory", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
}

private fun gestureActionIcon(a: GestureAction): ImageVector = when (a) {
    GestureAction.NONE -> Icons.Filled.Block
    GestureAction.UNDO -> Icons.AutoMirrored.Filled.Undo
    GestureAction.REDO -> Icons.AutoMirrored.Filled.Redo
    GestureAction.EYEDROP -> Icons.Filled.Colorize
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
