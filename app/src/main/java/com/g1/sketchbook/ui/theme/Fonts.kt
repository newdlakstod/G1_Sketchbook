package com.g1.sketchbook.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.g1.sketchbook.R

/** Playful hand-drawn display font (Cavorting) used for calendar / diary headings. */
val Cavorting = FontFamily(Font(R.font.cavorting))

/** Clean UI sans (Pretendard) — Korean + Latin, used for body/UI text and dev spec labels. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** Bold display serif (Bodoni MT Black) — used only for the "daymory" wordmark in the tab header. */
val BodoniMTBlack = FontFamily(Font(R.font.bodoni_mt_black))
