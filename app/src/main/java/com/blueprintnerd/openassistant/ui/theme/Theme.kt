package com.blueprintnerd.openassistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val AssistantBlue = Color(0xFF4285F4)
val AssistantRed = Color(0xFFEA4335)
val AssistantYellow = Color(0xFFFBBC05)
val AssistantGreen = Color(0xFF34A853)

private val DarkColors = darkColorScheme(
    primary = AssistantBlue,
    secondary = AssistantGreen,
    tertiary = AssistantYellow,
    error = AssistantRed,
    background = Color(0xFF131314),
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF2B2D30),
    onBackground = Color(0xFFE3E3E3),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFA8ABB0),
)

private val LightColors = lightColorScheme(
    primary = AssistantBlue,
    secondary = AssistantGreen,
    tertiary = AssistantYellow,
    error = AssistantRed,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F4),
    onBackground = Color(0xFF1F1F1F),
    onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF5F6368),
)

@Composable
fun OpenAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
