package com.g1.sketchbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun NicknameDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // 취소 동작 조절: 바깥 영역 터치와 뒤로가기도 취소 버튼과 같이 로그인 화면으로 돌아간다.
    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.width(280.dp), // 팝업 좌우 폭 조절: 숫자가 작을수록 좁아진다.
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    placeholder = {
                        Text(
                            "별명을 입력해주세요.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    // pill 입력창 조절: 내부는 투명하고 테두리만 남기며 안내문은 위의 alpha 값으로 조절한다.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) { Text("취소") }
                    TextButton(
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                    ) { Text("확인") }
                }
            }
        }
    }
}
