package com.stralarp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    secondary = NeonGreen,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = OnDark,
    onSurface = OnDark,
    surfaceVariant = CardDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00C853),
    onPrimary = Color.White,
    secondary = Color(0xFF00C853),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEEEEEE)
)

@Composable
fun StralarpTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
