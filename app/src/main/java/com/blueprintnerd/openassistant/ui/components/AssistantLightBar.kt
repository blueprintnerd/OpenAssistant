package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.AssistantRed
import com.blueprintnerd.openassistant.ui.theme.AssistantYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Crisp 4-color Google Assistant bottom bar that ONLY appears when listening/talking.
 */
@Composable
fun AssistantGlowingEdgeBar(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val active = isListening || isThinking
    val transition = rememberInfiniteTransition(label = "assistant_glow_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glow_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(150)) + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            val w = size.width
            val h = size.height
            val energy = if (isListening) (0.5f + amplitude * 0.5f).coerceIn(0.5f, 1f) else 0.7f
            val barH = (3.dp.toPx() + 3.dp.toPx() * energy).coerceAtMost(h)
            val top = h - barH

            val weights = FloatArray(4) { i ->
                1f + 0.30f * sin(phase + i * 1.4f) * energy
            }
            val weightSum = weights.sum()
            var x = 0f
            colors.forEachIndexed { i, color ->
                val segW = w * (weights[i] / weightSum)
                drawRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(segW + 1f, barH),
                )
                x += segW
            }
        }
    }
}

/**
 * Animated 4-dot / 4-bar equalizer inside the mic plate when active.
 */
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
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { idx, color ->
            Canvas(modifier = Modifier.size(width = 6.dp, height = 24.dp)) {
                val wave = sin(phase + idx * 0.9f)
                val barHeight = when {
                    isListening -> {
                        val dynamic = (0.35f + amplitude * 0.65f) * (0.65f + 0.35f * wave)
                        (size.height * dynamic).coerceIn(6f, size.height)
                    }
                    isThinking -> {
                        val pulse = 0.4f + 0.3f * wave
                        (size.height * pulse).coerceIn(6f, size.height)
                    }
                    else -> 6f
                }
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset((size.width - 5f) / 2f, top),
                    size = Size(5f, barHeight),
                    cornerRadius = CornerRadius(2.5f, 2.5f),
                )
            }
        }
    }
}

/**
 * Perfect circle with the 4-color Google gradient.
 */
@Composable
fun GoogleGradientCircleIcon(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val brush = Brush.linearGradient(
            colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen),
            start = Offset(0f, 0f),
            end = Offset(this.size.width, this.size.height),
        )
        drawCircle(brush = brush)
    }
}
