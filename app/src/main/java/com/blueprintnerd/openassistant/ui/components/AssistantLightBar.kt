package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.AssistantRed
import com.blueprintnerd.openassistant.ui.theme.AssistantYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * 1:1 Google Assistant edge-to-edge glowing 4-color bottom light bar.
 */
@Composable
fun AssistantGlowingEdgeBar(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant_glow_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glow_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val w = size.width
            val h = size.height
            val active = isListening || isThinking
            val energy = when {
                isListening -> (0.45f + amplitude * 0.55f).coerceIn(0.45f, 1f)
                isThinking -> 0.7f
                else -> 0.35f
            }

            // Soft upward ambient glow matching Google Assistant's bottom edge glow
            val glowHeight = h * (0.55f + 0.45f * energy)
            val segWidth = w / 4f
            colors.forEachIndexed { i, color ->
                val wave = if (active) sin(phase + i * 1.1f) * 0.18f else 0f
                val cx = segWidth * i + segWidth * (0.5f + wave)
                val radius = segWidth * (0.85f + 0.25f * energy)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.48f * energy),
                            color.copy(alpha = 0.14f * energy),
                            Color.Transparent,
                        ),
                        center = Offset(cx, h),
                        radius = radius,
                    ),
                    topLeft = Offset(0f, h - glowHeight),
                    size = Size(w, glowHeight),
                )
            }

            // Crisp 4-segment bottom light strip
            val barH = if (active) (4.dp.toPx() + 3.dp.toPx() * energy) else 4.dp.toPx()
            val top = h - barH

            // Smooth undulating segment widths when listening
            val weights = FloatArray(4) { i ->
                if (active) 1f + 0.28f * sin(phase + i * 1.4f) * energy else 1f
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
 * Perfect circle with the 4-color Google gradient (matches the app icon).
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

/**
 * Iconic 4-dot Google Assistant emblem (used in the overlay header).
 */
@Composable
fun AssistantFourDotsMark(
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        // Blue large dot
        drawCircle(
            color = AssistantBlue,
            radius = s * 0.26f,
            center = Offset(s * 0.30f, s * 0.42f),
        )
        // Red medium-small dot
        drawCircle(
            color = AssistantRed,
            radius = s * 0.13f,
            center = Offset(s * 0.68f, s * 0.30f),
        )
        // Yellow medium dot
        drawCircle(
            color = AssistantYellow,
            radius = s * 0.16f,
            center = Offset(s * 0.68f, s * 0.64f),
        )
        // Green tiny dot
        drawCircle(
            color = AssistantGreen,
            radius = s * 0.08f,
            center = Offset(s * 0.88f, s * 0.44f),
        )
    }
}

/**
 * Animated 4-bar equalizer dots when actively listening or processing.
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { idx, color ->
            Canvas(modifier = Modifier.size(width = 10.dp, height = 28.dp)) {
                val wave = sin(phase + idx * 0.9f)
                val barHeight = when {
                    isListening -> {
                        val dynamic = (0.35f + amplitude * 0.65f) * (0.65f + 0.35f * wave)
                        (this.size.height * dynamic).coerceIn(8f, this.size.height)
                    }
                    isThinking -> {
                        val pulse = 0.4f + 0.3f * wave
                        (this.size.height * pulse).coerceIn(8f, this.size.height)
                    }
                    else -> 8f
                }
                val top = (this.size.height - barHeight) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset((this.size.width - 8f) / 2f, top),
                    size = Size(8f, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
    }
}
