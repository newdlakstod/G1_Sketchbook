package com.g1.sketchbook.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
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
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FloatingNavBar(tab, onTab) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(nickname, avatar, onOpenBook, onGoSketchbooks = { onTab(1) })
                1 -> com.g1.sketchbook.sketchbook.SketchbookTab(nickname, myUid, onOpenBook)
                2 -> com.g1.sketchbook.diary.DiaryCalendarScreen(onOpenDiary)
                else -> SettingsTab(nickname, avatar, theme, onTheme, onSignOut, onRename, onSetAvatar)
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
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) }, contentAlignment = Alignment.Center) {
                    if (selected) {
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                            modifier = Modifier.size(58.dp).offset(y = (-18).dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, descs[i], tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
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
) {
    val ctx = LocalContext.current
    val repo = remember { SketchbookRepository(ctx) }
    val books = remember { repo.list() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("안녕하세요,", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("$nickname 님", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Avatar(avatar, 48.dp)
        }
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth().clickable { onGoSketchbooks() }, shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🦆", fontSize = 40.sp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("스케치북 열기", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("새로 만들거나 이어서 그려요", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 스케치북", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (books.isNotEmpty()) TextButton(onClick = onGoSketchbooks) { Text("전체 보기") }
        }
        Spacer(Modifier.height(6.dp))
        if (books.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp)) {
                    Text("아직 스케치북이 없어요", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("위 카드를 눌러 첫 스케치북을 만들어보세요.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(books.take(8)) { i, b ->
                    MiniCover(b, CoverColors[i % CoverColors.size]) { onOpenBook(b.id) }
                }
            }
        }
    }
}

@Composable
private fun MiniCover(book: Sketchbook, cover: Color, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable { onClick() }) {
        Box(Modifier.width(96.dp).aspectRatio(0.78f)
            .background(cover, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))) {
            Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.7f).align(Alignment.Center))
            if (book.shared) {
                Text("🤝", fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd)
                    .padding(5.dp).background(Color(0x33000000), CircleShape).padding(horizontal = 3.dp, vertical = 1.dp))
            }
        }
        Text(book.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(nickname: String, avatar: String, theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
                        onSignOut: () -> Unit, onRename: (String) -> Unit, onSetAvatar: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var avatarEditing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("설정", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
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

@Composable
private fun Avatar(emoji: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji.ifBlank { "🦆" }, fontSize = (size.value * 0.52f).sp)
    }
}
