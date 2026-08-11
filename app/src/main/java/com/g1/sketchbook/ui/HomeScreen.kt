package com.g1.sketchbook.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.data.model.SketchbookRef
import com.g1.sketchbook.ui.theme.paperTexture

private val SpineColors = listOf(
    Color(0xFF2B4C9B), Color(0xFF7E9A52), Color(0xFFDE7F3C),
    Color(0xFFE0B23C), Color(0xFFCE7A7A), Color(0xFF5B8A8C),
)

@Composable
fun HomeScreen(
    userName: String,
    busy: Boolean,
    error: String?,
    sketchbooks: List<SketchbookRef>,
    onOpenSketchbook: (SketchbookRef) -> Unit,
    onRemoveSketchbook: (String) -> Unit,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SketchbookRef?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paperTexture()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        // Greeting + avatar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("안녕하세요,", fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("$userName 님", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Avatar(userName)
        }

        Spacer(Modifier.height(24.dp))

        // Action icons: New / Join
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ActionButton(
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                label = "새로 만들기",
                container = MaterialTheme.colorScheme.primary,
                onClick = { showNew = true },
            )
            ActionButton(
                icon = { Icon(Icons.Filled.Key, contentDescription = null) },
                label = "코드로 참여",
                container = MaterialTheme.colorScheme.tertiary,
                onClick = { showJoin = true },
            )
        }

        Spacer(Modifier.height(28.dp))

        Text("내 스케치북", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (sketchbooks.isEmpty()) {
            EmptyBooks()
        } else {
            sketchbooks.forEachIndexed { i, book ->
                SketchbookCard(
                    book = book,
                    spine = SpineColors[i % SpineColors.size],
                    onOpen = { onOpenSketchbook(book) },
                    onDelete = { pendingDelete = book },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onSignOut) { Text("로그아웃") }
        }
    }

    if (showNew) {
        InputDialog(
            title = "새 스케치북 만들기",
            label = "이름 (예: 우리 둘의 낙서장)",
            confirmText = "만들기",
            busy = busy,
            transform = { it },
            keyboard = KeyboardOptions.Default,
            onConfirm = { onCreateRoom(it); showNew = false },
            onDismiss = { showNew = false },
        )
    }
    if (showJoin) {
        InputDialog(
            title = "코드로 참여하기",
            label = "6자리 코드",
            confirmText = "참여하기",
            busy = busy,
            transform = { it.uppercase() },
            keyboard = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
            ),
            onConfirm = { onJoinRoom(it); showJoin = false },
            onDismiss = { showJoin = false },
        )
    }
    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("목록에서 제거") },
            text = { Text("‘${book.name}’ 을(를) 목록에서 지울까요?\n그림은 지워지지 않고, 코드로 다시 참여할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { onRemoveSketchbook(book.id); pendingDelete = null }) {
                    Text("제거")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    container: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            contentColor = Color.White,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SketchbookCard(
    book: SketchbookRef,
    spine: Color,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Notebook spine
            Box(
                Modifier
                    .width(10.dp)
                    .height(64.dp)
                    .background(spine, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(vertical = 14.dp).weight(1f)) {
                Text(book.name.ifBlank { "우리 스케치북" },
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("코드 ${book.id}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "목록에서 제거",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyBooks() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🦆", fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text("아직 스케치북이 없어요.", fontWeight = FontWeight.Bold)
            Text("위의 ‘새로 만들기’ 또는 ‘코드로 참여’로 시작하세요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifBlank { "?" },
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
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
                value = value,
                onValueChange = { value = transform(it) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = keyboard,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = !busy && value.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
