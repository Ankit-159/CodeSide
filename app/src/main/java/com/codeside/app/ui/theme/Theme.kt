package com.codeside.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06202B),
    secondary = AccentAlt,
    onSecondary = Color(0xFF1D1633),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkSoft,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextMuted,
    outline = Outline,
    error = Danger,
    onError = Color.White,
)

@Composable
fun CodeSideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
