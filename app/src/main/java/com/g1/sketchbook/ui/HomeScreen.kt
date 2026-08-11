package com.g1.sketchbook.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.data.model.ArchiveEntry
import com.g1.sketchbook.data.model.SketchbookRef
import com.g1.sketchbook.ui.theme.paperTexture

private val SpineColors = listOf(
    Color(0xFF2B4C9B), Color(0xFF7E9A52), Color(0xFFDE7F3C),
    Color(0xFFE0B23C), Color(0xFFCE7A7A), Color(0xFF5B8A8C),
)

@Composable
fun HomeScreen(
    userName: String,
    userEmail: String,
    busy: Boolean,
    error: String?,
    sketchbooks: List<SketchbookRef>,
    recentEntry: ArchiveEntry?,
    onOpenSketchbook: (SketchbookRef) -> Unit,
    onRemoveSketchbook: (String) -> Unit,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SketchbookRef?>(null) }

    val recent = sketchbooks.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("홈") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Book, null) }, label = { Text("스케치북") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("내 정보") },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .paperTexture(strength = 0.5f),
        ) {
            when (tab) {
                0 -> HomeTab(
                    userName = userName,
                    recent = recent,
                    recentEntry = recentEntry,
                    onOpenRecent = { recent?.let(onOpenSketchbook) },
                    onNew = { showNew = true },
                    onJoin = { showJoin = true },
                )
                1 -> SketchbooksTab(
                    sketchbooks = sketchbooks,
                    onOpen = onOpenSketchbook,
                    onDelete = { pendingDelete = it },
                    onNew = { showNew = true },
                )
                else -> AccountTab(userName, userEmail, onSignOut)
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    if (showNew) {
        InputDialog(
            title = "새 스케치북 만들기", label = "이름 (예: 우리 둘의 낙서장)",
            confirmText = "만들기", busy = busy, transform = { it }, keyboard = KeyboardOptions.Default,
            onConfirm = { onCreateRoom(it); showNew = false }, onDismiss = { showNew = false },
        )
    }
    if (showJoin) {
        InputDialog(
            title = "코드로 참여하기", label = "6자리 코드",
            confirmText = "참여하기", busy = busy, transform = { it.uppercase() },
            keyboard = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii,
            ),
            onConfirm = { onJoinRoom(it); showJoin = false }, onDismiss = { showJoin = false },
        )
    }
    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("목록에서 제거") },
            text = { Text("‘${book.name}’ 을(를) 목록에서 지울까요?\n그림은 지워지지 않고, 코드로 다시 참여할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { onRemoveSketchbook(book.id); pendingDelete = null }) { Text("제거") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun HomeTab(
    userName: String,
    recent: SketchbookRef?,
    recentEntry: ArchiveEntry?,
    onOpenRecent: () -> Unit,
    onNew: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🐱", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("안녕하세요,", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("$userName 님", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Avatar(userName)
        }

        Spacer(Modifier.height(20.dp))

        RecentCard(recent = recent, entry = recentEntry, onClick = onOpenRecent)

        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(Icons.Filled.MailOutline, "참여", size = 56.dp,
                container = MaterialTheme.colorScheme.surface,
                content = MaterialTheme.colorScheme.onSurface, onClick = onJoin)
            CircleAction(Icons.Filled.Create, "이어 그리기", size = 68.dp,
                container = MaterialTheme.colorScheme.primary,
                content = Color.White, onClick = { if (recent != null) onOpenRecent() else onNew() })
            CircleAction(Icons.Filled.Add, "새로", size = 56.dp,
                container = MaterialTheme.colorScheme.surface,
                content = MaterialTheme.colorScheme.onSurface, onClick = onNew)
        }
    }
}

@Composable
private fun RecentCard(recent: SketchbookRef?, entry: ArchiveEntry?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                recent?.name?.ifBlank { "우리 스케치북" } ?: "최근 스케치북",
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            val image = remember(entry?.image) { entry?.image?.let(::decodeBase64Image) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .background(Color(0xFFFBF6EA), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(0.dp),
                    )
                } else {
                    Text(
                        if (recent == null) "아직 스케치북이 없어요" else "아직 저장된 그림이 없어요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
                // live dot
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .size(10.dp).background(Color(0xFF51CF66), CircleShape),
                )
            }
            if (entry != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    relativeTime(entry.savedAt),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun SketchbooksTab(
    sketchbooks: List<SketchbookRef>,
    onOpen: (SketchbookRef) -> Unit,
    onDelete: (SketchbookRef) -> Unit,
    onNew: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("스케치북", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "새 스케치북") }
        }
        Spacer(Modifier.height(12.dp))
        if (sketchbooks.isEmpty()) {
            Text("아직 스케치북이 없어요. + 로 만들어보세요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            sketchbooks.forEachIndexed { i, book ->
                SketchbookCard(book, SpineColors[i % SpineColors.size], { onOpen(book) }, { onDelete(book) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AccountTab(userName: String, userEmail: String, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(userName.trim().take(1).ifBlank { "?" },
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(userName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (userEmail.isNotBlank()) {
            Text(userEmail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small) { Text("로그아웃") }
    }
}

// --- small pieces ---

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = container, contentColor = content,
            modifier = Modifier.size(size), shadowElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SketchbookCard(book: SketchbookRef, spine: Color, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(64.dp)
                .background(spine, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(vertical = 14.dp).weight(1f)) {
                Text(book.name.ifBlank { "우리 스케치북" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("코드 ${book.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "제거", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).ifBlank { "?" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    confirmText: String,
    busy: Boolean,
    transform: (String) -> String,
    keyboard: KeyboardOptions,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = transform(it) },
                label = { Text(label) }, singleLine = true, keyboardOptions = keyboard,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = !busy && value.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun decodeBase64Image(data: String): ImageBitmap? {
    if (data.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

private fun relativeTime(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "방금"
        diff < 3_600_000L -> "${diff / 60_000L}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000L}시간 전"
        else -> "${diff / 86_400_000L}일 전"
    }
}
