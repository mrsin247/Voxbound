package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VoxDarkColorScheme = darkColorScheme(
    primary = BurnishedAmberDark,
    onPrimary = Color(0xFF3A1A00),
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = SlateSageDark,
    onSecondary = Color(0xFF0C2A27),
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = Color(0xFFCDE7E2),
    tertiary = AntiqueBrassDark,
    onTertiary = Color(0xFF3A2900),
    background = CharcoalInk,
    onBackground = InkPrimaryDark,
    surface = CharcoalInk,
    onSurface = InkPrimaryDark,
    surfaceVariant = ElevatedWalnut,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerLow = ElevatedWalnut,
    surfaceContainer = SmokedOak,
    surfaceContainerHigh = Color(0xFF372E27),
    outline = Color(0xFF6B5E53),
    outlineVariant = Color(0xFF3B322B),
    error = ErrorTerracottaDark
)

private val VoxLightColorScheme = lightColorScheme(
    primary = BurnishedAmberLight,
    onPrimary = Color.White,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = Color(0xFF381900),
    secondary = SlateSageLight,
    onSecondary = Color.White,
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = Color(0xFF0F2B28),
    tertiary = AntiqueBrassLight,
    onTertiary = Color.White,
    background = PaperCream,
    onBackground = InkPrimaryLight,
    surface = PaperCream,
    onSurface = InkPrimaryLight,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerLow = WarmParchment,
    surfaceContainer = SoftLinen,
    surfaceContainerHigh = Color(0xFFE0D4C2),
    outline = Color(0xFF8C7E71),
    outlineVariant = Color(0xFFD9CFC0),
    error = ErrorTerracotta
)

@Composable
fun VoxBoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoxDarkColorScheme else VoxLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoxBoundTypography,
        content = content
    )
}
