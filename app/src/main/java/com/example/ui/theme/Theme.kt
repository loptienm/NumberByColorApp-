package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorPopColorScheme =
  lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoDeep,
    secondary = MarigoldAccent,
    onSecondary = Color.White,
    secondaryContainer = MarigoldSoft,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = BerryAccent,
    onTertiary = Color.White,
    tertiaryContainer = BerrySoft,
    onTertiaryContainer = Color(0xFF831843),
    background = WarmCream,
    onBackground = SlateInk,
    surface = Color.White,
    onSurface = SlateInk,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SlateMuted,
    outline = BorderSoft,
    error = CoralError,
    onError = Color.White,
  )

private val AppShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun ColorPopTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ColorPopColorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
