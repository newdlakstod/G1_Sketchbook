package com.g1.sketchbook.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

        Spacer(Modifier.height(8.dp))
        RunningDuck(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
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
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Ivory),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ivory),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * A hand-drawn-style running duck carrying a sketchbook, rendered as ivory line art on the navy
 * background — a code-drawn stand-in for the mascot. Swap in the real illustration (vector/PNG)
 * later if you have the source art; this keeps the start screen on-brand meanwhile.
 */
@Composable
private fun RunningDuck(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val sw = s * 0.012f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Virtual layout on a ~1.4:1 box, centered.
        val cx = w * 0.5f
        val cy = h * 0.52f
        val u = s * 0.5f // base unit

        fun p(x: Float, y: Float) = Offset(cx + x * u, cy + y * u)

        // Ground motion dashes
        drawLine(Ivory, p(-0.95f, 0.92f), p(-0.15f, 0.92f), sw, StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.05f, s * 0.05f)))
        drawLine(Ivory, p(0.35f, 0.92f), p(0.95f, 0.92f), sw, StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.05f, s * 0.05f)))

        // Body (rounded teardrop leaning right)
        val body = Path().apply {
            moveTo(p(-0.55f, 0.05f).x, p(-0.55f, 0.05f).y)
            cubicTo(p(-0.7f, -0.5f).x, p(-0.7f, -0.5f).y, p(0.1f, -0.7f).x, p(0.1f, -0.7f).y, p(0.35f, -0.25f).x, p(0.35f, -0.25f).y)
            cubicTo(p(0.55f, 0.05f).x, p(0.55f, 0.05f).y, p(0.35f, 0.5f).x, p(0.35f, 0.5f).y, p(-0.1f, 0.5f).x, p(-0.1f, 0.5f).y)
            cubicTo(p(-0.4f, 0.5f).x, p(-0.4f, 0.5f).y, p(-0.5f, 0.35f).x, p(-0.5f, 0.35f).y, p(-0.55f, 0.05f).x, p(-0.55f, 0.05f).y)
            close()
        }
        drawPath(body, Ivory, style = stroke)

        // Head
        drawCircle(Ivory, radius = u * 0.28f, center = p(0.5f, -0.55f), style = stroke)
        // Neck connectors
        drawLine(Ivory, p(0.28f, -0.4f), p(0.34f, -0.62f), sw, StrokeCap.Round)
        drawLine(Ivory, p(0.5f, -0.28f), p(0.62f, -0.42f), sw, StrokeCap.Round)
        // Beak
        val beak = Path().apply {
            moveTo(p(0.72f, -0.6f).x, p(0.72f, -0.6f).y)
            lineTo(p(1.0f, -0.52f).x, p(1.0f, -0.52f).y)
            lineTo(p(0.72f, -0.44f).x, p(0.72f, -0.44f).y)
        }
        drawPath(beak, Ivory, style = stroke)
        // Eye
        drawCircle(Ivory, radius = u * 0.035f, center = p(0.55f, -0.62f))

        // Wing
        val wing = Path().apply {
            moveTo(p(-0.35f, -0.15f).x, p(-0.35f, -0.15f).y)
            cubicTo(p(-0.1f, -0.35f).x, p(-0.1f, -0.35f).y, p(0.2f, -0.25f).x, p(0.2f, -0.25f).y, p(0.1f, 0.1f).x, p(0.1f, 0.1f).y)
        }
        drawPath(wing, Ivory, style = stroke)

        // Sketchbook held under the wing (spiral notebook)
        val book = Path().apply {
            moveTo(p(-0.15f, -0.05f).x, p(-0.15f, -0.05f).y)
            lineTo(p(0.35f, 0.05f).x, p(0.35f, 0.05f).y)
            lineTo(p(0.28f, 0.4f).x, p(0.28f, 0.4f).y)
            lineTo(p(-0.22f, 0.3f).x, p(-0.22f, 0.3f).y)
            close()
        }
        drawPath(book, Ivory, style = stroke)
        // spiral binding
        for (i in 0..3) {
            val t = i / 3f
            val a = p(-0.15f + t * 0.5f, -0.05f + t * 0.1f)
            val b = p(-0.17f + t * 0.5f, -0.12f + t * 0.1f)
            drawLine(Ivory, a, b, sw * 0.8f, StrokeCap.Round)
        }

        // Legs / running feet
        drawLine(Ivory, p(-0.2f, 0.5f), p(-0.35f, 0.9f), sw, StrokeCap.Round)
        drawLine(Ivory, p(-0.35f, 0.9f), p(-0.5f, 0.9f), sw, StrokeCap.Round)
        drawLine(Ivory, p(-0.35f, 0.9f), p(-0.28f, 0.92f), sw, StrokeCap.Round)
        drawLine(Ivory, p(0.05f, 0.5f), p(0.25f, 0.82f), sw, StrokeCap.Round)
        drawLine(Ivory, p(0.25f, 0.82f), p(0.42f, 0.82f), sw, StrokeCap.Round)
        drawLine(Ivory, p(0.25f, 0.82f), p(0.3f, 0.86f), sw, StrokeCap.Round)
    }
}
