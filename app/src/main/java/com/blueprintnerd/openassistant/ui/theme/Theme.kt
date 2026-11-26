package com.blueprintnerd.openassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AssistantBlue = Color(0xFF4285F4)
val AssistantRed = Color(0xFFEA4335)
val AssistantYellow = Color(0xFFFBBC05)
val AssistantGreen = Color(0xFF34A853)

private val DarkColors = darkColorScheme(
    primary = AssistantBlue,
    secondary = AssistantGreen,
    tertiary = AssistantYellow,
    error = AssistantRed,
    background = Color(0xFF101216),
    surface = Color(0xFF181B22),
    surfaceVariant = Color(0xFF232833),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
)

private val LightColors = lightColorScheme(
    primary = AssistantBlue,
    secondary = AssistantGreen,
    tertiary = AssistantYellow,
    error = AssistantRed,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEF7),
    onBackground = Color(0xFF1F1F1F),
    onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF5F6368),
)

@Composable
fun OpenAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
