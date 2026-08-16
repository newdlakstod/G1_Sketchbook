package com.g1.sketchbook.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.ui.bounceClick
import com.g1.sketchbook.ui.theme.BodoniMTBlack
import com.g1.sketchbook.ui.theme.Cavorting
import com.g1.sketchbook.ui.theme.DaymoryTeal
import com.g1.sketchbook.ui.theme.Dimens
import com.g1.sketchbook.ui.theme.Pretendard

private val NavIcons = listOf(
    Icons.Filled.Home,
    Icons.Filled.Book,
    Icons.Filled.Groups,
    Icons.Filled.CalendarMonth,
    Icons.Filled.Settings,
)
private val NavLabels = listOf("Home", "List", "share", "Diary", "Other")
private val NavDescs = listOf("홈", "스케치북", "공유", "일기", "설정")

@Composable
internal fun MainTabLayout(
    tab: Int,
    onTab: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 메인 탭 전체 틀 조절: 가로 화면은 왼쪽 탭바, 세로 화면은 아래 탭바를 사용한다.
    if (landscape) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SideNavRail(tab, onTab)
            Box(
                Modifier.weight(1f).fillMaxHeight().systemBarsPadding().padding(end = 4.dp),
            ) { content() }
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

@Composable
fun MainTabPage(
    title: String,
    avatar: String,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    contentGap: Dp = Dimens.Screen.contentGap,
    contentSidePadding: Dp = Dimens.Screen.sideMargin,
    contentFillsRemaining: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // 공통 위치 조절: 헤더·제목·본문 여백은 Dimens.Screen 값에서 바꾼다.
    Column(
        Modifier.fillMaxSize().then(modifier)
            .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin),
    ) {
        Column(Modifier.padding(horizontal = Dimens.Screen.sideMargin)) {
            MainTabHeader(avatar, onAvatar, showAvatar = showAvatar, actions = actions)
            Spacer(Modifier.height(Dimens.Screen.titleGap))
            Text(
                title,
                fontFamily = Cavorting,
                fontSize = Dimens.Screen.titleSp,
                color = DaymoryTeal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(contentGap))
        val bodyModifier = Modifier.fillMaxWidth().padding(horizontal = contentSidePadding)
        if (contentFillsRemaining) {
            Column(bodyModifier.weight(1f), content = content)
        } else {
            Column(bodyModifier, content = content)
        }
    }
}

@Composable
private fun MainTabHeader(
    avatar: String,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        if (showAvatar) {
            Box(
                Modifier.align(Alignment.CenterStart).size(32.dp).bounceClick(onClick = onAvatar),
            ) { HeaderAvatar(avatar, 32.dp) }
        }
        Text(
            "daymory",
            fontFamily = BodoniMTBlack,
            fontSize = 20.sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = actions,
        )
    }
}

@Composable
private fun HeaderAvatar(emoji: String, size: Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji.ifBlank { "🦆" }, fontSize = (size.value * 0.52f).sp)
    }
}

@Composable
private fun SideNavRail(tab: Int, onTab: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().systemBarsPadding()
            .padding(vertical = Dimens.Screen.navBarPadding)
            .width(Dimens.Screen.navItemSize + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavIcons.forEachIndexed { index, icon ->
            NavigationItem(index, tab, icon, onTab)
        }
    }
}

@Composable
private fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = Dimens.Screen.sideMargin, vertical = Dimens.Screen.navBarPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavIcons.forEachIndexed { index, icon ->
            NavigationItem(index, tab, icon, onTab)
        }
    }
}

@Composable
private fun NavigationItem(
    index: Int,
    selectedTab: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onTab: (Int) -> Unit,
) {
    val selected = index == selectedTab
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(Dimens.Screen.navItemSize).bounceClick { onTab(index) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, NavDescs[index], tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            NavLabels[index],
            color = tint,
            fontFamily = Pretendard,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
