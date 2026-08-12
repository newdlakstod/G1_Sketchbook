package com.g1.sketchbook.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.brush.BrushPlaygroundScreen
import com.g1.sketchbook.ui.theme.ThemeMode

@Composable
fun MainScreen(
    nickname: String,
    tab: Int,
    theme: ThemeMode,
    onTab: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
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
                0 -> com.g1.sketchbook.sketchbook.SketchbookTab()
                1 -> com.g1.sketchbook.diary.DiaryScreen()
                2 -> HomeTab(nickname)
                3 -> com.g1.sketchbook.diary.DiaryCalendarScreen()
                else -> SettingsTab(nickname, theme, onTheme, onSignOut)
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
private fun HomeTab(nickname: String) {
    var showPlayground by remember { mutableStateOf(false) }
    if (showPlayground) {
        BackHandler { showPlayground = false }
        BrushPlaygroundScreen()
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("안녕하세요,", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("$nickname 님", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(nickname.take(1).ifBlank { "?" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🦆", fontSize = 40.sp)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("함께 쓰는 스케치북", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("새 스케치북 만들기 · 코드로 참여 (다음 단계)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { showPlayground = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small) { Text("🖌  브러시 놀이터 열기") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(nickname: String, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("설정", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp)) {
                Text("계정", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("별명: $nickname", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🦆", fontSize = 36.sp)
                Spacer(Modifier.height(10.dp))
                Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
