package com.g1.sketchbook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g1.sketchbook.R

private val Navy = Color(0xFF1E3FAE)
private val Ivory = Color(0xFFF3ECD9)

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(horizontal = 36.dp, vertical = 40.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("G1", color = Ivory, fontSize = 68.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "SKETCHBOOK",
            color = Ivory,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )

        Image(
            painter = painterResource(R.drawable.mascot_duck),
            contentDescription = "G1 Sketchbook 오리 마스코트",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(top = 8.dp),
        )

        Spacer(Modifier.weight(1f))

        Text(
            "Draw together,\nkeep the little days.",
            color = Ivory,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(20.dp))

        if (busy) {
            CircularProgressIndicator(color = Ivory)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton("Log in", onClick = onSignIn)
                PillButton("Enter", onClick = onSignIn)
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = Color(0xFFFFC9BB), fontSize = 13.sp)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, Ivory),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ivory),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
