# Main Tab Shared Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the five main tabs' shared frame into `MainTabLayout.kt` while preserving each tab's content and behavior.

**Architecture:** `MainTabLayout` becomes the adaptive portrait/landscape navigation shell. `MainTabPage` becomes the common header/title/margin column, while feature files retain only their screen-specific bodies and state.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Gradle Plugin

## Global Constraints

- Preserve callbacks, Preview data, local/Firebase access, copy, colors, and tab behavior.
- Preserve portrait bottom navigation and landscape side navigation.
- Keep feature-specific body sizing in the existing feature file or matching `Dimens` group.
- Add Korean comments beside shared position and spacing controls.
- Do not modify unrelated dirty files beyond the exact overlapping Compose sections.

---

### Task 1: Extract the adaptive navigation shell

**Files:**
- Create: `app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`

**Interfaces:**
- Produces: `@Composable internal fun MainTabLayout(tab: Int, onTab: (Int) -> Unit, content: @Composable () -> Unit)`
- Consumes: current tab index, tab callback, and selected tab content

- [ ] **Step 1: Run a failing structural assertion**

```powershell
if (Test-Path 'app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt') { exit 0 }
exit 1
```

Expected: exit code 1 because the shared layout file does not exist.

- [ ] **Step 2: Create the adaptive shell**

Move `NavIcons`, `NavLabels`, `NavDescs`, `SideNavRail`, and `FloatingNavBar` from `MainScreen.kt` into `MainTabLayout.kt`, then add:

```kotlin
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
            Box(Modifier.weight(1f).fillMaxHeight().systemBarsPadding().padding(end = 4.dp)) { content() }
        }
    } else {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { FloatingNavBar(tab, onTab) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) { content() }
        }
    }
}
```

Replace the orientation branch in `MainScreen` with `MainTabLayout(tab, onTab) { content() }`.

- [ ] **Step 3: Run the structural assertion again**

Expected: exit code 0.

- [ ] **Step 4: Compile**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the shell extraction**

```powershell
git add -- app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt
git commit -m "refactor: extract main tab navigation layout"
```

---

### Task 2: Extract the shared page header and spacing

**Files:**
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt`
- Modify: `app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt`

**Interfaces:**
- Produces: `@Composable fun MainTabPage(title: String, avatar: String, onAvatar: () -> Unit, modifier: Modifier = Modifier, showAvatar: Boolean = false, contentGap: Dp = Dimens.Screen.contentGap, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit)`
- Consumes: title, avatar callback, optional header actions, and screen-specific body

- [ ] **Step 1: Run a failing source assertion**

```powershell
$files = @(
  'app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt',
  'app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt',
  'app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt'
)
$count = ($files | ForEach-Object { (Select-String -LiteralPath $_ -Pattern 'MainTabPage\(' -AllMatches).Matches.Count } | Measure-Object -Sum).Sum
if ($count -ge 4) { exit 0 }
exit 1
```

Expected: exit code 1 because the tab bodies still duplicate their frame.

- [ ] **Step 2: Add the shared page composable**

Move `TabHeader` to `MainTabLayout.kt` and add:

```kotlin
@Composable
fun MainTabPage(
    title: String,
    avatar: String,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    contentGap: Dp = Dimens.Screen.contentGap,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // 공통 위치 조절: 헤더·제목·본문의 여백은 Dimens.Screen 값에서 바꾼다.
    Column(modifier.fillMaxSize().padding(horizontal = Dimens.Screen.sideMargin)
        .padding(top = Dimens.Screen.topMargin, bottom = Dimens.Screen.bottomMargin)) {
        TabHeader(avatar, onAvatar, showAvatar = showAvatar, actions = actions)
        Spacer(Modifier.height(Dimens.Screen.titleGap))
        Text(title, fontFamily = Cavorting, fontSize = Dimens.Screen.titleSp, color = DaymoryTeal,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(contentGap))
        content()
    }
}
```

- [ ] **Step 3: Route all tab bodies through `MainTabPage`**

Use these exact titles and gaps:

```kotlin
MainTabPage("Draw your time", avatar, onGoSettings, actions = {
    Switch(checked = showShared, onCheckedChange = { showShared = it }, modifier = Modifier.scale(0.7f))
})
MainTabPage(if (showShared) "Draw together" else "Sketchbook list", avatar, onGoSettings, contentGap = 10.dp, actions = {
    if (showShared) {
        IconButton(onClick = onNewShared) { Icon(Icons.Filled.Groups, "공유 스케치북 만들기") }
        IconButton(onClick = onJoinShared) { Icon(Icons.AutoMirrored.Filled.Login, "공유 스케치북 참여") }
    }
})
MainTabPage("A piece of today", avatar, onGoSettings, contentGap = Dimens.Calendar.topTitleGap)
MainTabPage("Setting", avatar, onAvatar = { avatarEditing = true }, modifier = Modifier.verticalScroll(rememberScrollState()))
```

Remove the sketchbook list's nested `Scaffold` and keep its filter/grid body inside the shared page.

- [ ] **Step 4: Run the source assertion and compile**

Run the Step 1 command, then:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
```

Expected: source assertion exits 0 and Gradle prints `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the shared page extraction**

```powershell
git add -- app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt
git commit -m "refactor: share main tab page layout"
```

---

### Task 3: Verify all main tab previews and document the result

**Files:**
- Verify: `app/src/main/java/com/g1/sketchbook/preview/MainTabPreviews.kt`
- Modify: `PROGRESS.md`

**Interfaces:**
- Consumes: five existing `MainScreen` preview states
- Produces: verified shared layout and updated handoff record

- [ ] **Step 1: Confirm all five Preview entries remain present**

```powershell
$s = Get-Content -LiteralPath 'app/src/main/java/com/g1/sketchbook/preview/MainTabPreviews.kt' -Raw
@('05 Home','06 Personal sketchbooks','07 Shared sketchbooks','08 Diary','09 Settings') | ForEach-Object {
    if (-not $s.Contains($_)) { exit 1 }
}
exit 0
```

Expected: exit code 0.

- [ ] **Step 2: Refresh `MainTabPreviews.kt` in Android Studio**

Confirm that all five previews retain aligned headers, titles, margins, and bottom navigation and that the Home switch, Shared create/join actions, diary controls, and Settings scrolling remain visible.

- [ ] **Step 3: Update `PROGRESS.md`**

Add a dated `Done` entry naming `MainTabLayout.kt`, the shared shell/page responsibilities, and the preserved feature bodies.

- [ ] **Step 4: Run final verification**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat compileDebugKotlin testDebugUnitTest --console=plain
git diff --check -- app/src/main/java/com/g1/sketchbook/ui/main/MainTabLayout.kt app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt app/src/main/java/com/g1/sketchbook/diary/DiaryScreens.kt PROGRESS.md
```

Expected: `BUILD SUCCESSFUL` and no whitespace errors.

- [ ] **Step 5: Commit the handoff update**

```powershell
git add -- PROGRESS.md
git commit -m "docs: record shared main tab layout"
```
