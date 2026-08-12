package com.g1.sketchbook.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.g1.sketchbook.brush.BrushPlaygroundScreen
import com.g1.sketchbook.sketchbook.Sketchbook
import com.g1.sketchbook.sketchbook.SketchbookRepository
import com.g1.sketchbook.ui.theme.ThemeMode

private val CoverColors = listOf(
    Color(0xFF2B4C9B), Color(0xFF7E9A52), Color(0xFFDE7F3C),
    Color(0xFFE0B23C), Color(0xFFCE7A7A), Color(0xFF5B8A8C),
)

@Composable
fun MainScreen(
    nickname: String,
    avatar: String,
    tab: Int,
    theme: ThemeMode,
    onTab: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onRename: (String) -> Unit,
    onSetAvatar: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavItem(tab, 0, Icons.Filled.Book, "스케치북", onTab)
                NavItem(tab, 1, Icons.Filled.Create, "그림일기", onTab)
                NavItem(tab, 2, Icons.Filled.Home, "홈", onTab)
                NavItem(tab, 3, Icons.Filled.CalendarMonth, "일기달력", onTab)
                NavItem(tab, 4, Icons.Filled.Settings, "설정", onTab)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> com.g1.sketchbook.sketchbook.SketchbookTab(onOpenBook)
                1 -> com.g1.sketchbook.diary.DiaryScreen()
                2 -> HomeTab(nickname, avatar) { onTab(0) }
                3 -> com.g1.sketchbook.diary.DiaryCalendarScreen()
                else -> SettingsTab(nickname, avatar, theme, onTheme, onSignOut, onRename, onSetAvatar)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    current: Int, index: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onTab: (Int) -> Unit,
) {
    NavigationBarItem(
        selected = current == index,
        onClick = { onTab(index) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 11.sp) },
    )
}

@Composable
private fun HomeTab(nickname: String, avatar: String, onGoSketchbooks: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SketchbookRepository(ctx) }
    val books = remember { repo.list() }
    var showPlayground by remember { mutableStateOf(false) }
    if (showPlayground) {
        BackHandler { showPlayground = false }
        BrushPlaygroundScreen()
        return
    }
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
                Column {
                    Text("함께 쓰는 스케치북", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("탭하여 스케치북 만들기 · 이어 그리기", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("최근 스케치북", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        if (books.isEmpty()) {
            Text("아직 없어요. 스케치북 탭에서 만들어보세요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(books.take(8)) { i, b ->
                    MiniCover(b, CoverColors[i % CoverColors.size], onGoSketchbooks)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { showPlayground = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small) { Text("🖌  브러시 놀이터 열기") }
    }
}

@Composable
private fun MiniCover(book: Sketchbook, cover: Color, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable { onClick() }) {
        Box(Modifier.width(96.dp).aspectRatio(0.78f)
            .background(cover, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp))) {
            Image(painterResource(R.drawable.mascot_duck), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.7f).align(Alignment.Center))
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
        Spacer(Modifier.height(16.dp))
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
private fun Avatar(emoji: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji.ifBlank { "🦆" }, fontSize = (size.value * 0.52f).sp)
    }
}
