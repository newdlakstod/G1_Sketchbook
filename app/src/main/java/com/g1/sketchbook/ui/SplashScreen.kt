package com.g1.sketchbook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.g1.sketchbook.R

private val Navy = Color(0xFF1E3FAE)
private val Ivory = Color(0xFFF3ECD9)

@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Navy),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("G1", color = Ivory, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
        Text("SKETCHBOOK", color = Ivory, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Image(
            painter = painterResource(R.drawable.mascot_duck),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Draw together, keep the little days.",
            color = Ivory.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}
