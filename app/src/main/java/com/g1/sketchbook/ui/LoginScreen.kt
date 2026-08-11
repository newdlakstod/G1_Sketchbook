package com.g1.sketchbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.ui.theme.Navy

private val Ivory = Color(0xFFF6EFDF)

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🦆", fontSize = 64.sp)
            }
            Spacer(Modifier.height(28.dp))
            Text("G1", color = Ivory, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "SKETCHBOOK",
                color = Ivory,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "함께 그리고, 소중한 하루하루를 담아요.",
                color = Ivory.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Draw together, keep the little days.",
                color = Ivory.copy(alpha = 0.55f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(52.dp))

            if (busy) {
                CircularProgressIndicator(color = Ivory)
            } else {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ivory,
                        contentColor = Navy,
                    ),
                ) {
                    Text("Google로 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFFC9BB), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
