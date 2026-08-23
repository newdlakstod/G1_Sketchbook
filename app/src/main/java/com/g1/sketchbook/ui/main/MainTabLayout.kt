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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    Icons.Filled.Share,
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
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.Landscape.dividerWidth)
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
    modifier: Modifier = Modifier,
    contentSidePadding: Dp = Dimens.Screen.sideMargin,
    contentFillsRemaining: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    /** 가로모드 전용 3번째 열(서브 컨텐츠) — null이면(기본값) 빈 칸으로만 자리를 잡는다("레이아웃만
     *  갖추고" 내용은 비워두는 탭들이 이 기본값을 그대로 씀). 세로모드에서는 아예 안 쓰인다. */
    sidePanel: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(Modifier.fillMaxSize().then(modifier)) {
            MainTabPageBody(
                title = title, modifier = Modifier.weight(Dimens.Landscape.contentWeight).fillMaxHeight(),
                contentSidePadding = contentSidePadding, contentFillsRemaining = contentFillsRemaining,
                actions = actions, content = content,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.Landscape.dividerWidth)
            // 3열은 2열과 같은 배경 위에 surfaceVariant를 살짝 덧칠해서(면 전체, 안쪽 여백은 내용에만)
            // 미세한 명암차로 구분한다 — 시안처럼 진한 경계선 대신 톤 차이로만 나눔.
            Box(
                Modifier.weight(Dimens.Landscape.sidePanelWeight).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Dimens.Landscape.sidePanelTintAlpha))
                    .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin, end = Dimens.Screen.sideMargin, start = 16.dp),
            ) {
                sidePanel?.invoke()
            }
        }
    } else {
        MainTabPageBody(title, modifier, contentSidePadding, contentFillsRemaining, actions, content)
    }
}

@Composable
private fun MainTabPageBody(
    title: String,
    modifier: Modifier = Modifier,
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
            // 시안의 25dp 브랜드 영역을 고정해 오른쪽 버튼 유무가 타이틀을 밀지 않게 한다.
            MainTabHeader(Modifier.height(Dimens.Screen.headerHeight))
            Spacer(Modifier.height(Dimens.Screen.titleGap))
            Box(
                Modifier.fillMaxWidth().height(Dimens.Screen.titleAreaHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontFamily = Cavorting,
                    fontSize = Dimens.Screen.titleSp,
                    color = DaymoryTeal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 화면별 액션은 모두 타이틀 아래 60dp 영역의 오른쪽 아래에 놓는다.
            Box(Modifier.fillMaxWidth().height(Dimens.Screen.actionAreaHeight)) {
                // 각 화면의 IconButton은 손대지 않고, 여기서 터치 영역 기본값(48dp)만
                // Dimens.Screen.actionButtonSize로 한 번에 덮어써서 버튼 크기를 통일한다.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dimens.Screen.actionButtonSize) {
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        content = actions,
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Text(
            "Daymory",
            fontFamily = BodoniMTBlack,
            fontSize = 20.sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun SideNavRail(tab: Int, onTab: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().systemBarsPadding()
            .padding(vertical = Dimens.Screen.navBarPadding)
            .width(Dimens.Landscape.navWidth),
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
        Icon(icon, NavDescs[index], tint = tint, modifier = Modifier.size(26.dp)) // 버튼아이콘 사이즈
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
