package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.AssistantRed
import com.blueprintnerd.openassistant.ui.theme.AssistantYellow
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AssistantFourColorBar(
    amplitude: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bar_phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val w = size.width
        val h = size.height
        val boost = if (isListening) (0.35f + amplitude * 0.65f).coerceIn(0.35f, 1f) else 0.45f
        val barH = h * boost
        val top = (h - barH) / 2f

        val shift = if (isListening) (sin(phase) * w * 0.08f) else 0f
        val brush = Brush.horizontalGradient(
            colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen),
            startX = shift,
            endX = w + shift,
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(0f, top),
            size = Size(w, barH),
            cornerRadius = CornerRadius(barH / 2f, barH / 2f),
        )
    }
}

@Composable
fun AssistantListeningDots(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant_dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { idx, color ->
            Canvas(modifier = Modifier.size(width = 14.dp, height = 34.dp)) {
                val wave = sin(phase + idx * 0.85f)
                val barHeight = when {
                    isListening -> {
                        val dynamic = (0.35f + amplitude * 0.65f) * (0.65f + 0.35f * wave)
                        (size.height * dynamic).coerceIn(10f, size.height)
                    }
                    isThinking -> {
                        val pulse = 0.4f + 0.3f * wave
                        (size.height * pulse).coerceIn(10f, size.height)
                    }
                    else -> 12f
                }
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset((size.width - 10f) / 2f, top),
                    size = Size(10f, barHeight),
                    cornerRadius = CornerRadius(5f, 5f),
                )
            }
        }
    }
}
