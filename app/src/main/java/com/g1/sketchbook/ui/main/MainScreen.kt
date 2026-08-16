package com.g1.sketchbook.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.brush.GestureAction
import com.g1.sketchbook.data.SessionStore
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.CoverColors
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.Pretendard
import com.g1.sketchbook.ui.theme.ThemeMode

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
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Home's 새 노트/공유/참여 buttons jump to the Sketchbook tab with the wizard pre-opened.
    var pendingWizardType by remember { mutableStateOf<com.g1.sketchbook.sketchbook.WType?>(null) }
    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> HomeTab(
                nickname, avatar, onOpenBook, onGoSketchbooks = { onTab(1) },
                onNewBook = { pendingWizardType = com.g1.sketchbook.sketchbook.WType.PERSONAL; onTab(1) },
                onNewShared = { pendingWizardType = com.g1.sketchbook.sketchbook.WType.SHARED_NEW; onTab(2) },
                onJoinShared = { pendingWizardType = com.g1.sketchbook.sketchbook.WType.SHARED_JOIN; onTab(2) },
                onGoSettings = { onTab(4) },
            )
            1 -> com.g1.sketchbook.sketchbook.SketchbookTab(nickname, avatar, myUid, onOpenBook,
                onGoSettings = { onTab(4) },
                openWizardAs = pendingWizardType, onWizardOpened = { pendingWizardType = null })
            2 -> com.g1.sketchbook.sketchbook.SketchbookTab(nickname, avatar, myUid, onOpenBook,
                initialShowShared = true, onGoSettings = { onTab(4) },
                openWizardAs = pendingWizardType, onWizardOpened = { pendingWizardType = null })
            3 -> com.g1.sketchbook.diary.DiaryCalendarScreen(avatar, onOpenDiary, onOpenCalendar, onGoSettings = { onTab(4) })
            else -> SettingsTab(nickname, avatar, theme, onTheme, onSignOut, onRename, onSetAvatar)
        }
    }
    if (landscape) {
        // Landscape: navigation rail on the left, content fills the rest.
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SideNavRail(tab, onTab)
            Box(Modifier.weight(1f).fillMaxHeight().systemBarsPadding().padding(end = 4.dp)) { content() }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { FloatingNavBar(tab, onTab) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) { content() }
        }
    }
}

private val NavIcons = listOf(Icons.Filled.Home, Icons.Filled.Book, Icons.Filled.Groups, Icons.Filled.CalendarMonth, Icons.Filled.Settings)
private val NavLabels = listOf("Home", "List", "share", "Diary", "Other")
private val NavDescs = listOf("홈", "스케치북", "공유", "일기", "설정")

/** Landscape side rail; mirrors the flat bottom bar — icon+label stacked, colour-only selection. */
@Composable
private fun SideNavRail(tab: Int, onTab: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().systemBarsPadding().padding(vertical = Dimens.Screen.navBarPadding).width(Dimens.Screen.navItemSize + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavIcons.forEachIndexed { i, icon ->
            val selected = i == tab
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.width(Dimens.Screen.navItemSize).bounceClick { onTab(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, NavDescs[i], tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(NavLabels[i], color = tint, fontFamily = Pretendard, fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Flat bottom nav bar — icon + label per tab, no pill/background; the active tab is coloured. */
@Composable
private fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = Dimens.Screen.sideMargin, vertical = Dimens.Screen.navBarPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavIcons.forEachIndexed { i, icon ->
            val selected = i == tab
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.width(Dimens.Screen.navItemSize).bounceClick { onTab(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, NavDescs[i], tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(NavLabels[i], color = tint, fontFamily = Pretendard, fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** 탭 공통 상단 헤더 — 왼쪽 아바타, 가운데 "daymory" 워드마크(Pretendard Bold), 오른쪽은 화면별
 *  액션 슬롯(비어있어도 무방). 5개 탭 모두 이 헤더를 공유하고, 그 아래 각자의 태그라인이 이어진다.
 *  좌우 여백은 호출부의 Column이 이미 Dimens.Screen.sideMargin으로 잡아준다고 가정(중복 방지). */
@Composable
fun TabHeader(avatar: String, onAvatar: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.CenterStart).size(32.dp).bounceClick(onClick = onAvatar)) { Avatar(avatar, 32.dp) }
        Text("daymory", fontFamily = com.g1.sketchbook.ui.theme.BodoniMTBlack, fontSize = 20.sp,
            color = com.g1.sketchbook.ui.theme.DaymoryTeal, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

@Composable
private fun HomeTab(
    nickname: String,
    avatar: String,
    onOpenBook: (String) -> Unit,
    onGoSketchbooks: () -> Unit,
    onNewBook: () -> Unit,
    onNewShared: () -> Unit,
    onJoinShared: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember { SketchbookRepository(ctx) }
    val allBooks = remember { repo.list() }
    // 우상단 토글로 개인/공유 노트를 전환해서 봄(시안: "공유노트인지, 개인노트인지 우상단 토글
    // 버튼을 통해 확인") — 스케치북 리스트 탭의 개인/공유받음 필터와 같은 개념, 홈 캐러셀에도 적용.
    var showShared by remember { mutableStateOf(false) }
    val books = remember(allBooks, showShared) { allBooks.filter { it.shared == showShared } }
    Column(Modifier.fillMaxSize()
        .padding(top = Dimens.Screen.topMargin, start = Dimens.Screen.sideMargin, end = Dimens.Screen.sideMargin)) {
        TabHeader(avatar, onAvatar = onGoSettings) {
            IconButton(onClick = { showShared = !showShared }) {
                Icon(
                    if (showShared) Icons.Filled.Groups else Icons.Filled.Person,
                    if (showShared) "공유 노트 보는 중 · 개인 노트로 전환" else "개인 노트 보는 중 · 공유 노트로 전환",
                )
            }
        }
        Spacer(Modifier.height(Dimens.Screen.titleGap))
        Text("Draw your time", fontFamily = com.g1.sketchbook.ui.theme.Cavorting, fontSize = Dimens.Screen.titleSp,
            color = com.g1.sketchbook.ui.theme.DaymoryTeal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.Screen.contentGap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HomeActionIcon(Icons.Filled.Add, "새 노트 추가", onNewBook)
            Spacer(Modifier.width(14.dp))
            HomeActionIcon(Icons.Filled.Share, "공유 스케치북 만들기", onNewShared)
            Spacer(Modifier.width(14.dp))
            HomeActionIcon(Icons.AutoMirrored.Filled.Login, "공유 스케치북 참여", onJoinShared)
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (books.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (showShared) "아직 공유받은 스케치북이 없어요" else "아직 스케치북이 없어요",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("위 + 버튼으로 첫 스케치북을 만들어보세요.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                HomeCarousel(books, onOpenBook)
            }
        }
        Spacer(Modifier.height(Dimens.Screen.bottomMargin))
    }
}

@Composable
private fun HomeActionIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Dimens.Home.actionIcon).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Swipeable carousel of sketchbook covers — the focused (centred) one is largest, neighbours peek
 *  in smaller and dimmer on either side. Tapping any cover opens it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeCarousel(books: List<Sketchbook>, onOpen: (String) -> Unit) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { books.size })
    // Cap each swipe to at most one page regardless of flick speed — a fast flick used to be able to
    // fly through several covers in a row; touching down still cancels any in-flight fling as usual.
    val flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(1),
    )
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // Side padding sized so the focused page gets its full spec width, with whatever room is left
    // over used to peek the neighbours (never negative, even on narrow phones).
    val peek = ((maxWidth - Dimens.Home.carouselCenterW) / 2).coerceAtLeast(20.dp)
    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = peek),
        pageSpacing = 16.dp,
        flingBehavior = flingBehavior,
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
        val coverShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp)
        val titleSp = androidx.compose.ui.unit.lerp(Dimens.Home.coverTitleCenterSp, Dimens.Home.coverTitleSideSp, distance)
        val dateSp = androidx.compose.ui.unit.lerp(Dimens.Home.coverDateCenterSp, Dimens.Home.coverDateSideSp, distance)
        val cover = CoverColors[page % CoverColors.size]
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(fade).bounceClick { onOpen(book.id) }) {
                Box(Modifier.width(w + 4.dp).height(h + 4.dp)) {
                    // 책처럼 두께감 있게 — 표지 뒤로 살짝 어긋난 종이 스택 2겹.
                    Box(Modifier.width(w).height(h).offset(x = 4.dp, y = 4.dp).clip(coverShape).background(cover.copy(alpha = 0.5f)))
                    Box(Modifier.width(w).height(h).offset(x = 2.dp, y = 2.dp).clip(coverShape).background(cover.copy(alpha = 0.75f)))
                    Box(Modifier.width(w).height(h)
                        .shadow(elevation, coverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(coverShape)
                        .background(cover)) {
                        Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(0.6f).align(Alignment.Center))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(nickname: String, avatar: String, theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
                        onSignOut: () -> Unit, onRename: (String) -> Unit, onSetAvatar: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var avatarEditing by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    if (showDev) { DevPreviewScreen(onBack = { showDev = false }); return }
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var gesture2Tap by remember { mutableStateOf(session.twoFingerTapAction) }
    var gesture3Tap by remember { mutableStateOf(session.threeFingerTapAction) }
    var gestureLongPress by remember { mutableStateOf(session.longPressAction) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin,
            start = Dimens.Screen.sideMargin, end = Dimens.Screen.sideMargin)) {
        TabHeader(avatar, onAvatar = { avatarEditing = true })
        Spacer(Modifier.height(Dimens.Screen.titleGap))
        Text("Setting", fontFamily = com.g1.sketchbook.ui.theme.Cavorting, fontSize = Dimens.Screen.titleSp,
            color = com.g1.sketchbook.ui.theme.DaymoryTeal, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.Screen.contentGap))
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
        Spacer(Modifier.height(18.dp))
        SettingLabel("개발자")
        OutlinedButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small) { Text("폰트 · 사이즈 미리보기") }
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

/** Dev-only: how Pretendard/Cavorting look at 10–120sp (step 5) and how squares look 10–200dp. */
@Composable
private fun DevPreviewScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .systemBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Create, "뒤로") }
            Text("폰트 · 사이즈 미리보기", fontFamily = Pretendard, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Text("Pretendard (10–120sp, 5단위)", fontFamily = Pretendard, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        for (s in 10..120 step 5) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s}sp", fontFamily = Pretendard, fontSize = 12.sp, modifier = Modifier.width(44.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("다람쥐 Ag 12", fontFamily = Pretendard, fontSize = s.sp, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Cavorting (10–120sp, 5단위)", fontFamily = Pretendard, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        for (s in 10..120 step 5) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s}sp", fontFamily = Pretendard, fontSize = 12.sp, modifier = Modifier.width(44.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("August 12", fontFamily = Cavorting, fontSize = s.sp, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("정사각형 (10–200dp)", fontFamily = Pretendard, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (d in 10..200 step 10) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(d.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text("${d}dp", fontFamily = Pretendard, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
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

/** One gesture's mapping: a label plus a chip row of the four possible actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureActionRow(label: String, selected: GestureAction, onSelect: (GestureAction) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        // 4개 칩("색상 스포이드" 포함)이 좁은 화면 폭을 넘어설 수 있어 가로 스크롤 허용.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GestureAction.entries.forEach { a ->
                FilterChip(selected == a, { onSelect(a) }, label = { Text(gestureActionLabel(a)) })
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
