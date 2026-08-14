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
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.Surface
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
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.Pretendard
import com.g1.sketchbook.ui.theme.ThemeMode

private val CoverColors = listOf(
    Color(0xFF1E2D4C), Color(0xFF6E8266), Color(0xFF9C8C82),
    Color(0xFF4F6E6A), Color(0xFFB79A94), Color(0xFF7C8A76),
)

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
                onNewShared = { pendingWizardType = com.g1.sketchbook.sketchbook.WType.SHARED_NEW; onTab(1) },
                onJoinShared = { pendingWizardType = com.g1.sketchbook.sketchbook.WType.SHARED_JOIN; onTab(1) },
            )
            1 -> com.g1.sketchbook.sketchbook.SketchbookTab(nickname, myUid, onOpenBook,
                openWizardAs = pendingWizardType, onWizardOpened = { pendingWizardType = null })
            2 -> com.g1.sketchbook.diary.DiaryCalendarScreen(onOpenDiary, onOpenCalendar)
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

/** Landscape side rail; mirrors the floating bottom bar — selected item pops out to the right. */
@Composable
private fun SideNavRail(tab: Int, onTab: (Int) -> Unit) {
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Book, Icons.Filled.CalendarMonth, Icons.Filled.Settings)
    val descs = listOf("홈", "스케치북", "일기", "설정")
    Box(Modifier.fillMaxHeight().systemBarsPadding().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.height(288.dp).width(86.dp)) {
            Surface(
                shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(60.dp),
            ) {}
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                icons.forEachIndexed { i, icon ->
                    val selected = i == tab
                    Box(Modifier.weight(1f).fillMaxWidth().bounceClick { onTab(i) }, contentAlignment = Alignment.Center) {
                        if (selected) {
                            Surface(
                                shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp,
                                modifier = Modifier.size(56.dp).offset(x = 18.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, descs[i], tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(27.dp))
                                }
                            }
                        } else {
                            Icon(icon, descs[i], tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Floating pill nav bar; the selected item rises into a white circle (icons only, no labels). */
@Composable
private fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit) {
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Book, Icons.Filled.CalendarMonth, Icons.Filled.Settings)
    val descs = listOf("홈", "스케치북", "일기", "설정")
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 10.dp).height(86.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(62.dp),
        ) {}
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.forEachIndexed { i, icon ->
                val selected = i == tab
                Box(Modifier.weight(1f).fillMaxHeight().bounceClick { onTab(i) }, contentAlignment = Alignment.Center) {
                    if (selected) {
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp,
                            modifier = Modifier.size(58.dp).offset(y = (-18).dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, descs[i], tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else {
                        Icon(icon, descs[i], tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
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
) {
    val ctx = LocalContext.current
    val repo = remember { SketchbookRepository(ctx) }
    val books = remember { repo.list() }
    Column(Modifier.fillMaxSize().padding(top = Dimens.Screen.topMargin)) {
        Text("Draw your time", fontFamily = com.g1.sketchbook.ui.theme.Cavorting, fontSize = Dimens.Screen.titleSp,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Dimens.Home.titleToIconGap))
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
                    Text("아직 스케치북이 없어요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
        val fade = 1f - distance * 0.45f
        val book = books[page]
        val coverShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(fade).bounceClick { onOpen(book.id) }) {
                Box(Modifier.width(w).height(h)
                    .shadow(12.dp, coverShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(coverShape)
                    .background(CoverColors[page % CoverColors.size])) {
                    Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.6f).align(Alignment.Center))
                    if (book.shared) {
                        Text("🤝", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopEnd)
                            .padding(8.dp).background(Color(0x33000000), CircleShape).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(book.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
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
        .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin, start = 20.dp, end = 20.dp)) {
        Text("Setting", fontFamily = com.g1.sketchbook.ui.theme.Cavorting, fontSize = Dimens.Screen.titleSp,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
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
                    Text("G1 Sketchbook", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
