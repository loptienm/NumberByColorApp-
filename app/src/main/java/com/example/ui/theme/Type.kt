package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val FredokaFontFamily = FontFamily(
  Font(R.font.fredoka, FontWeight.Normal),
  Font(R.font.fredoka, FontWeight.Medium),
  Font(R.font.fredoka, FontWeight.SemiBold),
  Font(R.font.fredoka, FontWeight.Bold),
)

val NunitoFontFamily = FontFamily(
  Font(R.font.nunito, FontWeight.Normal),
  Font(R.font.nunito, FontWeight.SemiBold),
  Font(R.font.nunito, FontWeight.Bold),
  Font(R.font.nunito, FontWeight.ExtraBold),
)

val AppTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = FredokaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
      ),
  )
