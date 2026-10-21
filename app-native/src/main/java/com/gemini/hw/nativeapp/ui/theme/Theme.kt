package com.gemini.hw.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GeminiBlue = Color(0xFF4E88F5)
val DarkBackground = Color(0xFF131314)
val DarkSurface = Color(0xFF1E1F20)
val DarkSurfaceVariant = Color(0xFF282A2C)
val TextWhite = Color(0xFFE3E3E3)
val TextMuted = Color(0xFF9AA0A6)

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF0F4F9)
val LightSurfaceVariant = Color(0xFFE1E3E1)
val TextBlack = Color(0xFF1F1F1F)

private val DarkColorScheme = darkColorScheme(
    primary = GeminiBlue,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextMuted
)

private val LightColorScheme = lightColorScheme(
    primary = GeminiBlue,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onBackground = TextBlack,
    onSurface = TextBlack,
    onSurfaceVariant = Color(0xFF444746)
)

@Composable
fun GeminiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
