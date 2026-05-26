package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    secondary = BronzeSecondary,
    tertiary = AmberTertiary,
    background = DeepDarkBackground,
    surface = SurfaceDarkCard,
    onPrimary = DeepDarkBackground,
    onSecondary = DeepDarkBackground,
    onTertiary = DeepDarkBackground,
    onBackground = TextCreamWhite,
    onSurface = TextCreamWhite,
    surfaceVariant = SurfaceLighterCard,
    onSurfaceVariant = TextMutedGray
)

// Simple placeholder for Light Scheme fallback
private val ColorPrimaryLight = androidx.compose.ui.graphics.Color(0xFFF9F9F9)

private val LightColorScheme = lightColorScheme(
    primary = BronzeSecondary,
    secondary = GoldPrimary,
    tertiary = AmberTertiary,
    background = TextCreamWhite,
    surface = ColorPrimaryLight, // Fallback placeholder
    onPrimary = TextCreamWhite,
    onSecondary = DeepDarkBackground,
    onTertiary = DeepDarkBackground,
    onBackground = DeepDarkBackground,
    onSurface = DeepDarkBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep consistent premium feel by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Force dark theme for a gorgeous luxurious dark deck!

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
